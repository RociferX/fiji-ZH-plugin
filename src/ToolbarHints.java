package org.fiji.zh;

import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.*;
import javax.swing.border.*;

/** Read-only toolbar observer. No tool selection, command or event is changed. */
final class ToolbarHints extends MouseAdapter implements AWTEventListener {
    private final Path dictionaryPath;
    private final Map<String, Hint> dictionary = new LinkedHashMap<>();
    private final Timer delay;
    private final KeyEventDispatcher keyObserver = event -> {
        if (event.getID() == KeyEvent.KEY_PRESSED) cancel();
        return false;
    };
    private final ComponentAdapter ownerChanges = new ComponentAdapter() {
        @Override public void componentMoved(ComponentEvent event) { cancel(); }
        @Override public void componentResized(ComponentEvent event) { cancel(); }
        @Override public void componentHidden(ComponentEvent event) { cancel(); }
    };
    private long modified = -1;
    private Component toolbar;
    private Window owner;
    private Method toolID, inGap, getName;
    private Field names;
    private boolean chinese = true;
    private String currentKey;
    private Point anchor;
    private JWindow popup;
    private JLabel title, description, usage;

    ToolbarHints(Path root) {
        dictionaryPath = root.resolve("locales/toolbar-hints.tsv");
        delay = new Timer(500, event -> showHint());
        delay.setRepeats(false);
    }

    void refresh(boolean useChinese) throws IOException {
        if (chinese != useChinese) { chinese = useChinese; cancel(); }
        if (Files.exists(dictionaryPath) && Files.getLastModifiedTime(dictionaryPath).toMillis() != modified) {
            Map<String, Hint> next = new LinkedHashMap<>();
            int number = 0;
            for (String line : Files.readAllLines(dictionaryPath, StandardCharsets.UTF_8)) {
                number++;
                if (line.startsWith("\ufeff")) line = line.substring(1);
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] fields = line.split("\t", -1);
                if (fields.length != 7 || fields[0].isEmpty() || fields[1].isEmpty() || fields[2].isEmpty())
                    throw new IOException("Invalid toolbar hint at line " + number);
                if (next.put(fields[0], new Hint(fields)) != null)
                    throw new IOException("Duplicate toolbar hint at line " + number + ": " + fields[0]);
            }
            dictionary.clear();
            dictionary.putAll(next);
            modified = Files.getLastModifiedTime(dictionaryPath).toMillis();
            cancel();
        }
        Component found = null;
        for (Frame frame : Frame.getFrames()) {
            if (frame.isShowing() && frame.getClass().getName().equals("ij.ImageJ")) {
                found = findToolbar(frame);
                if (found != null) break;
            }
        }
        if (toolbar != found) bind(found);
        if (toolbar != null && !toolbar.isShowing()) cancel();
        // A macro or tool palette can change a variant while the pointer stays still.
        if (toolbar != null && currentKey != null) {
            Point point = toolbar.getMousePosition();
            Hint current = point == null ? null : hintAt(point);
            if (current == null) cancel();
            else if (!current.key.equals(currentKey)) {
                cancel();
                currentKey = current.key;
                anchor = point;
                delay.restart();
            }
        }
    }

    private static Component findToolbar(Component component) {
        if (component.getClass().getName().equals("ij.gui.Toolbar")) return component;
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                Component result = findToolbar(child);
                if (result != null) return result;
            }
        }
        return null;
    }

    private void bind(Component target) throws IOException {
        cancel();
        if (toolbar != null) {
            toolbar.removeMouseListener(this);
            toolbar.removeMouseMotionListener(this);
            owner.removeComponentListener(ownerChanges);
            Toolkit.getDefaultToolkit().removeAWTEventListener(this);
            KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(keyObserver);
        }
        if (popup != null) { popup.dispose(); popup = null; }
        toolbar = null;
        owner = null;
        if (target == null) return;
        try {
            Class<?> type = target.getClass();
            toolID = type.getDeclaredMethod("toolID", int.class);
            inGap = type.getDeclaredMethod("inGap", int.class);
            getName = type.getDeclaredMethod("getName", int.class);
            names = type.getDeclaredField("names");
            toolID.setAccessible(true); inGap.setAccessible(true);
            getName.setAccessible(true); names.setAccessible(true);
            toolbar = target;
            owner = SwingUtilities.getWindowAncestor(target);
            toolbar.addMouseListener(this);
            toolbar.addMouseMotionListener(this);
            owner.addComponentListener(ownerChanges);
            Toolkit.getDefaultToolkit().addAWTEventListener(this,
                AWTEvent.WINDOW_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK);
            KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(keyObserver);
        } catch (ReflectiveOperationException exception) {
            throw new IOException("Unsupported Fiji toolbar: " + exception.getMessage(), exception);
        }
    }

    private Hint hintAt(Point point) {
        if (toolbar == null || !toolbar.contains(point)) return null;
        try {
            if (Boolean.TRUE.equals(inGap.invoke(toolbar, point.x))) return null;
            int id = (Integer) toolID.invoke(toolbar, point.x);
            String key = (String) getName.invoke(toolbar, id);
            String[] originalNames = (String[]) names.get(toolbar);
            if (id >= 10 && id < originalNames.length && originalNames[id] != null)
                key = originalNames[id];
            if (key == null || key.isBlank()) return null;
            Hint hint = dictionary.get(key);
            // An unknown plugin tool keeps its original name without inventing instructions.
            return hint == null ? new Hint(new String[] {key, key, key, "", "", "", ""}) : hint;
        } catch (ReflectiveOperationException exception) {
            System.err.println("[Fiji 中文] Toolbar hint unavailable: " + exception.getMessage());
            return null;
        }
    }

    @Override public void mouseEntered(MouseEvent event) { mouseMoved(event); }
    @Override public void mouseMoved(MouseEvent event) {
        if (event.getModifiersEx() != 0) { cancel(); return; }
        Hint hint = hintAt(event.getPoint());
        if (hint == null) { cancel(); return; }
        if (!hint.key.equals(currentKey)) {
            cancel();
            currentKey = hint.key;
            anchor = event.getPoint();
            delay.restart();
        }
    }
    @Override public void mouseExited(MouseEvent event) { cancel(); }
    @Override public void mousePressed(MouseEvent event) { cancel(); }
    @Override public void mouseDragged(MouseEvent event) { cancel(); }

    @Override public void eventDispatched(AWTEvent event) {
        if ((event instanceof MouseEvent && event.getID() == MouseEvent.MOUSE_PRESSED) ||
            (event.getSource() == owner && (event.getID() == WindowEvent.WINDOW_DEACTIVATED ||
                event.getID() == WindowEvent.WINDOW_CLOSING || event.getID() == WindowEvent.WINDOW_CLOSED))) cancel();
    }

    private void cancel() {
        delay.stop();
        currentKey = null;
        if (popup != null) popup.setVisible(false);
    }

    private void showHint() {
        if (toolbar == null || !toolbar.isShowing() || currentKey == null) return;
        Point local = toolbar.getMousePosition();
        if (local == null) { cancel(); return; }
        Hint hint = hintAt(local);
        if (hint == null || !hint.key.equals(currentKey)) { cancel(); return; }
        if (popup == null) {
            popup = new JWindow(owner);
            popup.setName("fiji.zh.toolbar.tooltip");
            popup.setFocusableWindowState(false);
            popup.setAutoRequestFocus(false);
            popup.setType(Window.Type.POPUP);
            JPanel panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setBackground(new Color(255, 251, 230));
            panel.setBorder(new CompoundBorder(new LineBorder(new Color(206, 189, 132)),
                new EmptyBorder(9, 11, 9, 11)));
            title = label(Font.BOLD, 14);
            description = label(Font.PLAIN, 13);
            usage = label(Font.PLAIN, 12);
            usage.setForeground(new Color(84, 91, 101));
            panel.add(title);
            panel.add(Box.createVerticalStrut(6));
            panel.add(description);
            panel.add(Box.createVerticalStrut(4));
            panel.add(usage);
            popup.setContentPane(panel);
        }
        title.setText(chinese && !hint.zhTitle.equals(hint.enTitle) ? hint.zhTitle + " · " + hint.enTitle : hint.enTitle);
        description.setText(html(chinese ? hint.zhDescription : hint.enDescription));
        usage.setText(html(chinese ? hint.zhUsage : hint.enUsage));
        description.setVisible(!(chinese ? hint.zhDescription : hint.enDescription).isBlank());
        usage.setVisible(!(chinese ? hint.zhUsage : hint.enUsage).isBlank());
        popup.pack();
        Point location = toolbar.getLocationOnScreen();
        GraphicsConfiguration configuration = toolbar.getGraphicsConfiguration();
        Rectangle bounds = configuration.getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration);
        Rectangle usable = new Rectangle(bounds.x + insets.left, bounds.y + insets.top,
            bounds.width - insets.left - insets.right, bounds.height - insets.top - insets.bottom);
        int x = Math.max(usable.x, Math.min(location.x + anchor.x + 12, usable.x + usable.width - popup.getWidth()));
        int y = location.y + toolbar.getHeight() + 8;
        if (y + popup.getHeight() > usable.y + usable.height) y = location.y - popup.getHeight() - 8;
        popup.setLocation(x, Math.max(usable.y, y));
        popup.setVisible(true);
    }

    private static JLabel label(int style, int size) {
        JLabel label = new JLabel();
        label.setFont(new Font("Microsoft YaHei UI", style, size));
        label.setForeground(new Color(33, 41, 52));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private static String html(String text) {
        return "<html><div style='width:260px'>" + text.replace("&", "&amp;")
            .replace("<", "&lt;").replace(">", "&gt;") + "</div></html>";
    }

    private static final class Hint {
        final String key, enTitle, zhTitle, enDescription, zhDescription, enUsage, zhUsage;
        Hint(String[] fields) {
            key = fields[0]; enTitle = fields[1]; zhTitle = fields[2];
            enDescription = fields[3]; zhDescription = fields[4]; enUsage = fields[5]; zhUsage = fields[6];
        }
    }
}
