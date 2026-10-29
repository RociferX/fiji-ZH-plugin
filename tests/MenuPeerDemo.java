package org.fiji.zh;

import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;

/** UI smoke test: the visible menu is Chinese but all public model values stay English. */
public final class MenuPeerDemo {
    public static void main(String[] args) {
        EventQueue.invokeLater(() -> {
            Frame frame = new Frame("Fiji Chinese menu mapping test");
            MenuBar bar = new MenuBar();
            Menu file = new Menu("File");
            MenuItem open = new MenuItem("Open...", new MenuShortcut(KeyEvent.VK_O));
            open.addActionListener(event -> {
                String result = "label=" + open.getLabel() + "\naction=" + event.getActionCommand();
                System.out.println(result);
                try { Files.writeString(Paths.get("build/demo-action.txt"), result); }
                catch (Exception exception) { throw new RuntimeException(exception); }
            });
            file.add(open);
            Menu process = new Menu("Process");
            process.add(new MenuItem("Gaussian Blur..."));
            process.add(new CheckboxMenuItem("Preview", true));
            bar.add(file);
            bar.add(process);
            frame.setMenuBar(bar);
            frame.add(new Label("Native menu labels are translated; Java labels and commands stay English."));
            frame.setSize(650, 160);
            frame.addWindowListener(new WindowAdapter() {
                public void windowClosing(WindowEvent event) { frame.dispose(); System.exit(0); }
            });
            frame.setLocation(200, 200);
            frame.setVisible(true);
            new javax.swing.Timer(1500, event -> {
                if (!file.getLabel().equals("File") || !open.getLabel().equals("Open...") ||
                    !open.getActionCommand().equals("Open..."))
                    throw new AssertionError("Menu model changed");
            }).start();
        });
    }
}
