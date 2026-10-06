package com.pp.shaderlod.lod.adapter.layout;

import java.util.Collections;
import java.util.List;

/** Explicit reviewed storage formats; no format inference or driver-support promise. */
public enum TargetFormat {
    R32F(1, 32, NumericClass.FLOAT), RGBA16F(4, 16, NumericClass.FLOAT),
    RGBA32F(4, 32, NumericClass.FLOAT), RGBA8(4, 8, NumericClass.NORMALIZED),
    R11F_G11F_B10F(List.of(11, 11, 10), NumericClass.FLOAT, false, 5),
    R32I(1, 32, NumericClass.SIGNED_INTEGER), RGBA16I(4, 16, NumericClass.SIGNED_INTEGER),
    RGBA32I(4, 32, NumericClass.SIGNED_INTEGER),
    R32UI(1, 32, NumericClass.UNSIGNED_INTEGER), RGBA16UI(4, 16, NumericClass.UNSIGNED_INTEGER),
    RGBA32UI(4, 32, NumericClass.UNSIGNED_INTEGER);

    public enum NumericClass { FLOAT, NORMALIZED, SIGNED_INTEGER, UNSIGNED_INTEGER }
    private final List<Integer> componentBits;
    private final NumericClass numericClass;
    private final boolean floatingSignBit;
    private final int floatingExponentBits;

    TargetFormat(int channels, int bits, NumericClass numericClass) {
        this(Collections.nCopies(channels, bits), numericClass, numericClass == NumericClass.FLOAT,
                numericClass == NumericClass.FLOAT ? (bits == 32 ? 8 : 5) : 0);
    }

    TargetFormat(List<Integer> componentBits, NumericClass numericClass,
                 boolean floatingSignBit, int floatingExponentBits) {
        this.componentBits = List.copyOf(componentBits);
        this.numericClass = numericClass;
        this.floatingSignBit = floatingSignBit;
        this.floatingExponentBits = floatingExponentBits;
    }

    public int channels() { return componentBits.size(); }
    /** Minimum stored component width; unchanged for uniform-width formats. */
    public int componentBits() { return componentBits.stream().mapToInt(Integer::intValue).min().orElseThrow(); }
    /** Stored width of channel 0=R, 1=G, 2=B, 3=A, without inventing absent channels. */
    public int componentBits(int channel) { return componentBits.get(channel); }
    public NumericClass numericClass() { return numericClass; }
    public boolean hasFloatingSignBit() { return floatingSignBit; }
    /** Zero for non-floating formats. */
    public int floatingExponentBits() { return floatingExponentBits; }
    /** Stored fraction bits, excluding the implicit leading bit and exponent/sign. */
    public int floatingFractionBits(int channel) {
        if (numericClass != NumericClass.FLOAT) throw new IllegalStateException("Not floating point");
        return componentBits(channel) - floatingExponentBits - (floatingSignBit ? 1 : 0);
    }
}
