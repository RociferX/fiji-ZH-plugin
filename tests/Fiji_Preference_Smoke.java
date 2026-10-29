import java.awt.*;
import java.awt.event.ActionEvent;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** An external test agent observes ordinary GUI launches without a startup macro. */
public final class Fiji_Preference_Smoke {
    public static void agentmain(String argument, Instrumentation instrumentation) {
        new Thread(() -> verify(argument, instrumentation), "Language-Restart-Test").start();
    }
    private static void verify(String argument, Instrumentation instrumentation) {
        String[] args=argument.split("\\|",2);
        String phase=args[0];Path root=Paths.get(args[1]);
        java.util.List<String> report=new ArrayList<>();
        try {
            Frame main=null;
            for(int i=0;i<200 && main==null;i++) {
                for(Class<?> type:instrumentation.getAllLoadedClasses())if(type.getName().equals("ij.IJ")) {
                    Object value=type.getMethod("getInstance").invoke(null);
                    if(value instanceof Frame && ((Frame)value).isShowing())main=(Frame)value;
                }
                if(main==null)Thread.sleep(100);
            }
            check(main!=null,"Original Fiji desktop visible without a startup macro",report);
            check(Arrays.stream(instrumentation.getAllLoadedClasses()).anyMatch(type -> type.getName().equals("org.fiji.zh.ChineseStartupService")),"Fiji discovers the language startup service",report);
            Class<?> agent=Class.forName("org.fiji.zh.MenuDisplayAgent",false,ClassLoader.getSystemClassLoader());
            Method active=agent.getMethod("isActive"), enabled=agent.getMethod("isEnabled");
            Path settings=root.resolve("Fiji/plugins/Fiji_Chinese_Display/settings.properties");
            if(phase.equals("first")) {
                check(!Boolean.TRUE.equals(active.invoke(null)),"First installation starts in English",report);
                select(main,"Use Chinese");
                waitSaved(settings,"zh");
                check(Boolean.TRUE.equals(active.invoke(null)),"Selecting Chinese activates and saves preference",report);
            } else if(phase.equals("chinese")) {
                for(int i=0;i<200 && !Boolean.TRUE.equals(active.invoke(null));i++)Thread.sleep(100);
                check(Boolean.TRUE.equals(active.invoke(null)) && Boolean.TRUE.equals(enabled.invoke(null)),"Restart restores Chinese without a menu command",report);
                Path home=Paths.get(System.getProperty("fiji.zh.home"));
                for(int i=0;i<30 && !Files.exists(home.resolve("build/menu-inventory.tsv"));i++)Thread.sleep(100);
                check(Files.readString(home.resolve("build/menu-inventory.tsv")).contains("File\tFile\t文件"),"Restarted main menu has Chinese display",report);
                Thread.sleep(600);capture(main,root.resolve("build/preference-chinese.png"));
                select(main,"Use English");waitSaved(settings,"en");
                for(int i=0;i<30 && Boolean.TRUE.equals(enabled.invoke(null));i++)Thread.sleep(100);
                check(!Boolean.TRUE.equals(enabled.invoke(null)),"Selecting English restores display and saves preference",report);
            } else {
                Thread.sleep(1500);
                check(!Boolean.TRUE.equals(active.invoke(null)),"Restart with English preference does not attach the agent",report);
                Properties saved=new Properties();try(java.io.InputStream input=Files.newInputStream(settings)){saved.load(input);}
                check("en".equals(saved.getProperty("language")),"English preference persists across restarts",report);
                capture(main,root.resolve("build/preference-english.png"));
            }
        } catch(Throwable error) {error.printStackTrace();report.add("FAIL: "+error);}
        try {Files.write(root.resolve("build/preference-"+phase+"-result.txt"),report);}catch(Exception error){error.printStackTrace();}
        Runtime.getRuntime().halt(report.stream().anyMatch(s->s.startsWith("FAIL:"))?1:0);
    }
    private static void select(Frame main,String command)throws Exception {
        MenuItem item=find(main.getMenuBar(),command);
        if(item==null)throw new AssertionError("Missing command: "+command);
        EventQueue.invokeAndWait(() -> item.dispatchEvent(new ActionEvent(item,ActionEvent.ACTION_PERFORMED,item.getActionCommand())));
    }
    private static MenuItem find(MenuComponent parent,String label) {
        if(parent instanceof MenuItem && ((MenuItem)parent).getLabel().equals(label))return (MenuItem)parent;
        int count=parent instanceof MenuBar?((MenuBar)parent).getMenuCount():parent instanceof Menu?((Menu)parent).getItemCount():0;
        for(int i=0;i<count;i++){
            MenuComponent child=parent instanceof MenuBar?((MenuBar)parent).getMenu(i):((Menu)parent).getItem(i);
            MenuItem match=find(child,label);if(match!=null)return match;
        }
        return null;
    }
    private static void waitSaved(Path file,String language)throws Exception {
        for(int i=0;i<200;i++){
            Properties saved=new Properties();
            if(Files.exists(file))try(java.io.InputStream input=Files.newInputStream(file)){saved.load(input);}
            if(language.equals(saved.getProperty("language")))return;
            Thread.sleep(100);
        }
        throw new AssertionError("Language was not saved: "+language);
    }
    private static void capture(Frame main,Path file)throws Exception {
        EventQueue.invokeAndWait(() -> {main.toFront();main.setAlwaysOnTop(true);});Thread.sleep(250);
        ImageIO.write(new Robot().createScreenCapture(main.getBounds()),"png",file.toFile());
    }
    private static void check(boolean condition,String message,java.util.List<String> report){
        if(!condition)throw new AssertionError(message);report.add("PASS: "+message);
    }
}
