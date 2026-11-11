package com.pp.shaderlod.dev;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.pp.shaderlod.dev.measurement.GpuCollector;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GpuCollectorTest {
  private static final class Queries implements GpuCollector.Queries {
    int next;
    final Map<Integer, Long> values = new HashMap<>();
    final Set<Integer> ready = new HashSet<>();
    final Set<Integer> deleted = new HashSet<>();

    @Override
    public int create() {
      return ++next;
    }

    @Override
    public void timestamp(int query) {}

    @Override
    public boolean available(int query) {
      return ready.contains(query);
    }

    @Override
    public long value(int query) {
      if (!ready.contains(query)) {
        throw new AssertionError("Blocking result read");
      }
      return values.get(query);
    }

    @Override
    public void delete(int query) {
      deleted.add(query);
    }
  }

  @Test
  void delayedQueriesKeepOwnershipAndNeverReadUnavailableValues() {
    var queries = new Queries();
    var collector = new GpuCollector(queries, 1);
    collector.begin(7, 10);
    collector.end(7, 10);
    collector.begin(8, 11);
    assertEquals(1, collector.snapshot().lost());
    queries.values.put(1, 100L);
    queries.values.put(2, 145L);
    queries.ready.addAll(Set.of(1, 2));
    collector.poll();
    assertEquals(new GpuCollector.Sample(7, 10, 45), collector.snapshot().samples().getFirst());
    assertEquals(Set.of(1, 2), queries.deleted);
    assertEquals(0, collector.snapshot().pending());
  }

  @Test
  void drainingExportsEachCompletedSampleOnce() {
    var queries = new Queries();
    var collector = new GpuCollector(queries, 2);
    collector.begin(1, 5);
    collector.end(1, 5);
    queries.values.put(1, 100L);
    queries.values.put(2, 150L);
    queries.ready.addAll(Set.of(1, 2));
    assertEquals(1, collector.drain().samples().size());
    assertEquals(0, collector.drain().samples().size());
    assertEquals(0, collector.snapshot().lost());
  }

  @Test
  void reloadBoundaryInvalidatesOnlyItsOwnOpenScope() {
    var queries = new Queries();
    var collector = new GpuCollector(queries, 2);
    collector.begin(7, 10);
    collector.end(8, 10);
    assertEquals(1, collector.snapshot().invalid());
    assertEquals(Set.of(1, 2), queries.deleted);
    assertEquals(0, collector.snapshot().samples().size());
    collector.close();
  }
}
