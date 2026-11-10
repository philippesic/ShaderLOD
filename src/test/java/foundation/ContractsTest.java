package foundation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import foundation.contracts.Evidence;
import foundation.contracts.Health;
import foundation.contracts.Measurement;
import org.junit.jupiter.api.Test;

class ContractsTest {
  @Test
  void measuredZero() {
    assertEquals(1, new Measurement.Measured(0, 1).count());
    assertThrows(IllegalArgumentException.class, () -> new Measurement.Measured(0, 0));
  }

  @Test
  void missingReason() {
    assertEquals("unscheduled", new Measurement.Missing("unscheduled").reason());
    assertThrows(IllegalArgumentException.class, () -> new Measurement.Missing(""));
  }

  @Test
  void closedEnums() {
    assertEquals(2, Health.values().length);
    assertEquals(5, Evidence.values().length);
    assertThrows(IllegalArgumentException.class, () -> Health.valueOf("UNKNOWN"));
  }
}
