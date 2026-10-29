package org.fiji.zh;

import java.awt.*;
import java.lang.instrument.ClassFileTransformer;
import java.lang.reflect.Field;
import java.security.ProtectionDomain;
import java.util.*;
import javax.swing.*;
import javax.swing.plaf.basic.BasicLabelUI;
import org.objectweb.asm.*;

/** Paints canvas messages and the Swing status label without changing their text model. */
public final class DisplayTextPainter {
    private static final Map<Component, String> messages = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Set<Window> coreDialogs = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<Window, Boolean>()));
    private static DialogDisplayMapper mapper;
    private static Field lines;
    private static JLabel status;
    private static String statusDisplay;

    static void install(DialogDisplayMapper displayMapper) { mapper = displayMapper; }

    public static void registerDialog(Window dialog) {
        if(coreDialogs.contains(dialog))return;
        if(dialog.getClass().getName().equals("ij.gui.HTMLDialog") && coreDialogs.contains(dialog.getOwner())) {coreDialogs.add(dialog);return;}
        Class<?> owner = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames -> frames
            .map(StackWalker.StackFrame::getDeclaringClass).filter(type -> {
                String name=type.getName();
                return !name.startsWith("java.") && !name.startsWith("jdk.") && !name.startsWith("sun.") &&
                    !name.startsWith("org.fiji.zh.") && !name.startsWith("ij.gui.GenericDialog") &&
                    !name.startsWith("ij.gui.NonBlockingGenericDialog") && !name.startsWith("ij.gui.HTMLDialog") && !name.startsWith("fiji.util.gui.GenericDialogPlus") && !name.equals("ij.gui.GUI");
            }).findFirst().orElse(null));
        if(PluginDisplaySupport.register(dialog,owner))return;
        if(owner == null || !owner.getName().startsWith("ij.") || owner.getName().startsWith("ij.macro.")) return;
        try {
            Class<?> plugin=Class.forName("ij.plugin.PlugIn",false,dialog.getClass().getClassLoader());
            java.net.URL expected=plugin.getProtectionDomain().getCodeSource().getLocation();
            java.net.URL source=owner.getProtectionDomain().getCodeSource().getLocation();
            // ImageJ's legacy patcher defines a few core classes with a null code-source URL.
            if(source==null) {
                java.net.URL resource=owner.getResource("/"+owner.getName().replace('.','/')+".class");
                if(resource!=null && resource.getProtocol().equals("jar"))source=((java.net.JarURLConnection)resource.openConnection()).getJarFileURL();
            }
            if(Objects.equals(source,expected))coreDialogs.add(dialog);
        } catch(ReflectiveOperationException | java.io.IOException exception) { throw new IllegalStateException(exception); }
    }

    static boolean isCoreDialog(Window dialog) { return coreDialogs.contains(dialog); }

    public static void registerHelp(Window help, Window source) {
        if (coreDialogs.contains(source)) coreDialogs.add(help);
    }

    static boolean refreshMessage(Component source, String context, StringBuilder inventory, DialogDisplayMapper dictionary) throws Exception {
        if (lines == null) { lines = source.getClass().getDeclaredField("lines"); lines.setAccessible(true); }
        String original = String.join("\n", (String[]) lines.get(source));
        String translated = dictionary.translate(context, original);
        inventory.append(context).append("\tMessage\t").append(original.replace("\n", "\\n"))
            .append('\t').append(translated.replace("\n", "\\n")).append('\n');
        String previous = messages.put(source, translated);
        if (translated.equals(previous)) return false;
        source.invalidate(); source.repaint(); return true;
    }

    public static boolean paint(Component source, Graphics graphics) {
        String text = messages.get(source);
        if (text == null) return false;
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setFont(MenuDisplayAgent.isEnabled() ? DisplayFonts.nativeFont(source.getFont()) : source.getFont()); g.setColor(source.getForeground());
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            FontMetrics fm = g.getFontMetrics(); String[] rows = text.split("\n", -1);
            int y = fm.getAscent() + Math.max(6, (source.getHeight() - rows.length * fm.getHeight())/2);
            for (String row : rows) { g.drawString(row, 6, y); y += fm.getHeight(); }
        } finally { g.dispose(); }
        return true;
    }

    public static Dimension preferred(Component source) {
        String text = messages.get(source);
        if (text == null || source.getFont() == null) return null;
        FontMetrics fm = source.getFontMetrics(MenuDisplayAgent.isEnabled() ? DisplayFonts.nativeFont(source.getFont()) : source.getFont()); int width = 0;
        String[] rows = text.split("\n", -1);
        for (String row : rows) width = Math.max(width, fm.stringWidth(row));
        return new Dimension(width+12, rows.length*fm.getHeight()+12);
    }

    private static final BasicLabelUI STATUS_UI = new BasicLabelUI() {
        @Override protected String layoutCL(JLabel label, FontMetrics metrics, String text, Icon icon,
            Rectangle view, Rectangle iconBounds, Rectangle textBounds) {
            return super.layoutCL(label, metrics, mapper.translateStatus(text), icon, view, iconBounds, textBounds);
        }
    };

    static void refreshStatus() throws Exception {
        if (status == null) {
            for (Frame frame : Frame.getFrames()) if (frame.getClass().getName().equals("ij.ImageJ")) {
                Field field = frame.getClass().getDeclaredField("statusLine"); field.setAccessible(true);
                status = (JLabel) field.get(frame);
                break;
            }
        }
        if (status != null) {
            String display = mapper.translateStatus(status.getText());
            boolean changed = !Objects.equals(display, statusDisplay);
            statusDisplay = display;
            if (status.getUI() != STATUS_UI) status.setUI(STATUS_UI);
            else if (changed) status.repaint();
        }
    }

    /** Intercepts only presentation methods of ImageJ's custom Canvas; JAR files stay intact. */
    public static final class Transformer implements ClassFileTransformer {
        @Override public byte[] transform(ClassLoader loader, String name, Class<?> type, ProtectionDomain domain, byte[] bytes) {
            boolean htmlDialog="ij/gui/HTMLDialog".equals(name);
            boolean generic="ij/gui/GenericDialog".equals(name) || htmlDialog;
            if (!generic && !"ij/gui/MultiLineLabel".equals(name)) return null;
            ClassReader reader = new ClassReader(bytes);
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
                @Override public MethodVisitor visitMethod(int access, String method, String desc, String signature, String[] exceptions) {
                    MethodVisitor base = super.visitMethod(access, method, desc, signature, exceptions);
                    if(generic) {
                        if(!htmlDialog && method.equals("showHelp"))return new MethodVisitor(Opcodes.ASM9,base) {
                            @Override public void visitMethodInsn(int opcode,String owner,String name,String descriptor,boolean itf) {
                                super.visitMethodInsn(opcode,owner,name,descriptor,itf);
                                if(opcode==Opcodes.INVOKESPECIAL && owner.equals("ij/gui/HTMLDialog") && name.equals("<init>")) {
                                    visitInsn(Opcodes.DUP);visitVarInsn(Opcodes.ALOAD,0);
                                    super.visitMethodInsn(Opcodes.INVOKESTATIC,"org/fiji/zh/DisplayTextPainter","registerHelp","(Ljava/awt/Window;Ljava/awt/Window;)V",false);
                                }
                            }
                        };
                        if(!method.equals("<init>"))return base;
                        return new MethodVisitor(Opcodes.ASM9,base) {
                            @Override public void visitMethodInsn(int opcode,String owner,String name,String descriptor,boolean itf){
                                super.visitMethodInsn(opcode,owner,name,descriptor,itf);
                                if(htmlDialog && opcode==Opcodes.INVOKESPECIAL && owner.equals("javax/swing/JDialog") && name.equals("<init>")){
                                    visitVarInsn(Opcodes.ALOAD,0);
                                    super.visitMethodInsn(Opcodes.INVOKESTATIC,"org/fiji/zh/DisplayTextPainter","registerDialog","(Ljava/awt/Window;)V",false);
                                }
                            }
                            @Override public void visitInsn(int opcode) {
                                if(opcode==Opcodes.RETURN) {
                                    visitVarInsn(Opcodes.ALOAD,0);
                                    visitMethodInsn(Opcodes.INVOKESTATIC,"org/fiji/zh/DisplayTextPainter","registerDialog","(Ljava/awt/Window;)V",false);
                                }
                                super.visitInsn(opcode);
                            }
                        };
                    }
                    boolean paint = method.equals("paint") && desc.equals("(Ljava/awt/Graphics;)V");
                    boolean size = (method.equals("getPreferredSize") || method.equals("getMinimumSize")) && desc.equals("()Ljava/awt/Dimension;");
                    if (!paint && !size) return base;
                    return new MethodVisitor(Opcodes.ASM9, base) {
                        @Override public void visitCode() {
                            super.visitCode(); org.objectweb.asm.Label fallback = new org.objectweb.asm.Label();
                            visitVarInsn(Opcodes.ALOAD, 0);
                            if (paint) {
                                visitVarInsn(Opcodes.ALOAD, 1);
                                visitMethodInsn(Opcodes.INVOKESTATIC, "org/fiji/zh/DisplayTextPainter", "paint", "(Ljava/awt/Component;Ljava/awt/Graphics;)Z", false);
                                visitJumpInsn(Opcodes.IFEQ, fallback); visitInsn(Opcodes.RETURN);
                            } else {
                                visitMethodInsn(Opcodes.INVOKESTATIC, "org/fiji/zh/DisplayTextPainter", "preferred", "(Ljava/awt/Component;)Ljava/awt/Dimension;", false);
                                visitInsn(Opcodes.DUP); visitJumpInsn(Opcodes.IFNULL, fallback); visitInsn(Opcodes.ARETURN);
                            }
                            visitLabel(fallback); if (size) visitInsn(Opcodes.POP);
                        }
                    };
                }
            }, 0);
            return writer.toByteArray();
        }
    }
}
