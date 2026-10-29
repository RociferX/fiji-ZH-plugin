import ij.IJ;
import ij.plugin.PlugIn;

/** Plugin menu actions call the agent loaded by the JVM's system class loader. */
public final class Fiji_Chinese_Display implements PlugIn {
    public void run(String argument) {
        try {
            Class<?> agent = Class.forName("org.fiji.zh.MenuDisplayAgent", false, ClassLoader.getSystemClassLoader());
            if (!Boolean.TRUE.equals(agent.getMethod("isActive").invoke(null))) {
                IJ.showMessage("中文界面", "请通过工作区的中文启动器启动 Fiji。");
                return;
            }
            agent.getMethod("setEnabled", boolean.class).invoke(null, !"en".equals(argument));
        } catch (ReflectiveOperationException exception) {
            IJ.showMessage("中文界面", "请通过工作区的中文启动器启动 Fiji。\n" + exception.getMessage());
        }
    }
}
