package com.pp.shaderlod.dev;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
/** No effect without the explicit dev probe property; never in the production JAR. */
@Mod(value = "shaderlod_dev", dist = Dist.CLIENT)
public final class PortProbe {
 public PortProbe() { if (NeoForgeRoundtrip.requested()) NeoForgeRoundtrip.register(); }
}
