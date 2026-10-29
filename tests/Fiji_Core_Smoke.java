import ij.*;
import ij.gui.*;
import java.awt.*;
import java.awt.event.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.*;
import javax.swing.*;
import org.fiji.zh.MenuDisplayAgent;
public class Fiji_Core_Smoke {
 static java.util.List<String> checks=new ArrayList<>();
 public static String status(){new Thread(()->{try{while(IJ.macroRunning())Thread.sleep(50);statusTest();finish(null,"status");}catch(Throwable ex){finish(ex,"status");}},"Status-Test").start();return "started";}
 public static String registry(){new Thread(()->{try{while(IJ.macroRunning())Thread.sleep(50);java.util.List<String> values=new ArrayList<>();Menus.getCommands().forEach((k,v)->values.add(k+"\t"+v));Collections.sort(values);Files.write(Paths.get("build/command-providers.tsv"),values,StandardCharsets.UTF_8);finish(null,"registry");}catch(Throwable ex){finish(ex,"registry");}}).start();return "started";}
 public static String probe(){new Thread(()->{try{while(IJ.macroRunning())Thread.sleep(50);probeDialogs();finish(null,"probe");}catch(Throwable ex){finish(ex,"probe");}},"Core-UI-Probe").start();return "started";}
 static void probeDialogs()throws Exception{
  Thread.sleep(900);
  java.nio.file.Path root=Paths.get("build/core-fixtures").toAbsolutePath();Files.createDirectories(root);Files.write(root.resolve("raw-data.raw"),new byte[1024]);
  ImageStack pixels=new ImageStack(64,64);for(int z=0;z<3;z++){ij.process.ByteProcessor ip=new ij.process.ByteProcessor(64,64);for(int i=0;i<4096;i++)ip.set(i,(i*7+z*31)%255);pixels.addSlice(ip);}
  ImagePlus image=new ImagePlus("Area",pixels);image.show();ImagePlus other=new ImagePlus("Mean",pixels.duplicate());other.show();image.setRoi(new Roi(5,5,20,20));
  String[] commands={"Image...","Hyperstack...","Image Sequence...","AVI...","Image Sequence... ","AVI... ",
   "Make Montage...","Reslice [/]...","3D Project...","Animation Options...","Set Slice...","Combine...","Insert...","Reduce...","Montage to Stack...","Images to Stack",
   "Enlarge...","Make Band...","Specify...","Scale... ","Rotate...","Translate... ",
   "Add...","Subtract...","Multiply...","Divide...","AND...","OR...","XOR...","Min...","Max...","Gamma...","Set...","Macro...",
   "Find Maxima...","FFT Options...","FD Math...","Custom Filter...","Histogram","Calibrate...","Surface Plot...","Grid...","Calibration Bar...","Fractal Box Count...","Gel Analyzer Options...",
   "Convert...","Macro... ","Virtual Stack...","Page Setup...","Add Shortcut... ","New..."};
  for(int index=0;index<commands.length;index++){
   String command=commands[index];if(command.equals("New..."))continue;
   if(command.equals("Images to Stack"))continue;
   image.setRoi(new Roi(5,5,20,20));
   final String cmd=command;Thread t=new Thread(()->new Executer(cmd).run(),"Core-Probe-"+cmd);WindowManager.setTempCurrentImage(t,image);t.start();
   Window w=waitDialog();capture(w,"core-"+index);dismiss(w);t.join(5000);if(t.isAlive())throw new AssertionError("Command not closed: "+cmd);
  }
  Thread raw=new Thread(()->new ij.io.ImportDialog("raw-data.raw",root.toString()+java.io.File.separator).openImage());raw.start();Window w=waitDialog();capture(w,"core-raw");dismiss(w);raw.join(5000);
  check(true,"Remaining ImageJ core dialogs were opened and inventoried");
 }
 static Window waitDialog()throws Exception{
  for(int i=0;i<160;i++){
   for(Window w:Window.getWindows())if(w.isShowing() && w instanceof Dialog)return w;
   Thread.sleep(50);
  }throw new AssertionError("No dialog opened");
 }
 static java.util.List<Component> children(Component c){java.util.List<Component> result=new ArrayList<>();result.add(c);if(c instanceof Container)for(Component ch:((Container)c).getComponents())result.addAll(children(ch));return result;}
 static void dismiss(Window w)throws Exception{
  EventQueue.invokeAndWait(()->{
   for(Component c:children(w))if(c instanceof Button && ((Button)c).getLabel().trim().equals("Cancel")){Button b=(Button)c;b.dispatchEvent(new ActionEvent(b,ActionEvent.ACTION_PERFORMED,b.getActionCommand()));return;}
   if(w instanceof ij.gui.MessageDialog)for(Component c:children(w))if(c instanceof Button){Button b=(Button)c;b.dispatchEvent(new ActionEvent(b,ActionEvent.ACTION_PERFORMED,b.getActionCommand()));return;}
   w.dispatchEvent(new WindowEvent(w,WindowEvent.WINDOW_CLOSING));
  });Thread.sleep(200);
 }
 static void capture(Window w,String name)throws Exception{
  Thread.sleep(850);EventQueue.invokeAndWait(()->{});
  String title=w instanceof Dialog?((Dialog)w).getTitle():((Frame)w).getTitle();
  String context=title.trim();if(context.endsWith("..."))context=context.substring(0,context.length()-3).trim();
  if(w instanceof MessageDialog || w instanceof YesNoCancelDialog)context="Messages";
  if(w.getClass().getName().equals("ij.gui.HTMLDialog"))context="Help/"+context;
  switch(w.getClass().getName()){
   case "ij.plugin.frame.Editor":context="Script Editor";break;
   case "ij.gui.HistogramWindow":context="Histogram Window";break;
   case "ij.gui.PlotWindow":context="Plot Window";break;
   case "org.scijava.ui.swing.script.TextEditor":context="Fiji Script Editor";break;
  }
  String marker=context+"\t";boolean ready=false;
  for(int i=0;i<100;i++){
   Path report=Paths.get("build/dialog-inventory.tsv");if(Files.exists(report)&&Files.readString(report,StandardCharsets.UTF_8).contains(marker)){ready=true;break;}Thread.sleep(50);
  }
  if(!ready)throw new AssertionError("Translation not ready: "+title);
  System.out.println("CORE "+name+" "+w.getClass().getName()+" "+title);
  Path inventory=Paths.get("build/dialog-inventory.tsv");if(Files.exists(inventory))Files.copy(inventory,Paths.get("build/dialog-"+name+".tsv"),StandardCopyOption.REPLACE_EXISTING);
  EventQueue.invokeAndWait(()->{w.validate();w.toFront();});Thread.sleep(100);javax.imageio.ImageIO.write(new Robot().createScreenCapture(w.getBounds()),"png",Paths.get("build/dialog-"+name+".png").toFile());
 }
 public static String settings(){new Thread(()->{try{while(IJ.macroRunning())Thread.sleep(50);settingsProbe();finish(null,"settings");}catch(Throwable ex){finish(ex,"settings");}},"Official-Settings-Probe").start();return "started";}
 static void settingsProbe()throws Exception{
  Thread.sleep(900);java.util.List<String> inventory=new ArrayList<>();
  String[] commands={"Python...","Look and Feel...","ImageJ2...","Search Bar...","Task Monitor Bar...","Desktop..."};
  for(int index=0;index<commands.length;index++){
   final String cmd=commands[index];Thread t=new Thread(()->new Executer(cmd).run());t.start();Window w=waitDialog();Thread.sleep(800);
   Thread.sleep(500);
   String title=((Dialog)w).getTitle();System.out.println("SETTINGS "+cmd+" -> "+w.getClass().getName()+" / "+title);
   for(Component c:children(w)){
    String text=c instanceof JLabel?((JLabel)c).getText():c instanceof AbstractButton?((AbstractButton)c).getText():c instanceof Label?((Label)c).getText():c instanceof Button?((Button)c).getLabel():null;
    if(text!=null)inventory.add(title+"\t"+c.getClass().getName()+"\t"+text.replace("\n","\\n"));
    if(c instanceof JComboBox){JComboBox<?> combo=(JComboBox<?>)c;for(int i=0;i<combo.getItemCount();i++)inventory.add(title+"\tCombo\t"+combo.getItemAt(i));}
   }
   JLabel first=null;for(Component c:children(w))if(c instanceof JLabel && ((JLabel)c).getText()!=null && !((JLabel)c).getText().isBlank()){first=(JLabel)c;break;}
   if(first!=null){
    final JLabel label=first;String original=label.getText();check(label.getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey)!=null,"Official settings page has a Chinese display view: "+cmd);
    MenuDisplayAgent.setEnabled(false);Thread.sleep(700);check(original.equals(label.getText()) && label.getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey)==null,"English restoration preserves original label model: "+cmd);
    MenuDisplayAgent.setEnabled(true);Thread.sleep(700);check(original.equals(label.getText()),"Chinese switch preserves original label model: "+cmd);
   }
   EventQueue.invokeAndWait(()->{w.validate();w.toFront();});Thread.sleep(100);javax.imageio.ImageIO.write(new Robot().createScreenCapture(w.getBounds()),"png",Paths.get("build/settings-"+index+".png").toFile());
   Files.copy(Paths.get("build/dialog-inventory.tsv"),Paths.get("build/settings-"+index+".tsv"),StandardCopyOption.REPLACE_EXISTING);
   EventQueue.invokeAndWait(()->{for(Component c:children(w))if(c instanceof AbstractButton && ("Cancel".equals(((AbstractButton)c).getText())||"取消".equals(((AbstractButton)c).getText()))){((AbstractButton)c).doClick();return;}w.dispatchEvent(new WindowEvent(w,WindowEvent.WINDOW_CLOSING));});
   t.join(5000);Thread.sleep(200);
  }
  Files.write(Paths.get("build/official-settings.tsv"),inventory,StandardCharsets.UTF_8);
 }
 public static String frames(){new Thread(()->{try{while(IJ.macroRunning())Thread.sleep(50);frameProbe();finish(null,"frames");}catch(Throwable ex){finish(ex,"frames");}},"Core-Frame-Probe").start();return "started";}
 static void frameProbe()throws Exception{
  Thread.sleep(1000);ImagePlus image=new ImagePlus("Area",new ij.process.ByteProcessor(64,64));image.show();ImagePlus second=new ImagePlus("Mean",new ij.process.ByteProcessor(64,64));second.show();WindowManager.setCurrentWindow(image.getWindow());
  ij.plugin.frame.Recorder recorder=new ij.plugin.frame.Recorder();capture(recorder,"core-recorder");
  ij.plugin.frame.Editor editor=new ij.plugin.frame.Editor();String code="print(\"Area\");\n";editor.create("Area.ijm",code);capture(editor,"core-editor");check(editor.getText().equals(code),"ImageJ editor preserves original macro code");
  ij.plugin.frame.Fitter fitter=new ij.plugin.frame.Fitter();capture(fitter,"core-fitter");
  ij.plugin.frame.PasteController paste=new ij.plugin.frame.PasteController();capture(paste,"core-paste");
  ij.plugin.frame.SyncWindows sync=new ij.plugin.frame.SyncWindows();capture(sync,"core-sync");
  ij.gui.HistogramWindow hist=new ij.gui.HistogramWindow(image);capture(hist,"core-histogram");
  ij.gui.Plot plot=new ij.gui.Plot("Area profile","Distance (pixels)","Gray Value",new double[]{0,1,2},new double[]{2,3,4});Window pw=plot.show();capture(pw,"core-plot");
  Files.copy(Paths.get("build/window-menu-inventory.tsv"),Paths.get("build/core-frame-menus.tsv"),StandardCopyOption.REPLACE_EXISTING);
  Thread t=new Thread(()->new Executer("Script...").run());t.start();Window script=null;
  for(int i=0;i<600;i++){for(Window w:Window.getWindows())if(w.isShowing()&&w.getClass().getName().equals("org.scijava.ui.swing.script.TextEditor")){script=w;break;}if(script!=null)break;Thread.sleep(50);}
  if(script==null)throw new AssertionError("Fiji script editor did not open");capture(script,"core-fiji-editor");
 }
 public static String workflows(){new Thread(()->{try{while(IJ.macroRunning())Thread.sleep(50);workflowTest();finish(null,"workflows");}catch(Throwable ex){finish(ex,"workflows");}},"Core-Workflow-Test").start();return "started";}
 static Class<?> ui;static Robot robot;
 static Object util(String method,Class<?>[]types,Object...args)throws Exception{Method m=ui.getDeclaredMethod(method,types);m.setAccessible(true);return m.invoke(null,args);}
 static void prepareUI()throws Exception{
  ui=Class.forName("Fiji_Expanded_Smoke");robot=new Robot();robot.setAutoDelay(70);
  Field f=ui.getDeclaredField("robot");f.setAccessible(true);f.set(null,robot);f=ui.getDeclaredField("nativeInput");f.setAccessible(true);f.setBoolean(null,true);
 }
 static Thread run(ImagePlus image,String command)throws Exception{return (Thread)util("command",new Class[]{ImagePlus.class,String.class},image,command);}
 static GenericDialog dialog(String title)throws Exception{GenericDialog d=(GenericDialog)util("waitWindow",new Class[]{String.class},title);Thread.sleep(1200);EventQueue.invokeAndWait(()->{});return d;}
 static void number(GenericDialog d,int index,String value)throws Exception{EventQueue.invokeAndWait(()->((TextField)d.getNumericFields().get(index)).setText(value));}
 static void string(GenericDialog d,int index,String value)throws Exception{EventQueue.invokeAndWait(()->((TextField)d.getStringFields().get(index)).setText(value));}
 static void choice(GenericDialog d,int index,String value)throws Exception{EventQueue.invokeAndWait(()->d.validate());util("choose",new Class[]{Choice.class,String.class},(Choice)d.getChoices().get(index),value);}
 static void flag(Window w,String caption,boolean state)throws Exception{util("setCheck",new Class[]{Window.class,String.class,boolean.class},w,caption,state);}
 static void accept(Window w)throws Exception{EventQueue.invokeAndWait(()->w.validate());Button b=(Button)util("button",new Class[]{Window.class,String.class},w,"OK");util("click",new Class[]{Component.class},b);}
 static void join(Thread t)throws Exception{util("join",new Class[]{Thread.class},t);}
 @SuppressWarnings("unchecked")static Set<Integer> ids()throws Exception{return (Set<Integer>)util("imageIds",new Class[]{});}
 static ImagePlus created(Set<Integer> before)throws Exception{return (ImagePlus)util("createdImage",new Class[]{Set.class},before);}
 static boolean equal(ImagePlus a,ImagePlus b)throws Exception{return (boolean)util("samePixels",new Class[]{ImagePlus.class,ImagePlus.class},a,b);}
 static void clear()throws Exception{util("discardAll",new Class[]{});}
 static ImagePlus sample(String name){ImageStack stack=new ImageStack(32,24);for(int z=0;z<3;z++){byte[] p=new byte[768];for(int i=0;i<p.length;i++)p[i]=(byte)((i*5+z*37)%230);stack.addSlice(new ij.process.ByteProcessor(32,24,p));}return new ImagePlus(name,stack);}
 static void workflowTest()throws Exception{
  prepareUI();Thread.sleep(900);ij.plugin.frame.Recorder recorder=new ij.plugin.frame.Recorder();
  Thread t=run(null,"Image...");GenericDialog d=dialog("New Image");string(d,0,"Mean");choice(d,0,"16-bit");choice(d,1,"White");number(d,0,"32");number(d,1,"24");number(d,2,"2");capture(d,"core-new-native");
  Set<Integer> before=ids();accept(d);join(t);ImagePlus result=created(before);
  System.out.println("NEW RESULT "+result+" pixel="+result.getProcessor().get(0)+" inverted="+result.getProcessor().isInvertedLut());
  check(result.getTitle().equals("Mean")&&result.getWidth()==32&&result.getHeight()==24&&result.getStackSize()==2&&result.getBitDepth()==16&&equal(result,NewImage.createShortImage("reference",32,24,2,NewImage.FILL_WHITE)) && result.getProcessor().isInvertedLut(),"New Image preserves title, dimensions, bit depth and white fill");clear();
  ImagePlus image=sample("Area");image.show();Path root=Files.createTempDirectory(Paths.get("build").toAbsolutePath(),"core-io-");
  java.io.ByteArrayOutputStream raw=new java.io.ByteArrayOutputStream();for(int z=1;z<=3;z++)raw.write((byte[])image.getStack().getPixels(z));Files.write(root.resolve("Mean.raw"),raw.toByteArray());
  Set<Integer> rawBefore=ids();t=new Thread(()->new ij.io.ImportDialog("Mean.raw",root.toString()+java.io.File.separator).openImage());t.start();d=dialog("Import>Raw");
  choice(d,0,"8-bit");number(d,0,"32");number(d,1,"24");number(d,2,"0");number(d,3,"3");number(d,4,"0");flag(d,"White is zero",false);flag(d,"Open all files in folder",false);flag(d,"Use virtual stack",false);
  capture(d,"core-raw-native");accept(d);join(t);check(equal(created(rawBefore),image),"Raw import UI recovers original byte pixels and stack order");
  t=run(image,"Image Sequence... ");d=dialog("Save Image Sequence");
  TextField directory=null;for(Component c:children(d))if(c instanceof TextField){directory=(TextField)c;break;}
  final TextField dir=directory;EventQueue.invokeAndWait(()->dir.setText(root.toString()+java.io.File.separator));choice(d,0,"PNG");string(d,1,"Area");number(d,0,"0");number(d,1,"2");flag(d,"Use slice labels as file names",false);capture(d,"core-sequence-save-native");accept(d);join(t);
  java.util.List<Path> files=new ArrayList<>();try(java.util.stream.Stream<Path> stream=Files.list(root)){stream.filter(p->p.getFileName().toString().endsWith(".png")).sorted().forEach(files::add);}
  check(files.size()==3,"Image Sequence export writes three numbered PNG files");
  for(int z=0;z<3;z++)check(Arrays.equals((byte[])IJ.openImage(files.get(z).toString()).getProcessor().getPixels(),(byte[])image.getStack().getPixels(z+1)),"Exported PNG slice "+(z+1)+" preserves source pixels");
  t=run(image,"Make Montage...");d=dialog("Make Montage");String[] values={"2","2","1","1","3","1","0","12"};for(int i=0;i<values.length;i++)number(d,i,values[i]);flag(d,"Label slices",false);flag(d,"Use foreground color",false);capture(d,"core-montage-native");before=ids();accept(d);join(t);result=created(before);
  ImagePlus reference=sample("reference");before=ids();IJ.run(reference,"Make Montage...","columns=2 rows=2 scale=1 first=1 last=3 increment=1 border=0 font=12");ImagePlus expected=created(before);check(equal(result,expected),"Montage native UI produces the same pixels as the English macro");clear();
  image=sample("Area");image.show();t=run(image,"Reslice [/]...");d=dialog("Reslice");number(d,0,"1");choice(d,0,"Top");flag(d,"Flip vertically",false);flag(d,"Rotate 90 degrees",false);flag(d,"Avoid interpolation",true);capture(d,"core-reslice-native");before=ids();accept(d);join(t);result=created(before);
  check(result.getWidth()==32&&result.getHeight()==3&&result.getStackSize()==24,"Reslice UI creates the expected orthogonal stack dimensions");
  for(int y=0;y<24;y++)for(int z=0;z<3;z++)for(int x=0;x<32;x++)if(result.getStack().getProcessor(y+1).get(x,z)!=image.getStack().getProcessor(z+1).get(x,y))throw new AssertionError("Reslice pixel correspondence");check(true,"Reslice UI preserves each source voxel");clear();
  roiWorkflows();
  String macro=recorder.getText();Files.writeString(Paths.get("build/core-recorded-macro.txt"),macro,StandardCharsets.UTF_8);
  check(!macro.matches("(?s).*[\u4e00-\u9fff].*"),"New core workflows record English commands and options");recorder.close();
  externalDialogTest();
 }
 static void roiWorkflows()throws Exception{
  ImagePlus image=sample("Area");image.show();ij.plugin.filter.Analyzer.setMeasurements(ij.measure.Measurements.AREA|ij.measure.Measurements.MEAN);
  ij.plugin.frame.RoiManager rm=new ij.plugin.frame.RoiManager();Roi a=new Roi(2,2,8,6);a.setName("Area");Roi b=new Roi(16,10,4,4);b.setName("Mean");rm.addRoi(a);rm.addRoi(b);
  Thread t=new Thread(()->rm.actionPerformed(new ActionEvent(rm,ActionEvent.ACTION_PERFORMED,"Multi Measure")));WindowManager.setTempCurrentImage(t,image);t.start();GenericDialog d=dialog("Multi Measure");
  for(Component c:children(d))if(c instanceof Checkbox){Checkbox box=(Checkbox)c;if(box.getLabel().startsWith("Measure all")&&!box.getState())util("click",new Class[]{Component.class},box);}
  flag(d,"One row per slice",true);flag(d,"Append results",false);capture(d,"core-multi-measure-native");accept(d);join(t);
  Field f=ij.plugin.frame.RoiManager.class.getDeclaredField("mmResults");f.setAccessible(true);ij.measure.ResultsTable table=(ij.measure.ResultsTable)f.get(null);
  check(table!=null&&table.size()==3&&table.getColumnHeadings().contains("Area"),"ROI Multi Measure keeps English result columns and one row per slice");
  t=new Thread(()->rm.actionPerformed(new ActionEvent(rm,ActionEvent.ACTION_PERFORMED,"Multi Crop")));WindowManager.setTempCurrentImage(t,image);t.start();d=dialog("Multi Crop");
  for(Component c:children(d))if(c instanceof Checkbox){Checkbox box=(Checkbox)c;boolean wanted=box.getLabel().startsWith("Show");if(box.getState()!=wanted)util("click",new Class[]{Component.class},box);}
  capture(d,"core-multi-crop-native");Set<Integer> before=ids();accept(d);join(t);Set<Integer> outputs=ids();outputs.removeAll(before);check(outputs.size()==1 && WindowManager.getImage(outputs.iterator().next()).getStackSize()==2,"ROI Multi Crop creates one stack containing the two cropped selections");
  check(rm.getName(0).equals("Area")&&rm.getName(1).equals("Mean"),"ROI auxiliary dialogs preserve original selection names");rm.reset();rm.close();clear();
 }
 static void externalDialogTest()throws Exception{
  AtomicReference<GenericDialog> ref=new AtomicReference<>();Thread t=new Thread(()->{GenericDialog d=new GenericDialog("New Image...");d.addMessage("Area");ref.set(d);d.showDialog();});t.start();Window w=waitDialog();Thread.sleep(1000);
  Class<?> painter=Class.forName("org.fiji.zh.DisplayTextPainter");Method check=painter.getDeclaredMethod("isCoreDialog",Window.class);check.setAccessible(true);
  check(!(boolean)check.invoke(null,w),"Non-core and user-created GenericDialogs stay outside automatic mapping");dismiss(w);join(t);
 }
 public static String help(){new Thread(()->{try{while(IJ.macroRunning())Thread.sleep(50);helpTest();finish(null,"help");}catch(Throwable ex){finish(ex,"help");}},"Core-Help-Test").start();return "started";}
 static void helpTest()throws Exception{
  prepareUI();ImagePlus image=sample("Area");image.setDimensions(3,1,1);ij.CompositeImage composite=new ij.CompositeImage(image,ij.CompositeImage.COMPOSITE);composite.show();
  Thread t=run(composite,"Channels Tool...");Window channels=(Window)util("waitWindow",new Class[]{String.class},"Channels");join(t);Thread.sleep(800);EventQueue.invokeAndWait(()->channels.setAlwaysOnTop(false));
  Button help=(Button)util("button",new Class[]{Window.class,String.class},channels,"Help");util("click",new Class[]{Component.class},help);
  Window html=null;for(int i=0;i<100;i++){for(Window w:Window.getWindows())if(w.isShowing()&&w.getClass().getName().equals("ij.gui.HTMLDialog")){html=w;break;}if(html!=null)break;Thread.sleep(50);}
  if(html==null)throw new AssertionError("Channel help did not open");capture(html,"core-help-channels");
  Dimension screen=Toolkit.getDefaultToolkit().getScreenSize();check(html.getWidth()<=screen.width && html.getHeight()<=screen.height,"Chinese help retains its screen-sized scroll viewport");
  String inventory=Files.readString(Paths.get("build/dialog-inventory.tsv"),StandardCharsets.UTF_8);check(inventory.contains("多通道显示模式"),"Channels help paints its Chinese help document");
  JEditorPane pane=null;for(Component c:children(html))if(c instanceof JEditorPane){pane=(JEditorPane)c;break;}
  check(pane!=null&&pane.getText().contains("Composite Display Modes"),"Chinese help preserves the original English document");
  EventQueue.invokeAndWait(()->{});final Window helpWindow=html;EventQueue.invokeAndWait(()->helpWindow.dispose());
  util("close",new Class[]{Window.class},channels);clear();
 }
 static void statusTest()throws Exception{
  Thread.sleep(1500);ImageJ imagej=IJ.getInstance();Point p=imagej.getLocationOnScreen();new Robot().mouseMove(p.x+100,p.y+8);
  IJ.showStatus("3D filtering...");Thread.sleep(1200);
  Field f=ImageJ.class.getDeclaredField("statusLine");f.setAccessible(true);JLabel label=(JLabel)f.get(imagej);
  AtomicInteger paints=new AtomicInteger();RepaintManager previous=RepaintManager.currentManager(label);
  RepaintManager observer=new RepaintManager(){@Override public void addDirtyRegion(JComponent c,int x,int y,int w,int h){if(c==label)paints.incrementAndGet();super.addDirtyRegion(c,x,y,w,h);}};
  EventQueue.invokeAndWait(()->RepaintManager.setCurrentManager(observer));
  Thread.sleep(2200);check(paints.get()==0,"Stable status text requests zero repaints over four mapping timer ticks");
  MenuDisplayAgent.setEnabled(false);Thread.sleep(700);check(paints.get()==1,"Language change requests one status repaint");
  check(ImageJ.getStatusBarText().equals("3D filtering..."),"Status API retains the original English text");
  paints.set(0);MenuDisplayAgent.setEnabled(true);Thread.sleep(700);check(paints.get()==1,"Chinese restoration requests one status repaint");
  java.awt.image.BufferedImage before=render(label);Thread.sleep(1600);java.awt.image.BufferedImage after=render(label);
  check(Arrays.equals(before.getRGB(0,0,before.getWidth(),before.getHeight(),null,0,before.getWidth()),after.getRGB(0,0,after.getWidth(),after.getHeight(),null,0,after.getWidth())),"Stable Chinese status renders identical pixels");
  javax.imageio.ImageIO.write(after,"png",Paths.get("build/status-no-flicker.png").toFile());
  EventQueue.invokeAndWait(()->RepaintManager.setCurrentManager(previous));
 }
 static java.awt.image.BufferedImage render(Component c)throws Exception{
  java.awt.image.BufferedImage im=new java.awt.image.BufferedImage(c.getWidth(),c.getHeight(),java.awt.image.BufferedImage.TYPE_INT_RGB);
  EventQueue.invokeAndWait(()->{Graphics g=im.getGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,im.getWidth(),im.getHeight());c.printAll(g);g.dispose();});return im;
 }
 static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks.add("PASS: "+text);System.out.println("PASS: "+text);}
 static void finish(Throwable ex,String mode){if(ex!=null){ex.printStackTrace();checks.add("FAIL: "+ex);}try{Files.write(Paths.get("build/core-"+mode+"-result.txt"),checks,StandardCharsets.UTF_8);}catch(Exception e){e.printStackTrace();}Runtime.getRuntime().halt(ex==null?0:1);}
}
