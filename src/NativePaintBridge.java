package org.fiji.zh;

import com.sun.jna.*;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.*;
import com.sun.jna.platform.win32.WinUser.WindowProc;
import java.awt.Component;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Subclasses only supported dialogs' HWNDs, delegating every message unchanged. */
final class NativePaintBridge {
    interface Painter { LRESULT paint(NativeCall original) throws Exception; }
    interface NativeCall { LRESULT call(); }
    private final Map<Long, Hook> hooks = new ConcurrentHashMap<>();
    private volatile Map<Long, Painter> painters = Map.of();

    long handle(Component component) { return Pointer.nativeValue(Native.getComponentPointer(component)); }
    void repaint(long handle) {
        HWND window = new HWND(new Pointer(handle));
        User32.INSTANCE.InvalidateRect(window, null, true);
        User32.INSTANCE.UpdateWindow(window);
    }

    void update(Map<Long, Painter> next, Set<Long> parentHandles) {
        painters = Map.copyOf(next);
        Set<Long> wanted = new HashSet<>(parentHandles);
        wanted.addAll(next.keySet());
        for (long handle : wanted) hooks.computeIfAbsent(handle, Hook::new);
        Iterator<Map.Entry<Long, Hook>> iterator = hooks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Hook> entry = iterator.next();
            if (!wanted.contains(entry.getKey()) && entry.getValue().detach()) iterator.remove();
        }
    }

    private final class Hook {
        final HWND hwnd;
        final WindowProc callback;
        final Pointer callbackPointer, previous;
        Hook(long handle) {
            hwnd = new HWND(new Pointer(handle));
            previous = new Pointer(User32.INSTANCE.GetWindowLongPtr(hwnd, -4).longValue());
            callback = (window, message, wParam, lParam) -> {
                long child = Pointer.nativeValue(window.getPointer());
                if (message == 0x002b && lParam.longValue() != 0) {
                    // DRAWITEMSTRUCT.hwndItem follows five UINTs, aligned to pointer size.
                    Pointer structure = new Pointer(lParam.longValue());
                    Pointer item = structure.getPointer(Native.POINTER_SIZE == 8 ? 24 : 20);
                    child = Pointer.nativeValue(item);
                } else if (message != 0x000f && message != 0x0318) child = 0;
                Painter painter = painters.get(child);
                NativeCall delegate = () -> User32.INSTANCE.CallWindowProc(previousPointer(), window, message, wParam, lParam);
                if (painter == null) return delegate.call();
                try { return painter.paint(delegate); }
                catch (Exception exception) {
                    System.err.println("[Fiji 中文] Native dialog paint: " + exception);
                    return delegate.call();
                }
            };
            callbackPointer = CallbackReference.getFunctionPointer(callback);
            Native.setLastError(0);
            Pointer displaced = User32.INSTANCE.SetWindowLongPtr(hwnd, -4, callbackPointer);
            if (displaced == null && Native.getLastError() != 0)
                throw new IllegalStateException("Cannot attach dialog paint mapping: " + Native.getLastError());
        }
        private Pointer previousPointer() { return previous; }
        boolean detach() {
            if (!User32.INSTANCE.IsWindow(hwnd)) return true;
            long current = User32.INSTANCE.GetWindowLongPtr(hwnd, -4).longValue();
            if (current != Pointer.nativeValue(callbackPointer)) return false;
            User32.INSTANCE.SetWindowLongPtr(hwnd, -4, previous);
            return true;
        }
    }
}
