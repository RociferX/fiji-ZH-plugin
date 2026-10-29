package org.fiji.zh;

import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;
import java.util.concurrent.atomic.*;
import javax.imageio.ImageIO;

/** Native controls remain the application's controls after display-only localization. */
public final class DialogPeerSmokeTest {
    private static Frame frame;
    private static Label label;
    private static Checkbox checkbox;
    private static Choice choice;
    private static Button button;
    private static TextField field;
    private static final AtomicReference<Object> actionSource = new AtomicReference<>();
    private static final AtomicReference<String> command = new AtomicReference<>();
    private static final AtomicReference<ItemEvent> selected = new AtomicReference<>();
    private static final AtomicReference<ItemEvent> checked = new AtomicReference<>();
    private static final AtomicInteger actions = new AtomicInteger(), escape = new AtomicInteger();

    public static void main(String[] args) throws Exception {
        EventQueue.invokeAndWait(() -> {
            frame = new Frame("Analyze Particles");
            frame.setLayout(new FlowLayout());
            label = new Label("Area");
            field = new TextField("123.45", 10);
            checkbox = new Checkbox("Display results", false);
            checkbox.addItemListener(checked::set);
            choice = new Choice();
            choice.add("Nothing"); choice.add("Outlines"); choice.add("Masks");
            choice.addItemListener(selected::set);
            button = new Button("Cancel");
            button.addActionListener(event -> { actionSource.set(event.getSource()); command.set(event.getActionCommand()); actions.incrementAndGet(); });
            button.addKeyListener(new KeyAdapter() {
                @Override public void keyPressed(KeyEvent event) {
                    if (event.getSource() == button && event.getKeyCode() == KeyEvent.VK_ESCAPE) escape.incrementAndGet();
                }
            });
            frame.add(label); frame.add(field); frame.add(checkbox); frame.add(choice); frame.add(button);
            frame.setBounds(220, 250, 720, 160);
            frame.setAlwaysOnTop(true);
            frame.setVisible(true);
            frame.toFront(); frame.requestFocus();
        });
        Thread.sleep(1700);
        check(frame.getTitle().equals("Analyze Particles") && label.getText().equals("Area"), "Original title and label");
        check(button.getLabel().equals("Cancel") && checkbox.getLabel().equals("Display results"), "Original button and checkbox labels");
        check(choice.getItem(1).equals("Outlines") && field.getText().equals("123.45"), "Original choice items and numeric text");
        Robot robot = new Robot(); robot.setAutoDelay(100);
        ImageIO.write(robot.createScreenCapture(frame.getBounds()), "png", Paths.get("build/dialog-native-chinese.png").toFile());
        click(robot, checkbox);
        Thread.sleep(250);
        if (!checkbox.getState()) {
            ImageIO.write(robot.createScreenCapture(frame.getBounds()), "png", Paths.get("build/dialog-native-failure.png").toFile());
            System.err.println("Checkbox event=" + checked.get() + "; focus=" + KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner() +
                "; pointer=" + MouseInfo.getPointerInfo().getLocation() + "; bounds=" + checkbox.getLocationOnScreen() + "/" + checkbox.getSize());
        }
        check(checkbox.getState(), "Native checkbox updates original control");
        click(robot, choice);
        robot.keyPress(KeyEvent.VK_DOWN); robot.keyRelease(KeyEvent.VK_DOWN);
        robot.keyPress(KeyEvent.VK_ENTER); robot.keyRelease(KeyEvent.VK_ENTER);
        Thread.sleep(250);
        System.out.println("Choice selected: " + choice.getSelectedIndex() + "/" + choice.getSelectedItem() + "; event=" + selected.get() + "; focus=" + KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner());
        check(choice.getSelectedItem().equals("Outlines"), "Native choice returns original English value");
        check(selected.get() != null && selected.get().getSource() == choice && selected.get().getItem().equals("Outlines"), "Choice event has original source and value");
        click(robot, button);
        Thread.sleep(200);
        check(actionSource.get() == button && command.get().equals("Cancel") && actions.get() == 1, "Native button action fires once on original control");
        EventQueue.invokeAndWait(() -> button.requestFocusInWindow());
        Thread.sleep(150);
        check(KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner() == button, "Focus owner is the original button");
        robot.keyPress(KeyEvent.VK_SPACE); robot.keyRelease(KeyEvent.VK_SPACE);
        robot.keyPress(KeyEvent.VK_ESCAPE); robot.keyRelease(KeyEvent.VK_ESCAPE);
        Thread.sleep(250);
        check(actions.get() == 2 && escape.get() == 1, "Keyboard activation and Escape reach original listeners once");
        NativePaintBridge paint = new NativePaintBridge();
        javax.swing.Timer repaint = new javax.swing.Timer(10, event -> paint.repaint(paint.handle(button)));
        EventQueue.invokeAndWait(repaint::start);
        robot.setAutoDelay(10);
        for (int i=0; i<40; i++) click(robot, button);
        EventQueue.invokeAndWait(repaint::stop);
        robot.waitForIdle(); Thread.sleep(200);
        check(actions.get() == 42 && actionSource.get() == button && command.get().equals("Cancel"),
            "Forty native clicks during repeated painting dispatch once each with original command");
        MenuDisplayAgent.setEnabled(false);
        Thread.sleep(800);
        ImageIO.write(robot.createScreenCapture(frame.getBounds()), "png", Paths.get("build/dialog-native-english.png").toFile());
        check(choice.getSelectedItem().equals("Outlines") && checkbox.getState(), "Language switch preserves parameter values");
        EventQueue.invokeAndWait(() -> frame.dispose());
        System.out.println("PASS: Dialog native display, input events, focus, original model and language switch.");
        System.exit(0);
    }

    private static void click(Robot robot, Component component) {
        Point point = component.getLocationOnScreen();
        robot.mouseMove(point.x + component.getWidth()/2, point.y + component.getHeight()/2);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK); robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();
    }
    private static void check(boolean result, String name) {
        if (!result) { System.err.println("FAIL: " + name); System.exit(1); }
        System.out.println("PASS: " + name);
    }
}
