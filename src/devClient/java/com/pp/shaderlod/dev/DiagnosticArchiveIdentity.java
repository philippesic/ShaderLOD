package com.pp.shaderlod.dev;

/** Diagnostic basename permission only; callers must still verify actual ZIP bytes and ownership. */
final class DiagnosticArchiveIdentity {
    private DiagnosticArchiveIdentity() {}
    static boolean allows(String name,String sha) {
        if(name==null||sha==null||!sha.matches("[a-f0-9]{64}"))return false;
        return name.matches("[A-Za-z0-9_.-]+\\.zip")
            || name.equals("Bliss_v2.1.2_(Chocapic13_Shaders_edit).zip")
                && sha.equals("f41db92acc585fe9ffe0cc124d6ecaea27cef9ded91129ebc2a8e60226947b2c")
            || name.equals("Solas Shader V3.7b.zip")
                && sha.equals("b7dfe4c7353d677a92cc4c86b87bc0633f1014305d927ca6a482c303427bf825")
            || name.equals("Sildur's Vibrant Shaders v2.02 Extreme.zip")
                && sha.equals("824504bc937f9570277c0a809f5059ae4a5fe70c413cf4459b6ccff4dfed7ebe");
    }
}
