package com.pp.shaderlod.dev.timing;

import com.google.gson.JsonObject;

/** Pure conversion/error fixtures; no client, GLFW initialization or Objective-C messages. */
public final class MacWindowStateEvidenceTest {
    public static void main(String[] arguments) {
        for (Object zero : new Object[] { (byte) 0, (short) 0, 0, 0L }) {
            check(MacWindowStateEvidence.activationPolicy(zero).equals("regular"), "regular integral conversion");
            check(MacWindowStateEvidence.objcBoolean(zero).equals(false), "false integral conversion");
        }
        check(MacWindowStateEvidence.activationPolicy(1L).equals("accessory"), "accessory enum");
        check(MacWindowStateEvidence.activationPolicy(2L).equals("prohibited"), "prohibited enum");
        check(MacWindowStateEvidence.activationPolicy(4294967296L).equals("unknown(4294967296)"), "unknown enum must not truncate");
        check(MacWindowStateEvidence.objcBoolean(-1L).equals(true), "nonzero ObjC BOOL");
        check(MacWindowStateEvidence.objcBoolean(true).equals(true), "Boolean bridge output");
        JsonObject observations = new JsonObject();
        MacWindowStateEvidence.observe(observations, "linkage", () -> { throw new UnsatisfiedLinkError("fixture native missing"); }, value -> value);
        MacWindowStateEvidence.observe(observations, "nil", () -> null, value -> value);
        MacWindowStateEvidence.observe(observations, "badGlfw", () -> 7, MacWindowStateEvidence::glfwBoolean);
        MacWindowStateEvidence.observe(observations, "badEnum", () -> 0.5, MacWindowStateEvidence::activationPolicy);
        MacWindowStateEvidence.observe(observations, "success", () -> 2L, MacWindowStateEvidence::activationPolicy);
        for (String key : new String[] { "linkage", "nil", "badGlfw", "badEnum" }) {
            JsonObject error = observations.getAsJsonObject(key);
            check(error.get("availability").getAsString().equals("unavailable"), "explicit failure " + key);
            check(error.has("errorType") && error.has("errorMessage") && !error.has("value"), "failure details " + key);
        }
        check(observations.getAsJsonObject("linkage").get("errorMessage").getAsString().equals("fixture native missing"), "original failure retained");
        check(observations.getAsJsonObject("badEnum").get("raw").getAsString().equals("0.5"), "conversion failure raw retained");
        check(observations.getAsJsonObject("success").get("value").getAsString().equals("prohibited"), "failure does not prevent next query");
        String old = System.getProperty(MacWindowStateEvidence.PROPERTY);
        try {
            System.clearProperty(MacWindowStateEvidence.PROPERTY);
            JsonObject disabled = MacWindowStateEvidence.capture(0, "fixture-disabled");
            check(disabled.get("availability").getAsString().equals("disabled") && !disabled.has("glfw"), "default-false no native queries");
        } finally {
            if (old == null) System.clearProperty(MacWindowStateEvidence.PROPERTY);
            else System.setProperty(MacWindowStateEvidence.PROPERTY, old);
        }
        System.out.println("PASS: integral/BOOL/enum conversion, explicit linkage/nil/conversion failures, sibling retention, default-false gate; no native calls");
    }

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }
}
