package org.fiji.zh;

import java.awt.*;
import java.awt.event.PaintEvent;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Only native painting sees translated copies. All input and application calls see originals. */
final class DialogDisplayMapper extends EventQueue {
    private static final Set<String> TITLES = Set.of("Gaussian Blur", "Subtract Background", "Set Measurements",
        "Set Scale", "Analyze Particles", "Threshold", "B&C", "Set Threshold Levels", "Set Display Range",
        "ZProjection", "Resize", "Rotate", "Resize Image Canvas", "Resize Stack Canvas", "Duplicate",
        "Enhance Contrast", "Scale Bar", "Median", "Mean", "Minimum", "Maximum", "Variance", "Unsharp Mask", "Binary Options",
        "ROI Manager", "Properties", "ROI Properties", "Selection Properties", "Add to Overlay", "Overlay Options", "Labels",
        "Image ROI Properties", "Create Image ROI", "Add Image", "Channels", "Merge Channels", "Threshold Color",
        "Substack Maker", "Concatenator", "Convert to HyperStack", "Image Calculator",
        "3D Gaussian Blur", "3D Mean", "3D Median", "3D Minimum", "3D Maximum", "3D Variance",
        "Remove Outliers", "FFT Bandpass Filter", "Convolver", "Appearance", "Arrow Tool", "Colors",
        "Compile and Run", "Conversion Options", "DICOM Options", "I/O Options", "Default Line Width",
        "Memory (64-bit)", "Memory (32-bit)", "Miscellaneous Options", "Plot Defaults", "Point Tool", "Proxy Settings",
        "ROI Defaults", "Rounded Rectangle Tool", "Startup Macro", "Wand Tool", "Reset Preferences",
        "ROI Manager Action", "Rename", "Options", "Remove", "Scale", "Translate", "Font", "Fonts");
    private final Path dictionaryPath, inventoryPath;
    private volatile Map<String, String> dictionary = Map.of();
    private volatile java.util.List<TextTemplate> templates = java.util.List.of();
    private final Map<Component, Stamp> mapped = new IdentityHashMap<>();
    private final Map<Choice, Boolean> imageChoices = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<Component, Component> originals = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<Window, String> titles = new WeakHashMap<>();
    private final Field peer, target, parent, labelText, buttonText, checkboxText, checkboxState, checkboxGroup, choiceValues, choiceIndex;
    private final java.util.List<Field> componentFields = new ArrayList<>();
    private final NativePaintBridge bridge = new NativePaintBridge();
    private volatile boolean chinese;
    private long modified = -1;
    private String lastInventory = "";

    DialogDisplayMapper(Path root) throws Exception {
        dictionaryPath = root.resolve("locales/dialogs.zh_CN.tsv");
        inventoryPath = root.resolve("build/dialog-inventory.tsv");
        peer = field(Component.class, "peer"); parent = field(Component.class, "parent");
        target = field(Class.forName("sun.awt.windows.WObjectPeer", false, null), "target");
        labelText = field(Label.class, "text"); buttonText = field(Button.class, "label");
        checkboxText = field(Checkbox.class, "label"); checkboxState = field(Checkbox.class, "state");
        checkboxGroup = field(Checkbox.class, "group");
        choiceValues = field(Choice.class, "pItems"); choiceIndex = field(Choice.class, "selectedIndex");
        for (String name : new String[]{"x", "y", "width", "height", "enabled", "visible", "font", "foreground", "background"})
            componentFields.add(field(Component.class, name));
        Toolkit.getDefaultToolkit().getSystemEventQueue().push(this);
        DisplayTextPainter.install(this);
    }
    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); return field;
    }

    @Override protected void dispatchEvent(AWTEvent event) {
        if (event instanceof ActionEvent) {
            Component original = originals.get(event.getSource());
            if (original == null && event.getSource() instanceof Button && mapped.containsKey(event.getSource()))
                original = (Component) event.getSource();
            // WButtonPeer's EDT runnable reads target again after the native click.
            // It can overlap a toolkit-thread paint, so restore both source and command.
            if (original instanceof Button) {
                ActionEvent action = (ActionEvent) event;
                event = new ActionEvent(original, action.getID(), ((Button) original).getActionCommand(), action.getWhen(), action.getModifiers());
            }
        }
        // A native label may enqueue a PaintEvent while drawing its display copy.
        if (event instanceof PaintEvent) {
            Component original = originals.get(event.getSource());
            if (original != null) event.setSource(original);
        }
        super.dispatchEvent(event);
    }

    void refresh(boolean useChinese) throws Exception {
        chinese = useChinese;
        reload();
        Set<Component> live = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Long> parents = new HashSet<>();
        Map<Long, NativePaintBridge.Painter> painters = new HashMap<>();
        StringBuilder inventory = new StringBuilder("window\ttype\toriginal\tdisplay\n");
        for (Window window : Window.getWindows()) {
            if (!window.isShowing()) continue;
            String originalTitle = window instanceof Dialog ? ((Dialog) window).getTitle() :
                window instanceof Frame ? ((Frame) window).getTitle() : "";
            String context = contextFor(window, originalTitle);
            if (context == null) continue;
            boolean userTitle=Set.of("Image Properties", "Script Editor", "Fiji Script Editor", "Histogram Window", "Plot Window").contains(context);
            if(!userTitle && DisplayTextPainter.isCoreDialog(window)) {
                Class<?> manager=Class.forName("ij.WindowManager",false,window.getClass().getClassLoader());
                String[] imageTitles=(String[])manager.getMethod("getImageTitles").invoke(null);
                userTitle=java.util.Arrays.asList(imageTitles).contains(originalTitle);
            }
            String translatedTitle = userTitle ? originalTitle : translate(context, originalTitle);
            inventory.append(clean(context)).append("\tTitle\t").append(clean(originalTitle)).append('\t').append(clean(translatedTitle)).append('\n');
            if ((window instanceof Frame || window instanceof Dialog) && !translatedTitle.equals(titles.get(window))) {
                String type = window instanceof Dialog ? "DialogPeer" : "FramePeer";
                Class.forName("java.awt.peer." + type).getMethod("setTitle", String.class).invoke(peer.get(window), translatedTitle);
                titles.put(window, translatedTitle);
            }
            boolean layoutChanged = visit(window, context, live, parents, painters, inventory);
            if (layoutChanged) {
                window.invalidate();
                // HTMLDialog already constrains its scroll viewport to the screen.
                // Packing again sizes it to the full document's preferred width.
                if(!context.startsWith("Help/"))window.pack();
                if(context.startsWith("Plugin/")){
                    Rectangle screen=window.getGraphicsConfiguration().getBounds();
                    Insets inset=Toolkit.getDefaultToolkit().getScreenInsets(window.getGraphicsConfiguration());
                    screen=new Rectangle(screen.x+inset.left,screen.y+inset.top,screen.width-inset.left-inset.right,screen.height-inset.top-inset.bottom);
                    int width=Math.min(window.getWidth(),screen.width),height=Math.min(window.getHeight(),screen.height);
                    window.setBounds(Math.max(screen.x,Math.min(window.getX(),screen.x+screen.width-width)),Math.max(screen.y,Math.min(window.getY(),screen.y+screen.height-height)),width,height);
                }
                window.validate();
            }
        }
        bridge.update(painters, parents);
        DisplayTextPainter.refreshStatus();
        for (Map.Entry<Component, Stamp> entry : mapped.entrySet()) {
            if (live.contains(entry.getKey()) && entry.getValue().repaintNeeded) {
                bridge.repaint(bridge.handle(entry.getKey()));
                entry.getValue().repaintNeeded = false;
            }
        }
        Iterator<Map.Entry<Component, Stamp>> entries = mapped.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<Component, Stamp> entry = entries.next();
            if (!live.contains(entry.getKey())) {
                originals.remove(entry.getValue().copy);
                imageChoices.remove(entry.getKey());
                entries.remove();
            }
        }
        if (!inventory.toString().equals(lastInventory)) {
            Files.createDirectories(inventoryPath.getParent());
            Files.writeString(inventoryPath, inventory.toString(), StandardCharsets.UTF_8);
            lastInventory = inventory.toString();
        }
    }

    private String contextFor(Window window, String title) {
        if (Boolean.getBoolean("fiji.zh.demo")) return title;
        String plugin=PluginDisplaySupport.context(window);
        if(plugin!=null)return plugin;
        String settings=SwingDisplayMapper.settingsContext(window);
        if(settings!=null)return settings;
        String tooltip=SwingDisplayMapper.tooltipContext(window);
        if(tooltip!=null)return tooltip;
        if(window.getClass().getName().equals("ij.gui.HTMLDialog") && DisplayTextPainter.isCoreDialog(window)){
            String topic=title.trim();
            if(topic.equals("Help") && window.getOwner() instanceof Dialog)topic=((Dialog)window.getOwner()).getTitle().trim();
            if(topic.endsWith("..."))topic=topic.substring(0,topic.length()-3).trim();
            return "Help/"+topic;
        }
        switch(window.getClass().getName()) {
            case "ij.plugin.frame.Recorder": return "Recorder";
            case "ij.plugin.frame.Editor": return "Script Editor";
            case "ij.plugin.frame.Fitter": return "Curve Fitter";
            case "ij.plugin.frame.PasteController": return "Paste Control";
            case "ij.plugin.frame.LineWidthAdjuster": return "Line Width";
            case "ij.plugin.frame.SyncWindows": return "Synchronize Windows";
            case "ij.plugin.frame.Commands": return "Commands";
            case "ij.plugin.LUT_Editor": return "LUT Editor";
            case "ij.macro.FunctionFinder": return "Built-in Functions";
            case "ij.gui.HistogramWindow": return "Histogram Window";
            case "ij.gui.PlotWindow": return "Plot Window";
        }
        boolean generic = false, allowed = false;
        for (Class<?> type = window.getClass(); type != null; type = type.getSuperclass())
            if (type.getName().equals("ij.gui.GenericDialog")) { generic = true; allowed = true; }
            else if (type.getName().equals("ij.plugin.frame.ThresholdAdjuster") ||
                type.getName().equals("ij.plugin.frame.ContrastAdjuster")) allowed = true;
        if (window.getClass().getName().equals("ij.gui.MessageDialog") ||
            window.getClass().getName().equals("ij.gui.YesNoCancelDialog")) return "Messages";
        if (Set.of("ij.plugin.frame.RoiManager", "ij.plugin.frame.Channels", "ij.plugin.frame.ColorThresholder", "ij.plugin.Text").contains(window.getClass().getName())) allowed = true;
        if (!allowed) return null;
        if (generic && !DisplayTextPainter.isCoreDialog(window)) return null;
        // ImageProperties uses the user's image title, so identify its fixed fields.
        if (generic) {
            Set<String> labels = new HashSet<>(); collectLabels(window, labels);
            if (labels.containsAll(Set.of("Channels (c):", "Slices (z):", "Frames (t):", "Pixel width:", "Pixel height:", "Voxel depth:")))
                return "Image Properties";
        }
        String canonical = title.trim();
        if (canonical.endsWith("...")) canonical = canonical.substring(0, canonical.length()-3).trim();
        return generic || TITLES.contains(canonical) ? canonical : null;
    }
    private static void collectLabels(Component source, Set<String> labels) {
        if (source instanceof Label) labels.add(((Label) source).getText().trim());
        if (source instanceof Container)
            for (Component child : ((Container) source).getComponents()) collectLabels(child, labels);
    }

    private void reload() throws IOException {
        if (!Files.exists(dictionaryPath)) return;
        Path plugins=dictionaryPath.resolveSibling("plugins.zh_CN.tsv");
        long nextModified = Files.getLastModifiedTime(dictionaryPath).toMillis() + (Files.exists(plugins)?Files.getLastModifiedTime(plugins).toMillis():0);
        if (nextModified == modified) return;
        Map<String, String> next = new LinkedHashMap<>();
        java.util.List<String> rows=new ArrayList<>(Files.readAllLines(dictionaryPath, StandardCharsets.UTF_8));
        if(Files.exists(plugins))rows.addAll(Files.readAllLines(plugins,StandardCharsets.UTF_8));
        for (String line : rows) {
            if (line.startsWith("\ufeff")) line = line.substring(1);
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] values = line.split("\t", -1);
            if (values.length != 2 || values[0].isEmpty() || values[1].isEmpty() || next.put(values[0].replace("\\n", "\n"), values[1].replace("\\n", "\n")) != null)
                throw new IOException("Invalid or duplicate dialog entry: " + line);
        }
        Map<String,String> aliases=new LinkedHashMap<>(next);
        next.forEach((key,value)->aliases.putIfAbsent(key.trim(),value));
        next=aliases;
        java.util.List<TextTemplate> formats=new ArrayList<>();
        next.forEach((key,value)->{if(key.contains("{") && key.startsWith("@"))formats.add(new TextTemplate(key,value));});
        templates=java.util.List.copyOf(formats);
        dictionary = Map.copyOf(next); modified = nextModified; titles.clear();
    }
    String translate(String context, String value) {
        if (!chinese || value == null) return value;
        String canonical = context.endsWith("...") ? context.substring(0, context.length()-3).trim() : context;
        String translated = dictionary.get("@" + canonical + "/" + value);
        if (translated == null) translated = dictionary.get("@" + canonical + "/" + value.trim());
        if (translated == null) translated = dictionary.get(value);
        if (translated == null) translated = dictionary.get(value.trim());
        if (translated == null && value.trim().endsWith(":")) {
            String base=dictionary.get(value.trim().substring(0,value.trim().length()-1));
            if(base!=null)translated=base.endsWith("：")?base:base+"：";
        }
        if (translated == null && value.endsWith("...")) {
            String base = dictionary.get(value.substring(0, value.length()-3).trim());
            if (base != null) translated = base + "…";
        }
        if(translated==null) {
            String menu=MenuDisplayAgent.translation("",value);
            if(!Objects.equals(menu,value))translated=menu;
        }
        if (translated == null && canonical.equals("Set Scale") && value.trim().startsWith("Scale: ")) {
            String prefix = dictionary.get("@Set Scale/Scale:");
            String pixels = dictionary.get("@Set Scale/pixels/");
            String status = value.trim().substring(7);
            if (prefix != null && pixels != null)
                translated = prefix + status.replaceFirst("^([0-9.,Ee+\\-]+) pixels/", "$1 " + pixels);
        }
        if (translated == null && canonical.equals("Set Display Range")) {
            java.util.regex.Matcher propagation = java.util.regex.Pattern.compile(
                "^(Propagate to all other |Propagate to the other )(\\d+)( channel images| channels of this image)$").matcher(value.trim());
            if (propagation.matches()) {
                String template = dictionary.get("@Set Display Range/" + propagation.group(1) + "{n}" + propagation.group(3));
                if (template != null) translated = template.replace("{n}", propagation.group(2));
            }
        }
        if (translated == null && canonical.equals("Image Properties") && value.trim().matches("Note: c\\*z\\*t must equal \\d+")) {
            String template = dictionary.get("@Image Properties/Note: c*z*t must equal {n}");
            if (template != null) translated = template.replace("{n}", value.trim().substring(value.trim().lastIndexOf(' ') + 1));
        }
        if (translated == null && canonical.equals("Enhance Contrast") && value.trim().matches("Process all \\d+ slices")) {
            String template = dictionary.get("@Enhance Contrast/Process all {n} slices");
            if (template != null) translated = template.replace("{n}", value.trim().split(" ")[2]);
        }
        if (translated == null && canonical.equals("Scale Bar")) {
            java.util.regex.Matcher dimension = java.util.regex.Pattern.compile("^(Width|Height) in (.+):$").matcher(value.trim());
            if (dimension.matches()) {
                String template = dictionary.get("@Scale Bar/" + dimension.group(1) + " in {unit}:");
                if (template != null) translated = template.replace("{unit}", dimension.group(2));
            }
        }
        if (translated == null && canonical.equals("Messages")) {
            java.util.regex.Matcher save = java.util.regex.Pattern.compile("(?s)Save changes to[ \\n]+\"(.*?)\"\\?(.*)").matcher(value.trim());
            if (save.matches()) {
                String format = dictionary.get("@Messages/Save changes to {name}?");
                if (format != null) translated = format.replace("{name}", save.group(1)) + translate("Messages", save.group(2));
            }
            java.util.regex.Matcher stack = java.util.regex.Pattern.compile("(?s)Process all ([0-9]+) images\\?(.*)").matcher(value.trim());
            if (stack.matches()) {
                String format = dictionary.get("@Messages/Process all {n} images?");
                if (format != null) translated = format.replace("{n}", stack.group(1)) + translate("Messages", stack.group(2));
            }
        }
        if (translated == null && canonical.equals("ROI Manager") && value.trim().matches("Save the [0-9]+ displayed ROIs as an overlay\\?")) {
            String format = dictionary.get("@ROI Manager/Save the {n} displayed ROIs as an overlay?");
            if (format != null) translated = format.replace("{n}", value.trim().replaceAll("[^0-9]", ""));
        }
        if (translated == null && canonical.equals("Channels") && value.trim().matches("Channel ?[0-9]+")) {
            String format = dictionary.get("@Channels/Channel {n}");
            if (format != null) translated = format.replace("{n}", value.trim().substring(7).trim());
        }
        if (translated == null && canonical.equals("Concatenator") && value.trim().matches("Image[0-9]+:")) {
            String format = dictionary.get("@Concatenator/Image{n}:");
            if (format != null) translated = format.replace("{n}", value.trim().substring(5,value.trim().length()-1));
        }
        for (String stem : new String[]{"List coordinates", "List properties"}) {
            if (translated == null && value.trim().matches(stem + " \\([0-9]+\\)")) {
                String format = dictionary.get("@Messages/" + stem + " ({n})");
                if (format != null) translated = format.replace("{n}", value.trim().replaceAll("[^0-9]", ""));
            }
        }
        if (translated == null && value.trim().matches("Current overlay has [0-9]+ elements?")) {
            String format = dictionary.get("@Overlay Options/Current overlay has {n} elements");
            if (format != null) translated = format.replace("{n}", value.trim().replaceAll("[^0-9]", ""));
        }
        if(translated==null)translated=format(canonical,value);
        return translated == null ? value : translated;
    }

    private String format(String canonical,String value){
        for(TextTemplate template:templates){
            if(!canonical.equals(template.context) && !canonical.startsWith(template.context+" ("))continue;
            java.util.regex.Matcher match=template.pattern.matcher(value.trim());
            if(!match.matches())continue;
            return template.render(match);
        }
        return null;
    }

    String translateStatus(String value) {
        if (!chinese || value == null) return value;
        String translated = dictionary.get("@Status/" + value);
        if (translated == null) translated=dictionary.get("@Status/"+value.trim());
        if (translated == null) translated=format("Status",value);
        if (translated != null) return translated;
        java.util.regex.Matcher progress = java.util.regex.Pattern.compile("^(3D filtering|Z blurring|Convolving|Gaussian Blur|Median|Mean|Minimum|Maximum|Variance)((?:\\.\\.\\.|:| [0-9]).*)$").matcher(value);
        if (progress.matches()) {
            String prefix = dictionary.get("@Status/" + progress.group(1));
            if (prefix != null) return prefix + progress.group(2);
        }
        return value;
    }

    String helpText(String topic,String original){
        if(!chinese)return null;
        if(topic.startsWith("Plugin/")){
            if(topic.equals("Plugin/Coloc 2")){
                String report=original;
                for(String[] pair:new String[][]{{"Warnings","警告"},{"No warnings occurred.","未出现警告。"},{"Results","结果"},{"Type","类型"},{"Message","说明"},{"Name","名称"},{"Result","结果"}})
                    report=report.replaceAll("(?i)(<(?:h1|th)(?:\\s[^>]*)?>\\s*)"+java.util.regex.Pattern.quote(pair[0])+"(\\s*</(?:h1|th)>)","$1"+pair[1]+"$2");
                if(!report.equals(original))return report;
            }
            String prefix="@HelpMatch/"+topic+"/";
            for(Map.Entry<String,String> entry:dictionary.entrySet())if(entry.getKey().startsWith(prefix) && original.contains(entry.getKey().substring(prefix.length())))return entry.getValue();
            return null;
        }
        String translated=dictionary.get("@Help/"+topic);
        if(translated!=null)return translated;
        for(Map.Entry<String,String> entry:dictionary.entrySet())
            if(entry.getKey().startsWith("@HelpMatch/") && original.contains(entry.getKey().substring(11)))return entry.getValue();
        return null;
    }

    private boolean visit(Component source, String context, Set<Component> live, Set<Long> parents,
        Map<Long, NativePaintBridge.Painter> painters, StringBuilder inventory) throws Exception {
        // File names and table/tree/list renderer values belong to user data.
        if(source instanceof javax.swing.JFileChooser || source instanceof javax.swing.CellRendererPane)return false;
        boolean changed = SwingDisplayMapper.visit(source,context,this,inventory);
        if (source instanceof Label || source instanceof Button || source instanceof Checkbox || source instanceof Choice) {
            String value = caption(source);
            String display = translate(context, value);
            java.util.List<String> items = source instanceof Choice ? translatedItems((Choice) source, context) : java.util.List.of();
            if (source instanceof Choice) {
                Choice choice = (Choice) source;
                for (int i=0; i<choice.getItemCount(); i++) inventory.append(clean(context)).append("\tChoice\t")
                    .append(clean(choice.getItem(i))).append('\t').append(clean(items.get(i))).append('\n');
            }
            String signature = display + "\u0000" + String.join("\u0000", items);
            Stamp stamp = mapped.get(source);
            boolean translated = source instanceof Choice ? !items.equals(originalItems((Choice) source)) : !Objects.equals(value, display);
            if (peer.get(source) != null && (translated || stamp != null)) {
                live.add(source);
                if (stamp == null || stamp.peer != peer.get(source)) {
                    Component copy = source instanceof Label ? new Label(value, ((Label) source).getAlignment()) :
                        source instanceof Button ? new Button(value) : source instanceof Checkbox ? new Checkbox(value) : new Choice();
                    stamp = new Stamp(source, copy, peer.get(source), context);
                    mapped.put(source, stamp); originals.put(copy, source);
                    parent.set(copy, source.getParent());
                }
                if (!signature.equals(stamp.signature)) {
                    FontMetrics metrics = source.getFontMetrics(source.getFont());
                    Dimension minimum = new Dimension(stamp.initialPreferred);
                    int textWidth = metrics.stringWidth(display);
                    if (source instanceof Choice) {
                        for (String item : items) textWidth = Math.max(textWidth, metrics.stringWidth(item));
                        textWidth += 30;
                    } else textWidth += source instanceof Checkbox ? 28 : 16;
                    if (chinese) source.setPreferredSize(new Dimension(Math.max(minimum.width, textWidth), minimum.height));
                    else source.setPreferredSize(stamp.preferredExplicit ? stamp.initialPreferred : null);
                    source.invalidate(); stamp.signature = signature; stamp.repaintNeeded = true; changed = true;
                }
                Stamp renderer = stamp;
                painters.put(bridge.handle(source), delegate -> render(renderer, delegate));
                Component container = source.getParent();
                while(container!=null && container.isLightweight())container=container.getParent();
                if (container != null) parents.add(bridge.handle(container));
            }
            if (!(source instanceof Choice) && value != null && !value.isBlank())
                inventory.append(clean(context)).append('\t').append(source.getClass().getSimpleName()).append('\t')
                    .append(clean(value)).append('\t').append(clean(display)).append('\n');
        }
        if (source instanceof Container)
            for (Component child : ((Container) source).getComponents()) changed |= visit(child, context, live, parents, painters, inventory);
        if(source instanceof javax.swing.JMenu)for(Component item:((javax.swing.JMenu)source).getMenuComponents())
            changed|=visit(item,context,live,parents,painters,inventory);
        if (source.getClass().getName().equals("ij.gui.MultiLineLabel")) changed |= DisplayTextPainter.refreshMessage(source, context, inventory, this);
        return changed;
    }

    private com.sun.jna.platform.win32.WinDef.LRESULT render(Stamp stamp, NativePaintBridge.NativeCall delegate) throws Exception {
        Component source = stamp.source, copy = stamp.copy;
        for (Field field : componentFields) field.set(copy, field.get(source));
        if (source instanceof Label) labelText.set(copy, translate(stamp.context, (String) labelText.get(source)));
        else if (source instanceof Button) buttonText.set(copy, translate(stamp.context, (String) buttonText.get(source)));
        else if (source instanceof Checkbox) {
            checkboxText.set(copy, translate(stamp.context, (String) checkboxText.get(source)));
            checkboxState.set(copy, checkboxState.get(source));
            // Native checkbox painting only needs to know whether it is a radio control.
            checkboxGroup.set(copy, checkboxGroup.get(source));
        } else {
            @SuppressWarnings("unchecked") Vector<String> values = (Vector<String>) choiceValues.get(source);
            Object[] valuesSnapshot = values.toArray();
            Vector<String> translated = new Vector<>();
            for (int i = 0; i < valuesSnapshot.length; i++)
                translated.add(translateChoice((Choice) source, stamp.context, i, valuesSnapshot.length, (String) valuesSnapshot[i]));
            choiceValues.set(copy, translated); choiceIndex.set(copy, choiceIndex.get(source));
        }
        Object previous = target.get(stamp.peer);
        target.set(stamp.peer, copy);
        try { return delegate.call(); }
        finally { target.set(stamp.peer, previous); }
    }

    private java.util.List<String> translatedItems(Choice choice, String context) {
        java.util.List<String> result = new ArrayList<>();
        java.util.List<String> items = originalItems(choice);
        if (context.equals("Image Calculator")) {
            int ordinal = 0;
            for (Component sibling : choice.getParent().getComponents()) {
                if (sibling == choice) break;
                if (sibling instanceof Choice) ordinal++;
            }
            imageChoices.put(choice, ordinal != 1);
        }
        else {
            Component[] siblings=choice.getParent().getComponents();
            for(int i=1;i<siblings.length;i++)if(siblings[i]==choice && siblings[i-1] instanceof Label){
                String label=((Label)siblings[i-1]).getText().trim();
                boolean imageTitle=label.matches("(?i)(Image ?[0-9]*|Stack ?[0-9]*|Source|Destination|Image to add|Redirect to|Template Plot|Select Plot|Select Table):?");
                if(context.equals("Plugin/Coloc 2") && label.matches("Channel [12]:?"))imageTitle=true;
                if(context.equals("Plugin/Stitching") && label.matches("(First image \\(reference\\)|Second image \\(to register\\)):?"))imageTitle=true;
                if(context.equals("FFT Filter") && label.equals("Filter:"))imageTitle=true;
                imageChoices.put(choice,imageTitle);break;
            }
        }
        for (int i = 0; i < items.size(); i++) result.add(translateChoice(choice, context, i, items.size(), items.get(i)));
        return result;
    }
    private String translateChoice(Choice choice, String context, int index, int count, String item) {
        // No AWT locks here: native painting can run while the EDT holds the tree lock.
        // Image selectors retain user titles, including titles identical to UI words.
        if (context.equals("Merge Channels")) return index == count-1 && item.equals("*None*") ? translate(context, item) : item;
        if (context.equals("Concatenator")) return index == count-1 && item.equals("-- None --") ? translate(context, item) : item;
        if (Boolean.TRUE.equals(imageChoices.get(choice))) return item;
        if(context.equals("Plugin/Coloc 2") && count>4 && index>=4)return item;
        if(context.equals("Plugin/Coloc 2")) {
            String fixed=dictionary.get("@"+context+"/"+item.trim());
            return fixed==null?item:fixed;
        }
        if ((context.equals("Create Image ROI") || context.equals("Add Image"))) return item;
        // Redirect-to choices after the first None entry are user image titles.
        if (context.startsWith("Set Measurements") && (index != 0 || !item.equals("None"))) return item;
        return translate(context, item);
    }
    private static java.util.List<String> originalItems(Choice choice) {
        java.util.List<String> result = new ArrayList<>();
        for (int i = 0; i < choice.getItemCount(); i++) result.add(choice.getItem(i));
        return result;
    }
    private static String caption(Component component) {
        if (component instanceof Label) return ((Label) component).getText();
        if (component instanceof Button) return ((Button) component).getLabel();
        if (component instanceof Checkbox) return ((Checkbox) component).getLabel();
        return "";
    }
    private static String clean(String text) { return text == null ? "" : text.replace('\t', ' ').replace('\n', ' '); }
    private static final class TextTemplate {
        final String context,text;
        final java.util.regex.Pattern pattern;
        final java.util.List<String> variables=new ArrayList<>();
        TextTemplate(String key,String value){
            int slash=key.startsWith("@Plugin/")?key.indexOf('/',8):key.indexOf('/');context=key.substring(1,slash);text=value;String source=key.substring(slash+1);
            java.util.regex.Matcher fields=java.util.regex.Pattern.compile("\\{([a-z][a-z0-9_]*)\\}").matcher(source);
            StringBuilder regex=new StringBuilder();int end=0;
            while(fields.find()){regex.append(literal(source.substring(end,fields.start()))).append("(.+?)");variables.add(fields.group(1));end=fields.end();}
            regex.append(literal(source.substring(end)));pattern=java.util.regex.Pattern.compile(regex.toString(),java.util.regex.Pattern.DOTALL);
        }
        private static String literal(String text){
            return java.util.Arrays.stream(text.split("\\s+",-1)).map(java.util.regex.Pattern::quote).collect(java.util.stream.Collectors.joining("\\s+"));
        }
        String render(java.util.regex.Matcher values){
            java.util.regex.Matcher fields=java.util.regex.Pattern.compile("\\{([a-z][a-z0-9_]*)\\}").matcher(text);
            StringBuffer result=new StringBuffer();
            while(fields.find()){
                int index=variables.indexOf(fields.group(1));
                String value=index<0?fields.group():values.group(index+1);
                fields.appendReplacement(result,java.util.regex.Matcher.quoteReplacement(value));
            }
            fields.appendTail(result);return result.toString();
        }
    }
    private static final class Stamp {
        final Component source, copy;
        final Object peer;
        final String context;
        final Dimension initialPreferred;
        final boolean preferredExplicit;
        String signature;
        boolean repaintNeeded;
        Stamp(Component source, Component copy, Object peer, String context) {
            this.source = source; this.copy = copy; this.peer = peer; this.context = context;
            initialPreferred = source.getPreferredSize(); preferredExplicit = source.isPreferredSizeSet();
        }
    }
}
