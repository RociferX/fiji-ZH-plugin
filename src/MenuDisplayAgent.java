package org.fiji.zh;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.swing.Timer;

/** Translates native AWT menu peers while leaving the Java menu model intact. */
public final class MenuDisplayAgent {
    private static final Map<String, String> dictionary = new LinkedHashMap<>();
    private static final Map<MenuItem, Stamp> stamps = new WeakHashMap<>();
    private static Field peerField;
    private static Field targetField;
    private static Field parentField;
    private static Field itemsField;
    private static Field popupsField;
    private static Method setLabel;
    private static Path dictionaryPath;
    private static Path exportPath;
    private static String lastInventory = "";
    private static String lastSecondary = "";
    private static Set<MenuItem> liveItems;
    private static MenuBar mainMenuBar;
    private static ToolbarHints toolbarHints;
    private static DialogDisplayMapper dialogs;
    private static volatile boolean enabled = true;
    private static boolean failed;
    private static long dictionaryModified = -1;

    public static void premain(String options, Instrumentation instrumentation) {
        try {
            Path base = Paths.get(System.getProperty("fiji.zh.home", ".")).toAbsolutePath().normalize();
            Path fontConfig = base.resolve("locales/fontconfig.zh.properties");
            if (Files.exists(fontConfig)) System.setProperty("sun.awt.fontconfig", fontConfig.toString());
            Module desktop = MenuItem.class.getModule();
            Set<Module> self = Set.of(MenuDisplayAgent.class.getModule());
            instrumentation.redefineModule(desktop, Set.of(), Map.of("java.awt.peer", self),
                Map.of("java.awt", self, "sun.awt.windows", self), Set.of(), Map.of());
            peerField = MenuComponent.class.getDeclaredField("peer");
            peerField.setAccessible(true);
            parentField = MenuComponent.class.getDeclaredField("parent");
            parentField.setAccessible(true);
            itemsField = Menu.class.getDeclaredField("items");
            itemsField.setAccessible(true);
            popupsField = Component.class.getDeclaredField("popups");
            popupsField.setAccessible(true);
            targetField = Class.forName("sun.awt.windows.WObjectPeer", false, null).getDeclaredField("target");
            targetField.setAccessible(true);
            setLabel = Class.forName("java.awt.peer.MenuItemPeer").getMethod("setLabel", String.class);
            dictionaryPath = base.resolve("locales/menus.zh_CN.tsv");
            exportPath = base.resolve("build/menu-inventory.tsv");
            enabled = !"en".equals(System.getProperty("fiji.zh.language"));
            instrumentation.addTransformer(new DisplayTextPainter.Transformer());
            EventQueue.invokeLater(() -> {
                toolbarHints = new ToolbarHints(base);
                try { dialogs = new DialogDisplayMapper(base); }
                catch (Exception exception) { System.err.println("[Fiji 中文] Dialog mapping unavailable: " + exception); }
                Timer timer = new Timer(500, event -> refresh());
                timer.setInitialDelay(0);
                timer.start();
            });
            System.out.println("[Fiji 中文] Native menu display mapping enabled; Java labels and commands stay original.");
        } catch (Exception exception) {
            System.err.println("[Fiji 中文] Mapping unavailable: " + exception);
        }
    }

    public static void setEnabled(boolean translate) {
        EventQueue.invokeLater(() -> {
            enabled = translate;
            refresh();
        });
    }

    public static boolean isActive() { return dictionaryPath != null && !failed; }
    public static boolean isEnabled() { return enabled; }

    private static void reload() throws IOException {
        if (!Files.exists(dictionaryPath)) return;
        long modified = Files.getLastModifiedTime(dictionaryPath).toMillis();
        if (modified == dictionaryModified) return;
        Map<String, String> updated = new LinkedHashMap<>();
        int line = 0;
        for (String value : Files.readAllLines(dictionaryPath, StandardCharsets.UTF_8)) {
            line++;
            if (value.startsWith("\ufeff")) value = value.substring(1);
            if (value.isBlank() || value.startsWith("#")) continue;
            String[] entry = value.split("\t", -1);
            if (entry.length != 2 || entry[0].isEmpty() || entry[1].isEmpty())
                throw new IOException("Invalid dictionary entry at line " + line);
            if (updated.put(entry[0], entry[1]) != null)
                throw new IOException("Duplicate dictionary key at line " + line + ": " + entry[0]);
        }
        dictionary.clear();
        dictionary.putAll(updated);
        dictionaryModified = modified;
        System.out.println("[Fiji 中文] Loaded " + dictionary.size() + " menu translations.");
    }

    public static String translation(String path, String original) {
        String result = dictionary.get("@" + path);
        if (result == null) result = dictionary.get(original);
        if (result == null) result = dictionary.get(original.trim());
        return result == null ? original : result;
    }

    private static void refresh() {
        if (failed) return;
        try {
            reload();
            try { toolbarHints.refresh(enabled); }
            catch (IOException exception) { System.err.println("[Fiji 中文] Toolbar hints: " + exception.getMessage()); }
            if (dialogs != null) {
                try { dialogs.refresh(enabled); }
                catch (Exception exception) { System.err.println("[Fiji 中文] Dialog mapping: " + exception); }
            }
            StringBuilder inventory = new StringBuilder("path\toriginal\tdisplay\taction_command\tshortcut\n");
            StringBuilder secondary = new StringBuilder("window\tpath\toriginal\tdisplay\taction_command\tshortcut\n");
            liveItems = Collections.newSetFromMap(new IdentityHashMap<>());
            for (Frame frame : Frame.getFrames()) {
                if (!frame.isDisplayable() || frame.getMenuBar() == null) continue;
                // Limit application windows: system dialogs and unrelated processes are untouched.
                if (!frame.getClass().getName().startsWith("ij.") &&
                    !Boolean.getBoolean("fiji.zh.demo")) continue;
                MenuBar bar = frame.getMenuBar();
                if (frame.getClass().getName().equals("ij.ImageJ") || Boolean.getBoolean("fiji.zh.demo"))
                    mainMenuBar = bar;
                StringBuilder rows=new StringBuilder();
                for (int i = 0; i < bar.getMenuCount(); i++) visit(bar.getMenu(i), "", rows);
                if(!frame.getClass().getName().equals("ij.ImageJ"))for(String row:rows.toString().split("\n"))
                    if(!row.isEmpty())secondary.append(frame.getClass().getName()).append('\t').append(row).append('\n');
            }
            if(!secondary.toString().equals(lastSecondary)){
                Files.writeString(exportPath.resolveSibling("window-menu-inventory.tsv"),secondary.toString(),StandardCharsets.UTF_8);lastSecondary=secondary.toString();
            }
            for (Window window : Window.getWindows()) if (window.isShowing() &&
                Set.of("ij.plugin.frame.RoiManager", "ij.plugin.frame.Channels", "ij.plugin.frame.Editor", "ij.gui.PlotWindow", "ij.plugin.LUT_Editor").contains(window.getClass().getName())) visitPopups(window);
            if (mainMenuBar != null)
                for (int i = 0; i < mainMenuBar.getMenuCount(); i++) visit(mainMenuBar.getMenu(i), "", inventory);
            stamps.keySet().retainAll(liveItems);
            if (mainMenuBar != null && !inventory.toString().equals(lastInventory)) {
                Files.createDirectories(exportPath.getParent());
                Files.writeString(exportPath, inventory.toString(), StandardCharsets.UTF_8);
                lastInventory = inventory.toString();
            }
        } catch (IOException exception) {
            System.err.println("[Fiji 中文] Dictionary/inventory: " + exception.getMessage());
        } catch (Exception exception) {
            failed = true;
            System.err.println("[Fiji 中文] Native display mapping stopped: " + exception);
            exception.printStackTrace();
        }
    }

    private static void visit(MenuItem item, String parent, StringBuilder inventory) throws Exception {
        liveItems.add(item);
        String original = item.getLabel();
        if (original == null) return;
        // Replacing a separator's native label changes it into a selectable blank row.
        if (original.equals("-")) return;
        String path = parent.isEmpty() ? original : parent + "/" + original;
        boolean dynamic = parent.equals("File/Open Recent") ||
            (parent.equals("Window") && !Set.of("Show All", "Main Window [enter]", "Put Behind [tab]", "Cascade", "Tile", "Console").contains(original));
        String display = enabled && !dynamic ? translation(path, original) : original;
        Object peer = peerField.get(item);
        Stamp previous = stamps.get(item);
        if (peer != null && (previous == null || previous.peer != peer)) {
            MenuItem shadow;
            if (item instanceof CheckboxMenuItem) {
                CheckboxMenuItem source = (CheckboxMenuItem) item;
                CheckboxMenuItem proxy = new CheckboxMenuItem(display, source.getState());
                proxy.addItemListener(event -> {
                    source.setState(proxy.getState());
                    source.dispatchEvent(new ItemEvent(source, ItemEvent.ITEM_STATE_CHANGED,
                        source.getLabel(), event.getStateChange()));
                });
                shadow = proxy;
            } else if (item instanceof Menu) {
                shadow = new Menu(display);
                itemsField.set(shadow, itemsField.get(item));
            } else {
                shadow = new MenuItem(display);
            }
            shadow.setActionCommand(item.getActionCommand());
            shadow.setShortcut(item.getShortcut());
            shadow.setEnabled(item.isEnabled());
            shadow.addActionListener(event -> item.dispatchEvent(new ActionEvent(item,
                ActionEvent.ACTION_PERFORMED, item.getActionCommand(), event.getWhen(), event.getModifiers())));
            parentField.set(shadow, item.getParent());
            peerField.set(shadow, peer);
            targetField.set(peer, shadow);
            previous = new Stamp(peer, shadow, "");
            stamps.put(item, previous);
        }
        if (peer != null && previous != null) {
            Font sourceFont = item.getFont();
            Font displayFont = sourceFont;
            if (enabled && display.codePoints().anyMatch(c -> c >= 0x4e00 && c <= 0x9fff)) {
                int size = sourceFont == null ? 13 : sourceFont.getSize();
                if (item.getParent() instanceof MenuBar) size = Math.min(size, 13);
                displayFont = new Font("Microsoft YaHei UI", sourceFont == null ? Font.PLAIN : sourceFont.getStyle(), size);
            }
            if (!Objects.equals(previous.shadow.getFont(), displayFont)) previous.shadow.setFont(displayFont);
            if (!Objects.equals(previous.shadow.getShortcut(), item.getShortcut())) previous.shadow.setShortcut(item.getShortcut());
            previous.shadow.setActionCommand(item.getActionCommand());
            if (previous.shadow.isEnabled() != item.isEnabled()) previous.shadow.setEnabled(item.isEnabled());
            if (item instanceof CheckboxMenuItem &&
                ((CheckboxMenuItem) previous.shadow).getState() != ((CheckboxMenuItem) item).getState())
                ((CheckboxMenuItem) previous.shadow).setState(((CheckboxMenuItem) item).getState());
            if (!previous.display.equals(display)) {
                // The native peer reads its display copy; application getters and
                // command dispatch continue to use the original object.
                previous.shadow.setLabel(display);
                setLabel.invoke(peer, display);
                previous.display = display;
            }
        }
        if (!original.equals("-") && !dynamic) inventory.append(clean(path)).append('\t').append(clean(original))
            .append('\t').append(clean(display)).append('\t').append(clean(item.getActionCommand()))
            .append('\t').append(item.getShortcut() == null ? "" : item.getShortcut()).append('\n');
        if (item instanceof Menu) {
            Menu menu = (Menu) item;
            for (int i = 0; i < menu.getItemCount(); i++) visit(menu.getItem(i), path, inventory);
        }
    }

    private static void visitPopups(Component component) throws Exception {
        @SuppressWarnings("unchecked") Vector<PopupMenu> popups = (Vector<PopupMenu>) popupsField.get(component);
        if (popups != null) for (PopupMenu popup : popups) visit(popup, "", new StringBuilder());
        if (component instanceof Container) for (Component child : ((Container) component).getComponents()) visitPopups(child);
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace('\t', ' ').replace('\n', ' ');
    }

    private static final class Stamp {
        final Object peer;
        final MenuItem shadow;
        String display;
        Stamp(Object peer, MenuItem shadow, String display) {
            this.peer = peer; this.shadow = shadow; this.display = display;
        }
    }

}
