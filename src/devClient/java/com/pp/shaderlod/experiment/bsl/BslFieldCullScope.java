package com.pp.shaderlod.experiment.bsl;
import java.util.function.BooleanSupplier;
/** Restrict native culling normalization to the known field quad. Coverage
 * validation happens AFTER disable and BEFORE draw; native state is restored
 * even when qualification, draw or completed-field observation throws. */
public final class BslFieldCullScope {
    private BslFieldCullScope() {}
    public static void run(boolean field,BooleanSupplier cull,Runnable disable,Runnable enable,
                           Runnable qualify,Runnable draw,Runnable returned) {
        if(!field){draw.run();return;}
        boolean prior=cull.getAsBoolean();
        try {disable.run();qualify.run();draw.run();returned.run();}
        finally {if(prior)enable.run();else disable.run();}
    }
}
