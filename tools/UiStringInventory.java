import java.io.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
public class UiStringInventory {
 public static void main(String[]args)throws Exception{
  try(JarFile jar=new JarFile(args[0]);PrintWriter out=new PrintWriter(args[1],"UTF-8")){
   out.println("class\tmethod\tcall\tstrings");
   for(JarEntry e:Collections.list(jar.entries())){
    if(!e.getName().endsWith(".class"))continue;
    String cls=e.getName().replace('/','.').replace(".class","");
    new ClassReader(jar.getInputStream(e)).accept(new ClassVisitor(Opcodes.ASM9){
     public MethodVisitor visitMethod(int acc,String method,String desc,String sig,String[]ex){return new MethodVisitor(Opcodes.ASM9){
      java.util.List<String> recent=new ArrayList<>();
      public void visitLdcInsn(Object v){if(v instanceof String){if(((String)v).startsWith("Color Thresholder\n"))out.println(cls+"\t"+method+"\thelp\t"+((String)v).replace("\n","\\n"));recent.add((String)v);if(recent.size()>6)recent.remove(0);}}
      public void visitMethodInsn(int op,String owner,String name,String desc,boolean itf){
       boolean ui=(owner.equals("ij/gui/GenericDialog")||owner.equals("ij/gui/NonBlockingGenericDialog"))&&(name.equals("<init>")||name.startsWith("add")||name.startsWith("set")&&name.endsWith("Label")) ||
        Set.of("java/awt/Button","java/awt/Label","java/awt/Checkbox","java/awt/Choice").contains(owner)&&(name.equals("<init>")||name.equals("add"));
       if(ui&&!recent.isEmpty()){out.println(cls+"\t"+method+"\t"+name+"\t"+String.join(" | ",recent).replace("\n","\\n").replace("\t","\\t"));recent.clear();}
      }
     };}
    },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
   }
  }
 }
}
