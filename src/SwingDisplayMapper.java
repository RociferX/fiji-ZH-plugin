package org.fiji.zh;

import java.awt.*;
import java.util.*;
import javax.swing.*;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.plaf.basic.BasicEditorPaneUI;
import javax.swing.plaf.TextUI;

/** Uses Swing's presentation views; text, button actions and combo models stay original. */
final class SwingDisplayMapper {
    private static final String STATE="org.fiji.zh.textView";
    private static final Set<String> SETTINGS=Set.of("Python", "Look and Feel", "ImageJ2 Options", "Search Options", "Task Monitor Options", "Desktop");

    static String settingsContext(Window window){
        if(window!=null){String plugin=PluginDisplaySupport.context(window);if(plugin!=null)return plugin;}
        if(window!=null && window.getClass().getName().equals("org.scijava.ui.swing.script.TextEditor"))return "Fiji Script Editor";
        if(window instanceof Dialog){
            for(Window owner=window.getOwner();owner!=null;owner=owner.getOwner())
                if(owner.getClass().getName().equals("org.scijava.ui.swing.script.TextEditor"))return ((Dialog)window).getTitle();
        }
        if(!(window instanceof JDialog))return null;
        String title=((JDialog)window).getTitle().trim();
        if(title.endsWith("..."))title=title.substring(0,title.length()-3).trim();
        return SETTINGS.contains(title)?title:null;
    }

    static String tooltipContext(Component component){
        if(component instanceof JToolTip){
            JComponent source=((JToolTip)component).getComponent();
            return source==null?null:settingsContext(SwingUtilities.getWindowAncestor(source));
        }
        if(component instanceof Container)for(Component child:((Container)component).getComponents()){
            String context=tooltipContext(child);if(context!=null)return context;
        }
        return null;
    }

    static boolean visit(Component source,String context,DialogDisplayMapper dictionary,StringBuilder inventory){
        if(context.equals("Fiji Script Editor") && source.getClass().getName().contains("FlatTitlePane$"))return false;
        if(source instanceof JMenuItem){
            Component item=source;
            while(item.getParent() instanceof JPopupMenu && ((JPopupMenu)item.getParent()).getInvoker() instanceof JMenu){
                JMenu parentMenu=(JMenu)((JPopupMenu)item.getParent()).getInvoker();
                if(parentMenu.getText().equals("Templates"))return false;
                item=parentMenu;
            }
            Container parent=source.getParent();
            if(parent instanceof JPopupMenu && ((JPopupMenu)parent).getInvoker() instanceof JMenu){
                String menu=((JMenu)((JPopupMenu)parent).getInvoker()).getText();
                if(menu!=null && menu.toLowerCase(java.util.Locale.ROOT).contains("recent") && !((JMenuItem)source).getText().startsWith("Clear"))return false;
            }
        }
        String text=source instanceof JLabel?((JLabel)source).getText():source instanceof AbstractButton?((AbstractButton)source).getText():
            source instanceof JToolTip?((JToolTip)source).getTipText():null;
        boolean changed=false;
        if(source instanceof JComponent && context.startsWith("Plugin/")){
            JComponent component=(JComponent)source;
            javax.swing.border.Border border=component.getBorder();
            javax.swing.border.Border display=displayBorder(border,context,dictionary,inventory);
            if(display!=border){component.setBorder(display);changed=true;}
        }
        if(source instanceof JTabbedPane){
            JTabbedPane tabs=(JTabbedPane)source;
            for(int i=0;i<tabs.getTabCount();i++){
                String title=tabs.getTitleAt(i);
                if(!Set.of("Outline","File Explorer").contains(title) && !context.equals("Plugin/Bio-Formats"))continue;
                Component existing=tabs.getTabComponentAt(i);
                if(existing==null){tabs.setTabComponentAt(i,new JLabel(title));changed=true;}
                Component caption=tabs.getTabComponentAt(i);
                if(caption instanceof JLabel)changed|=apply((JLabel)caption,title,dictionary.translate(context,title));
                inventory.append(context).append("\tTab\t").append(title).append('\t').append(dictionary.translate(context,title)).append('\n');
            }
        }
        if(source instanceof JEditorPane && !((JEditorPane)source).isEditable()) {
            JEditorPane pane=(JEditorPane)source;
            String topic=context.startsWith("Help/")?context.substring(5):context.startsWith("Plugin/")?context:context.equals("Search Options") && pane.getText().contains("Search Tips:")?context:null;
            if(topic!=null) {
                String help=dictionary.helpText(topic,pane.getText());
                if(help==null && pane.getUI() instanceof MessagePaneUI) {
                    pane.setUI(((MessagePaneUI)pane.getUI()).original);changed=true;
                } else if(help!=null && (!(pane.getUI() instanceof MessagePaneUI) || !help.equals(((MessagePaneUI)pane.getUI()).text))) {
                    TextUI original=pane.getUI() instanceof MessagePaneUI?((MessagePaneUI)pane.getUI()).original:pane.getUI();
                    pane.setUI(new MessagePaneUI(pane,original,help));changed=true;
                }
                if(help!=null)inventory.append(context).append("\tHelp\t").append(topic).append('\t').append(help.replace("\n","\\n")).append('\n');
            }
        }
        if(source instanceof JTextArea && context.equals("Plugin/Weka") && !((JTextArea)source).isEditable()){
            JTextArea area=(JTextArea)source;String original=area.getText(),display=dictionary.translate(context,original);
            if(original.equals(display)){
                if(area.getUI() instanceof MessageAreaUI){area.setUI(((MessageAreaUI)area.getUI()).original);changed=true;}
            }else if(!(area.getUI() instanceof MessageAreaUI) || !display.equals(((MessageAreaUI)area.getUI()).text)){
                TextUI originalUI=area.getUI() instanceof MessageAreaUI?((MessageAreaUI)area.getUI()).original:area.getUI();
                area.setUI(new MessageAreaUI(area,originalUI,display));changed=true;
            }
            inventory.append(context).append("\tDescription\t").append(original.replace("\n","\\n")).append('\t').append(display.replace("\n","\\n")).append('\n');
        }
        if(text!=null && !text.isBlank()){
            String display=dictionary.translate(context,text);
            changed|=apply((JComponent)source,text,display);
            inventory.append(context).append('\t').append(source.getClass().getSimpleName()).append('\t')
                .append(text.replace("\n","\\n")).append('\t').append(display.replace("\n","\\n")).append('\n');
        }
        if(source instanceof JComboBox){
            @SuppressWarnings("unchecked") JComboBox<Object> combo=(JComboBox<Object>)source;
            if(!(combo.getRenderer() instanceof DisplayRenderer)){
                @SuppressWarnings("unchecked") ListCellRenderer<Object> original=(ListCellRenderer<Object>)combo.getRenderer();
                combo.setRenderer(new DisplayRenderer(original,context,dictionary));changed=true;
            }
            for(int i=0;i<combo.getItemCount();i++){
                String value=String.valueOf(combo.getItemAt(i));
                inventory.append(context).append("\tCombo\t").append(value).append('\t').append(dictionary.translate(context,value)).append('\n');
            }
        }
        return changed;
    }

    private static boolean apply(JComponent component,String original,String display){
        TextView previous=(TextView)component.getClientProperty(STATE);
        if(original.equals(display)){
            if(previous==null)return false;
            component.putClientProperty("html.disable",previous.disabled);
            BasicHTML.updateRenderer(component,original);component.putClientProperty(STATE,null);component.revalidate();component.repaint();return true;
        }
        if(previous!=null && display.equals(previous.text) && component.getClientProperty(BasicHTML.propertyKey)==previous.view)return false;
        String html=display.startsWith("<html>")?display:"<html>"+display.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\n","<br>")+"</html>";
        if(component instanceof AbstractButton && !display.startsWith("<html>") && !display.contains("\n"))
            html="<html><nobr>"+html.substring(6,html.length()-7)+"</nobr></html>";
        html=html.replaceFirst("<html>","<html><font face='Microsoft YaHei UI'>");
        html=html.endsWith("</html>")?html.substring(0,html.length()-7)+"</font></html>":html+"</font>";
        Object disabled=previous==null?component.getClientProperty("html.disable"):previous.disabled;
        component.putClientProperty("html.disable",Boolean.FALSE);
        BasicHTML.updateRenderer(component,html);
        component.putClientProperty(STATE,new TextView(display,component.getClientProperty(BasicHTML.propertyKey),disabled));
        component.revalidate();component.repaint();return true;
    }

    private static final class TextView {
        final String text;final Object view,disabled;
        TextView(String text,Object view,Object disabled){this.text=text;this.view=view;this.disabled=disabled;}
    }
    private static javax.swing.border.Border displayBorder(javax.swing.border.Border border,String context,DialogDisplayMapper dictionary,StringBuilder inventory){
        if(border instanceof javax.swing.border.TitledBorder){
            javax.swing.border.TitledBorder titled=(javax.swing.border.TitledBorder)border;
            String original=titled.getTitle(),display=dictionary.translate(context,original);
            inventory.append(context).append("\tBorder\t").append(original).append('\t').append(display).append('\n');
            if(!Objects.equals(original,display) && !(titled instanceof DisplayBorder))return new DisplayBorder(titled,context,dictionary);
        }else if(border instanceof javax.swing.border.CompoundBorder){
            javax.swing.border.CompoundBorder compound=(javax.swing.border.CompoundBorder)border;
            javax.swing.border.Border outside=displayBorder(compound.getOutsideBorder(),context,dictionary,inventory),inside=displayBorder(compound.getInsideBorder(),context,dictionary,inventory);
            if(outside!=compound.getOutsideBorder() || inside!=compound.getInsideBorder())return new javax.swing.border.CompoundBorder(outside,inside);
        }
        return border;
    }
    private static final class DisplayBorder extends javax.swing.border.TitledBorder {
        final String context;final DialogDisplayMapper dictionary;
        DisplayBorder(javax.swing.border.TitledBorder source,String context,DialogDisplayMapper dictionary){
            super(source.getBorder(),source.getTitle(),source.getTitleJustification(),source.getTitlePosition(),source.getTitleFont(),source.getTitleColor());
            this.context=context;this.dictionary=dictionary;
        }
        @Override public void paintBorder(Component c,Graphics g,int x,int y,int width,int height){
            String title=dictionary.translate(context,getTitle());
            new javax.swing.border.TitledBorder(getBorder(),title,getTitleJustification(),getTitlePosition(),Objects.equals(title,getTitle())?getTitleFont():DisplayFonts.nativeFont(getTitleFont()),getTitleColor()).paintBorder(c,g,x,y,width,height);
        }
    }
    private static final class MessagePaneUI extends BasicEditorPaneUI {
        final TextUI original;final String text;final JEditorPane display;
        MessagePaneUI(JEditorPane source,TextUI original,String text){
            this.original=original;this.text=text;display=new JEditorPane("text/html",text);display.setEditable(false);
            display.setFont(DisplayFonts.nativeFont(source.getFont()));display.setForeground(source.getForeground());display.setBackground(source.getBackground());
        }
        @Override protected void paintSafely(Graphics graphics){display.setSize(getComponent().getSize());display.printAll(graphics);}
    }
    private static final class MessageAreaUI extends javax.swing.plaf.basic.BasicTextAreaUI {
        final TextUI original;final String text;final JTextArea display;
        MessageAreaUI(JTextArea source,TextUI original,String text){
            this.original=original;this.text=text;display=new JTextArea(text);display.setEditable(false);
            display.setLineWrap(source.getLineWrap());display.setWrapStyleWord(source.getWrapStyleWord());
            display.setFont(DisplayFonts.nativeFont(source.getFont()));display.setForeground(source.getForeground());display.setBackground(source.getBackground());
        }
        @Override protected void paintSafely(Graphics graphics){display.setSize(getComponent().getSize());display.printAll(graphics);}
    }
    private static final class DisplayRenderer implements ListCellRenderer<Object>{
        final ListCellRenderer<Object> original;final String context;final DialogDisplayMapper dictionary;
        DisplayRenderer(ListCellRenderer<Object> original,String context,DialogDisplayMapper dictionary){this.original=original;this.context=context;this.dictionary=dictionary;}
        public Component getListCellRendererComponent(JList<?> list,Object value,int index,boolean selected,boolean focus){
            Component cell=original.getListCellRendererComponent(list,value,index,selected,focus);
            if(cell instanceof JLabel){
                JLabel label=(JLabel)cell;String text=label.getText();
                if(text!=null){
                    String translated=dictionary.translate(context,text);
                    if(!Objects.equals(text,translated)){
                        JLabel copy=new JLabel(translated,label.getIcon(),label.getHorizontalAlignment());
                        copy.setFont(DisplayFonts.nativeFont(label.getFont()));copy.setForeground(label.getForeground());copy.setBackground(label.getBackground());
                        copy.setOpaque(label.isOpaque());copy.setBorder(label.getBorder());copy.setEnabled(label.isEnabled());
                        copy.setVerticalAlignment(label.getVerticalAlignment());return copy;
                    }
                }
            }
            return cell;
        }
    }
}
