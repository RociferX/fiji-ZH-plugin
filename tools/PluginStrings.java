import java.io.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
public class PluginStrings {
 public static void main(String[] args)throws Exception{
  try(PrintWriter out=new PrintWriter(args[0],"UTF-8")){
   for(int i=1;i<args.length;i++)try(JarFile jar=new JarFile(args[i])){
    for(JarEntry entry:Collections.list(jar.entries()))if(entry.getName().endsWith(".class")){
     String cls=entry.getName().replace('/','.').replace(".class","");
     new ClassReader(jar.getInputStream(entry)).accept(new ClassVisitor(Opcodes.ASM9){
      void emit(String method,String s){if(!s.isBlank())out.println(cls+"\t"+method+"\t"+s.replace("\r","").replace("\n","\\n").replace("\t","\\t"));}
      public FieldVisitor visitField(int a,String n,String d,String s,Object v){if(v instanceof String)emit(n,(String)v);return null;}
      public MethodVisitor visitMethod(int a,String n,String d,String s,String[]e){return new MethodVisitor(Opcodes.ASM9){public void visitLdcInsn(Object v){if(v instanceof String)emit(n,(String)v);}};}
     },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
    }
   }
  }
 }
}
