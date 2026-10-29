import ij.IJ;
import ij.plugin.PlugIn;
import java.io.*;
import java.net.JarURLConnection;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

/** Installs embedded dictionaries and attaches to this running Fiji. */
public final class Fiji_Chinese_Display implements PlugIn {
    private static final String VERSION = "0.12";
    private static final Object LOCK = new Object();
    public void run(String argument) {
        // Attach initializes AWT on the EDT; never block that thread here.
        new Thread(() -> {
            try { enable(!"en".equals(argument)); }
            catch (Exception exception) {
                Throwable cause = exception instanceof java.lang.reflect.InvocationTargetException && exception.getCause() != null ? exception.getCause() : exception;
                IJ.log("[Fiji 中文] " + cause);
                IJ.showMessage("中文界面", "中文显示启用失败：" + cause.getMessage());
            }
        }, "Fiji-Chinese-Loader").start();
    }

    private static void enable(boolean chinese) throws Exception {
        synchronized (LOCK) {
            Class<?> agent = agent();
            if (agent == null || !Boolean.TRUE.equals(agent.getMethod("isActive").invoke(null))) {
                if (!chinese) return;
                JarURLConnection resource = (JarURLConnection) Fiji_Chinese_Display.class.getResource("Fiji_Chinese_Display.class").openConnection();
                Path jar = Paths.get(resource.getJarFileURL().toURI());
                Path root = jar.getParent().resolve("Fiji_Chinese_Display").resolve(VERSION);
                Files.createDirectories(root.resolve("locales"));
                Files.createDirectories(root.resolve("build"));
                for (String name : new String[]{"menus.zh_CN.tsv", "dialogs.zh_CN.tsv", "plugins.zh_CN.tsv", "toolbar-hints.tsv", "fontconfig.zh.properties"}) {
                    Path target = root.resolve("locales").resolve(name);
                    if (!Files.exists(target)) try (InputStream input = Fiji_Chinese_Display.class.getResourceAsStream("/locales/" + name)) {
                        if (input == null) throw new IOException("Missing embedded dictionary: " + name);
                        Files.copy(input, target);
                    }
                }
                Path java = Paths.get(System.getProperty("java.home"), "bin", "javaw.exe");
                Path log = root.resolve("build/attach.log");
                Process helper = new ProcessBuilder(java.toString(), "--add-modules=jdk.attach", "-cp", jar.toString(),
                    "org.fiji.zh.AttachHelper", Long.toString(ProcessHandle.current().pid()), jar.toString(), root.toString())
                    .redirectErrorStream(true).redirectOutput(log.toFile()).start();
                if (!helper.waitFor(40, TimeUnit.SECONDS)) {
                    helper.destroyForcibly();
                    throw new IOException("加载超时。日志：" + log);
                }
                if (helper.exitValue() != 0) throw new IOException("运行时加载失败。日志：" + log);
                agent = agent();
                if (agent != null) agent.getMethod("activate").invoke(null);
                if (agent == null || !Boolean.TRUE.equals(agent.getMethod("isActive").invoke(null)))
                    throw new IOException("适配层未完成初始化。日志：" + log);
            }
            agent.getMethod("setEnabled", boolean.class).invoke(null, chinese);
        }
    }

    private static Class<?> agent() {
        try { return Class.forName("org.fiji.zh.MenuDisplayAgent", false, ClassLoader.getSystemClassLoader()); }
        catch (ClassNotFoundException absent) { return null; }
    }
}
