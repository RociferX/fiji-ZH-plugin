package org.fiji.zh;
import java.awt.*;
import java.util.*;
/** Explicitly supported plugin families; class ownership identifies UI, never window titles. */
final class PluginDisplaySupport {
 private static final Map<Window,String> dialogs=Collections.synchronizedMap(new WeakHashMap<>());
 static String family(Class<?> type){
  String n=type.getName();
  if(n.startsWith("loci.plugins."))return "Bio-Formats";
  if(n.startsWith("fiji.plugin.trackmate."))return "TrackMate";
  if(n.startsWith("trainableSegmentation.") || n.startsWith("weka.gui."))return "Weka";
  if(n.startsWith("sc.fiji.coloc."))return "Coloc 2";
  if(n.startsWith("plugin.Stitching") || n.equals("plugin.GridType") || n.startsWith("stitching."))return "Stitching";
  return null;
 }
 static boolean register(Window window,Class<?> creator){
  String family=creator==null?null:family(creator);
  if(family==null)return false;
  dialogs.put(window,"Plugin/"+family);return true;
 }
 static String context(Window window){
  String known=dialogs.get(window);if(known!=null)return known;
  for(Window owner=window.getOwner();owner!=null;owner=owner.getOwner()){
   String parent=dialogs.get(owner);if(parent==null){String family=find(owner);if(family!=null)parent="Plugin/"+family;}
   if(parent!=null)return parent;
  }
  if(window instanceof javax.swing.JDialog && ((javax.swing.JDialog)window).getTitle().equals("Z/T swapped?"))return "Plugin/TrackMate";
  String family=find(window);
  return family==null?null:"Plugin/"+family;
 }
 private static String find(Component component){
  String family=family(component.getClass());if(family!=null)return family;
  if(component instanceof javax.swing.JFileChooser || component instanceof javax.swing.CellRendererPane)return null;
  if(component instanceof Container)for(Component child:((Container)component).getComponents()){
   family=find(child);if(family!=null)return family;
  }
  return null;
 }
}
