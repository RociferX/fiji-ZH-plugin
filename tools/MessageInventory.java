import java.io.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
public class MessageInventory {
 static String clean(String s){return s.replace("\r","").replace("\n","\\n").replace("\t","\\t");}
 public static void main(String[]args)throws Exception{
  try(JarFile jar=new JarFile(args[0]);PrintWriter out=new PrintWriter(args[1],"UTF-8")){
   out.println("kind\tclass\tmethod\ttext");
   for(JarEntry e:Collections.list(jar.entries()))if(e.getName().endsWith(".class")){
    String cls=e.getName().replace('/','.').replace(".class","");
    new ClassReader(jar.getInputStream(e)).accept(new ClassVisitor(Opcodes.ASM9){
     public FieldVisitor visitField(int a,String n,String d,String s,Object v){if(v instanceof String&&((String)v).length()>100)out.println("help\t"+cls+"\t"+n+"\t"+clean((String)v));return null;}
     public MethodVisitor visitMethod(int a,String method,String d,String s,String[]ex){return new MethodVisitor(Opcodes.ASM9){
      LinkedHashSet<String> strings=new LinkedHashSet<>();Set<String> calls=new LinkedHashSet<>();ArrayDeque<String> recent=new ArrayDeque<>();
      public void visitLdcInsn(Object v){if(v instanceof String){strings.add((String)v);recent.add((String)v);if(recent.size()>4)recent.removeFirst();}}
      public void visitMethodInsn(int op,String owner,String name,String desc,boolean itf){
       if(owner.equals("ij/IJ")&&name.equals("showStatus"))out.println("status-site\t"+cls+"\t"+method+"\t"+clean(String.join(" | ",recent)));
       if(owner.equals("ij/IJ")&&Set.of("showStatus","showMessage","showMessageWithCancel","error","noImage","outOfMemory").contains(name))calls.add(name);
       if(Set.of("ij/gui/HTMLDialog","ij/gui/MessageDialog","ij/gui/YesNoCancelDialog").contains(owner)&&name.equals("<init>"))calls.add("help");
      }
      public void visitEnd(){for(String kind:calls)for(String text:strings)if(!text.isBlank())out.println(kind+"\t"+cls+"\t"+method+"\t"+clean(text));}
     };}
    },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
   }
  }
 }
}
