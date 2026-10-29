import ij.*;
import ij.measure.ResultsTable;
import ij.plugin.frame.Recorder;
import ij.process.ByteProcessor;
import java.awt.*;
import java.awt.event.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.fiji.zh.MenuDisplayAgent;

/** Runs inside the actual Fiji distribution through tests/verify.ijm. */
public final class Fiji_Chinese_Smoke {
    public static String start() {
        new Thread(() -> {
            try { while (IJ.macroRunning()) Thread.sleep(100); }
            catch (InterruptedException exception) { throw new RuntimeException(exception); }
            verify();
        }, "Fiji-Chinese-Integration-Test").start();
        return "started";
    }
    public static String verify() {
        java.util.List<String> checks = new ArrayList<>();
        try {
            check(MenuDisplayAgent.isActive(), "Agent active in real Fiji", checks);
            check("UTF-8".equals(System.getProperty("file.encoding")), "Default file encoding stays UTF-8", checks);
            FontMetrics monospace = new Canvas().getFontMetrics(new Font("Monospaced", Font.PLAIN, 12));
            check(monospace.charWidth('i') == monospace.charWidth('W'), "Monospaced Latin text stays monospaced", checks);
            Thread.sleep(1600);
            Map<?, ?> commands = new HashMap<>(Menus.getCommands());
            check(commands.containsKey("Gaussian Blur...") && commands.containsKey("Measure"), "English command keys", checks);
            MenuItem gaussian = find(Menus.getMenuBar(), "Gaussian Blur...");
            check(gaussian != null && gaussian.getLabel().equals("Gaussian Blur..."), "Original real menu label", checks);
            MenuDisplayAgent.setEnabled(false);
            Thread.sleep(800);
            String english = analyze("mapping-English");
            MenuDisplayAgent.setEnabled(true);
            Thread.sleep(800);
            String chinese = analyze("mapping-Chinese");
            check(english.equals(chinese), "English/Chinese macro analysis and output fields match", checks);
            check(commands.equals(new HashMap<>(Menus.getCommands())), "Command registry unchanged after language switch", checks);
            Recorder recorder = new Recorder();
            ImagePlus image = new ImagePlus("mapping-recorder-test", new ByteProcessor(16, 16));
            image.show();
            image.setRoi(2, 3, 5, 6);
            MenuItem measure = find(Menus.getMenuBar(), "Measure");
            EventQueue.invokeAndWait(() -> measure.dispatchEvent(new ActionEvent(measure,
                ActionEvent.ACTION_PERFORMED, measure.getActionCommand())));
            for (int i = 0; i < 80 && !recorder.getText().contains("Measure"); i++) Thread.sleep(100);
            String recorded = recorder.getText();
            Files.writeString(Paths.get("build/recorded-macro.txt"), recorded, StandardCharsets.UTF_8);
            check(recorded.contains("Measure"), "Recorder retains English Measure command", checks);
            recorder.close();
            image.changes = false;
            image.close();
            Menus.updateMenus();
            Thread.sleep(1600);
            check(find(Menus.getMenuBar(), "Gaussian Blur...").getLabel().equals("Gaussian Blur..."),
                "Menu rebuild retains English model", checks);
            check(commands.keySet().equals(Menus.getCommands().keySet()), "Command keys survive menu rebuild", checks);
            Frame main = IJ.getInstance();
            EventQueue.invokeAndWait(() -> { main.setLocation(160, 120); main.setAlwaysOnTop(true); main.toFront(); main.requestFocus(); });
            Thread.sleep(300);
            Fiji_Tooltip_Smoke.verify(checks);
            Robot robot = new Robot();
            robot.setAutoDelay(150);
            ImageIO.write(robot.createScreenCapture(main.getBounds()), "png", Paths.get("build/fiji-chinese.png").toFile());
            Point p = main.getLocationOnScreen();
            robot.mouseMove(p.x + 15, p.y + 95);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK); robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseMove(p.x + 25, p.y + 40);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK); robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            checks.add("UI focus after native menu click: " + main.isActive() + "; insets: " + main.getInsets());
            Thread.sleep(300);
            ImageIO.write(robot.createScreenCapture(new Rectangle(p.x, p.y, Math.max(main.getWidth(), 650), 820)),
                "png", Paths.get("build/fiji-chinese-file-menu.png").toFile());
            robot.keyPress(KeyEvent.VK_ESCAPE); robot.keyRelease(KeyEvent.VK_ESCAPE);
            checks.add("ImageJ " + IJ.getFullVersion() + "; Java " + System.getProperty("java.version"));
            checks.add("PASS: All real-Fiji compatibility checks completed.");
            Files.write(Paths.get("build/fiji-smoke-result.txt"), checks, StandardCharsets.UTF_8);
            System.out.println(String.join("\n", checks));
            Runtime.getRuntime().halt(0);
            return "PASS";
        } catch (Throwable failure) {
            checks.add("FAIL: " + failure);
            try { Files.write(Paths.get("build/fiji-smoke-result.txt"), checks, StandardCharsets.UTF_8); }
            catch (Exception ignored) { failure.printStackTrace(); }
            failure.printStackTrace();
            Runtime.getRuntime().halt(1);
            return "FAIL";
        }
    }

    private static String analyze(String title) {
        byte[] pixels = new byte[64 * 64];
        for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++)
            if ((x-18)*(x-18)+(y-18)*(y-18) <= 64 || (x-45)*(x-45)+(y-44)*(y-44) <= 36)
                pixels[y*64+x] = (byte) 200;
        ImagePlus image = new ImagePlus(title, new ByteProcessor(64, 64, pixels));
        image.show();
        IJ.run(image, "Gaussian Blur...", "sigma=1");
        IJ.run(image, "Set Measurements...", "area mean centroid redirect=None decimal=4");
        image.getProcessor().setThreshold(100, 255, ij.process.ImageProcessor.NO_LUT_UPDATE);
        IJ.run(image, "Analyze Particles...", "size=10-Infinity circularity=0.00-1.00 show=Nothing display clear");
        ResultsTable table = ResultsTable.getResultsTable();
        if (table.size() != 2) throw new AssertionError("Expected two particles, got " + table.size());
        String result = table.getColumnHeadings() + "\n" + table.getRowAsString(0) + "\n" + table.getRowAsString(1);
        image.changes = false;
        image.close();
        return result;
    }

    private static MenuItem find(MenuBar bar, String label) {
        for (int i = 0; i < bar.getMenuCount(); i++) {
            MenuItem result = find(bar.getMenu(i), label);
            if (result != null) return result;
        }
        return null;
    }

    private static MenuItem find(MenuItem item, String label) {
        if (label.equals(item.getLabel())) return item;
        if (item instanceof Menu) {
            Menu menu = (Menu) item;
            for (int i = 0; i < menu.getItemCount(); i++) {
                MenuItem result = find(menu.getItem(i), label);
                if (result != null) return result;
            }
        }
        return null;
    }

    private static void check(boolean pass, String label, java.util.List<String> checks) {
        if (!pass) throw new AssertionError(label);
        checks.add("PASS: " + label);
    }
}
