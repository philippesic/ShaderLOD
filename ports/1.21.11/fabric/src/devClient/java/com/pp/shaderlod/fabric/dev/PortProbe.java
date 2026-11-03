package com.pp.shaderlod.fabric.dev;
import net.fabricmc.api.ClientModInitializer;
/** No effect without the explicit dev probe property; never in the production JAR. */
public final class PortProbe implements ClientModInitializer {
 @Override public void onInitializeClient() { if (FabricRoundtrip.requested()) FabricRoundtrip.register(); }
}
