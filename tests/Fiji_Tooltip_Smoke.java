import ij.IJ;
import ij.gui.Toolbar;
import java.awt.*;
import java.awt.event.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.JLabel;

/** Real mouse input against the unmodified ImageJ toolbar inside Fiji. */
public final class Fiji_Tooltip_Smoke {
    private static Toolbar toolbar;
    private static Robot robot;
    private static Method toolID, inGap, getName;
    private static Field names;

    public static void verify(java.util.List<String> checks) throws Exception {
        toolbar = Toolbar.getInstance();
        toolID = Toolbar.class.getDeclaredMethod("toolID", int.class);
        inGap = Toolbar.class.getDeclaredMethod("inGap", int.class);
        getName = Toolbar.class.getDeclaredMethod("getName", int.class);
        names = Toolbar.class.getDeclaredField("names");
        toolID.setAccessible(true); inGap.setAccessible(true); getName.setAccessible(true); names.setAccessible(true);
        robot = new Robot();
        robot.setAutoDelay(35);
        setTool("rectangle");
        setTool("hand");
        String[] originalNames = ((String[]) names.get(toolbar)).clone();
        leave();
        hover("rectangle");
        Thread.sleep(100);
        check(popup() == null, "Tooltip waits before appearing", checks);
        Thread.sleep(600);
        check(text().contains("矩形选区") && text().contains("Rectangle") && text().contains("测量"),
            "Chinese tooltip contains name, English term and purpose", checks);
        check(Toolbar.getToolName().equals("hand"), "Hover does not select a tool", checks);
        Window tip = popup();
        check(tip != null && !tip.getFocusableWindowState() && IJ.getInstance().isActive(),
            "Tooltip does not steal focus", checks);
        Rectangle capture = IJ.getInstance().getBounds().union(tip.getBounds());
        capture.grow(3, 3);
        ImageIO.write(robot.createScreenCapture(capture), "png", Paths.get("build/fiji-toolbar-tooltip.png").toFile());
        leave();
        for (int i = 0; i < 10 && popup() != null; i++) Thread.sleep(50);
        check(popup() == null, "Tooltip closes when pointer leaves", checks);

        String[][] modes = {
            {"roundrect", "圆角矩形"}, {"rotrect", "旋转矩形"}, {"oval", "椭圆选区"},
            {"ellipse", "旋转椭圆"}, {"brush", "选区画笔"}, {"polygon", "多边形"},
            {"freehand", "自由选区"}, {"line", "直线工具"}, {"arrow", "箭头工具"},
            {"polyline", "折线工具"}, {"freeline", "自由曲线"}, {"angle", "角度工具"},
            {"point", "点工具"}, {"multipoint", "多点工具"}, {"wand", "魔棒"},
            {"text", "文字工具"}, {"zoom", "放大镜"}, {"hand", "抓手"}, {"dropper", "取色器"}
        };
        for (String[] mode : modes) {
            leave();
            setTool(mode[0]);
            // Read-only hover is tested with another tool still selected.
            String selected = mode[0].equals("hand") ? "rectangle" : "hand";
            setTool(selected);
            hover(mode[0]);
            for (int attempt = 0; attempt < 20 && !text().contains(mode[1]); attempt++) Thread.sleep(100);
            if (!text().contains(mode[1])) throw new AssertionError("Missing tooltip: " + mode[0] + ": " + text() + state());
            if (!Toolbar.getToolName().equals(selected)) throw new AssertionError("Hover changed tool: " + mode[0]);
        }
        check(Arrays.equals(originalNames, (String[]) names.get(toolbar)), "Twenty tool variants keep original tool names", checks);
        checks.add("PASS: Chinese hover display for all twenty built-in tool variants.");

        leave();
        setTool("rectangle");
        setTool("hand");
        hover("rectangle");
        Thread.sleep(120);
        leave();
        Thread.sleep(600);
        check(popup() == null, "Leaving before delay cancels pending tooltip", checks);

        IJ.run("Use English");
        Thread.sleep(650);
        hover("rectangle");
        Thread.sleep(650);
        check(text().contains("Rectangle") && !hasChinese(text()), "Language plugin switches tooltips to English", checks);
        IJ.run("Use Chinese");
        Thread.sleep(650);
        Point main = IJ.getInstance().getLocationOnScreen();
        robot.mouseMove(main.x + 15, main.y + 95);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK); robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        leave(); hover("rectangle");
        Thread.sleep(650);
        check(text().contains("矩形选区"), "Language plugin restores Chinese tooltips", checks);
        check(IJ.getInstance().isActive(), "Fiji owns keyboard focus for Escape test", checks);
        robot.keyPress(KeyEvent.VK_ESCAPE); robot.keyRelease(KeyEvent.VK_ESCAPE);
        Thread.sleep(150);
        check(popup() == null, "Escape dismisses tooltip", checks);

        leave(); hover("rectangle");
        Thread.sleep(650);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK); robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        Thread.sleep(150);
        check(popup() == null && Toolbar.getToolName().equals("rectangle"), "Click dismisses tooltip and selects original tool", checks);

        leave();
        EventQueue.invokeAndWait(() -> toolbar.addTool("Untranslated Tooltip Probe"));
        hover("Untranslated Tooltip Probe");
        Thread.sleep(650);
        check(text().contains("Untranslated Tooltip Probe") && !hasChinese(text()), "Unknown plugin tool keeps original English name", checks);
        leave();
    }

    private static void setTool(String name) throws Exception {
        EventQueue.invokeAndWait(() -> {
            if (!toolbar.setTool(name)) throw new AssertionError("Cannot select test mode " + name);
        });
    }

    private static void hover(String key) throws Exception {
        int first = -1, last = -1;
        for (int x = 0; x < toolbar.getWidth(); x++) {
            if ((Boolean) inGap.invoke(toolbar, x)) continue;
            int id = (Integer) toolID.invoke(toolbar, x);
            String value = (String) getName.invoke(toolbar, id);
            String[] originals = (String[]) names.get(toolbar);
            if (id >= 10 && id < originals.length && originals[id] != null) value = originals[id];
            if (key.equals(value)) { if (first < 0) first = x; last = x; }
        }
        if (first < 0) throw new AssertionError("Toolbar has no button for " + key);
        Point location = toolbar.getLocationOnScreen();
        robot.mouseMove(location.x + (first + last) / 2, location.y + toolbar.getHeight() / 2);
        robot.waitForIdle();
    }

    private static void leave() {
        Point location = toolbar.getLocationOnScreen();
        robot.mouseMove(location.x + 5, location.y + toolbar.getHeight() + 160);
        robot.waitForIdle();
    }

    private static Window popup() throws Exception {
        AtomicReference<Window> result = new AtomicReference<>();
        EventQueue.invokeAndWait(() -> {
            for (Window window : Window.getWindows())
                if ("fiji.zh.toolbar.tooltip".equals(window.getName()) && window.isShowing()) result.set(window);
        });
        return result.get();
    }

    private static String text() throws Exception {
        Window window = popup();
        if (window == null) return "";
        AtomicReference<String> result = new AtomicReference<>();
        EventQueue.invokeAndWait(() -> result.set(text(window)));
        return result.get();
    }

    private static String text(Component component) {
        if (!component.isVisible()) return "";
        StringBuilder value = new StringBuilder();
        if (component instanceof JLabel) value.append(((JLabel) component).getText()).append('\n');
        if (component instanceof Container)
            for (Component child : ((Container) component).getComponents()) value.append(text(child));
        return value.toString();
    }

    private static boolean hasChinese(String text) { return text.codePoints().anyMatch(c -> c >= 0x4e00 && c <= 0x9fff); }
    private static String state() throws Exception {
        Field hintsField = org.fiji.zh.MenuDisplayAgent.class.getDeclaredField("toolbarHints");
        hintsField.setAccessible(true);
        Object hints = hintsField.get(null);
        StringBuilder result = new StringBuilder("; pointer=").append(MouseInfo.getPointerInfo().getLocation())
            .append("; toolbar=").append(toolbar.getLocationOnScreen()).append("/").append(toolbar.getSize())
            .append("; local=").append(toolbar.getMousePosition()).append("; active=").append(IJ.getInstance().isActive());
        for (String name : new String[]{"currentKey", "delay", "toolbar", "owner"}) {
            Field field = hints.getClass().getDeclaredField(name); field.setAccessible(true);
            result.append("; ").append(name).append("=").append(field.get(hints));
        }
        return result.toString();
    }
    private static void check(boolean pass, String label, java.util.List<String> checks) {
        if (!pass) throw new AssertionError(label + "; pointer=" + MouseInfo.getPointerInfo().getLocation() +
            "; toolbar=" + toolbar.getLocationOnScreen() + "/" + toolbar.getSize() +
            "; localPointer=" + toolbar.getMousePosition());
        checks.add("PASS: " + label);
    }
}
