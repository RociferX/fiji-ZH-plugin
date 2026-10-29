import ij.*;
import ij.gui.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.*;
import javax.swing.*;
import javax.imageio.ImageIO;
import org.fiji.zh.MenuDisplayAgent;

/** Incremental checks only for the new help documents, error templates and status messages. */
public final class Fiji_Message_Smoke {
 static final List<String> checks=new ArrayList<>();
 static Object mapper;static Method translate,status;static Robot robot;
 static String phase="all";
 public static String start(String selected){phase=selected;new Thread(()->{try{while(IJ.macroRunning())Thread.sleep(50);run();finish(null);}catch(Throwable ex){finish(ex);}},"Message-Only-Test").start();return "started";}
 static void run()throws Exception{
  Thread.sleep(1100);robot=new Robot();
  Field field=MenuDisplayAgent.class.getDeclaredField("dialogs");field.setAccessible(true);mapper=field.get(null);
  translate=mapper.getClass().getDeclaredMethod("translate",String.class,String.class);translate.setAccessible(true);
  status=mapper.getClass().getDeclaredMethod("translateStatus",String.class);status.setAccessible(true);
  if(phase.equals("native-help")){nativeHelp();return;}
  if(phase.equals("help")){embeddedHelp();return;}
  String path="C:\\实验数据\\Area {n} $1\\Mean 03.tif";
  String[][] errors={
   {"Directory not found: "+path,"没有找到目录："+path},
   {"Image is locked: \\\"Area\\\"".replace("\\\"","\""),"图像已锁定：“Area”"},
   {"Command not found:\\n \\n   \\\"Gaussian Blur...\\\"".replace("\\n","\n").replace("\\\"","\""),"未找到命令：“Gaussian Blur...”"},
   {"The command \\\"Area {value}\\\" is already installed.".replace("\\\"","\""),"命令“Area {value}”已经安装。"},
   {"More than 3 digits are required to generate \\nunique file names for 1200 images.".replace("\\n","\n"),"为 1200 幅图像生成互不重复的文件名，需要超过 3 位编号。"},
   {"Row 7 is not the same length\\nas the first row".replace("\\n","\n"),"第 7 行的长度与第一行不一致。"},
   {"Kernel must have odd width and height.\\nThis one is 4x6.".replace("\\n","\n"),"卷积核的宽和高必须为奇数；当前尺寸为 4x6。"},
   {"Unable to interpolate slice 12. More\\nintermediate ROIs needed.".replace("\\n","\n"),"无法插值生成第 12 张切片，需要更多中间位置的 ROI。"},
   {"The first file on the list does not exist:\\n \\n".replace("\\n","\n")+path,"列表中的第一个文件不存在："+path},
   {"The \\\"F7\\\" shortcut is in use.".replace("\\\"","\""),"快捷键“F7”已被占用。"},
   {"No particles detected. The assumed threshold (80-255) may not be correct.","未检测到粒子。推定阈值（80-255）可能不正确。"},
   {"Apply brightness and contrast settings\\nto all 14 slices in the stack?\\n \\nNOTE: There is no Undo for this operation.".replace("\\n","\n"),"将亮度与对比度设置应用到堆栈中的全部 14 张切片？\n\n提示：此操作无法撤销。"},
   {"Duplicate keyword:\\n \\n    Command: \\\"{value} Area\\\"\\n    Keyword: \\\"{command}\\\"\\n    Value: C:\\images\\Mean\\n \\nAdd an underscore to the corresponding label\\nin the dialog to make the first word unique.".replace("\\n","\n").replace("\\\"","\""),"参数键重复：\n命令：“{value} Area”\n参数键：“{command}”\n数值：C:\\images\\Mean\n\n请在对话框相应标签中添加下划线，使首个单词互不重复。"},
   {"This keyboard shortcut is already in use: Ctrl+M","此快捷键已被占用：Ctrl+M"},
   {"Gamma must be between 0.05 and 5.0","伽马值必须介于 0.05 和 5.0 之间。"},
   {"An error occured writing the file.\\n \\n".replace("\\n","\n")+path,"写入文件时出错。\n"+path}
  };
  for(String[] sample:errors)equal(tr(sample[0]),sample[1],"Error template: "+sample[0]);
  check(true,errors.length+" new error cases preserve file names, command keys, counts and literal braces");
  String[][] progress={
   {"Opening: "+path,"正在打开："+path},
   {"writing: "+path,"正在写入："+path},
   {"Saving 15 ROIs  to "+path,"正在将 15 个 ROI 保存到 "+path},
   {"FFT Filter: 3/15","FFT 滤波：3/15"},
   {"Convolve: 5x7 kernel","卷积：5×7 卷积核"},
   {"Drawing slice 7... (65% done)","正在绘制第 7 张切片…（已完成 65%）"},
   {"Done in 12.5 msec.","已完成，用时 12.5 毫秒。"},
   {"Lane 1 selected (vertical lanes)","已选择第 1 条泳道（纵向泳道）"},
   {"Lane 9 selected","已选择第 9 条泳道"},
   {"3 pixel values copied to clipboard","已将 3 个像素值复制到剪贴板"},
   {"Font settings saved (size=14, monospaced=true)","字体设置已保存（字号=14，等宽=true）"},
   {"Running command Gaussian Blur...","正在运行命令 Gaussian Blur..."},
   {"Menus updated: 1199 commands, 3 macros","菜单已更新：1199 个命令，3 个宏"},
   {"Regex error: [a-z","正则表达式错误：[a-z"},
   {"Optimization: Iteration 27","正在优化：第 27 次迭代"},
   {"Segmenting (Esc to cancel)","正在分割（按 Esc 取消）"},
   {"Reslice... (press 'Esc' to abort)","正在重新切片…（按 Esc 中止）"}
  };
  for(String[] sample:progress)equal(st(sample[0]),sample[1],"Status template: "+sample[0]);
  equal(st("User note Area: 3/9"),"User note Area: 3/9","Unknown status remains original");
  check(true,progress.length+" new status cases retain original numeric progress and data");
  runtimeError("Directory not found: "+path,"没有找到目录："+path);
  runtimeStatus("Saving 15 ROIs  to "+path,"正在将 15 个 ROI 保存到 "+path);
  embeddedHelp();
  nativeHelp();
 }
 static String tr(String input)throws Exception{return (String)translate.invoke(mapper,"Messages",input);}
 static void nativeHelp()throws Exception{
  String control="This plugin displays a panel with ImageJ commands in a hierarchical tree structure.\n \nUsage:\n \n     Click on a leaf node to launch the corresponding ImageJ command (or plugin)\n     (double-click on X Window Systems)\n \n     Double-click on a tree branch node (folder) to expand or collapse it\n \n     Click and drag on a tree branch node (folder) to display its descendants,\n     in a separate (child) panel (\"tear-off\" mock-up)\n \n     In a child panel, use the \"Show Parent\" menu item to re-open the parent panel\n     if it was accidentally closed\n \nAuthor: Cezar M. Tigaret (c.tigaret@ucl.ac.uk)\nThis code is in the public domain.";
  String tools="Use this drop down menu to switch to alternative\nmacro toolsets or to load additional plugin tools.\nThe toolsets listed in the menu are located\nin the ImageJ/macros/toolsets folder and the\nplugin tools are the ones installed in the\nPlugins>Tools submenu.\n \nHold the shift key down while selecting a\ntoolset to view its source code.\n \nMore macro toolsets are available at\n  <http://imagej.net/ij/macros/toolsets/>\n \nPlugin tools can be downloaded from\nthe Tools section of the Plugins page at\n  <http://imagej.net/ij/plugins/>\n";
  for(String[] sample:new String[][]{{"About Control Panel...",control,"控制面板以层级树展示","control"},{"Tool Switcher and Loader",tools,"按住 Shift","tools"}}){
   Thread t=new Thread(()->IJ.showMessage(sample[0],sample[1]));t.start();Window w=waitWindow(c->c instanceof MessageDialog);
   awaitInventory(sample[2]);check(true,"Native help body translated: "+sample[3]);
   Field lines=Class.forName("ij.gui.MultiLineLabel").getDeclaredField("lines");lines.setAccessible(true);
   for(Component c:children(w))if(c.getClass().getName().equals("ij.gui.MultiLineLabel"))equal(String.join("\n",(String[])lines.get(c)),sample[1].stripTrailing(),"Native help retains original source: "+sample[3]);
   capture(w,"help-"+sample[3]);close(w);t.join(3000);check(!t.isAlive(),"Native help closes normally: "+sample[3]);
  }
 }
 static String st(String input)throws Exception{return (String)status.invoke(mapper,input);}
 static void runtimeError(String message,String expected)throws Exception{
  Thread command=new Thread(()->IJ.error("Import",message),"Error-Preview");command.start();Window w=waitWindow(c->c instanceof MessageDialog);
  awaitInventory(expected);String inventory=Files.readString(Paths.get("build/dialog-inventory.tsv"),StandardCharsets.UTF_8);check(inventory.contains(expected),"Dynamic error is mapped in a real ImageJ message window");
  Component body=null;Field lines=Class.forName("ij.gui.MultiLineLabel").getDeclaredField("lines");lines.setAccessible(true);
  for(Component c:children(w))if(c.getClass().getName().equals("ij.gui.MultiLineLabel")){body=c;break;}
  equal(String.join("\n",(String[])lines.get(body)),message,"Error body API remains original");
  BufferedImage chinese=render(body);MenuDisplayAgent.setEnabled(false);Thread.sleep(650);equal(tr(message),message,"English mode returns the unchanged dynamic error");BufferedImage english=render(body);
  check(!same(chinese,english),"Dynamic error body paints different Chinese and English text");MenuDisplayAgent.setEnabled(true);Thread.sleep(650);
  ImageIO.write(chinese,"png",Paths.get("build/messages-error-body.png").toFile());capture(w,"error");close(w);command.join(3000);check(!command.isAlive(),"Closing the mapped message completes the original error call");
 }
 static void runtimeStatus(String message,String expected)throws Exception{
  IJ.showStatus(message);Thread.sleep(650);equal(ImageJ.getStatusBarText(),message,"Progress API stays original");equal(st(message),expected,"Runtime progress retains its original path");
  Field f=ImageJ.class.getDeclaredField("statusLine");f.setAccessible(true);JLabel label=(JLabel)f.get(IJ.getInstance());
  AtomicInteger dirty=new AtomicInteger();RepaintManager old=RepaintManager.currentManager(label);RepaintManager observer=new RepaintManager(){public void addDirtyRegion(JComponent c,int x,int y,int width,int height){if(c==label)dirty.incrementAndGet();super.addDirtyRegion(c,x,y,width,height);}};
  EventQueue.invokeAndWait(()->RepaintManager.setCurrentManager(observer));Thread.sleep(1600);check(dirty.get()==0,"New dynamic status stays stable without recurring repaints");EventQueue.invokeAndWait(()->RepaintManager.setCurrentManager(old));
 }
 static void embeddedHelp()throws Exception{
  ImagePlus image=new ImagePlus("Mean",new ij.process.ByteProcessor(24,24));image.show();image.setRoi(new Roi(2,2,10,10));
  if(!phase.equals("help"))helpFromCommand(image,"Coordinates...","xOrigin","角点坐标","coordinates");
  ImageStack stack=new ImageStack(24,24);stack.addSlice(image.getProcessor().duplicate());stack.addSlice(image.getProcessor().duplicate());ImagePlus virtual=new ImagePlus("Area",stack);virtual.show();
  helpFromCommand(virtual,"Virtual Stack...","SaveBatchOutput","虚拟堆栈批处理","batch");
  ij.measure.ResultsTable table=new ij.measure.ResultsTable();table.incrementCounter();table.setValue("Area",0,1);
  Thread t=new Thread(()->new ij.measure.ResultsTableMacros(table),"Table-Help");t.start();Window parameter=waitWindow(c->c instanceof GenericDialog);
  openHelp(parameter);verifyHtml("Macro Equations for Results Tables","结果表的宏运算","table");close(parameter);t.join(3000);
  ij.plugin.frame.Recorder recorder=new ij.plugin.frame.Recorder();Thread.sleep(600);openHelp(recorder);Window help=waitWindow(c->c instanceof MessageDialog);
  Thread.sleep(700);String inventory=Files.readString(Paths.get("build/dialog-inventory.tsv"),StandardCharsets.UTF_8);check(inventory.contains("点击“创建”"),"Recorder help is translated in its original message window");capture(help,"recorder");close(help);recorder.close();
  ij.plugin.CommandFinder finder=new ij.plugin.CommandFinder();Thread finderThread=new Thread(()->finder.run(""));finderThread.start();
  Field helpField=ij.plugin.CommandFinder.class.getDeclaredField("helpButton");helpField.setAccessible(true);
  for(int i=0;i<100&&helpField.get(finder)==null;i++)Thread.sleep(50);JButton button=(JButton)helpField.get(finder);EventQueue.invokeLater(button::doClick);
  verifyHtml("Alphabetic scroll","命令查找快捷键","finder");
 }
 static void helpFromCommand(ImagePlus image,String name,String original,String chinese,String id)throws Exception{
  Thread thread=new Thread(()->new Executer(name).run(),"Help-"+name);WindowManager.setTempCurrentImage(thread,image);thread.start();Window parameter=waitWindow(c->c instanceof GenericDialog);
  Thread.sleep(600);openHelp(parameter);verifyHtml(original,chinese,id);close(parameter);thread.join(3000);check(!thread.isAlive(),"New help closes and returns to "+name);
 }
 static void openHelp(Window w)throws Exception{
  EventQueue.invokeLater(()->{for(Component c:children(w))if(c instanceof Button&&((Button)c).getLabel().trim().equals("Help")){Button b=(Button)c;b.dispatchEvent(new ActionEvent(b,ActionEvent.ACTION_PERFORMED,b.getActionCommand()));return;}});
 }
 static void verifyHtml(String original,String expected,String id)throws Exception{
  Window w=waitWindow(c->c.getClass().getName().equals("ij.gui.HTMLDialog"));Thread.sleep(750);
  JEditorPane pane=null;for(Component c:children(w))if(c instanceof JEditorPane){pane=(JEditorPane)c;break;}
  check(pane!=null&&pane.getText().contains(original),"Original embedded help document preserved: "+id);
  awaitInventory(expected);String inventory=Files.readString(Paths.get("build/dialog-inventory.tsv"),StandardCharsets.UTF_8);check(inventory.contains(expected),"Chinese embedded help is selected: "+id);
  BufferedImage chinese=render(pane);MenuDisplayAgent.setEnabled(false);Thread.sleep(650);BufferedImage english=render(pane);
  check(!same(chinese,english),"Chinese/English help rendering differs: "+id);MenuDisplayAgent.setEnabled(true);Thread.sleep(650);
  Dimension screen=Toolkit.getDefaultToolkit().getScreenSize();check(w.getWidth()<=screen.width&&w.getHeight()<=screen.height,"New help preserves its screen-sized viewport: "+id);capture(w,"help-"+id);close(w);
 }
 static void awaitInventory(String expected)throws Exception{
  for(int i=0;i<120;i++){Path p=Paths.get("build/dialog-inventory.tsv");if(Files.exists(p)&&Files.readString(p,StandardCharsets.UTF_8).contains(expected))return;Thread.sleep(50);}
  throw new AssertionError("Message mapping did not become ready for "+expected);
 }
 static Window waitWindow(java.util.function.Predicate<Window> wanted)throws Exception{
  for(int i=0;i<160;i++){for(Window w:Window.getWindows())if(w.isShowing()&&wanted.test(w))return w;Thread.sleep(50);}throw new AssertionError("Expected help/message window did not open");
 }
 static List<Component> children(Component c){List<Component> result=new ArrayList<>();result.add(c);if(c instanceof Container)for(Component child:((Container)c).getComponents())result.addAll(children(child));return result;}
 static void close(Window w)throws Exception{
  EventQueue.invokeAndWait(()->{for(Component c:children(w))if(c instanceof Button&&Set.of("Cancel","OK").contains(((Button)c).getLabel().trim())){Button b=(Button)c;b.dispatchEvent(new ActionEvent(b,ActionEvent.ACTION_PERFORMED,b.getActionCommand()));return;}w.dispatchEvent(new WindowEvent(w,WindowEvent.WINDOW_CLOSING));});Thread.sleep(150);
 }
 static BufferedImage render(Component c)throws Exception{BufferedImage image=new BufferedImage(c.getWidth(),c.getHeight(),BufferedImage.TYPE_INT_RGB);EventQueue.invokeAndWait(()->{Graphics g=image.getGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,image.getWidth(),image.getHeight());c.printAll(g);g.dispose();});return image;}
 static boolean same(BufferedImage a,BufferedImage b){return a.getWidth()==b.getWidth()&&a.getHeight()==b.getHeight()&&Arrays.equals(a.getRGB(0,0,a.getWidth(),a.getHeight(),null,0,a.getWidth()),b.getRGB(0,0,b.getWidth(),b.getHeight(),null,0,b.getWidth()));}
 static void capture(Window w,String name)throws Exception{EventQueue.invokeAndWait(()->{w.setAlwaysOnTop(true);w.toFront();});Thread.sleep(150);ImageIO.write(robot.createScreenCapture(w.getBounds()),"png",Paths.get("build/messages-"+name+".png").toFile());}
 static void equal(String actual,String expected,String note){if(!Objects.equals(actual,expected))throw new AssertionError(note+" expected="+expected+" actual="+actual);}
 static void check(boolean pass,String note){if(!pass)throw new AssertionError(note);checks.add("PASS: "+note);System.out.println("PASS: "+note);}
 static void finish(Throwable failure){if(failure!=null){failure.printStackTrace();checks.add("FAIL: "+failure);}try{Files.write(Paths.get("build/messages-"+phase+"-result.txt"),checks,StandardCharsets.UTF_8);}catch(Exception ex){ex.printStackTrace();}Runtime.getRuntime().halt(failure==null?0:1);}
}
