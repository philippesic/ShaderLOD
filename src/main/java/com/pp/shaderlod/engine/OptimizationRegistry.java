package com.pp.shaderlod.engine;

import com.pp.shaderlod.domain.PackIdentity;
import com.pp.shaderlod.domain.ResourcePatch;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.TreeMap;

public final class OptimizationRegistry {
  private final Map<String, OptimizationProvider> providers;

  private OptimizationRegistry(Collection<OptimizationProvider> candidates) {
    var found = new TreeMap<String, OptimizationProvider>();
    for (var provider : candidates) {
      String id = provider.id();
      if (id == null
          || !id.matches("[a-z][a-z0-9.-]*")
          || found.putIfAbsent(id, provider) != null) {
        throw new IllegalArgumentException("Invalid or duplicate optimization ID: " + id);
      }
    }
    providers = Map.copyOf(found);
  }

  public static OptimizationRegistry load(ClassLoader loader) {
    return of(
        ServiceLoader.load(OptimizationProvider.class, loader).stream()
            .map(ServiceLoader.Provider::get)
            .toList());
  }

  public static OptimizationRegistry of(Collection<OptimizationProvider> providers) {
    return new OptimizationRegistry(providers);
  }

  public List<String> ids() {
    return providers.keySet().stream().sorted().toList();
  }

  public List<ResourcePatch> prepare(
      PackIdentity identity, List<String> selected, Map<String, Map<String, String>> parameters) {
    if (!selected.containsAll(parameters.keySet())
        || selected.stream().distinct().count() != selected.size()) {
      throw new IllegalArgumentException("Unexpected parameters or duplicate selection");
    }
    var patches = new ArrayList<ResourcePatch>();
    var resources = new HashSet<String>();
    for (String id : selected) {
      var provider = providers.get(id);
      if (provider == null || !provider.supports(identity)) {
        throw new IllegalArgumentException("Unsupported optimization/input: " + id);
      }
      if (provider.conflicts().stream().anyMatch(selected::contains)) {
        throw new IllegalArgumentException("Conflicting optimization: " + id);
      }
      for (var patch : provider.patches(identity, parameters.getOrDefault(id, Map.of()))) {
        if (!resources.add(patch.member())
            || !patch.expectedSha256().equals(identity.memberSha256().get(patch.member()))) {
          throw new IllegalArgumentException("Overlapping or stale patch: " + patch.member());
        }
        patches.add(patch);
      }
    }
    return List.copyOf(patches);
  }
}
