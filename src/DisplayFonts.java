package org.fiji.zh;

import java.awt.Font;
import java.lang.reflect.*;
import java.nio.charset.Charset;
import java.util.concurrent.ConcurrentHashMap;

/** Private display fonts with a Chinese AWT charset; original fonts remain untouched. */
final class DisplayFonts {
    private static final ConcurrentHashMap<String,Font> fonts = new ConcurrentHashMap<>();
    static Font nativeFont(Font original) {
        int style = original == null ? Font.PLAIN : original.getStyle();
        int size = original == null ? 13 : original.getSize();
        return fonts.computeIfAbsent(style+":"+size, key -> create(style,size));
    }
    private static Font create(int style,int size) {
        try {
            Font font = new Font("Microsoft YaHei UI",style,size);
            Class<?> peerType=Class.forName("sun.awt.windows.WFontPeer");
            Constructor<?> constructor=peerType.getDeclaredConstructor(String.class,int.class);constructor.setAccessible(true);
            Object peer=constructor.newInstance("dialog",style);
            Class<?> descriptor=Class.forName("sun.awt.FontDescriptor");
            Constructor<?> descriptorConstructor=descriptor.getConstructor(String.class,java.nio.charset.CharsetEncoder.class,int[].class);
            descriptorConstructor.setAccessible(true);
            Object chinese=descriptorConstructor.newInstance("Microsoft YaHei UI,GB2312_CHARSET",Charset.forName("GBK").newEncoder(),new int[0]);
            Object components=Array.newInstance(descriptor,1);Array.set(components,0,chinese);
            Class<?> platform=Class.forName("sun.awt.PlatformFont");
            set(platform,peer,"componentFonts",components);set(platform,peer,"defaultFont",chinese);
            set(Font.class,font,"peer",peer);
            return font;
        } catch(ReflectiveOperationException exception) { throw new IllegalStateException("Cannot initialize Chinese display font",exception); }
    }
    private static void set(Class<?> owner,Object target,String name,Object value)throws ReflectiveOperationException {
        Field field=owner.getDeclaredField(name);field.setAccessible(true);field.set(target,value);
    }
}
