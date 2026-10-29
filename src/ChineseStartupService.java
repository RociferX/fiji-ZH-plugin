package org.fiji.zh;

import ij.IJ;
import java.awt.GraphicsEnvironment;
import java.util.concurrent.atomic.AtomicBoolean;
import org.scijava.event.EventHandler;
import org.scijava.plugin.Plugin;
import org.scijava.service.AbstractService;
import org.scijava.service.Service;
import org.scijava.service.SciJavaService;
import org.scijava.ui.event.UIShownEvent;

/** Restores the saved language after Fiji has shown its normal desktop UI. */
@Plugin(type = Service.class)
public final class ChineseStartupService extends AbstractService implements SciJavaService {
    private final AtomicBoolean restored = new AtomicBoolean();

    @EventHandler
    public void onUIShown(UIShownEvent event) {
        if (System.getProperty("fiji.zh.language") != null) return;
        if (!GraphicsEnvironment.isHeadless() && restored.compareAndSet(false, true))
            IJ.runPlugIn("Fiji_Chinese_Display", "restore");
    }
}
