import ij.*;
import ij.gui.GenericDialog;
import ij.measure.Calibration;
import ij.measure.ResultsTable;
import ij.plugin.frame.Recorder;
import ij.process.ByteProcessor;
import java.awt.*;
import java.awt.event.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import javax.imageio.ImageIO;
import org.fiji.zh.MenuDisplayAgent;

/** Native UI workflow checks for localized parameter windows. */
public final class Fiji_Dialog_Smoke {
    private static Robot robot;
    private static final java.util.List<String> checks = new ArrayList<>();
    private static final Path REPORT = Paths.get("build/dialog-smoke-result.txt");
    private static boolean thirdOnly;
    private static boolean fourthOnly;

    public static String startThirdBatch() { thirdOnly = true; return start(); }
    public static String startFourthBatch() { fourthOnly = true; return start(); }

    public static String start() {
        new Thread(() -> {
            try { while (IJ.macroRunning()) Thread.sleep(100); run(); }
            catch (Throwable exception) { finish(exception); }
        }, "Fiji-Dialog-UI-Test").start();
        return "started";
    }

    private static void run() throws Exception {
        robot = new Robot(); robot.setAutoDelay(80);
        MenuDisplayAgent.setEnabled(true);
        Thread.sleep(800);
        Recorder recorder = new Recorder();
        if (fourthOnly) {
            fourthBatch(); checkFourthRecording(recorder);
            recorder.close(); finish(null); return;
        }
        if (thirdOnly) {
            thirdBatch();
            checkThirdRecording(recorder);
            recorder.close(); finish(null); return;
        }
        ImagePlus image = image("dialog-test"); image.show();

        Thread command = command(image, "Gaussian Blur...");
        Window window = waitWindow("Gaussian Blur"); snapshot(window, "gaussian");
        GenericDialog gaussian = (GenericDialog) window;
        TextField sigma = (TextField) gaussian.getNumericFields().get(0);
        input(sigma, "2.5"); click(button(window, "OK")); join(command);
        ImagePlus reference = image("macro-reference");
        IJ.run(reference, "Gaussian Blur...", "sigma=2.5");
        check(Arrays.equals((byte[]) image.getProcessor().getPixels(), (byte[]) reference.getProcessor().getPixels()),
            "Gaussian UI and English macro produce identical pixels");

        command = command(image, "Set Scale...");
        window = waitWindow("Set Scale"); snapshot(window, "scale");
        GenericDialog scale = (GenericDialog) window;
        input((TextField) scale.getNumericFields().get(0), "10");
        input((TextField) scale.getNumericFields().get(1), "2");
        input((TextField) scale.getStringFields().get(0), "um");
        Checkbox global = checkbox(window, "Global"); if (global != null && global.getState()) click(global);
        snapshot(window, "scale-calibrated");
        enter(); join(command);
        Calibration expectedScale = new Calibration(); expectedScale.setUnit("um");
        check(Math.abs(image.getCalibration().pixelWidth - 0.2) < 1e-10 && image.getCalibration().getUnit().equals(expectedScale.getUnit()),
            "Set Scale keyboard confirmation preserves numeric calibration and unit");

        command = command(image, "Set Measurements...");
        window = waitWindow("Set Measurements"); snapshot(window, "measurements");
        for (Component component : components(window)) if (component instanceof Checkbox) {
            Checkbox item = (Checkbox) component;
            boolean wanted = Set.of("Area", "Mean gray value", "Centroid").contains(item.getLabel().trim());
            if (item.getState() != wanted) click(item);
        }
        input((TextField) ((GenericDialog) window).getNumericFields().get(0), "3");
        click(button(window, "OK")); join(command);
        int flags = ij.plugin.filter.Analyzer.getMeasurements();
        check((flags & ij.measure.Measurements.AREA) != 0 && (flags & ij.measure.Measurements.MEAN) != 0 &&
            (flags & ij.measure.Measurements.CENTROID) != 0, "Measurement checkbox actions update the original settings");

        image.changes = false; image.close();
        image = image("particle-ui-test"); image.show();
        image.getProcessor().setThreshold(100, 255, ij.process.ImageProcessor.NO_LUT_UPDATE);
        command = command(image, "Analyze Particles...");
        window = waitWindow("Analyze Particles"); snapshot(window, "particles");
        GenericDialog particles = (GenericDialog) window;
        input((TextField) particles.getStringFields().get(0), "10-Infinity");
        input((TextField) particles.getStringFields().get(1), "0.00-1.00");
        Choice show = (Choice) particles.getChoices().get(0);
        choose(show, "Outlines");
        check(show.getSelectedItem().equals("Outlines"), "Translated particle output choice returns English Outlines");
        choose(show, "Nothing");
        for (Component component : components(window)) if (component instanceof Checkbox) {
            Checkbox item = (Checkbox) component;
            boolean wanted = Set.of("Display results", "Clear results").contains(item.getLabel().trim());
            if (item.getState() != wanted) click(item);
        }
        click(button(window, "OK")); join(command);
        check(ResultsTable.getResultsTable().size() == 2 && ResultsTable.getResultsTable().columnExists("Area"),
            "Particle UI finds two objects and keeps English result headings");

        image.changes = false; image.close();
        image = image("background-ui-test"); image.show();
        command = command(image, "Subtract Background...");
        window = waitWindow("Subtract Background"); snapshot(window, "background");
        input((TextField) ((GenericDialog) window).getNumericFields().get(0), "12");
        for (Component component : components(window)) if (component instanceof Checkbox && ((Checkbox) component).getState()) click(component);
        click(button(window, "OK")); join(command);
        reference = image("background-macro-reference");
        IJ.run(reference, "Subtract Background...", "rolling=12");
        check(Arrays.equals((byte[]) image.getProcessor().getPixels(), (byte[]) reference.getProcessor().getPixels()),
            "Background subtraction UI and English macro produce identical pixels");

        image.changes = false; image.close();
        image = image("display-ui-test"); image.show();
        byte[] before = ((byte[]) image.getProcessor().getPixels()).clone();
        command(image, "Brightness/Contrast...");
        window = waitWindow("B&C"); snapshot(window, "contrast");
        click(button(window, "Auto")); Thread.sleep(400);
        click(button(window, "Reset")); Thread.sleep(400);
        check(image.getDisplayRangeMin() == 0 && image.getDisplayRangeMax() == 255 &&
            Arrays.equals(before, (byte[]) image.getProcessor().getPixels()), "Contrast Auto/Reset preserve pixels and reset display range");
        close(window);

        command(image, "Threshold...");
        window = waitWindow("Threshold"); snapshot(window, "threshold");
        click(button(window, "Set"));
        Window levels = waitWindow("Set Threshold Levels"); snapshot(levels, "threshold-levels");
        GenericDialog thresholds = (GenericDialog) levels;
        input((TextField) thresholds.getNumericFields().get(0), "100");
        input((TextField) thresholds.getNumericFields().get(1), "200");
        enter(); Thread.sleep(400);
        check(image.getProcessor().getMinThreshold() == 100 && image.getProcessor().getMaxThreshold() == 200,
            "Threshold Set and keyboard confirmation update original threshold values");
        click(button(window, "Reset")); Thread.sleep(350);
        check(image.getProcessor().getMinThreshold() == ij.process.ImageProcessor.NO_THRESHOLD, "Threshold Reset reaches original handler");
        close(window);

        command = command(image, "Gaussian Blur...");
        window = waitWindow("Gaussian Blur"); Thread.sleep(700);
        robot.keyPress(KeyEvent.VK_ESCAPE); robot.keyRelease(KeyEvent.VK_ESCAPE); join(command);
        check(Arrays.equals(before, (byte[]) image.getProcessor().getPixels()), "Escape cancels parameter dialog without processing pixels");
        secondBatch();
        thirdBatch();
        checkThirdRecording(recorder);
        fourthBatch();
        checkFourthRecording(recorder);
        String recorded = recorder.getText();
        Files.writeString(Paths.get("build/dialog-recorded-macro.txt"), recorded, StandardCharsets.UTF_8);
        check(recorded.contains("Gaussian Blur") && recorded.contains("sigma=2.5") && recorded.contains("Analyze Particles") &&
            recorded.contains("Z Project") && recorded.contains("Max Intensity") && recorded.contains("Size...") && recorded.contains("Bilinear") &&
            recorded.codePoints().noneMatch(c -> c >= 0x4e00 && c <= 0x9fff), "Recorded workflows retain English commands and parameter keys");
        recorder.close(); image.changes = false; image.close();
        finish(null);
    }

    private static void secondBatch() throws Exception {
        ImagePlus stack = stack("projection-ui-test"); stack.show();
        Set<Integer> existing = imageIds();
        Thread command = command(stack, "Z Project...");
        Window window = waitWindow("ZProjection");
        Thread.sleep(700);
        GenericDialog projection = (GenericDialog) window;
        input((TextField) projection.getNumericFields().get(0), "1");
        input((TextField) projection.getNumericFields().get(1), "3");
        Choice method = (Choice) projection.getChoices().get(0);
        choose(method, "Max Intensity");
        check(method.getSelectedItem().equals("Max Intensity"), "Chinese projection choice returns English Max Intensity");
        snapshot(window, "z-projection");
        click(button(window, "OK")); join(command);
        ImagePlus projected = createdImage(existing);
        existing = imageIds();
        IJ.run(stack("projection-macro-reference"), "Z Project...", "start=1 stop=3 projection=[Max Intensity]");
        ImagePlus expectedProjection = createdImage(existing);
        check(Arrays.equals((byte[]) projected.getProcessor().getPixels(), (byte[]) expectedProjection.getProcessor().getPixels()),
            "Z projection UI and English macro produce identical pixels");
        discard(projected); discard(expectedProjection); discard(stack);

        ImagePlus image = image("resize-ui-test"); image.show();
        ImagePlus reference = image("resize-macro-reference");
        command = command(image, "Size...");
        window = waitWindow("Resize"); Thread.sleep(700);
        GenericDialog resize = (GenericDialog) window;
        setCheckbox(window, "Constrain aspect ratio", false);
        setCheckbox(window, "Average when downsizing", false);
        input((TextField) resize.getNumericFields().get(0), "32");
        input((TextField) resize.getNumericFields().get(1), "24");
        Choice interpolation = (Choice) resize.getChoices().get(0);
        choose(interpolation, "Bilinear");
        snapshot(window, "resize");
        check(interpolation.getSelectedItem().equals("Bilinear"), "Chinese interpolation choice returns English Bilinear");
        click(button(window, "OK")); join(command);
        IJ.run(reference, "Size...", "width=32 height=24 interpolation=Bilinear");
        check(image.getWidth() == 32 && image.getHeight() == 24 &&
            Arrays.equals((byte[]) image.getProcessor().getPixels(), (byte[]) reference.getProcessor().getPixels()),
            "Resize UI preserves requested dimensions and matches English macro pixels");
        discard(image);

        image = image("rotate-ui-test"); image.show(); reference = image("rotate-macro-reference");
        // Image/Transform uses a trailing space; the other Rotate command rotates a selection.
        command = command(image, "Rotate... ");
        window = waitWindow("Rotate"); Thread.sleep(700);
        GenericDialog rotate = (GenericDialog) window;
        input((TextField) rotate.getNumericFields().get(0), "30");
        input((TextField) rotate.getNumericFields().get(1), "1");
        choose((Choice) rotate.getChoices().get(0), "Bilinear");
        for (Component component : components(window))
            if (component instanceof Checkbox && ((Checkbox) component).getState()) click(component);
        snapshot(window, "rotate");
        click(button(window, "OK")); join(command);
        IJ.run(reference, "Rotate... ", "angle=30 grid=1 interpolation=Bilinear");
        check(image.getWidth() == reference.getWidth() && image.getHeight() == reference.getHeight() &&
            Arrays.equals((byte[]) image.getProcessor().getPixels(), (byte[]) reference.getProcessor().getPixels()),
            "Rotation UI and English macro produce identical dimensions and pixels");
        discard(image);

        short[] values = new short[32*32];
        for (int i=0; i<values.length; i++) values[i] = (short) (i * 10);
        image = new ImagePlus("display-range-ui-test", new ij.process.ShortProcessor(32,32,values,null)); image.show();
        command(image, "Brightness/Contrast...");
        Window contrast = waitWindow("B&C"); Thread.sleep(700);
        click(button(contrast, "Set")); window = waitWindow("Set Display Range"); Thread.sleep(700);
        GenericDialog range = (GenericDialog) window;
        Choice bitRange = (Choice) range.getChoices().get(0);
        choose(bitRange, "12-bit (0-4095)");
        check(bitRange.getSelectedItem().equals("12-bit (0-4095)"), "Translated 16-bit range choice keeps original English value");
        choose(bitRange, "Automatic");
        input((TextField) range.getNumericFields().get(0), "100");
        input((TextField) range.getNumericFields().get(1), "700");
        setCheckbox(window, "Propagate to all other open images", false);
        snapshot(window, "display-range");
        click(button(window, "OK"));
        for (int i=0; i<30 && image.getDisplayRangeMin() != 100; i++) Thread.sleep(100);
        check(image.getDisplayRangeMin() == 100 && image.getDisplayRangeMax() == 700 &&
            Arrays.equals(values, (short[]) image.getProcessor().getPixels()),
            "Set Display Range updates numeric limits without changing 16-bit pixels");
        close(contrast); discard(image);

        ImagePlus multichannel = stack("composite-range-ui-test"); multichannel.setDimensions(3,1,1);
        image = new CompositeImage(multichannel, CompositeImage.COMPOSITE); image.show();
        command(image, "Brightness/Contrast..."); contrast = waitWindow("B&C"); Thread.sleep(700);
        click(button(contrast, "Set")); window = waitWindow("Set Display Range");
        snapshot(window, "display-range-composite");
        check(checkbox(window, "Propagate to all other 3 channel images") != null &&
            checkbox(window, "Propagate to the other 2 channels of this image") != null,
            "Composite propagation captions retain original channel counts");
        robot.keyPress(KeyEvent.VK_ESCAPE); robot.keyRelease(KeyEvent.VK_ESCAPE); Thread.sleep(300);
        close(contrast); discard(image);
    }

    private static void setCheckbox(Window window, String text, boolean state) throws Exception {
        Checkbox item = checkbox(window, text);
        if (item == null) throw new AssertionError("Missing original checkbox " + text);
        if (item.getState() != state) click(item);
    }

    private static void thirdBatch() throws Exception {
        ImagePlus image = image("canvas-ui-test"); image.show();
        ImagePlus reference = image("canvas-macro-reference");
        Thread command = command(image, "Canvas Size...");
        Window window = waitWindow("Resize Image Canvas"); Thread.sleep(700);
        GenericDialog canvas = (GenericDialog) window;
        input((TextField) canvas.getNumericFields().get(0), "80");
        input((TextField) canvas.getNumericFields().get(1), "72");
        Choice position = (Choice) canvas.getChoices().get(0);
        choose(position, "Top-Left"); setCheckbox(window, "Zero Fill", true);
        snapshot(window, "canvas");
        check(position.getSelectedItem().equals("Top-Left"), "Translated canvas anchor returns English Top-Left");
        click(button(window, "OK")); join(command);
        IJ.run(reference, "Canvas Size...", "width=80 height=72 position=Top-Left zero");
        check(image.getWidth() == 80 && image.getHeight() == 72 && samePixels(image, reference) && image.getProcessor().get(79,71) == 0,
            "Canvas dimensions, zero fill and pixels match English macro");
        discard(image);

        image = stack("stack-canvas-ui-test"); image.show(); reference = stack("stack-canvas-reference");
        command = command(image, "Canvas Size...");
        window = waitWindow("Resize Stack Canvas"); Thread.sleep(700);
        canvas = (GenericDialog) window;
        input((TextField) canvas.getNumericFields().get(0), "40");
        input((TextField) canvas.getNumericFields().get(1), "30");
        choose((Choice) canvas.getChoices().get(0), "Center"); setCheckbox(window, "Zero Fill", true);
        snapshot(window, "canvas-stack"); click(button(window, "OK")); join(command);
        IJ.run(reference, "Canvas Size...", "width=40 height=30 position=Center zero");
        check(image.getStackSize() == 3 && samePixels(image, reference), "Stack canvas processes every slice exactly like English macro");
        discard(image);

        image = stack("duplicate-ui-test"); image.show(); reference = stack("duplicate-macro-reference");
        Set<Integer> existing = imageIds();
        command = command(image, "Duplicate..."); window = waitWindow("Duplicate"); Thread.sleep(700);
        GenericDialog duplicate = (GenericDialog) window;
        input((TextField) duplicate.getStringFields().get(0), "Threshold");
        setCheckbox(window, "Duplicate stack", true);
        input((TextField) duplicate.getStringFields().get(1), "2-3");
        snapshot(window, "duplicate"); enter(); join(command);
        ImagePlus copied = createdImage(existing);
        existing = imageIds();
        IJ.run(reference, "Duplicate...", "title=macro-copy duplicate range=2-3");
        ImagePlus expectedCopy = createdImage(existing);
        check(copied.getTitle().equals("Threshold") && copied.getStackSize() == 2 && samePixels(copied, expectedCopy),
            "Duplicate preserves user title and slice range and matches English macro pixels");
        discard(copied); discard(expectedCopy); discard(image);

        // A dictionary word as the image title must remain a user title.
        image = stack("Area"); image.show(); reference = stack("properties-reference");
        byte[] original = ((byte[]) image.getStack().getPixels(1)).clone();
        command = command(image, "Properties..."); window = waitWindow("Area"); Thread.sleep(700);
        GenericDialog properties = (GenericDialog) window;
        input((TextField) properties.getStringFields().get(0), "um");
        input((TextField) properties.getNumericFields().get(3), "0.25");
        input((TextField) properties.getNumericFields().get(4), "0.5");
        input((TextField) properties.getNumericFields().get(5), "2");
        input((TextField) properties.getStringFields().get(3), "1.5 sec");
        input((TextField) properties.getStringFields().get(4), "2,3,4");
        setCheckbox(window, "Global", false); setCheckbox(window, "Invert Y coordinates", false);
        snapshot(window, "properties");
        check(((Dialog) window).getTitle().equals("Area"), "Image Properties retains the original user title");
        click(button(window, "OK")); join(command);
        IJ.run(reference, "Properties...", "channels=1 slices=3 frames=1 unit=um pixel_width=0.25 pixel_height=0.5 voxel_depth=2 frame=[1.5 sec] origin=2,3,4");
        Calibration calibration = image.getCalibration(), expected = reference.getCalibration();
        check(calibration.pixelWidth == 0.25 && calibration.pixelHeight == 0.5 && calibration.pixelDepth == 2 &&
            calibration.frameInterval == 1.5 && calibration.xOrigin == 2 && calibration.yOrigin == 3 && calibration.zOrigin == 4 &&
            calibration.getUnit().equals(expected.getUnit()) && expected.pixelWidth == calibration.pixelWidth &&
            expected.pixelHeight == calibration.pixelHeight && expected.pixelDepth == calibration.pixelDepth &&
            Arrays.equals(original, (byte[]) image.getStack().getPixels(1)),
            "Image Properties preserves units, voxel sizes, time, origin and pixels and matches English macro calibration");
        discard(image);

        image = stack("enhance-ui-test"); image.show(); reference = stack("enhance-reference");
        command = command(image, "Enhance Contrast..."); window = waitWindow("Enhance Contrast"); Thread.sleep(700);
        GenericDialog enhance = (GenericDialog) window;
        input((TextField) enhance.getNumericFields().get(0), "0.5");
        setCheckbox(window, "Normalize", true); setCheckbox(window, "Equalize histogram", false);
        setCheckbox(window, "Process all 3 slices", true); setCheckbox(window, "Use stack histogram", true);
        snapshot(window, "enhance-contrast"); click(button(window, "OK")); join(command);
        IJ.run(reference, "Enhance Contrast...", "saturated=0.5 normalize process_all use");
        check(samePixels(image, reference), "Enhance Contrast processes the entire stack exactly like English macro");
        discard(image);

        image = image("scale-bar-ui-test"); image.getCalibration().setUnit("um");
        image.getCalibration().pixelWidth = 0.5; image.getCalibration().pixelHeight = 0.5;
        reference = image("scale-bar-reference"); reference.setCalibration(image.getCalibration().copy());
        original = ((byte[]) image.getProcessor().getPixels()).clone(); image.show();
        command = command(image, "Scale Bar..."); window = waitWindow("Scale Bar"); Thread.sleep(700);
        GenericDialog scaleBar = (GenericDialog) window;
        input((TextField) scaleBar.getNumericFields().get(0), "10");
        input((TextField) scaleBar.getNumericFields().get(1), "10");
        input((TextField) scaleBar.getNumericFields().get(2), "4");
        input((TextField) scaleBar.getNumericFields().get(3), "12");
        choose((Choice) scaleBar.getChoices().get(0), "White");
        choose((Choice) scaleBar.getChoices().get(1), "None");
        Choice location = (Choice) scaleBar.getChoices().get(2); choose(location, "Upper Left");
        for (Component component : components(window)) if (component instanceof Checkbox) {
            Checkbox item = (Checkbox) component;
            boolean wanted = Set.of("Horizontal", "Hide Text", "Overlay").contains(item.getLabel().trim());
            if (item.getState() != wanted) click(item);
        }
        snapshot(window, "scale-bar");
        check(location.getSelectedItem().equals("Upper Left"), "Translated scale bar location returns English Upper Left");
        click(button(window, "OK")); join(command);
        IJ.run(reference, "Scale Bar...", "width=10 height=10 thickness=4 font=12 color=White background=None location=[Upper Left] horizontal hide overlay");
        check(image.getOverlay() != null && image.getOverlay().size() == 1 && image.getOverlay().get(0).getBounds().width == 20 &&
            Arrays.equals(original, (byte[]) image.getProcessor().getPixels()) &&
            Arrays.equals((int[]) image.flatten().getProcessor().getPixels(), (int[]) reference.flatten().getProcessor().getPixels()),
            "Scale bar overlay preserves original pixels and calibrated length and matches English macro rendering");
        command = command(image, "Scale Bar..."); window = waitWindow("Scale Bar");
        snapshot(window, "scale-bar-remove"); click(button(window, "Remove Scale Bar")); join(command);
        check((image.getOverlay() == null || image.getOverlay().size() == 0) && Arrays.equals(original, (byte[]) image.getProcessor().getPixels()),
            "Remove Scale Bar reaches the original handler without changing pixels");
        discard(image);
    }

    private static boolean samePixels(ImagePlus actual, ImagePlus expected) {
        if (actual.getWidth() != expected.getWidth() || actual.getHeight() != expected.getHeight() || actual.getStackSize() != expected.getStackSize()) return false;
        for (int i=1; i<=actual.getStackSize(); i++)
            if (!Arrays.equals((byte[]) actual.getStack().getPixels(i), (byte[]) expected.getStack().getPixels(i))) return false;
        return true;
    }
    private static void fourthBatch() throws Exception {
        for (String filter : new String[]{"Median", "Mean", "Minimum", "Maximum", "Variance"}) {
            ImagePlus image = filterImage("filter-ui-" + filter); image.show();
            ImagePlus reference = filterImage("filter-macro-" + filter);
            Thread command = command(image, filter + "...");
            Window window = waitWindow(filter); Thread.sleep(700);
            GenericDialog dialog = (GenericDialog) window;
            TextField radius = (TextField) dialog.getNumericFields().get(0);
            input(radius, "2.5");
            if (filter.equals("Median")) {
                MenuDisplayAgent.setEnabled(false); Thread.sleep(650);
                check(radius.getText().equals("2.5") && ((Dialog) window).getTitle().startsWith("Median"),
                    "Language switch preserves filter radius and original window title");
                MenuDisplayAgent.setEnabled(true); Thread.sleep(650);
                setCheckbox(window, "Preview", true);
                waitPixelsChanged(image, filterImage("original"));
                check(!samePixels(image, filterImage("original")), "Median preview reaches the original filter");
            }
            snapshot(window, "filter-" + filter.toLowerCase(Locale.ROOT));
            click(button(window, "OK")); join(command);
            IJ.run(reference, filter + "...", "radius=2.5");
            check(samePixels(image, reference), filter + " UI and English macro produce identical pixels");
            discard(image);
        }

        ImagePlus image = filterImage("filter-preview-cancel"); image.show();
        ImagePlus reference = filterImage("filter-cancel-original");
        Thread command = command(image, "Median..."); Window window = waitWindow("Median"); Thread.sleep(700);
        setCheckbox(window, "Preview", true); waitPixelsChanged(image, reference);
        check(!samePixels(image, reference), "Preview changes the dedicated test image before cancellation");
        robot.keyPress(KeyEvent.VK_ESCAPE); robot.keyRelease(KeyEvent.VK_ESCAPE); join(command);
        check(samePixels(image, reference), "Escape restores original pixels after filter preview"); discard(image);

        image = filterImage("unsharp-ui-test"); image.show(); reference = filterImage("unsharp-reference");
        command = command(image, "Unsharp Mask..."); window = waitWindow("Unsharp Mask"); Thread.sleep(700);
        GenericDialog unsharp = (GenericDialog) window;
        input((TextField) unsharp.getNumericFields().get(0), "1.5");
        input((TextField) unsharp.getNumericFields().get(1), "0.6");
        snapshot(window, "unsharp-mask"); enter(); join(command);
        IJ.run(reference, "Unsharp Mask...", "radius=1.5 mask=0.6");
        check(samePixels(image, reference), "Unsharp Mask keyboard confirmation and pixels match English macro"); discard(image);

        image = binaryImage("binary-ui-test"); image.show(); reference = binaryImage("binary-reference");
        command = command(image, "Options..."); window = waitWindow("Binary Options"); Thread.sleep(700);
        GenericDialog binary = (GenericDialog) window;
        input((TextField) binary.getNumericFields().get(0), "2");
        input((TextField) binary.getNumericFields().get(1), "3");
        setCheckbox(window, "Black background", true); setCheckbox(window, "Pad edges when eroding", false);
        Choice output = (Choice) binary.getChoices().get(0), operation = (Choice) binary.getChoices().get(1);
        choose(output, "16-bit"); choose(operation, "Dilate");
        check(output.getSelectedItem().equals("16-bit") && operation.getSelectedItem().equals("Dilate"),
            "Chinese binary choices return English output type and operation");
        snapshot(window, "binary-options"); click(button(window, "OK")); join(command);
        check(ij.Prefs.blackBackground && !ij.Prefs.padEdges && ij.plugin.filter.EDM.getOutputType() == 2,
            "Binary Options writes original background, edge and EDM settings");
        IJ.run(reference, "Options...", "iterations=2 count=3 black edm=16-bit do=Dilate");
        check(samePixels(image, reference), "Binary Options applies selected morphology exactly like English macro");
        discard(image);

        // Verify the same settings dialog also works with no image open.
        for (int id : imageIds()) discard(WindowManager.getImage(id));
        command = command(null, "Options..."); window = waitWindow("Binary Options"); Thread.sleep(700);
        binary = (GenericDialog) window;
        input((TextField) binary.getNumericFields().get(0), "1");
        input((TextField) binary.getNumericFields().get(1), "1");
        choose((Choice) binary.getChoices().get(0), "Overwrite");
        snapshot(window, "binary-options-no-image"); click(button(window, "OK")); join(command);
        check(binary.getChoices().size() == 1 && ij.plugin.filter.EDM.getOutputType() == 0,
            "Binary Options accepts settings with no image open");
    }
    private static void waitPixelsChanged(ImagePlus image, ImagePlus original) throws Exception {
        for (int i=0; i<60 && samePixels(image, original); i++) Thread.sleep(50);
    }
    private static ImagePlus filterImage(String title) {
        byte[] pixels = new byte[64*64];
        for (int y=0; y<64; y++) for (int x=0; x<64; x++)
            pixels[y*64+x] = (byte) ((x*7+y*11+(x*y)%43)%210 + ((x+y)%17 == 0 ? 40 : 0));
        return new ImagePlus(title, new ByteProcessor(64,64,pixels));
    }
    private static ImagePlus binaryImage(String title) {
        byte[] pixels = new byte[64*64];
        for (int y=0; y<64; y++) for (int x=0; x<64; x++)
            pixels[y*64+x] = (byte) (((x-18)*(x-18)+(y-18)*(y-18) <= 64 || (x-45)*(x-45)+(y-44)*(y-44) <= 36) ? 255 : 0);
        return new ImagePlus(title, new ByteProcessor(64,64,pixels));
    }
    private static void checkFourthRecording(Recorder recorder) throws Exception {
        String recorded = recorder.getText();
        Files.writeString(Paths.get("build/dialog-batch4-recorded-macro.txt"), recorded, StandardCharsets.UTF_8);
        check(recorded.contains("Median...") && recorded.contains("Variance...") && recorded.contains("Unsharp Mask...") &&
            recorded.contains("mask=0.6") && recorded.contains("do=Dilate") && recorded.contains("edm=16-bit") &&
            recorded.codePoints().noneMatch(c -> c >= 0x4e00 && c <= 0x9fff), "Filters and Binary Options record original English commands and parameter keys");
    }
    private static void checkThirdRecording(Recorder recorder) throws Exception {
        String recorded = recorder.getText();
        Files.writeString(Paths.get("build/dialog-batch3-recorded-macro.txt"), recorded, StandardCharsets.UTF_8);
        check(recorded.contains("Canvas Size") && recorded.contains("Duplicate") && recorded.contains("Enhance Contrast") &&
            recorded.contains("Scale Bar") && recorded.contains("Upper Left") &&
            recorded.codePoints().noneMatch(c -> c >= 0x4e00 && c <= 0x9fff), "New batch records English commands and options");
    }
    private static Set<Integer> imageIds() {
        Set<Integer> result = new HashSet<>(); int[] ids = WindowManager.getIDList();
        if (ids != null) for (int id : ids) result.add(id);
        return result;
    }
    private static ImagePlus createdImage(Set<Integer> previous) throws Exception {
        for (int i=0; i<40; i++) {
            for (int id : imageIds()) if (!previous.contains(id)) return WindowManager.getImage(id);
            Thread.sleep(50);
        }
        throw new AssertionError("Command did not create its output image");
    }
    private static void discard(ImagePlus image) { image.changes = false; image.close(); }
    private static ImagePlus stack(String title) {
        ImageStack stack = new ImageStack(32,24);
        for (int z=0; z<3; z++) {
            byte[] pixels = new byte[32*24];
            for (int y=0; y<24; y++) for (int x=0; x<32; x++) pixels[y*32+x] = (byte) ((x*3+y*5+z*37)%230);
            stack.addSlice(new ByteProcessor(32,24,pixels));
        }
        return new ImagePlus(title, stack);
    }

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
                    if (window instanceof ij.gui.ImageWindow) continue;
                    String value = window instanceof Dialog ? ((Dialog) window).getTitle() : window instanceof Frame ? ((Frame) window).getTitle() : "";
                    String canonical = value.endsWith("...") ? value.substring(0,value.length()-3).trim() : value;
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
        Point point = component.getLocationOnScreen();
        robot.mouseMove(point.x+component.getWidth()/2, point.y+component.getHeight()/2);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK); robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK); robot.waitForIdle();
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
    private static void enter() { robot.keyPress(KeyEvent.VK_ENTER); robot.keyRelease(KeyEvent.VK_ENTER); }
    private static void choose(Choice choice, String value) throws Exception {
        EventQueue.invokeAndWait(() -> choice.requestFocusInWindow());
        Thread.sleep(150);
        robot.keyPress(KeyEvent.VK_HOME); robot.keyRelease(KeyEvent.VK_HOME);
        for (int index=0; index<choice.getItemCount() && !choice.getItem(index).equals(value); index++) {
            robot.keyPress(KeyEvent.VK_DOWN); robot.keyRelease(KeyEvent.VK_DOWN);
        }
        for (int i=0; i<20 && !choice.getSelectedItem().equals(value); i++) Thread.sleep(50);
        // Commit the choice and leave its popup before the next native mouse click.
        // Enter would also accept GenericDialog; Tab only advances focus.
        robot.keyPress(KeyEvent.VK_TAB); robot.keyRelease(KeyEvent.VK_TAB); robot.waitForIdle();
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
        checks.add(failure == null ? (fourthOnly ? "PASS: Seven filter and binary parameter workflows." : thirdOnly ? "PASS: Five new real-Fiji parameter workflows." : "PASS: All twenty-three real-Fiji parameter workflows and threshold subdialog.") : "FAIL: " + failure);
        if (failure != null) failure.printStackTrace();
        try { Files.write(fourthOnly ? Paths.get("build/dialog-batch4-result.txt") : thirdOnly ? Paths.get("build/dialog-batch3-result.txt") : REPORT, checks, StandardCharsets.UTF_8); } catch (Exception exception) { exception.printStackTrace(); }
        Runtime.getRuntime().halt(failure == null ? 0 : 1);
    }
}
