package com.pp.shaderlod.optimization.makeupprime;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class C01PlatformTest {
    @Test void exactPrivateAsahiBoundary() {
        String gpu="Apple M1 (G13G B1)", driver="4.6 (Core Profile) Mesa 25.1.0-asahi20250221";
        assertTrue(C01Platform.allowed("Linux","aarch64",gpu,driver));
        assertFalse(C01Platform.allowed("Linux","amd64",gpu,driver));
        assertFalse(C01Platform.allowed("Linux","aarch64","Apple M2",driver));
        assertFalse(C01Platform.allowed("Linux","aarch64",gpu,driver+" changed"));
        assertFalse(C01Platform.allowed("Windows","aarch64",gpu,driver));
        assertFalse(C01Platform.allowed(null,null,null,null));
        assertTrue(C01Platform.allowed("Mac OS X","aarch64",null,null));
    }
}
