package com.pp.shaderlod.fabric.dev;
import net.fabricmc.api.ClientModInitializer;
/** No effect without explicit dev roundtrip/original-source mode; never in production JAR. */
public final class PortProbe implements ClientModInitializer {
 @Override public void onInitializeClient() {
  if (FabricRoundtrip.requested()) FabricRoundtrip.register();
 }
}
