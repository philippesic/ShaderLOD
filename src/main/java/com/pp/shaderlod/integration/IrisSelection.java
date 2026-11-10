package com.pp.shaderlod.integration;

import com.pp.shaderlod.domain.Activation;
import com.pp.shaderlod.domain.NativeState;
import com.pp.shaderlod.domain.Selection;
import com.pp.shaderlod.engine.SelectionTransaction;
import com.pp.shaderlod.storage.FileJournal;
import com.pp.shaderlod.storage.NativeWriteScope;
import com.pp.shaderlod.storage.PackArchive;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.loading.FMLPaths;

public final class IrisSelection implements SelectionTransaction.Backend {
  private final Path root = FMLPaths.GAMEDIR.get().toAbsolutePath().normalize();
  private final Path config =
      FMLPaths.CONFIGDIR.get().resolve("iris.properties").toAbsolutePath().normalize();
  private final Path journalPath = root.resolve("shaderlod-recovery.json");
  private FileJournal journal;
  private boolean restoring;
  private boolean originalFallback;

  public void recover() throws IOException {
    if (Files.exists(journalPath)) {
      var recovery = FileJournal.recover(root, journalPath);
      recovery.restoreFiles();
      Iris.getIrisConfig().load();
      Iris.getIrisConfig().setShaderPackName(recovery.metadata().get("pack"));
      Iris.getIrisConfig()
          .setShadersEnabled(Boolean.parseBoolean(recovery.metadata().get("enabled")));
      Iris.clearShaderPackOptionQueue();
      Iris.reload();
      recovery.restoreFiles();
      recovery.finish();
    }
  }

  @Override
  public NativeState snapshot() {
    if (journal == null) {
      originalFallback = Iris.isFallback();
    }
    return new NativeState(
        Iris.getIrisConfig().getShaderPackName(),
        Iris.getIrisConfig().areShadersEnabled(),
        Map.copyOf(Iris.getShaderPackOptionQueue()),
        effectiveOptions());
  }

  public static Map<String, String> effectiveOptions() {
    var result = new HashMap<String, String>();
    Iris.getCurrentPack()
        .ifPresent(pack -> {
          var options = pack.getShaderPackOptions();
          var values = options.getOptionValues();
          options
              .getOptionSet()
              .getBooleanOptions()
              .keySet()
              .forEach(
                  key -> result.put(key, Boolean.toString(values.getBooleanValueOrDefault(key))));
          options
              .getOptionSet()
              .getStringOptions()
              .keySet()
              .forEach(key -> result.put(key, values.getStringValueOrDefault(key)));
        });
    return Map.copyOf(result);
  }

  @Override
  public void select(Selection selection) throws IOException {
    if (!Minecraft.getInstance().isSameThread() || Minecraft.getInstance().level == null) {
      throw new IOException("Open a world before applying a shader selection");
    }
    if (!Iris.getShaderPackOptionQueue().isEmpty()) {
      throw new IOException("Apply or cancel pending native shader options first");
    }
    if (!selection.enabled()) {
      throw new IOException("Use Off to restore the native preimage");
    }
    Path archive = archive(selection.packName());
    PackArchive.read(archive, PackArchive.Limits.defaults());
    Path optionsPath = archive.resolveSibling(archive.getFileName() + ".txt");
    var tracked = new java.util.LinkedHashSet<Path>(List.of(config, optionsPath));
    var previous = Iris.getIrisConfig().getShaderPackName();
    if (previous.isPresent()) {
      var previousArchive = archive(previous.orElseThrow());
      tracked.add(previousArchive.resolveSibling(previousArchive.getFileName() + ".txt"));
    }
    journal =
        FileJournal.begin(
            root,
            journalPath,
            List.copyOf(tracked),
            Map.of(
                "pack",
                Iris.getIrisConfig().getShaderPackName().orElse(""),
                "enabled",
                Boolean.toString(Iris.getIrisConfig().areShadersEnabled()),
                "fallback",
                Boolean.toString(originalFallback)));
    var properties = new Properties();
    if (Files.exists(config)) {
      properties.load(new ByteArrayInputStream(Files.readAllBytes(config)));
    }
    properties.setProperty("shaderPack", selection.packName());
    properties.setProperty("enableShaders", "true");
    journal.write(config, encode(properties));
    if (!selection.options().isEmpty()) {
      var options = new Properties();
      if (Files.exists(optionsPath)) {
        options.load(new ByteArrayInputStream(Files.readAllBytes(optionsPath)));
      }
      selection.options().forEach(options::setProperty);
      journal.write(optionsPath, encode(options));
    }
    try (var scope = new NativeWriteScope(journal)) {
      Iris.getIrisConfig().load();
      scope.verify();
    }
  }

  private static byte[] encode(Properties properties) throws IOException {
    var output = new ByteArrayOutputStream();
    properties.store(output, null);
    return output.toByteArray();
  }

  public static Path archive(String name) throws IOException {
    Path directory = Iris.getShaderpacksDirectory().toRealPath();
    Path path = directory.resolve(name).normalize();
    if (!path.getParent().equals(directory) || Files.isSymbolicLink(path)) {
      throw new IOException("Shader source must be one regular archive inside shaderpacks");
    }
    return path;
  }

  @Override
  public void reload() throws IOException {
    if (journal == null) {
      Iris.reload();
    } else {
      try (var scope = new NativeWriteScope(journal)) {
        Iris.reload();
        scope.verify();
      }
    }
    if (restoring && journal != null) {
      journal.restoreFiles();
    }
    if ((!restoring || !originalFallback)
        && (Iris.isFallback() || Iris.getStoredError().isPresent())) {
      throw new IOException("Iris rejected the shader graph: " + Iris.getStoredError());
    }
  }

  @Override
  public Activation observe() throws IOException {
    String name = Iris.getCurrentPackName();
    var identity = PackArchive.read(archive(name), PackArchive.Limits.defaults()).identity();
    boolean linked =
        Minecraft.getInstance().level != null
            && !Iris.isFallback()
            && Iris.getStoredError().isEmpty()
            && IrisApi.getInstance().isShaderPackInUse()
            && Iris.getPipelineManager().getPipeline().isPresent();
    return new Activation(
        name,
        identity.archiveSha256(),
        Iris.getPipelineManager().getVersionCounterForSodiumShaderReload(),
        linked,
        effectiveOptions(),
        linked
            ? (hasSubmittedFrame()
                ? Set.of(
                    "linked-archive:" + identity.archiveSha256(),
                    "submitted-frame:" + identity.archiveSha256())
                : Set.of("linked-archive:" + identity.archiveSha256()))
            : Set.of());
  }

  public boolean hasSubmittedFrame() {
    return FrameWitness.observedFor(Iris.getPipelineManager().getPipelineNullable());
  }

  @Override
  public void restore(NativeState original) throws IOException {
    restoring = true;
    if (journal != null) {
      journal.restoreFiles();
    }
    if (journal != null) {
      try (var scope = new NativeWriteScope(journal)) {
        Iris.getIrisConfig().load();
        scope.verify();
      }
    }
    Iris.getIrisConfig().setShaderPackName(original.packName().orElse(null));
    Iris.getIrisConfig().setShadersEnabled(original.shadersEnabled());
    Iris.clearShaderPackOptionQueue();
    Iris.getShaderPackOptionQueue().putAll(original.queuedOptions());
  }

  @Override
  public void completeRestoration() throws IOException {
    if (journal != null) {
      journal.finish();
      journal = null;
    }
    restoring = false;
  }
}
