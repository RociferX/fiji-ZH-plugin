import ij.*;
import ij.gui.*;
import ij.plugin.frame.RoiManager;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Starts through the original Fiji executable, without -javaagent or module flags. */
public final class Fiji_Runtime_Smoke {
    private static Path root;
    private static final java.util.List<String> checks = new ArrayList<>();
    public static String startPlugin(String mode) {
        new Thread(() -> {
            try {
                while (IJ.macroRunning()) Thread.sleep(50);
                EventQueue.invokeAndWait(() -> IJ.getInstance().setVisible(true));
                IJ.run("Use Chinese");
                Class<?> agent=Class.forName("org.fiji.zh.MenuDisplayAgent",false,ClassLoader.getSystemClassLoader());
                for(int i=0;i<150 && !Boolean.TRUE.equals(agent.getMethod("isActive").invoke(null));i++)Thread.sleep(100);
                if(!Boolean.TRUE.equals(agent.getMethod("isActive").invoke(null)))throw new IllegalStateException("Runtime attach failed");
                Thread.sleep(700);
                Class.forName("Fiji_Plugin_Robot").getMethod("start",String.class).invoke(null,mode);
            } catch(Throwable error) {error.printStackTrace();Runtime.getRuntime().halt(1);}
        },"Runtime-Plugin-Test").start();
        return "started";
    }
    public static String start(String directory) {
        root = Paths.get(directory);
        new Thread(() -> {
            try {
                while (IJ.macroRunning()) Thread.sleep(50);
                verify();
                finish(null);
            } catch (Throwable error) { finish(error); }
        }, "Runtime-Chinese-Test").start();
        return "started";
    }
    private static void verify() throws Exception {
        check(java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments().stream().noneMatch(s -> s.startsWith("-javaagent")), "Original launcher has no startup agent");
        // Force all three transformed classes to load before attach.
        for (String name : new String[]{"ij.gui.GenericDialog", "ij.gui.HTMLDialog", "ij.gui.MultiLineLabel"}) Class.forName(name);
        EventQueue.invokeAndWait(() -> IJ.getInstance().setVisible(true));
        RoiManager roi = new RoiManager();
        Map<?, ?> commands = new HashMap<>(Menus.getCommands());
        Class<?> agent = Class.forName("org.fiji.zh.MenuDisplayAgent", false, ClassLoader.getSystemClassLoader());
        check(!Boolean.TRUE.equals(agent.getMethod("isActive").invoke(null)), "Agent inactive before plugin command");
        IJ.run("Use Chinese");
        for (int i=0; i<150 && !Boolean.TRUE.equals(agent.getMethod("isActive").invoke(null)); i++) Thread.sleep(100);
        check(Boolean.TRUE.equals(agent.getMethod("isActive").invoke(null)), "Plugin attaches agent to running original Fiji");
        Thread.sleep(1000);
        Path home = Paths.get(System.getProperty("fiji.zh.home"));
        StringBuilder frames=new StringBuilder("IJ instance: "+IJ.getInstance().getClass().getName()+"\n");
        for(Frame frame:Frame.getFrames())frames.append(frame.getClass().getName()).append(" ").append(frame.getTitle()).append(" displayable=").append(frame.isDisplayable()).append(" menu=").append(frame.getMenuBar()).append('\n');
        Files.writeString(root.resolve("build/runtime-frames.txt"),frames.toString());
        check(Files.readString(home.resolve("build/menu-inventory.tsv")).contains("File\tFile\t文件"), "Existing main menu translated");
        check(Files.readString(home.resolve("build/dialog-inventory.tsv")).contains("ROI Manager"), "Existing ROI Manager inventoried");
        check(commands.equals(new HashMap<>(Menus.getCommands())), "Runtime activation preserves English command registry");
        Robot robot = new Robot(); robot.setAutoDelay(80);
        Frame main = IJ.getInstance();
        EventQueue.invokeAndWait(() -> { main.setLocation(140,100); main.setAlwaysOnTop(true); main.toFront(); });
        ImageIO.write(robot.createScreenCapture(main.getBounds()), "png", root.resolve("build/runtime-main.png").toFile());
        ImagePlus image=IJ.createImage("Area", "8-bit ramp", 48,48,1); image.show();
        byte[] pixels=((byte[])image.getProcessor().getPixels()).clone();
        Thread gaussian=new Thread(() -> IJ.run(image,"Gaussian Blur...","")); gaussian.start();
        Window dialog=waitDialog("Gaussian Blur");
        Window gaussianDialog=dialog;
        EventQueue.invokeAndWait(() -> { gaussianDialog.setAlwaysOnTop(true); gaussianDialog.toFront(); gaussianDialog.requestFocus(); });
        Thread.sleep(900);
        String inventory=Files.readString(home.resolve("build/dialog-inventory.tsv"));
        check(inventory.contains("高斯模糊") && inventory.contains("标准差"), "New core dialog translated after loaded-class retransformation");
        ImageIO.write(robot.createScreenCapture(dialog.getBounds()),"png",root.resolve("build/runtime-gaussian.png").toFile());
        Button cancel=null;
        for(Component child:children(dialog)) if(child instanceof Button && ((Button)child).getLabel().trim().equals("Cancel"))cancel=(Button)child;
        check(cancel!=null,"Cancel keeps English model");
        click(robot,cancel); gaussian.join(5000);
        check(!gaussian.isAlive() && Arrays.equals(pixels,(byte[])image.getProcessor().getPixels()),"Robot Cancel closes dialog without changing image");
        IJ.run("Channels Tool...");
        Window channels=waitDialog("Channels");
        Button help=null;
        for(Component child:children(channels))if(child instanceof Button && ((Button)child).getLabel().trim().equals("Help"))help=(Button)child;
        check(help!=null,"Channels help button retains original label");
        Window channelWindow=channels;
        EventQueue.invokeAndWait(() -> {channelWindow.setAlwaysOnTop(true);channelWindow.toFront();});
        click(robot,help);
        Window html=null;
        for(int i=0;i<100 && html==null;i++){
            for(Window w:Window.getWindows())if(w.isShowing() && w.getClass().getName().equals("ij.gui.HTMLDialog"))html=w;
            if(html==null)Thread.sleep(100);
        }
        check(html!=null,"Core HTML help opens after runtime attachment");
        Thread.sleep(900);
        check(Files.readString(home.resolve("build/dialog-inventory.tsv")).contains("多通道显示模式"),"HTML help translated after retransformation");
        javax.swing.JEditorPane pane=null;
        for(Component child:children(html))if(child instanceof javax.swing.JEditorPane)pane=(javax.swing.JEditorPane)child;
        check(pane!=null && pane.getText().contains("Composite Display Modes"),"Runtime HTML help preserves English document");
        ImageIO.write(robot.createScreenCapture(html.getBounds()),"png",root.resolve("build/runtime-help.png").toFile());
        Window helpWindow=html;EventQueue.invokeAndWait(() -> {helpWindow.dispose();channelWindow.dispose();});
        Thread error=new Thread(() -> IJ.error("Error","Gamma must be between 0.05 and 5.0"));error.start();
        Window message=waitDialog("Error");
        EventQueue.invokeAndWait(() -> {message.setAlwaysOnTop(true);message.toFront();});
        Thread.sleep(900);
        check(Files.readString(home.resolve("build/dialog-inventory.tsv")).contains("伽马值必须介于 0.05 和 5.0 之间"),"Already loaded message Canvas paints Chinese");
        ImageIO.write(robot.createScreenCapture(message.getBounds()),"png",root.resolve("build/runtime-message.png").toFile());
        for(Component child:children(message))if(child instanceof Button && ((Button)child).getLabel().trim().equals("OK")){click(robot,child);break;}
        error.join(5000);check(!error.isAlive(),"Robot closes translated message");
        // A user-created dialog with a core title must stay outside core ownership.
        NonBlockingGenericDialog custom=new NonBlockingGenericDialog("Gaussian Blur");
        custom.addMessage("Standard deviation:"); new Thread(custom::showDialog).start(); Thread.sleep(700);
        check(!Files.readString(home.resolve("build/dialog-inventory.tsv")).contains("Standard deviation:\t"),"User dialog is not enrolled by its title");
        custom.dispose();
        IJ.run("Use English"); Thread.sleep(900);
        check(!Boolean.TRUE.equals(agent.getMethod("isEnabled").invoke(null)),"Plugin restores English");
        IJ.run("Use Chinese"); Thread.sleep(900);
        check(Boolean.TRUE.equals(agent.getMethod("isEnabled").invoke(null)),"Second activation reuses active agent");
        check(commands.equals(new HashMap<>(Menus.getCommands())),"Repeated switching preserves command registry");
        image.changes=false; image.close(); roi.close();
    }
    private static Window waitDialog(String title)throws Exception {
        for(int i=0;i<100;i++){
            for(Window w:Window.getWindows())if(w.isShowing() && w instanceof Dialog && ((Dialog)w).getTitle().startsWith(title))return w;
            Thread.sleep(100);
        }
        throw new AssertionError("Missing dialog: "+title);
    }
    private static java.util.List<Component> children(Component source){
        java.util.List<Component> all=new ArrayList<>();all.add(source);
        if(source instanceof Container)for(Component c:((Container)source).getComponents())all.addAll(children(c));return all;
    }
    private static void click(Robot robot,Component control)throws Exception{
        Point at=control.getLocationOnScreen();robot.mouseMove(at.x+control.getWidth()/2,at.y+control.getHeight()/2);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);robot.waitForIdle();
    }
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks.add("PASS: "+message);}
    private static void finish(Throwable error){
        if(error!=null){error.printStackTrace();checks.add("FAIL: "+error);}
        try{Files.writeString(root.resolve("build/runtime-ij.log"),String.valueOf(IJ.getLog()));Files.write(root.resolve("build/runtime-result.txt"),checks);}catch(Exception ignored){}
        Runtime.getRuntime().halt(error==null?0:1);
    }
}
