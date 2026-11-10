package foundation.contracts;

public sealed interface Measurement {
  record Measured(double value, int count) implements Measurement {
    public Measured {
      if (!Double.isFinite(value) || count < 1) {
        throw new IllegalArgumentException("D5: invalid measured sample");
      }
    }
  }

  record Missing(String reason) implements Measurement {
    public Missing {
      if (reason == null || reason.isBlank()) {
        throw new IllegalArgumentException("D5: missing reason required");
      }
    }
  }
}
