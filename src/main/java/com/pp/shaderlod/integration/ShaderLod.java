package com.pp.shaderlod.integration;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = "shaderlod", dist = Dist.CLIENT)
public final class ShaderLod {
  public ShaderLod(ModContainer container) {
    var controller = new ShaderController();
    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
        (net.neoforged.neoforge.client.event.ClientTickEvent.Post event) -> controller.tick());
    container.registerExtensionPoint(
        IConfigScreenFactory.class, (mod, parent) -> new SettingsScreen(parent, controller));
  }
}
