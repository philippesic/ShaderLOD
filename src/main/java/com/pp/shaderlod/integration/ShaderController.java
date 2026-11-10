package com.pp.shaderlod.integration;

import com.pp.shaderlod.domain.Activation;
import com.pp.shaderlod.domain.Selection;
import com.pp.shaderlod.engine.OptimizationRegistry;
import com.pp.shaderlod.engine.SelectionTransaction;
import com.pp.shaderlod.storage.PackArchive;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ShaderController {
  private final OptimizationRegistry registry =
      OptimizationRegistry.load(getClass().getClassLoader());
  private final com.pp.shaderlod.engine.PackCatalog.Primary primary =
      com.pp.shaderlod.engine.PackCatalog.primary(getClass().getClassLoader());
  private final IrisSelection backend = new IrisSelection();
  private SelectionTransaction transaction;
  private String dimension;
  private String sourceName;
  private boolean recoveryAttempted;
  private String recoveryFailure;

  public void tick() {
    if (!recoveryAttempted && net.irisshaders.iris.Iris.getIrisConfig() != null) {
      recoveryAttempted = true;
      try {
        backend.recover();
      } catch (IOException | IllegalArgumentException | IllegalStateException failure) {
        recoveryFailure = failure.getMessage();
        net.irisshaders.iris.Iris.logger.error("ShaderLOD recovery requires attention", failure);
      }
    }
    invalidateIfChanged();
    if (transaction != null
        && transaction.state() == SelectionTransaction.State.LINKED
        && backend.hasSubmittedFrame()) {
      try {
        transaction.confirmRendered();
      } catch (IOException | IllegalArgumentException | IllegalStateException failure) {
        transaction.invalidate();
      }
    }
  }

  public List<String> providers() {
    return registry.ids();
  }

  public Activation apply(String sourceName, List<String> ids) throws IOException {
    return apply(sourceName, ids, Map.of());
  }

  public Activation apply(String sourceName, List<String> ids, Map<String, String> overrides)
      throws IOException {
    if (recoveryFailure != null) {
      throw new IOException("Recovery unresolved: " + recoveryFailure);
    }
    off();
    backend.recover();
    this.sourceName = sourceName;
    var source = IrisSelection.archive(sourceName);
    var archive = PackArchive.read(source, PackArchive.Limits.defaults());
    if (!archive.identity().archiveSha256().equals(primary.sha256())) {
      throw new IOException("Unsupported archive: required pinned pack is " + primary.id());
    }
    String selected = sourceName;
    String expected = archive.identity().archiveSha256();
    var patches = registry.prepare(archive.identity(), ids, Map.of());
    if (!ids.isEmpty()) {
      var derived = archive.cached(source.getParent(), patches);
      selected = derived.path().getFileName().toString();
      expected = derived.identity().archiveSha256();
    }

    var options = new java.util.TreeMap<>(inheritedOptions(sourceName));
    options.putAll(overrides);
    transaction =
        SelectionTransaction.prepare(
            backend,
            new Selection(selected, true, options, ids),
            expected,
            Set.of("linked-archive:" + expected),
            net.irisshaders.iris.Iris.getPipelineManager()
                .getVersionCounterForSodiumShaderReload());
    var active = transaction.apply();
    dimension = String.valueOf(net.irisshaders.iris.Iris.getCurrentDimension());
    return active;
  }

  private static Map<String, String> inheritedOptions(String sourceName) throws IOException {
    var properties = new java.util.Properties();
    var source = IrisSelection.archive(sourceName);
    var options = source.resolveSibling(sourceName + ".txt");
    if (Files.isSymbolicLink(options)) {
      throw new IOException("Unsafe source options symlink");
    }
    if (Files.exists(options)) {
      try (var input = Files.newInputStream(options)) {
        properties.load(input);
      }
    }
    var result = new java.util.TreeMap<String, String>();
    properties.stringPropertyNames().forEach(key -> result.put(key, properties.getProperty(key)));
    return Map.copyOf(result);
  }

  public void off() throws IOException {
    if (transaction != null) {
      transaction.off();
      transaction = null;
    }
  }

  public void invalidateIfChanged() {
    if (transaction != null
        && (transaction.state() == SelectionTransaction.State.ACTIVE
            || transaction.state() == SelectionTransaction.State.LINKED)) {
      var active = transaction.linkedObservation();
      if (!active.packName().equals(net.irisshaders.iris.Iris.getCurrentPackName())
          || active.generation()
              != net.irisshaders.iris.Iris.getPipelineManager()
                  .getVersionCounterForSodiumShaderReload()
          || !java.util.Objects.equals(
              dimension, String.valueOf(net.irisshaders.iris.Iris.getCurrentDimension()))
          || net.minecraft.client.Minecraft.getInstance().level == null) {
        transaction.invalidate();
      }
    }
  }

  public String sourceName() {
    return sourceName == null
        ? net.irisshaders.iris.Iris.getIrisConfig().getShaderPackName().orElse(primary.filename())
        : sourceName;
  }

  public Activation activation() {
    if (transaction == null) {
      throw new IllegalStateException("No selection");
    }
    return transaction.activation();
  }

  public String status() {
    if (transaction == null) {
      return "Native settings; no ShaderLOD transaction";
    }
    return transaction.state().toString();
  }
}
