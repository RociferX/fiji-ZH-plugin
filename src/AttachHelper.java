package org.fiji.zh;

import com.sun.tools.attach.VirtualMachine;

/** Short-lived JVM: Fiji does not need self-attach enabled. */
public final class AttachHelper {
    public static void main(String[] args) throws Exception {
        VirtualMachine target = VirtualMachine.attach(args[0]);
        try { target.loadAgent(args[1], args[2]); }
        finally { target.detach(); }
    }
}
