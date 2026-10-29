import ij.*;
import ij.measure.Calibration;
import ij.gui.*;
import ij.process.*;
import ij.plugin.frame.*;
import java.awt.*;
import java.awt.event.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import javax.imageio.ImageIO;
import org.fiji.zh.MenuDisplayAgent;

/** Real Fiji tests for the requested ROI/channel/stack/process/settings release. */
public final class Fiji_Expanded_Smoke {
    private static Robot robot;
    private static boolean nativeInput;
    private static boolean probeOnly;
    private static boolean fontsOnly;
    public static String fontsNative(){nativeInput=true;fontsOnly=true;return launch(false);}
    private static boolean processingOnly;
    public static String processingNative() { nativeInput=true; processingOnly=true; return launch(false); }
    private static boolean channelsOnly;
    public static String channelsNative() { nativeInput=true; channelsOnly=true; return launch(false); }
    public static String startNative() { nativeInput=true; return launch(false); }
    private static boolean messagesOnly;
    public static String messages() { messagesOnly=true; return launch(false); }
    private static final java.util.List<String> checks = new ArrayList<>();
    public static String start() { return launch(false); }
    public static String probe() { return launch(true); }
    private static String launch(boolean probe) {
        probeOnly=probe;
        new Thread(() -> {
            try { while (IJ.macroRunning()) Thread.sleep(100); robot=new Robot(); robot.setAutoDelay(60);
                Thread.sleep(800); if (probe) probeWindows(); else if(messagesOnly)settingsAndMessages();else if(channelsOnly){new Recorder();channelWorkflows();}else if(processingOnly){new Recorder();processingWorkflows();}else if(fontsOnly){Thread t=command(null,"Fonts...");Window w=waitWindow("Fonts");snapshot(w,"fonts");assertInventory("Fonts\tTitle\tFonts\t字体");click(button(w,"Close"));join(t);check(!w.isShowing(),"Font preferences close on one native click");}else run(); finish(null);
            } catch(Throwable ex) { finish(ex); }
        }, "Expanded-UI-Test").start(); return "started";
    }
    private static ImagePlus stack(String title) {
        ImageStack data=new ImageStack(32,24);
        for(int z=0;z<3;z++) { byte[] p=new byte[32*24]; for(int i=0;i<p.length;i++)p[i]=(byte)((i*7+z*31)%230);data.addSlice(new ByteProcessor(32,24,p)); }
        return new ImagePlus(title,data);
    }
    private static void probeWindows() throws Exception {
        ImagePlus image=stack("Area"); image.show(); image.setRoi(new Roi(4,4,12,10));
        ImagePlus other=stack("Mean"); other.show();
        String[][] windows={
            {"ROI Manager...","ROI Manager"}, {"Properties... ","Properties"}, {"Overlay Options...","Overlay Options"},
            {"Labels...","Labels"}, {"Add Selection...","Add to Overlay"}, {"Add Image...","Add Image"},
            {"Merge Channels...","Merge Channels"}, {"Make Substack...","Substack Maker"},
            {"Concatenate...","Concatenator"}, {"Stack to Hyperstack...","Convert to HyperStack"},
            {"Image Calculator...","Image Calculator"}, {"Gaussian Blur 3D...","3D Gaussian Blur"},
            {"Mean 3D...","3D Mean"}, {"Median 3D...","3D Median"}, {"Minimum 3D...","3D Minimum"},
            {"Maximum 3D...","3D Maximum"}, {"Variance 3D...","3D Variance"},
            {"Remove Outliers...","Remove Outliers"}, {"Bandpass Filter...","FFT Bandpass Filter"}, {"Convolve...","Convolver"},
            {"Appearance...","Appearance"}, {"Arrow Tool...","Arrow Tool"}, {"Colors...","Colors"},
            {"Compiler...","Compile and Run"}, {"Conversions...","Conversion Options"}, {"DICOM...","DICOM Options"},
            {"Fonts...","Fonts"}, {"Input/Output...","I/O Options"}, {"Line Width...","Default Line Width"},
            {"Memory & Threads...","Memory (64-bit)"}, {"Misc...","Miscellaneous Options"}, {"Plots...","Plot Defaults"},
            {"Point Tool...","Point Tool"}, {"Proxy Settings...","Proxy Settings"}, {"Roi Defaults...","ROI Defaults"},
            {"Rounded Rect Tool...","Rounded Rectangle Tool"}, {"Startup...","Startup Macro"}, {"Wand Tool...","Wand Tool"}
        };
        for(int i=0;i<windows.length;i++) {
            if(windows[i][0].equals("Add Selection..."))IJ.setKeyDown(KeyEvent.VK_ALT);
            Thread thread=command(image,windows[i][0]);
            Window window=waitProbeWindow();
            System.out.println("PROBE "+windows[i][0]+" => "+(window instanceof Dialog?((Dialog)window).getTitle():((Frame)window).getTitle())); IJ.setKeyUp(KeyEvent.VK_ALT); snapshot(window,"expanded-"+i);
            if(window instanceof GenericDialog) {
                Button cancel=null; for(Component c:components(window))if(c instanceof Button && ((Button)c).getLabel().trim().equals("Cancel"))cancel=(Button)c;
                if(cancel!=null)click(cancel);else close(window);
            } else close(window);
            join(thread); Thread.sleep(200);
            image.setRoi(new Roi(4,4,12,10));
        }
        ImagePlus composite=new CompositeImage(stack("channels-test"),CompositeImage.COMPOSITE);composite.setDimensions(3,1,1);composite.show();
        Thread cmd=command(composite,"Channels Tool..."); Window w=waitWindow("Channels"); snapshot(w,"expanded-channels");close(w);join(cmd);
        ImagePlus rgb=new ImagePlus("rgb-test",new ColorProcessor(32,24));rgb.show();
        cmd=command(rgb,"Color Threshold...");w=waitWindow("Threshold Color");snapshot(w,"expanded-color");close(w);join(cmd);
        check(true,"All requested windows open and close with original commands");
    }
    private static Window waitProbeWindow() throws Exception {
        for(int i=0;i<160;i++) {
            for(Window w:Window.getWindows()) if(w.isShowing() && (w instanceof Dialog || w.getClass().getName().startsWith("ij.plugin.frame."))) {
                return w;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("No new dialog appeared");
    }
    private static void run() throws Exception {
        Recorder recorder=new Recorder();
        roiWorkflows(); discardAll();
        channelWorkflows(); discardAll();
        stackWorkflows(); discardAll();
        processingWorkflows(); discardAll();
        settingsAndMessages();
        String macro=recorder.getText();
        Files.writeString(Paths.get("build/expanded-recorded-macro.txt"),macro,StandardCharsets.UTF_8);
        check(macro.contains("Gaussian Blur 3D") && macro.contains("imageCalculator(\"Add create 32-bit\"") && !macro.matches("(?s).*[\u4e00-\u9fff].*"),
            "Recorder preserves English commands and parameter keys across expanded workflows");
        recorder.close();
    }
    private static void roiWorkflows() throws Exception {
        ImagePlus image=image("Area"); image.show(); Roi roi=new Roi(5,6,14,12);roi.setName("Area");image.setRoi(roi);
        Thread thread=command(image,"ROI Manager..."); RoiManager manager=(RoiManager)waitWindow("ROI Manager");snapshot(manager,"roi-manager");join(thread);
        click(button(manager,"Add [t]"));
        check(manager.getCount()==1 && manager.getName(0).equals("Area"),"ROI Add preserves the original ROI name Area");
        click(checkbox(manager,"Show All"));click(checkbox(manager,"Labels"));
        check(manager.getRoi(0).getBounds().equals(roi.getBounds()),"ROI Show All and Labels preserve selection geometry");
        EventQueue.invokeLater(()->manager.dispatchEvent(new WindowEvent(manager,WindowEvent.WINDOW_CLOSING)));
        Window prompt=waitWindow("ROI Manager");snapshot(prompt,"roi-close-confirmation");click(button(prompt,"Discard"));Thread.sleep(350);
        image.setRoi(roi);
        thread=command(image,"Properties... "); GenericDialog dialog=(GenericDialog)waitWindow("Properties");snapshot(dialog,"roi-properties");
        setNumber(dialog,0,"3");click(button(dialog,"OK"));join(thread);
        check(image.getRoi().getStrokeWidth()==3 && "Area".equals(image.getRoi().getName()),"Selection Properties changes width and preserves user name");
        IJ.setKeyDown(KeyEvent.VK_ALT);thread=command(image,"Add Selection...");dialog=(GenericDialog)waitWindow("Add to Overlay");IJ.setKeyUp(KeyEvent.VK_ALT);
        snapshot(dialog,"overlay-add");click(button(dialog,"OK"));join(thread);
        check(image.getOverlay()!=null && image.getOverlay().size()==1,"Add Selection creates the overlay using original command");
        thread=command(image,"Labels...");dialog=(GenericDialog)waitWindow("Labels");snapshot(dialog,"overlay-labels");
        setCheck(dialog,"Show labels",true);setCheck(dialog,"Use names as labels",true);click(button(dialog,"OK"));join(thread);
        check(image.getOverlay().getDrawLabels() && image.getOverlay().getDrawNames(),"Overlay label controls update original overlay flags");
        thread=command(image,"Overlay Options...");dialog=(GenericDialog)waitWindow("Overlay Options");snapshot(dialog,"overlay-options");
        click(button(dialog,"OK"));join(thread);
        check(image.getOverlay().size()==1,"Overlay Options retains the original ROI");
    }
    private static void channelWorkflows() throws Exception {
        ImagePlus base=stack("channel-source");base.setDimensions(3,1,1);
        CompositeImage composite=new CompositeImage(base,CompositeImage.COMPOSITE);composite.show();
        ImagePlus saved=composite.duplicate();
        Thread thread=command(composite,"Channels Tool...");Window window=waitWindow("Channels");snapshot(window,"channels");join(thread);
        Choice mode=null;for(Component c:components(window))if(c instanceof Choice){mode=(Choice)c;break;}
        choose(mode,"Color");Thread.sleep(250);check(composite.getMode()==CompositeImage.COLOR,"Channel mode choice returns original Color and updates display mode");
        choose(mode,"Composite");Thread.sleep(250);
        Checkbox second=null;for(Component c:components(window))if(c instanceof Checkbox && ((Checkbox)c).getLabel().replace(" ","").equals("Channel2"))second=(Checkbox)c;
        click(second);Thread.sleep(250);check(!composite.getActiveChannels()[1] && samePixels(composite,saved),"Channel visibility changes without altering image pixels");
        Field popupField=window.getClass().getDeclaredField("pm");popupField.setAccessible(true);PopupMenu popup=(PopupMenu)popupField.get(window);
        MenuItem red=null;for(int i=0;i<popup.getItemCount();i++)if(popup.getItem(i).getLabel().equals("Red"))red=popup.getItem(i);
        final MenuItem action=red;
        if(nativeInput) {
            AtomicReference<String> selected=new AtomicReference<>();
            for(int i=0;i<popup.getItemCount();i++)popup.getItem(i).addActionListener(event->selected.set(event.getActionCommand()));
            Button more=null;for(Component c:components(window))if(c instanceof Button && ((Button)c).getLabel().startsWith("More"))more=(Button)c;
            Point at=window.getLocationOnScreen(), buttonAt=more.getLocationOnScreen();
            int moreX=buttonAt.x+more.getWidth()/2,moreY=buttonAt.y+more.getHeight()/2;
            int steps=0;for(int i=0;i<popup.getItemCount() && popup.getItem(i)!=action;i++)
                if(!popup.getItem(i).getLabel().equals("-") && popup.getItem(i).isEnabled())steps++;
            // Windows runs a modal menu loop on the EDT until an item is selected.
            // Query geometry first; waitForIdle/tree-lock calls cannot run while it is open.
            robot.mouseMove(moreX,moreY);robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);Thread.sleep(700);
            ImageIO.write(robot.createScreenCapture(new Rectangle(at.x,at.y,450,450)),"png",Paths.get("build/dialog-channels-popup.png").toFile());
            // AWT popup menus start without a selected row; the first Down selects row one.
            for(int i=0;i<=steps;i++) {
                robot.keyPress(KeyEvent.VK_DOWN);robot.keyRelease(KeyEvent.VK_DOWN);
            }
            ImageIO.write(robot.createScreenCapture(new Rectangle(at.x,at.y,450,450)),"png",Paths.get("build/dialog-channels-popup-selected.png").toFile());
            robot.keyPress(KeyEvent.VK_ENTER);robot.keyRelease(KeyEvent.VK_ENTER);Thread.sleep(800);
            System.out.println("Popup steps="+steps+" selected="+selected.get());
            check("Red".equals(selected.get()),"Native Channels popup selects original English Red action");
        } else EventQueue.invokeAndWait(()->action.dispatchEvent(new ActionEvent(action,ActionEvent.ACTION_PERFORMED,action.getActionCommand())));
        check(samePixels(composite,saved),"Channels popup keeps English action labels and source pixels");close(window);discardAll();
        ImagePlus first=image("Area"),secondImage=image("Mean");first.show();secondImage.show();
        thread=command(first,"Merge Channels...");GenericDialog dialog=(GenericDialog)waitWindow("Merge Channels");
        for(int i=0;i<dialog.getChoices().size();i++)choose((Choice)dialog.getChoices().get(i),i==0?"Area":i==1?"Mean":"*None*");
        setCheck(dialog,"Create composite",true);setCheck(dialog,"Keep source images",true);snapshot(dialog,"merge-channels");
        assertInventory("Merge Channels\tChoice\tArea\tArea");assertInventory("Merge Channels\tChoice\tMean\tMean");
        Set<Integer> before=imageIds();click(button(dialog,"OK"));join(thread);ImagePlus merged=createdImage(before);
        check(merged.getNChannels()==2 && Arrays.equals((byte[])merged.getStack().getPixels(1),(byte[])first.getProcessor().getPixels()) &&
            Arrays.equals((byte[])merged.getStack().getPixels(2),(byte[])secondImage.getProcessor().getPixels()),"Merge Channels preserves source names and channel pixels");discardAll();
        ColorProcessor colors=new ColorProcessor(32,24);for(int y=0;y<24;y++)for(int x=0;x<32;x++)colors.set(x,y,x<16?0xff0000:0x00ff00);
        ImagePlus rgb=new ImagePlus("color-source",colors);rgb.show();int[] original=((int[])colors.getPixels()).clone();
        thread=command(rgb,"Color Threshold...");window=waitWindow("Threshold Color");snapshot(window,"color-threshold");join(thread);
        Choice space=null;for(Component c:components(window))if(c instanceof Choice && ((Choice)c).getItemCount()==4 && ((Choice)c).getItem(0).equals("HSB"))space=(Choice)c;
        choose(space,"RGB");Thread.sleep(350);click(button(window,"Filtered"));click(button(window,"Original"));Thread.sleep(350);
        check(Arrays.equals(original,(int[])rgb.getProcessor().getPixels()) && space.getSelectedItem().equals("RGB"),"Color Threshold RGB choice and Original button restore original pixels");
        click(button(window,"Macro"));Thread.sleep(250);close(window);
    }
    private static void stackWorkflows() throws Exception {
        ImagePlus image=stack("Area");image.show();
        Thread thread=command(image,"Make Substack...");GenericDialog dialog=(GenericDialog)waitWindow("Substack Maker");
        setText((TextField)dialog.getStringFields().get(0),"1-3-2");setCheck(dialog,"Delete slices from original stack",false);setCheck(dialog,"Do not create substack",false);
        snapshot(dialog,"substack");Set<Integer> before=imageIds();click(button(dialog,"OK"));join(thread);ImagePlus output=createdImage(before);
        check(output.getStackSize()==2 && image.getStackSize()==3 && Arrays.equals((byte[])output.getStack().getPixels(2),(byte[])image.getStack().getPixels(3)),"Substack range and increment select the original slices");discardAll();
        ImagePlus first=stack("Area"),second=stack("Mean");first.show();second.show();
        thread=command(first,"Concatenate...");dialog=(GenericDialog)waitWindow("Concatenator");
        setCheck(dialog,"All open windows",false);
        for(int i=0;i<dialog.getChoices().size();i++)choose((Choice)dialog.getChoices().get(i),i==0?"Area":i==1?"Mean":"-- None --");
        setCheck(dialog,"Keep original images",true);setCheck(dialog,"Open as 4D image",false);setText((TextField)dialog.getStringFields().get(0),"Threshold");snapshot(dialog,"concatenate");
        assertInventory("Concatenator\tChoice\tArea\tArea");before=imageIds();click(button(dialog,"OK"));join(thread);output=createdImage(before);
        check(output.getStackSize()==6 && output.getTitle().equals("Threshold") && Arrays.equals((byte[])output.getStack().getPixels(4),(byte[])second.getStack().getPixels(1)),"Concatenation keeps source order, source names and user output title");discardAll();
        image=stack("hyperstack-source");image.show();ImagePlus saved=image.duplicate();
        thread=command(image,"Stack to Hyperstack...");dialog=(GenericDialog)waitWindow("Convert to HyperStack");
        choose((Choice)dialog.getChoices().get(0),"xyczt(default)");setNumber(dialog,0,"3");setNumber(dialog,1,"1");setNumber(dialog,2,"1");
        choose((Choice)dialog.getChoices().get(1),"Composite");snapshot(dialog,"hyperstack");click(button(dialog,"OK"));join(thread);
        ImagePlus converted=WindowManager.getCurrentImage();
        check(converted.getNChannels()==3 && converted.getNSlices()==1 && converted.getNFrames()==1 && samePixels(converted,saved),"Stack to Hyperstack preserves pixels and selected dimensions");
    }
    private static void processingWorkflows() throws Exception {
        ImagePlus first=image("Area"),second=image("Mean");first.show();second.show();
        Thread thread=command(first,"Image Calculator...");GenericDialog dialog=(GenericDialog)waitWindow("Image Calculator");
        choose((Choice)dialog.getChoices().get(0),"Area");choose((Choice)dialog.getChoices().get(1),"Add");choose((Choice)dialog.getChoices().get(2),"Mean");
        setCheck(dialog,"Create new window",true);setCheck(dialog,"32-bit (float) result",true);snapshot(dialog,"calculator");
        assertInventory("Image Calculator\tChoice\tArea\tArea");assertInventory("Image Calculator\tChoice\tAdd\t相加");
        Set<Integer> before=imageIds();click(button(dialog,"OK"));join(thread);ImagePlus result=createdImage(before);
        ImagePlus expected=new ij.plugin.ImageCalculator().run("Add create 32-bit",first,second);
        check(samePixels(result,expected) && result.getBitDepth()==32,"Image Calculator keeps original operands and float arithmetic");discardAll();
        String[][] filters={{"Gaussian Blur 3D...","3D Gaussian Blur","x=1 y=1 z=1"},{"Mean 3D...","3D Mean","x=1 y=1 z=1"},
            {"Median 3D...","3D Median","x=1 y=1 z=1"},{"Minimum 3D...","3D Minimum","x=1 y=1 z=1"},
            {"Maximum 3D...","3D Maximum","x=1 y=1 z=1"},{"Variance 3D...","3D Variance","x=1 y=1 z=1"}};
        for(int i=0;i<filters.length;i++) {
            ImagePlus image=stack("filter-3d-"+i);image.show();expected=stack("reference");
            thread=command(image,filters[i][0]);dialog=(GenericDialog)waitWindow(filters[i][1]);for(int n=0;n<3;n++)setNumber(dialog,n,"1");
            snapshot(dialog,"filter-3d-"+i);click(button(dialog,"OK"));join(thread);IJ.run(expected,filters[i][0],filters[i][2]);
            check(samePixels(image,expected),filters[i][0]+" UI equals English macro pixel for pixel");discardAll();
        }
        ImagePlus image=image("outlier-test");image.show();expected=image("reference");
        thread=command(image,"Remove Outliers...");dialog=(GenericDialog)waitWindow("Remove Outliers");setNumber(dialog,0,"2");setNumber(dialog,1,"30");choose((Choice)dialog.getChoices().get(0),"Bright");
        snapshot(dialog,"outliers");click(button(dialog,"OK"));join(thread);IJ.run(expected,"Remove Outliers...","radius=2 threshold=30 which=Bright");
        check(samePixels(image,expected),"Remove Outliers UI equals English macro pixels");discardAll();
        image=image("bandpass-test");image.show();expected=image("reference");thread=command(image,"Bandpass Filter...");dialog=(GenericDialog)waitWindow("FFT Bandpass Filter");
        setNumber(dialog,0,"10");setNumber(dialog,1,"2");setNumber(dialog,2,"5");choose((Choice)dialog.getChoices().get(0),"None");
        for(Component c:components(dialog))if(c instanceof Checkbox && ((Checkbox)c).getState())click(c);
        snapshot(dialog,"bandpass");click(button(dialog,"OK"));join(thread);IJ.run(expected,"Bandpass Filter...","filter_large=10 filter_small=2 suppress=None tolerance=5");
        check(samePixels(image,expected),"Bandpass filter UI equals English macro pixels");discardAll();
        image=image("convolve-test");image.show();expected=image("reference");thread=command(image,"Convolve...");dialog=(GenericDialog)waitWindow("Convolver");
        final TextArea kernel=dialog.getTextArea1();EventQueue.invokeAndWait(()->kernel.setText("1 1 1\n1 1 1\n1 1 1"));setCheck(dialog,"Normalize Kernel",true);
        snapshot(dialog,"convolver");click(button(dialog,"OK"));join(thread);IJ.run(expected,"Convolve...","text1=[1 1 1\n1 1 1\n1 1 1] normalize");
        check(samePixels(image,expected),"Convolver kernel text and normalization equal English macro pixels");
    }
    private static void settingsAndMessages() throws Exception {
        Thread thread=command(null,"Line Width...");GenericDialog dialog=(GenericDialog)waitWindow("Default Line Width");
        setNumber(dialog,0,"4");snapshot(dialog,"line-preference");MenuDisplayAgent.setEnabled(false);Thread.sleep(650);
        check(((TextField)dialog.getNumericFields().get(0)).getText().equals("4"),"English switch preserves edited preference value");MenuDisplayAgent.setEnabled(true);Thread.sleep(650);
        click(button(dialog,"OK"));join(thread);check(Line.getWidth()==4,"Preference OK applies the original numeric value");
        AtomicReference<YesNoCancelDialog> result=new AtomicReference<>();
        thread=new Thread(()->result.set(new YesNoCancelDialog(IJ.getInstance(),"Save Changes?","Save changes to \"Area\"?")));thread.start();
        Window window=waitWindow("Save Changes?");snapshot(window,"save-confirmation");
        assertInventory("保存对“Area”的更改？");
        Field text=Class.forName("ij.gui.MultiLineLabel").getDeclaredField("lines");text.setAccessible(true);
        for(Component c:components(window))if(c.getClass().getName().equals("ij.gui.MultiLineLabel"))
            check(String.join("\n",(String[])text.get(c)).equals("Save changes to \"Area\"?"),"Confirmation original message keeps user image name and English model");
        for(Component c:components(window))if(c.getClass().getName().equals("ij.gui.MultiLineLabel")) {
            java.awt.image.BufferedImage zh=render(c);MenuDisplayAgent.setEnabled(false);Thread.sleep(650);
            java.awt.image.BufferedImage en=render(c);MenuDisplayAgent.setEnabled(true);Thread.sleep(650);
            check(!Arrays.equals(zh.getRGB(0,0,zh.getWidth(),zh.getHeight(),null,0,zh.getWidth()),en.getRGB(0,0,en.getWidth(),en.getHeight(),null,0,en.getWidth())),
                "Transformed confirmation Canvas paints Chinese and English without changing original lines");
            ImageIO.write(zh,"png",Paths.get("build/message-chinese.png").toFile());
        }
        click(button(window,"Cancel"));join(thread);check(result.get().cancelPressed(),"Translated confirmation Cancel keeps original cancel action");
        thread=new Thread(()->IJ.error("Error","Stack required"));thread.start();window=waitWindow("Error");snapshot(window,"error-message");assertInventory("需要图像堆栈。");click(button(window,"OK"));join(thread);
        IJ.showStatus("3D filtering...");Thread.sleep(650);
        check(ImageJ.getStatusBarText().equals("3D filtering..."),"Status rendering preserves original English API value");
        Field field=ImageJ.class.getDeclaredField("statusLine");field.setAccessible(true);javax.swing.JLabel label=(javax.swing.JLabel)field.get(IJ.getInstance());
        java.awt.image.BufferedImage chinese=new java.awt.image.BufferedImage(label.getWidth(),label.getHeight(),java.awt.image.BufferedImage.TYPE_INT_RGB);
        EventQueue.invokeAndWait(()->{ Graphics g=chinese.getGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,chinese.getWidth(),chinese.getHeight());label.printAll(g);g.dispose(); });MenuDisplayAgent.setEnabled(false);Thread.sleep(650);
        java.awt.image.BufferedImage english=new java.awt.image.BufferedImage(label.getWidth(),label.getHeight(),java.awt.image.BufferedImage.TYPE_INT_RGB);
        EventQueue.invokeAndWait(()->{ Graphics g=english.getGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,english.getWidth(),english.getHeight());label.printAll(g);g.dispose(); });
        System.out.println("STATUS UI="+label.getUI()+" text="+label.getText()+" size="+label.getSize());
        ImageIO.write(chinese,"png",Paths.get("build/status-chinese.png").toFile());ImageIO.write(english,"png",Paths.get("build/status-english.png").toFile());
        check(!Arrays.equals(chinese.getRGB(0,0,chinese.getWidth(),chinese.getHeight(),null,0,chinese.getWidth()),english.getRGB(0,0,english.getWidth(),english.getHeight(),null,0,english.getWidth())),"Status paints distinct Chinese and English text while preserving its model");
        ImageIO.write(chinese,"png",Paths.get("build/status-chinese.png").toFile());ImageIO.write(english,"png",Paths.get("build/status-english.png").toFile());
        MenuDisplayAgent.setEnabled(true);
    }
    private static java.awt.image.BufferedImage render(Component c) throws Exception {
        java.awt.image.BufferedImage image=new java.awt.image.BufferedImage(c.getWidth(),c.getHeight(),java.awt.image.BufferedImage.TYPE_INT_RGB);
        EventQueue.invokeAndWait(()->{Graphics g=image.getGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,image.getWidth(),image.getHeight());c.paint(g);g.dispose();});return image;
    }
    private static void setNumber(GenericDialog dialog,int index,String value) throws Exception { setText((TextField)dialog.getNumericFields().get(index),value); }
    private static void setText(TextField field,String value) throws Exception { if(nativeInput){input(field,value);return;}EventQueue.invokeAndWait(()->field.setText(value));Thread.sleep(100); }
    private static void setCheck(Window window,String caption,boolean state) throws Exception { Checkbox c=checkbox(window,caption);if(c!=null && c.getState()!=state)click(c); }
    private static void assertInventory(String text) throws Exception {
        String inventory=Files.readString(Paths.get("build/dialog-inventory.tsv"),StandardCharsets.UTF_8);
        check(inventory.contains(text),"Display mapping contains "+text.replace('\t',' '));
    }
    private static boolean samePixels(ImagePlus a,ImagePlus b) {
        if(a.getWidth()!=b.getWidth() || a.getHeight()!=b.getHeight() || a.getStackSize()!=b.getStackSize() || a.getBitDepth()!=b.getBitDepth())return false;
        for(int i=1;i<=a.getStackSize();i++) {
            Object p=a.getStack().getPixels(i),q=b.getStack().getPixels(i);
            if(p instanceof byte[] && !Arrays.equals((byte[])p,(byte[])q))return false;
            if(p instanceof short[] && !Arrays.equals((short[])p,(short[])q))return false;
            if(p instanceof float[] && !Arrays.equals((float[])p,(float[])q))return false;
            if(p instanceof int[] && !Arrays.equals((int[])p,(int[])q))return false;
        }return true;
    }
    private static Set<Integer> imageIds() {Set<Integer> ids=new HashSet<>();int[] list=WindowManager.getIDList();if(list!=null)for(int id:list)ids.add(id);return ids;}
    private static ImagePlus createdImage(Set<Integer> before) throws Exception {
        for(int n=0;n<40;n++){for(int id:imageIds())if(!before.contains(id))return WindowManager.getImage(id);Thread.sleep(50);}throw new AssertionError("No output image created");
    }
    private static void discardAll() {for(int id:imageIds()){ImagePlus image=WindowManager.getImage(id);image.changes=false;image.close();}}
    private static Thread command(ImagePlus image, String name) {
        Thread thread = new Thread(() -> new Executer(name).run(), "UI-" + name);
        WindowManager.setTempCurrentImage(thread, image);
        thread.start(); return thread;
    }
    private static void join(Thread thread) throws Exception {
        thread.join(8000);
        if (thread.isAlive()) {
            StringBuilder details = new StringBuilder();
            for (Window window : Window.getWindows()) if (window.isShowing() && window instanceof Dialog) {
                details.append(window).append('\n');
                ImageIO.write(robot.createScreenCapture(window.getBounds()), "png", Paths.get("build/dialog-failure.png").toFile());
                for (Component component : components(window)) if (component instanceof TextField) details.append(component).append('\n');
            }
            throw new AssertionError("Command did not finish: " + thread.getName() + "; " + details + "; focus=" + KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner());
        }
    }
    private static Window waitWindow(String title) throws Exception {
        for (int attempt = 0; attempt < 160; attempt++) {
            AtomicReference<Window> found = new AtomicReference<>();
            EventQueue.invokeAndWait(() -> {
                for (Window window : Window.getWindows()) {
                    if (!window.isShowing() || window instanceof ij.gui.ImageWindow) continue;
                    String value = window instanceof Dialog ? ((Dialog) window).getTitle() : window instanceof Frame ? ((Frame) window).getTitle() : "";
                    if(value==null)continue;
                    String canonical = value.endsWith("...") ? value.substring(0,value.length()-3).trim() : value.trim();
                    if (window.isShowing() && title.equals(canonical)) { window.setAlwaysOnTop(true); window.toFront(); found.set(window); }
                }
            });
            if (found.get() != null) return found.get();
            Thread.sleep(50);
        }
        StringBuilder visible = new StringBuilder();
        for (Window window : Window.getWindows()) if (window.isShowing()) visible.append(window).append('\n');
        throw new AssertionError("Missing parameter window " + title + "; visible=" + visible);
    }
    private static void snapshot(Window window, String name) throws Exception {
        Thread.sleep(900);
        EventQueue.invokeAndWait(()->{});
        String title=window instanceof Dialog?((Dialog)window).getTitle():((Frame)window).getTitle();
        String context=title.trim();if(context.endsWith("..."))context=context.substring(0,context.length()-3).trim();
        if(window instanceof YesNoCancelDialog || window instanceof MessageDialog)context="Messages";
        final String marker=context+"\t";boolean mapped=false;
        for(int attempt=0;attempt<100;attempt++){
            Path report=Paths.get("build/dialog-inventory.tsv");
            if(Files.exists(report) && Files.readString(report,StandardCharsets.UTF_8).lines().anyMatch(line->line.startsWith(marker))){mapped=true;break;}
            Thread.sleep(50);
        }
        if(!mapped)throw new AssertionError("Display mapping not ready for "+title);
        ImageIO.write(robot.createScreenCapture(window.getBounds()), "png", Paths.get("build/dialog-" + name + ".png").toFile());
        Path inventory = Paths.get("build/dialog-inventory.tsv");
        if (Files.exists(inventory)) Files.copy(inventory, Paths.get("build/dialog-" + name + ".tsv"), StandardCopyOption.REPLACE_EXISTING);
        System.out.println("Captured parameter window: " + name);
    }
    private static ImagePlus image(String title) {
        byte[] pixels = new byte[64*64];
        for (int y=0; y<64; y++) for (int x=0; x<64; x++)
            pixels[y*64+x] = (byte) (((x-18)*(x-18)+(y-18)*(y-18) <= 64 || (x-45)*(x-45)+(y-44)*(y-44) <= 36) ? 200 : 10);
        ImagePlus image = new ImagePlus(title, new ByteProcessor(64,64,pixels));
        image.setCalibration(new Calibration()); return image;
    }
    private static java.util.List<Component> components(Component parent) {
        java.util.List<Component> result = new ArrayList<>(); result.add(parent);
        if (parent instanceof Container) for (Component child : ((Container) parent).getComponents()) result.addAll(components(child));
        return result;
    }
    private static Button button(Window window, String text) {
        for (Component component : components(window))
            if (component instanceof Button && ((Button) component).getLabel().trim().equals(text)) return (Button) component;
        throw new AssertionError("Missing original button " + text);
    }
    private static Checkbox checkbox(Window window, String text) {
        for (Component component : components(window))
            if (component instanceof Checkbox && ((Checkbox) component).getLabel().trim().equals(text)) return (Checkbox) component;
        return null;
    }
    private static void click(Component component) throws Exception {
        if(nativeInput) {
            Point point=component.getLocationOnScreen();
            AtomicInteger delivered=new AtomicInteger();
            ActionListener trace=event->delivered.incrementAndGet();
            if(component instanceof Button)((Button)component).addActionListener(trace);
            robot.mouseMove(point.x+component.getWidth()/2,point.y+component.getHeight()/2);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);robot.waitForIdle();Thread.sleep(100);
            if(component instanceof Button){
                ((Button)component).removeActionListener(trace);
                System.out.println("CLICK "+((Button)component).getActionCommand()+" events="+delivered.get()+" focus="+KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner());
            }
            return;
        }
        EventQueue.invokeAndWait(() -> {
            if(component instanceof Button) {
                Button b=(Button)component; b.dispatchEvent(new ActionEvent(b,ActionEvent.ACTION_PERFORMED,b.getActionCommand()));
            } else if(component instanceof Checkbox) {
                Checkbox c=(Checkbox)component;c.setState(!c.getState());c.dispatchEvent(new ItemEvent(c,ItemEvent.ITEM_STATE_CHANGED,c.getLabel(),c.getState()?ItemEvent.SELECTED:ItemEvent.DESELECTED));
            }
        });
        Thread.sleep(150);
    }
    private static void input(TextField field, String value) throws Exception {
        click(field); robot.keyPress(KeyEvent.VK_CONTROL); robot.keyPress(KeyEvent.VK_A); robot.keyRelease(KeyEvent.VK_A); robot.keyRelease(KeyEvent.VK_CONTROL);
        for (char c : value.toCharArray()) {
            int key = KeyEvent.getExtendedKeyCodeForChar(c);
            boolean shift = Character.isUpperCase(c);
            if (shift) robot.keyPress(KeyEvent.VK_SHIFT);
            robot.keyPress(key); robot.keyRelease(key);
            if (shift) robot.keyRelease(KeyEvent.VK_SHIFT);
        }
    }
    private static void choose(Choice choice, String value) throws Exception {
        if(choice==null)throw new AssertionError("Missing choice for "+value);
        if(nativeInput) {
            click(choice);Thread.sleep(150);
            robot.keyPress(KeyEvent.VK_HOME);robot.keyRelease(KeyEvent.VK_HOME);
            for(int i=0;i<choice.getItemCount() && !choice.getItem(i).equals(value);i++){robot.keyPress(KeyEvent.VK_DOWN);robot.keyRelease(KeyEvent.VK_DOWN);}
            robot.keyPress(KeyEvent.VK_TAB);robot.keyRelease(KeyEvent.VK_TAB);robot.waitForIdle();return;
        }
        EventQueue.invokeAndWait(()->{
            choice.select(value); choice.dispatchEvent(new ItemEvent(choice,ItemEvent.ITEM_STATE_CHANGED,value,ItemEvent.SELECTED));
        });Thread.sleep(100);
    }
    private static void close(Window window) throws Exception {
        EventQueue.invokeAndWait(() -> window.dispatchEvent(new WindowEvent(window, WindowEvent.WINDOW_CLOSING)));
        Thread.sleep(600);
    }
    private static void check(boolean pass, String label) throws Exception {
        if (!pass) throw new AssertionError(label);
        checks.add("PASS: " + label); System.out.println("PASS: " + label);
    }
    private static void finish(Throwable failure) {
        checks.add(failure==null ? (messagesOnly?"PASS: Messages and status rendering.":fontsOnly?"PASS: Font preference title and native close.":probeOnly?"PASS: Forty requested windows inventoried.":channelsOnly?"PASS: Channel workflows with native input.":processingOnly?"PASS: Processing workflows with native input.":"PASS: Expanded Fiji workflows; input="+(nativeInput?"Robot mouse and keyboard":"original control events")) : "FAIL: "+failure);
        if(failure!=null)failure.printStackTrace();
        try { Files.write(Paths.get(messagesOnly?"build/expanded-messages-result.txt":fontsOnly?"build/expanded-fonts-result.txt":probeOnly?"build/expanded-probe-result.txt":channelsOnly?"build/expanded-channels-result.txt":processingOnly?"build/expanded-processing-result.txt":"build/expanded-smoke-result.txt"),checks,StandardCharsets.UTF_8); }catch(Exception ex){ex.printStackTrace();}
        Runtime.getRuntime().halt(failure==null?0:1);
    }
}
