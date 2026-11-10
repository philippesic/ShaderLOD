package com.pp.shaderlod.dev;

import com.google.gson.Gson;
import com.pp.shaderlod.integration.DhObservation;
import com.pp.shaderlod.storage.AtomicFiles;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.UUID;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

final class ProfileExport {
  private ProfileExport() {}

  static void register(RegisterClientCommandsEvent event) {
    event
        .getDispatcher()
        .register(
            Commands.literal("shaderlod-profile")
                .executes(context -> {
                  var path =
                      FMLPaths.GAMEDIR
                          .get()
                          .resolve("shaderlod-profile-" + UUID.randomUUID() + ".json");
                  var record = new LinkedHashMap<String, Object>();
                  record.put("schema", "shaderlod-profile-v1");
                  record.put("instrumentation", "diagnostic");
                  record.put(
                      "scope",
                      "bounded client-frame CPU/GPU streams; no per-pass coverage or performance qualification");
                  record.put("pack", net.irisshaders.iris.Iris.getCurrentPackName());
                  record.put("fallback", net.irisshaders.iris.Iris.isFallback());
                  record.put(
                      "dimension", String.valueOf(net.irisshaders.iris.Iris.getCurrentDimension()));
                  var target = net.minecraft.client.Minecraft.getInstance().getMainRenderTarget();
                  record.put("surface", java.util.List.of(target.width, target.height));
                  record.put("dh", DhObservation.current());
                  record.put("cpu", DevelopmentProfiler.snapshot());
                  record.put("gpu", DevelopmentProfiler.gpuSnapshot());
                  try {
                    AtomicFiles.publish(
                        path, new Gson().toJson(record).getBytes(StandardCharsets.UTF_8));
                    context
                        .getSource()
                        .sendSuccess(
                            () -> Component.literal("Profile saved: " + path.getFileName()), false);
                    return 1;
                  } catch (IOException failure) {
                    context
                        .getSource()
                        .sendFailure(
                            Component.literal(
                                "Profile publication failed: " + failure.getMessage()));
                    return 0;
                  }
                }));
  }
}
