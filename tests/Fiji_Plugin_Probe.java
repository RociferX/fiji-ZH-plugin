import ij.*;
import ij.gui.*;
import java.awt.*;
import java.awt.event.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.swing.*;
public class Fiji_Plugin_Probe {
 public static String start(String name){new Thread(()->{try{while(IJ.macroRunning())Thread.sleep(50);run(name);finish(name,null);}catch(Throwable t){finish(name,t);}},"Plugin-Probe").start();return "started";}
 public static String preview(String name){new Thread(()->{try{while(IJ.macroRunning())Thread.sleep(50);launch(name);}catch(Throwable t){t.printStackTrace();}},"Plugin-Preview").start();return "preview";}
 static void launch(String name)throws Exception{
  ImagePlus a=IJ.createImage("Area","8-bit ramp",64,64,3);a.setDimensions(1,1,3);a.show();ImagePlus b=a.duplicate();b.setTitle("Mean");b.show();
  String cls=name.equals("config")?"loci.plugins.config.LociConfig":name.equals("export")?"loci.plugins.LociExporter":name.equals("weka")?"trainableSegmentation.Weka_Segmentation":name.equals("stitch")?"plugin.Stitching_Grid":name.equals("coloc")?"sc.fiji.coloc.Coloc_2":"fiji.plugin.trackmate.TrackMatePlugIn";
  if(name.equals("export")){loci.plugins.LociExporter exporter=new loci.plugins.LociExporter();exporter.setup("outfile=build/preview-export.ome.tif",b);exporter.run(b.getProcessor());return;}
  IJ.runPlugIn(cls,"");
 }
 static void run(String name)throws Exception{
  ImagePlus a=IJ.createImage("Area","8-bit ramp",32,32,3);a.setDimensions(1,1,3);a.show();ImagePlus b=a.duplicate();b.setTitle("Mean");b.show();
  if(name.equals("coloc-names")){a.setTitle("<None>");b.setTitle("ROI Manager");}
  Runnable action;
  if(name.startsWith("bio"))action=()->{try{
   loci.plugins.in.ImporterOptions opts=new loci.plugins.in.ImporterOptions();opts.setId("test.tif");
   loci.plugins.in.MainDialog main=new loci.plugins.in.MainDialog(new loci.plugins.in.ImportProcess(opts));
   Method m=main.getClass().getDeclaredMethod("constructDialog");m.setAccessible(true);GenericDialog dialog=(GenericDialog)m.invoke(main);
   if(name.equals("bio-help"))new Thread(()->{try{Thread.sleep(900);for(Component c:components(dialog))if(c instanceof Checkbox && ((Checkbox)c).getLabel().equals("Autoscale")){EventQueue.invokeAndWait(()->main.focusGained(new FocusEvent(c,FocusEvent.FOCUS_GAINED)));break;}}catch(Exception e){throw new RuntimeException(e);}}).start();
   dialog.showDialog();
  }catch(Exception e){throw new RuntimeException(e);}};
  else if(name.startsWith("coloc"))action=()->IJ.runPlugIn("sc.fiji.coloc.Coloc_2","");
  else if(name.equals("stitch"))action=()->IJ.runPlugIn("plugin.Stitching_Grid","");
  else if(name.equals("weka"))action=()->IJ.runPlugIn("trainableSegmentation.Weka_Segmentation","");
  else action=()->IJ.runPlugIn("fiji.plugin.trackmate.TrackMatePlugIn","");
  new Thread(action,"Plugin-UI").start();
  Thread.sleep(3500);
  String inventory=Files.readString(Paths.get("build/dialog-inventory.tsv"),StandardCharsets.UTF_8);
  Files.writeString(Paths.get("build/plugin-"+name+".tsv"),inventory,StandardCharsets.UTF_8);
  StringBuilder windows=new StringBuilder();
  for(Window w:Window.getWindows())if(w.isShowing()){
   windows.append(w.getClass().getName()).append("\t").append(w instanceof Frame?((Frame)w).getTitle():w instanceof Dialog?((Dialog)w).getTitle():"").append("\n");
  }
  Files.writeString(Paths.get("build/plugin-"+name+"-windows.txt"),windows,StandardCharsets.UTF_8);
  if(!inventory.contains("Plugin/"))throw new AssertionError("Plugin UI not mapped: "+windows);
  snapshot(name,"main");
  if(name.equals("coloc-names")){
   String data=Files.readString(Paths.get("build/dialog-inventory.tsv"),StandardCharsets.UTF_8);
   if(!data.contains("Choice\t<None>\t<None>") || !data.contains("Choice\tROI Manager\tROI Manager"))throw new AssertionError("Conflicting user image names were translated");
   System.out.println("PASS: Coloc image names matching fixed options remain original");
  }
  if(name.equals("bio-help")){
   Thread.sleep(900);
   boolean translated=false;for(Window w:Window.getWindows())if(w.isShowing())for(Component c:components(w))if(c instanceof JEditorPane){
    JEditorPane pane=(JEditorPane)c;if(pane.getText().contains("Autoscale")){
     if(!pane.getUI().getClass().getName().contains("MessagePaneUI"))throw new AssertionError("Bio-Formats help view missing: "+pane.getUI().getClass()+" text="+pane.getText());
     Field text=pane.getUI().getClass().getDeclaredField("text");text.setAccessible(true);if(!((String)text.get(pane.getUI())).contains("自动调整显示范围"))throw new AssertionError("Wrong Chinese option help");translated=true;
    }
   }if(!translated)throw new AssertionError("Bio-Formats help not displayed");
   System.out.println("PASS: Bio-Formats contextual Chinese help preserves original English document");
  }
  if(name.equals("weka")){click("Settings");Thread.sleep(1200);snapshot(name,"settings");click("Cancel");}
  if(name.equals("stitch")){click("OK");Thread.sleep(1200);snapshot(name,"parameters");click("Cancel");}
  if(name.equals("track"))for(int i=1;i<=10;i++){click("Next");Thread.sleep(1600);snapshot(name,"step"+i);}
 }
 static java.util.List<Component> components(Component c){java.util.List<Component> out=new ArrayList<>();out.add(c);if(c instanceof Container)for(Component child:((Container)c).getComponents())out.addAll(components(child));return out;}
 static void click(String text)throws Exception{
  final boolean[] done={false};EventQueue.invokeAndWait(()->{for(Window w:Window.getWindows())if(w.isShowing())for(Component c:components(w)){
   if(!c.isShowing() || !c.isEnabled())continue;
   if(c instanceof Button&&((Button)c).getLabel().trim().equals(text)){Button b=(Button)c;EventQueue.invokeLater(()->b.dispatchEvent(new ActionEvent(b,ActionEvent.ACTION_PERFORMED,b.getActionCommand())));done[0]=true;return;}
   if(c instanceof AbstractButton&&text.equals(((AbstractButton)c).getText())){AbstractButton b=(AbstractButton)c;EventQueue.invokeLater(b::doClick);done[0]=true;return;}
  }});if(!done[0])throw new AssertionError("Button missing: "+text);
 }
 static void snapshot(String name,String stage)throws Exception{
  String data=Files.readString(Paths.get("build/dialog-inventory.tsv"),StandardCharsets.UTF_8);Files.writeString(Paths.get("build/plugin-"+name+"-"+stage+".tsv"),data,StandardCharsets.UTF_8);
  if(name.equals("coloc") && (data.contains("Choice\tArea\t面积")||data.contains("Choice\tMean\t均值")))throw new AssertionError("User image names translated");
  java.util.Map<Component,String> originals=new IdentityHashMap<>();
  for(Window w:Window.getWindows())if(w.isShowing())for(Component c:components(w)){
   if(c instanceof Button)originals.put(c,((Button)c).getLabel());
   if(c instanceof AbstractButton)originals.put(c,((AbstractButton)c).getText());
   if(c instanceof JLabel)originals.put(c,((JLabel)c).getText());
   if(c instanceof Choice)originals.put(c,String.join("|",java.util.stream.IntStream.range(0,((Choice)c).getItemCount()).mapToObj(((Choice)c)::getItem).toArray(String[]::new)));
  }
  org.fiji.zh.MenuDisplayAgent.setEnabled(false);Thread.sleep(600);org.fiji.zh.MenuDisplayAgent.setEnabled(true);Thread.sleep(600);
  for(java.util.Map.Entry<Component,String> e:originals.entrySet()){
   Component c=e.getKey();String value=c instanceof Button?((Button)c).getLabel():c instanceof AbstractButton?((AbstractButton)c).getText():c instanceof JLabel?((JLabel)c).getText():String.join("|",java.util.stream.IntStream.range(0,((Choice)c).getItemCount()).mapToObj(((Choice)c)::getItem).toArray(String[]::new));
   if(!Objects.equals(value,e.getValue()))throw new AssertionError("Original model changed: "+e.getValue());
  }
  System.out.println("PASS: "+name+" "+stage+" language switching preserves "+originals.size()+" original controls");
 }
 static void finish(String name,Throwable failure){try{if(failure!=null)failure.printStackTrace();Files.writeString(Paths.get("build/plugin-"+name+"-result.txt"),(failure==null?"PASS: plugin UI inventoried: "+name:"FAIL: "+failure),StandardCharsets.UTF_8);}catch(Exception e){e.printStackTrace();}Runtime.getRuntime().halt(failure==null?0:1);}
}
