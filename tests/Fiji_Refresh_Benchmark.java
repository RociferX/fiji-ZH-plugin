import ij.IJ;
import ij.plugin.frame.RoiManager;
import java.awt.*;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Measures the real EDT refresh path after normal plugin activation. */
public final class Fiji_Refresh_Benchmark {
    private static final java.util.List<String> report = new ArrayList<>();
    private static Method refresh;
    private static Path home;

    public static String start(String argument) {
        String[] args = argument.split("\\|", 2);
        new Thread(() -> {
            try {
                while (IJ.macroRunning()) Thread.sleep(50);
                EventQueue.invokeAndWait(() -> IJ.getInstance().setVisible(true));
                IJ.run("Use Chinese");
                Class<?> agent = Class.forName("org.fiji.zh.MenuDisplayAgent", false, ClassLoader.getSystemClassLoader());
                for (int i = 0; i < 150 && !Boolean.TRUE.equals(agent.getMethod("isActive").invoke(null)); i++) Thread.sleep(100);
                check(Boolean.TRUE.equals(agent.getMethod("isActive").invoke(null)), "Normal launcher activates plugin");
                refresh = agent.getDeclaredMethod("refresh"); refresh.setAccessible(true);
                home = Paths.get(System.getProperty("fiji.zh.home"));
                Thread.sleep(1000);
                measure("main");
                EventQueue.invokeAndWait(() -> { new RoiManager(); new loci.plugins.config.LociConfig().run(""); });
                IJ.run("Channels Tool...");
                Thread.sleep(1000);
                measure("multi-window");
                verifyDynamic(agent);
            } catch (Throwable error) { error.printStackTrace(); report.add("FAIL: " + error); }
            try { Files.write(Paths.get(args[0], "build", "refresh-" + args[1] + "-result.txt"), report, StandardCharsets.UTF_8); }
            catch (Exception error) { error.printStackTrace(); }
            Runtime.getRuntime().halt(report.stream().anyMatch(s -> s.startsWith("FAIL:")) ? 1 : 0);
        }, "Refresh-Benchmark").start();
        return "started";
    }

    private static void measure(String scenario) throws Exception {
        com.sun.management.ThreadMXBean bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        if (!bean.isThreadAllocatedMemoryEnabled()) bean.setThreadAllocatedMemoryEnabled(true);
        long[] wall = new long[120], allocated = new long[120];
        for (int i = -30; i < wall.length; i++) {
            final int sample = i;
            EventQueue.invokeAndWait(() -> {
                try {
                    long id = Thread.currentThread().getId();
                    long bytes = bean.getThreadAllocatedBytes(id), start = System.nanoTime();
                    refresh.invoke(null);
                    if (sample >= 0) {
                        wall[sample] = System.nanoTime() - start;
                        allocated[sample] = bean.getThreadAllocatedBytes(id) - bytes;
                    }
                } catch (Exception error) { throw new RuntimeException(error); }
            });
        }
        Arrays.sort(wall);
        report.add(String.format(Locale.ROOT, "MEASURE: %s samples=%d wall-median-ms=%.3f wall-p95-ms=%.3f allocated-mean-KiB=%.1f",
            scenario, wall.length, wall[wall.length / 2] / 1e6, wall[(int)(wall.length * .95)] / 1e6,
            Arrays.stream(allocated).average().orElse(0) / 1024));
    }

    private static void verifyDynamic(Class<?> agent) throws Exception {
        Path dictionary = home.resolve("locales/menus.zh_CN.tsv");
        byte[] original = Files.readAllBytes(dictionary);
        Menu menu = IJ.getInstance().getMenuBar().getMenu(0);
        MenuItem probe = new MenuItem("Refresh performance probe");
        probe.setActionCommand("original-refresh-probe");
        try {
            EventQueue.invokeAndWait(() -> menu.add(probe));
            Files.writeString(dictionary, "\nRefresh performance probe\t刷新测试\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
            waitInventory("Refresh performance probe\tRefresh performance probe\t刷新测试\toriginal-refresh-probe");
            check(probe.getLabel().equals("Refresh performance probe") && probe.getActionCommand().equals("original-refresh-probe"), "Dynamic menu and dictionary reload preserve original model");
            // Changes in existing controls must still be observed without reopening a window.
            RoiManager roi = RoiManager.getInstance();
            Label label = new Label("Help");
            EventQueue.invokeAndWait(() -> { roi.add(label, BorderLayout.SOUTH); roi.validate(); });
            waitDialog("Label\tHelp\t帮助");
            EventQueue.invokeAndWait(() -> label.setText("Cancel"));
            waitDialog("Label\tCancel\t取消");
            check(true, "Existing window picks up added controls and changed text");
            agent.getMethod("setEnabled", boolean.class).invoke(null, false);
            waitInventory("Refresh performance probe\tRefresh performance probe\tRefresh performance probe\toriginal-refresh-probe");
            check(true, "English switch immediately restores dynamic menu");
            agent.getMethod("setEnabled", boolean.class).invoke(null, true);
            waitInventory("Refresh performance probe\tRefresh performance probe\t刷新测试\toriginal-refresh-probe");
            check(true, "Chinese switch immediately restores dynamic translation");
        } finally {
            Files.write(dictionary, original);
            EventQueue.invokeAndWait(() -> menu.remove(probe));
        }
    }

    private static void waitInventory(String expected) throws Exception { waitFile(home.resolve("build/menu-inventory.tsv"), expected); }
    private static void waitDialog(String expected) throws Exception { waitFile(home.resolve("build/dialog-inventory.tsv"), expected); }
    private static void waitFile(Path file, String expected) throws Exception {
        for (int i = 0; i < 30; i++) {
            if (Files.exists(file) && Files.readString(file).contains(expected)) return;
            Thread.sleep(100);
        }
        throw new AssertionError("Refresh did not observe: " + expected);
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        report.add("PASS: " + message);
    }
}
