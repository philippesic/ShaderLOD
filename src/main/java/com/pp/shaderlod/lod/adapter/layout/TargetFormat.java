package com.pp.shaderlod.lod.adapter.layout;

/** Explicit reviewed storage formats; no format inference or driver-support promise. */
public enum TargetFormat {
    R32F(1, 32, NumericClass.FLOAT), RGBA16F(4, 16, NumericClass.FLOAT),
    RGBA32F(4, 32, NumericClass.FLOAT), RGBA8(4, 8, NumericClass.NORMALIZED),
    R32I(1, 32, NumericClass.SIGNED_INTEGER), RGBA16I(4, 16, NumericClass.SIGNED_INTEGER),
    RGBA32I(4, 32, NumericClass.SIGNED_INTEGER),
    R32UI(1, 32, NumericClass.UNSIGNED_INTEGER), RGBA16UI(4, 16, NumericClass.UNSIGNED_INTEGER),
    RGBA32UI(4, 32, NumericClass.UNSIGNED_INTEGER);

    public enum NumericClass { FLOAT, NORMALIZED, SIGNED_INTEGER, UNSIGNED_INTEGER }
    private final int channels;
    private final int bits;
    private final NumericClass numericClass;

    TargetFormat(int channels, int bits, NumericClass numericClass) {
        this.channels = channels;
        this.bits = bits;
        this.numericClass = numericClass;
    }

    public int channels() { return channels; }
    public int componentBits() { return bits; }
    public NumericClass numericClass() { return numericClass; }
}
