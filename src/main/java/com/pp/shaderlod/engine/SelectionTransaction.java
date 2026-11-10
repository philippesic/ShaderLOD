package com.pp.shaderlod.engine;

import com.pp.shaderlod.domain.Activation;
import com.pp.shaderlod.domain.NativeState;
import com.pp.shaderlod.domain.PackIdentity;
import com.pp.shaderlod.domain.Selection;
import java.io.IOException;
import java.util.Set;

public final class SelectionTransaction {
  public enum State {
    PREPARED,
    APPLYING,
    ACTIVE,
    RESTORING,
    RESTORED,
    FAILED
  }

  public interface Backend {
    NativeState snapshot() throws IOException;

    void select(Selection selection) throws IOException;

    void reload() throws IOException;

    Activation observe() throws IOException;

    void restore(NativeState original) throws IOException;

    default void completeRestoration() throws IOException {}
  }

  private final Backend backend;
  private final NativeState original;
  private final Selection requested;
  private final String expectedSha256;
  private final Set<String> requiredWitnesses;
  private final long priorGeneration;
  private State state = State.PREPARED;
  private Activation activation;

  private SelectionTransaction(
      Backend backend,
      Selection requested,
      String expectedSha256,
      Set<String> requiredWitnesses,
      long priorGeneration)
      throws IOException {
    PackIdentity.requireDigest(expectedSha256);
    if (priorGeneration < -1) {
      throw new IllegalArgumentException("Invalid previous graph generation");
    }
    this.backend = java.util.Objects.requireNonNull(backend);
    this.requested = java.util.Objects.requireNonNull(requested);
    if (!requested.enabled()) {
      throw new IllegalArgumentException(
          "Use Off to restore native state; disabled activation is unavailable");
    }
    this.expectedSha256 = expectedSha256;
    this.requiredWitnesses = Set.copyOf(requiredWitnesses);
    this.priorGeneration = priorGeneration;
    original = backend.snapshot();
  }

  public static SelectionTransaction prepare(
      Backend backend, Selection requested, String expectedSha256, Set<String> requiredWitnesses)
      throws IOException {
    return prepare(backend, requested, expectedSha256, requiredWitnesses, -1);
  }

  public static SelectionTransaction prepare(
      Backend backend,
      Selection requested,
      String expectedSha256,
      Set<String> requiredWitnesses,
      long priorGeneration)
      throws IOException {
    return new SelectionTransaction(
        backend, requested, expectedSha256, requiredWitnesses, priorGeneration);
  }

  public State state() {
    return state;
  }

  public Activation activation() {
    if (state != State.ACTIVE || activation == null) {
      throw new IllegalStateException("No currently observed activation");
    }
    return activation;
  }

  public Activation apply() throws IOException {
    if (state != State.PREPARED) {
      throw new IllegalStateException("Selection can only apply once");
    }
    state = State.APPLYING;
    try {
      backend.select(requested);
      backend.reload();
      var observed = backend.observe();
      if (!observed.linked()
          || !requested.packName().equals(observed.packName())
          || !expectedSha256.equals(observed.archiveSha256())
          || observed.generation() <= priorGeneration
          || !observed.witnesses().containsAll(requiredWitnesses)
          || !observed.effectiveOptions().entrySet().containsAll(requested.options().entrySet())) {
        throw new IOException("Requested selection did not produce matching live activation");
      }
      activation = observed;
      state = State.ACTIVE;
      return observed;
    } catch (IOException | IllegalArgumentException | IllegalStateException failure) {
      try {
        restore();
      } catch (IOException | IllegalArgumentException | IllegalStateException restoration) {
        failure.addSuppressed(restoration);
        state = State.FAILED;
      }
      throw failure;
    }
  }

  public void cancel() throws IOException {
    if (state == State.PREPARED) {
      state = State.RESTORED;
      return;
    }
    restore();
  }

  public void off() throws IOException {
    cancel();
  }

  public void invalidate() {
    activation = null;
    if (state == State.ACTIVE) {
      state = State.FAILED;
    }
  }

  private void restore() throws IOException {
    if (state == State.RESTORED) {
      return;
    }
    state = State.RESTORING;
    activation = null;
    backend.restore(original);
    backend.reload();
    if (!original.equals(backend.snapshot())) {
      state = State.FAILED;
      throw new IOException("Native state restoration did not match preimage");
    }
    backend.completeRestoration();
    state = State.RESTORED;
  }
}
