import ij.*;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.swing.*;
import javax.imageio.ImageIO;
public class Fiji_Plugin_Robot {
 static Robot robot;static String mode;static final java.util.List<String> report=new ArrayList<>();
 public static String start(String name){mode=name;new Thread(()->{try{while(IJ.macroRunning())Thread.sleep(50);robot=new Robot();robot.setAutoDelay(80);run();finish(null);}catch(Throwable t){finish(t);}},"Plugin-Robot").start();return "started";}
 static void run()throws Exception{
  ImagePlus a=IJ.createImage("Area","8-bit ramp",48,48,3);a.setDimensions(1,1,3);a.show();ImagePlus b=a.duplicate();b.setTitle("Mean");b.show();
  if(mode.equals("config")){
   new loci.plugins.config.LociConfig().run("");Window w=waitWindow("Bio-Formats Plugins Configuration");ready(w);
   JTabbedPane tabs=find(w,JTabbedPane.class,null);String original=tabs.getTitleAt(1);capture(w,"general");
   Rectangle rect=tabs.getBoundsAt(1);Point origin=tabs.getLocationOnScreen();click(origin.x+rect.x+rect.width/2,origin.y+rect.y+rect.height/2);
   check(tabs.getSelectedIndex()==1,"Robot mouse selects Formats tab");check(original.equals(tabs.getTitleAt(1)),"Tab API keeps original English title");capture(w,"formats");
   robot.keyPress(KeyEvent.VK_RIGHT);robot.keyRelease(KeyEvent.VK_RIGHT);Thread.sleep(400);check(tabs.getSelectedIndex()==2,"Robot Right arrow selects Libraries tab");capture(w,"libraries");
  }else if(mode.equals("weka") || mode.equals("weka-editor")){
   async(()->IJ.runPlugIn("trainableSegmentation.Weka_Segmentation",""));Window w=waitWindow("Trainable Weka Segmentation");ready(w);if(mode.equals("weka"))capture(w,"training");
   AbstractButton settings=find(w,AbstractButton.class,"Settings");String command=settings.getActionCommand();click(settings);
   Window dialog=waitWindow("Segmentation settings");ready(dialog);if(mode.equals("weka")){capture(dialog,"features");check(settings.getText().equals("Settings")&&command.equals(settings.getActionCommand()),"Robot settings click preserves button text and command");}
   if(mode.equals("weka-editor")){
    click(find(dialog,AbstractButton.class,"Choose"));Thread.sleep(600);
    Rectangle bounds=dialog.getBounds();ImageIO.write(robot.createScreenCapture(bounds),"png",Paths.get("build/robot-weka-editor-choose.png").toFile());
    JTree tree=null;for(Window popup:Window.getWindows())if(popup.isShowing())for(Component c:children(popup))if(c instanceof JTree && c.isShowing())tree=(JTree)c;
    if(tree==null)throw new AssertionError("Classifier selection tree not found");
    Rectangle selected=tree.getRowBounds(tree.getLeadSelectionRow());Point treeAt=tree.getLocationOnScreen();
    robot.mouseMove(treeAt.x+selected.x+Math.min(25,selected.width/2),treeAt.y+selected.y+selected.height/2);
    for(int press=0;press<2;press++){robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);}Thread.sleep(600);
    StringBuilder controls=new StringBuilder();for(Component c:children(dialog))controls.append(c.getClass().getName()).append(" ").append(c.getBounds()).append("\n");
    Files.writeString(Paths.get("build/robot-weka-editor-controls.txt"),controls.toString(),StandardCharsets.UTF_8);
    Component property=null;for(Component c:children(dialog))if(c instanceof weka.gui.PropertyPanel)property=c;
    check(property!=null,"Classifier property panel found");click(property);
    Window editor=waitWindowClass("weka.gui.PropertyDialog");ready(editor);robot.mouseMove(editor.getX()+10,editor.getY()+10);Thread.sleep(500);capture(editor,"properties");
    String inventory=Files.readString(Paths.get("build/dialog-inventory.tsv"),StandardCharsets.UTF_8);
    check(inventory.contains("numTrees\t决策树数量") && inventory.contains("False\t否"),"Classifier property captions and Boolean choices translated");
    JTextArea description=find(editor,JTextArea.class,null);String english=description.getText();
    check(description.getUI().getClass().getName().contains("MessageAreaUI"),"Classifier description has Chinese display view");
    org.fiji.zh.MenuDisplayAgent.setEnabled(false);Thread.sleep(700);
    check(description.getText().equals(english) && !description.getUI().getClass().getName().contains("MessageAreaUI"),"English switch restores original description UI and document");
    org.fiji.zh.MenuDisplayAgent.setEnabled(true);Thread.sleep(700);
    check(description.getText().equals(english) && description.getUI().getClass().getName().contains("MessageAreaUI"),"Chinese switch preserves original description document");
    JLabel features=null;for(Component c:children(editor))if(c instanceof JLabel && "numFeatures".equals(((JLabel)c).getText()))features=(JLabel)c;
    check(features.getToolTipText()!=null,"Classifier parameter has original tooltip");
    click(editor.getX()+30,editor.getY()+12);robot.mouseMove(editor.getX()-15,editor.getY()-15);Thread.sleep(250);
    Point tipAt=features.getLocationOnScreen();robot.mouseMove(tipAt.x+features.getWidth()/2,tipAt.y+features.getHeight()/2);
    for(int wait=0;wait<35;wait++){Thread.sleep(100);inventory=Files.readString(Paths.get("build/dialog-inventory.tsv"),StandardCharsets.UTF_8);if(inventory.contains("随机选取的特征数量（参见 RandomTree2）"))break;}
    Rectangle hoverBounds=editor.getBounds();for(Window popup:Window.getWindows())if(popup.isShowing())for(Component c:children(popup))if(c instanceof JToolTip){Point at=c.getLocationOnScreen();hoverBounds=hoverBounds.union(new Rectangle(at.x,at.y,c.getWidth(),c.getHeight()));}
    ImageIO.write(robot.createScreenCapture(hoverBounds),"png",Paths.get("build/robot-weka-editor-tooltip.png").toFile());
    check(inventory.contains("随机选取的特征数量（参见 RandomTree2）"),"Robot hover shows translated classifier parameter help");
    click(find(editor,AbstractButton.class,"Cancel"));check(!editor.isShowing(),"Robot Cancel closes classifier properties");return;
   }
   Button cancel=find(dialog,Button.class,"Cancel");click(cancel);check(!dialog.isShowing(),"Robot Cancel closes settings once");
  }else if(mode.equals("scheme") || mode.equals("scheme-tip")){
   fiji.plugin.trackmate.Model model=new fiji.plugin.trackmate.Model();model.beginUpdate();
   fiji.plugin.trackmate.Spot p=new fiji.plugin.trackmate.Spot(5,5,0,2,1),q=new fiji.plugin.trackmate.Spot(6,5,0,2,1);
   model.addSpotTo(p,0);model.addSpotTo(q,1);model.addEdge(p,q,1);model.endUpdate();
   fiji.plugin.trackmate.visualization.trackscheme.TrackScheme scheme=new fiji.plugin.trackmate.visualization.trackscheme.TrackScheme(model,new fiji.plugin.trackmate.SelectionModel(model),new fiji.plugin.trackmate.gui.displaysettings.DisplaySettings());scheme.render();
   Window w=scheme.getGUI();ready(w);if(mode.equals("scheme"))capture(w,"trackscheme");
   AbstractButton zoom=null;for(Component c:children(w))if(c instanceof AbstractButton && "Zoom in 2x".equals(((JComponent)c).getToolTipText()))zoom=(AbstractButton)c;
   if(zoom==null)throw new AssertionError("Zoom button not found");String tip=zoom.getToolTipText();
   if(mode.equals("scheme-tip")){
    Point position=zoom.getLocationOnScreen();robot.mouseMove(position.x+zoom.getWidth()/2,position.y+zoom.getHeight()/2);Thread.sleep(1600);
    String data=Files.readString(Paths.get("build/dialog-inventory.tsv"),StandardCharsets.UTF_8);
    check(data.contains("Zoom in 2x\t放大两倍"),"Robot hover displays Chinese TrackScheme tooltip");
    check(tip.equals(zoom.getToolTipText()),"Hover keeps original tooltip text");
    Rectangle bounds=w.getBounds();for(Window popup:Window.getWindows())if(popup.isShowing())for(Component c:children(popup))if(c instanceof JToolTip){Point at=c.getLocationOnScreen();bounds=bounds.union(new Rectangle(at.x,at.y,c.getWidth(),c.getHeight()));}
    ImageIO.write(robot.createScreenCapture(bounds),"png",Paths.get("build/robot-scheme-tip-tooltip.png").toFile());return;
   }
   click(zoom);check(tip.equals(zoom.getToolTipText()),"Robot zoom preserves original tooltip");capture(w,"zoom");
  }else if(mode.equals("coloc-report")){
   net.imglib2.img.array.ArrayImg<net.imglib2.type.numeric.integer.UnsignedByteType,net.imglib2.img.basictypeaccess.array.ByteArray> img=net.imglib2.img.array.ArrayImgs.unsignedBytes(48,48);
   sc.fiji.coloc.gadgets.DataContainer<net.imglib2.type.numeric.integer.UnsignedByteType> data=new sc.fiji.coloc.gadgets.DataContainer<>(img,img,1,2,"Area","Mean");
   sc.fiji.coloc.results.SingleWindowDisplay<net.imglib2.type.numeric.integer.UnsignedByteType> display=new sc.fiji.coloc.results.SingleWindowDisplay<>(data,null);
   display.handleValue("Results",0.75);display.handleValue("Area","Results");display.process();ready(display);capture(display,"results");
   JEditorPane pane=find(display,JEditorPane.class,null);String original=pane.getText();
   check(original.contains("Results") && original.contains("0.750"),"Coloc report retains original result names and numbers");
   java.lang.reflect.Field text=pane.getUI().getClass().getDeclaredField("text");text.setAccessible(true);String translated=(String)text.get(pane.getUI());
   check(translated.contains("未出现警告") && translated.contains("结果") && translated.contains("Results"),"Coloc translates headings and preserves result cells");
   check(Files.readString(Paths.get("build/dialog-inventory.tsv"),StandardCharsets.UTF_8).contains("通道 1<br>(Area)"),"Coloc channel caption translates around original image name");
   click(find(display,AbstractButton.class,"List"));check(pane.getText().equals(original),"Robot List keeps original report document");
  }else if(mode.equals("export")){
   async(()->{loci.plugins.LociExporter exporter=new loci.plugins.LociExporter();exporter.setup("outfile=build/robot-export.ome.tif",b);exporter.run(b.getProcessor());});
   Window w=waitWindow("Bio-Formats Exporter - Multiple Files");ready(w);capture(w,"split");Button ok=find(w,Button.class,"OK");click(ok);
   Window options=waitWindow("Bio-Formats Exporter Options");ready(options);capture(options,"compression");
   check(Files.readString(Paths.get("build/dialog-inventory.tsv"),StandardCharsets.UTF_8).contains("Uncompressed\t不压缩"),"Compression display translated");
   check(find(options,Choice.class,null).getSelectedItem().equals("Uncompressed"),"Compression model keeps English option");
   Button cancel=find(options,Button.class,"Cancel");click(cancel);check(!options.isShowing(),"Robot Cancel closes exporter options");
  }else{
   async(()->IJ.runPlugIn(mode.equals("stitch")?"plugin.Stitching_Grid":"sc.fiji.coloc.Coloc_2",""));Window w=waitWindow(mode.equals("stitch")?"Grid/Collection stitching":"Coloc 2");ready(w);capture(w,"main");
   if(mode.equals("stitch")){click(find(w,Button.class,"OK"));Window params=waitWindow("Grid stitching:");ready(params);capture(params,"parameters");check(Files.readString(Paths.get("build/dialog-inventory.tsv"),StandardCharsets.UTF_8).contains("网格拼接：网格：逐行，向右、向下"),"Dynamic stitching title translated");click(find(params,Button.class,"Cancel"));check(!params.isShowing(),"Robot Cancel closes stitching parameters");}
   else {click(find(w,Button.class,"Cancel"));check(!w.isShowing(),"Robot Cancel closes Coloc parameters");}
  }
 }
 static void async(Runnable action){new Thread(action,"Plugin-Window").start();}
 static java.util.List<Component> children(Component c){java.util.List<Component> out=new ArrayList<>();out.add(c);if(c instanceof Container)for(Component child:((Container)c).getComponents())out.addAll(children(child));return out;}
 static <T> T find(Component w,Class<T> cls,String text){for(Component c:children(w))if(cls.isInstance(c)&&(text==null||text.equals(c instanceof Button?((Button)c).getLabel().trim():c instanceof AbstractButton?((AbstractButton)c).getText():"")))return cls.cast(c);throw new AssertionError("Missing control "+text);}
 static Window waitWindow(String title)throws Exception{for(int i=0;i<180;i++){for(Window w:Window.getWindows())if(w.isShowing()){String t=w instanceof Frame?((Frame)w).getTitle():w instanceof Dialog?((Dialog)w).getTitle():"";if(t.startsWith(title))return w;}Thread.sleep(70);}throw new AssertionError("Missing window "+title);}
 static Window waitWindowClass(String name)throws Exception{for(int i=0;i<180;i++){for(Window w:Window.getWindows())if(w.isShowing() && w.getClass().getName().equals(name))return w;Thread.sleep(70);}throw new AssertionError("Missing window class "+name);}
 static void ready(Window w)throws Exception{EventQueue.invokeAndWait(()->{w.toFront();w.setAlwaysOnTop(true);w.validate();});Thread.sleep(850);}
 static void click(Component c)throws Exception{Point p=c.getLocationOnScreen();click(p.x+c.getWidth()/2,p.y+c.getHeight()/2);}
 static void click(int x,int y)throws Exception{robot.mouseMove(x,y);robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);robot.waitForIdle();Thread.sleep(700);}
 static void capture(Window w,String stage)throws Exception{Rectangle screen=w.getGraphicsConfiguration().getBounds();check(screen.contains(w.getBounds()),"Window fits screen: "+stage);ImageIO.write(robot.createScreenCapture(w.getBounds()),"png",Paths.get("build/robot-"+mode+"-"+stage+".png").toFile());Files.copy(Paths.get("build/dialog-inventory.tsv"),Paths.get("build/robot-"+mode+"-"+stage+".tsv"),StandardCopyOption.REPLACE_EXISTING);}
 static void check(boolean pass,String note){if(!pass)throw new AssertionError(note);report.add("PASS: "+note);System.out.println("PASS: "+note);}
 static void finish(Throwable failure){if(failure!=null){failure.printStackTrace();report.add("FAIL: "+failure);}try{Files.write(Paths.get("build/robot-"+mode+"-result.txt"),report,StandardCharsets.UTF_8);}catch(Exception e){e.printStackTrace();}Runtime.getRuntime().halt(failure==null?0:1);}
}
