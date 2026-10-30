package org.fiji.zh;

import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;

/** Exercises actual Windows menu display, menu clicks, shortcuts, refresh and restore. */
public final class MenuPeerSmokeTest {
    private static Frame frame;
    private static Menu file;
    private static MenuItem open;
    private static CheckboxMenuItem preview;
    private static final AtomicReference<String> command = new AtomicReference<>();
    private static final AtomicReference<ItemEvent> checkboxEvent = new AtomicReference<>();

    public static void main(String[] args) throws Exception {
        EventQueue.invokeAndWait(() -> {
            frame = new Frame("Fiji Chinese display compatibility test");
            file = new Menu("File");
            open = new MenuItem("Open...", new MenuShortcut(KeyEvent.VK_O));
            open.addActionListener(event -> command.set(event.getActionCommand()));
            file.addSeparator();
            file.add(open);
            Menu process = new Menu("Process");
            process.add(new MenuItem("Gaussian Blur..."));
            preview = new CheckboxMenuItem("Preview", true);
            preview.addItemListener(checkboxEvent::set);
            process.add(preview);
            MenuBar bar = new MenuBar();
            bar.add(file);
            bar.add(process);
            frame.setMenuBar(bar);
            frame.add(new Label("English Java model / Chinese native menu display"));
            frame.setBounds(250, 250, 650, 180);
            frame.setVisible(true);
            frame.setAlwaysOnTop(true);
            frame.toFront();
            frame.requestFocus();
        });
        Robot robot = new Robot();
        robot.setAutoDelay(150);
        Thread.sleep(1800);
        EventQueue.invokeAndWait(() -> {
            check(file.getLabel().equals("File"), "Original top-level label");
            check(open.getLabel().equals("Open..."), "Original item label");
            check(open.getActionCommand().equals("Open..."), "Original action command");
            check(open.getShortcut().getKey() == KeyEvent.VK_O, "Shortcut key");
        });
        ImageIO.write(robot.createScreenCapture(frame.getBounds()), "png", Paths.get("build/native-menu.png").toFile());
        // The coordinate belongs to this newly created test frame, not another app.
        Point location = frame.getLocationOnScreen();
        robot.mouseMove(location.x + 23, location.y + frame.getInsets().top - 10);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        Thread.sleep(400);
        ImageIO.write(robot.createScreenCapture(new Rectangle(location.x, location.y, 650, 260)), "png",
            Paths.get("build/native-menu-open.png").toFile());
        robot.keyPress(KeyEvent.VK_DOWN);
        robot.keyRelease(KeyEvent.VK_DOWN);
        robot.keyPress(KeyEvent.VK_ENTER);
        robot.keyRelease(KeyEvent.VK_ENTER);
        Thread.sleep(400);
        check("Open...".equals(command.get()), "Native menu click returns original command");
        command.set(null);
        robot.keyPress(KeyEvent.VK_CONTROL);
        robot.keyPress(KeyEvent.VK_O);
        robot.keyRelease(KeyEvent.VK_O);
        robot.keyRelease(KeyEvent.VK_CONTROL);
        Thread.sleep(400);
        check("Open...".equals(command.get()), "Native shortcut returns original command");
        robot.mouseMove(location.x + 60, location.y + frame.getInsets().top - 10);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK); robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        Thread.sleep(400);
        robot.keyPress(KeyEvent.VK_DOWN); robot.keyRelease(KeyEvent.VK_DOWN);
        robot.keyPress(KeyEvent.VK_DOWN); robot.keyRelease(KeyEvent.VK_DOWN);
        robot.keyPress(KeyEvent.VK_ENTER); robot.keyRelease(KeyEvent.VK_ENTER);
        Thread.sleep(300);
        check(!preview.getState(), "Native checkbox updates original state");
        check(checkboxEvent.get() != null && checkboxEvent.get().getSource() == preview &&
            "Preview".equals(checkboxEvent.get().getItem()), "Checkbox event keeps original source and English item");
        MenuDisplayAgent.setEnabled(false);
        Thread.sleep(700);
        ImageIO.write(robot.createScreenCapture(frame.getBounds()), "png", Paths.get("build/native-menu-english.png").toFile());
        check(file.getLabel().equals("File"), "Restore keeps model");
        MenuDisplayAgent.setEnabled(true);
        Thread.sleep(700);
        EventQueue.invokeAndWait(() -> {
            MenuItem dynamic = new MenuItem("Preview");
            file.add(dynamic);
        });
        Thread.sleep(700);
        check(Files.readString(Paths.get("build/menu-inventory.tsv")).contains("File/Preview\tPreview\t预览"),
            "New menu items are mapped");
        Font originalFont = open.getFont();
        EventQueue.invokeAndWait(() -> open.setFont(new Font("Dialog", Font.BOLD, 17)));
        Thread.sleep(700);
        java.lang.reflect.Field peer = MenuComponent.class.getDeclaredField("peer"); peer.setAccessible(true);
        java.lang.reflect.Field target = Class.forName("sun.awt.windows.WObjectPeer").getDeclaredField("target"); target.setAccessible(true);
        MenuItem display = (MenuItem) target.get(peer.get(open));
        check(display.getFont().getSize() == 17 && display.getFont().isBold(), "Cached font follows source font changes");
        MenuDisplayAgent.setEnabled(false); Thread.sleep(700);
        check(display.getFont().equals(open.getFont()), "English restores source font after cached Chinese font");
        EventQueue.invokeAndWait(() -> open.setFont(originalFont));
        EventQueue.invokeAndWait(() -> frame.dispose());
        System.out.println("PASS: Native menus, click dispatch, shortcut, checkbox, language restore, dynamic entries, original Java model.");
        System.exit(0);
    }

    private static void check(boolean success, String label) {
        if (!success) { System.err.println("FAIL: " + label); System.exit(1); }
        System.out.println("PASS: " + label);
    }
}
