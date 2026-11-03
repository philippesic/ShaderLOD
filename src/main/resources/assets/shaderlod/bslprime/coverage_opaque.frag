#version 330 core
// SOLID AlphaTest.ALWAYS only. Native mat 0 includes full opaque blockID 250.
// Same native vertex object and invariant interpolation; NaN/nonzero refuse.
invariant in float mat;
void main() { if (!(mat == 0.0)) discard; }
