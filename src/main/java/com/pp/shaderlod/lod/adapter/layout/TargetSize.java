package com.pp.shaderlod.lod.adapter.layout;

import java.util.Objects;

/** Explicit allocation formula relative to the rendered surface, never display size. */
public sealed interface TargetSize permits TargetSize.Absolute, TargetSize.Relative {
    Dimensions resolve(Dimensions renderedSurface);

    enum Rounding { FLOOR, CEIL }

    record Ratio(int numerator, int denominator, Rounding rounding) {
        public Ratio {
            if (numerator <= 0 || denominator <= 0) {
                throw new IllegalArgumentException("Ratio terms must be positive");
            }
            Objects.requireNonNull(rounding, "rounding");
        }

        int resolve(int input) {
            // Both factors are positive int values; their product and ceil adjustment fit long.
            long product = (long) input * numerator;
            long result = rounding == Rounding.CEIL
                    ? (product + denominator - 1L) / denominator : product / denominator;
            if (result <= 0 || result > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Resolved dimension outside positive int range: " + result);
            }
            return (int) result;
        }
    }

    record Absolute(Dimensions dimensions) implements TargetSize {
        public Absolute { Objects.requireNonNull(dimensions, "dimensions"); }
        @Override public Dimensions resolve(Dimensions renderedSurface) {
            Objects.requireNonNull(renderedSurface, "renderedSurface");
            return dimensions;
        }
    }

    record Relative(Ratio width, Ratio height) implements TargetSize {
        public Relative {
            Objects.requireNonNull(width, "width");
            Objects.requireNonNull(height, "height");
        }
        @Override public Dimensions resolve(Dimensions renderedSurface) {
            Objects.requireNonNull(renderedSurface, "renderedSurface");
            return new Dimensions(width.resolve(renderedSurface.width()), height.resolve(renderedSurface.height()));
        }
    }

    static TargetSize absolute(int width, int height) {
        return new Absolute(new Dimensions(width, height));
    }

    static TargetSize relative(int numerator, int denominator, Rounding rounding) {
        var ratio = new Ratio(numerator, denominator, rounding);
        return new Relative(ratio, ratio);
    }
}
