package com.pp.shaderlod.optimization.bslprime;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BslDepthInvariantTest {
    // Compact spelling from saved Iris stage 084, including both distinct discard sites.
    static final String SOLID = """
        #version 330 core
        invariant in float mat;
        uniform int shaderlodBslSpatialPhase;
        bool shaderlodBslNativeAlphaReject(float finalAlpha) { return false; }
        void main() {
          if (shaderlodBslEarlyAlphaEnabled() && shaderlodBslNativeAlphaReject(albedo.a)) discard;
          if (shaderlodBslSpatialPhase == 1) {
            if (shaderlodBslSpatialAlphaRef >= 0.0f && !(albedo.a > shaderlodBslSpatialAlphaRef)) discard;
            iris_FragData0 = albedo;
            return;
          }
        }
        """;
    static Map<String,String> options(String depth) throws Exception {
        var result = new TreeMap<String,String>();
        try (var in = BslDepthInvariantTest.class.getResourceAsStream("/assets/shaderlod/bslprime/options398.json")) {
            assertNotNull(in);
            JsonParser.parseReader(new InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))
                .getAsJsonObject().entrySet().forEach(e -> result.put(e.getKey(), e.getValue().getAsString()));
        }
        result.put(BslDepthPriming.OPTION, depth);
        return result;
    }
    @Test void exactDormantProducerRequiresFullDepthOneProfile() throws Exception {
        assertTrue(BslDepthPriming.invariantStage(SOLID, false, options("1")));
        assertFalse(BslDepthPriming.invariantStage(SOLID, false, options("0")));
        assertFalse(BslDepthPriming.invariantStage(SOLID, false, Map.of(BslDepthPriming.OPTION,"1")));
        var foreign = new TreeMap<>(options("1"));
        foreign.put("FOREIGN", "1");
        assertFalse(BslDepthPriming.invariantStage(SOLID, false, foreign));
    }
    @Test void changedGuardsAndOtherDiscardsRemainRefused() throws Exception {
        var options = options("1");
        for (var changed : new String[]{
                SOLID.replace("shaderlodBslSpatialPhase == 1", "shaderlodBslSpatialPhase >= 1"),
                SOLID.replace("shaderlodBslSpatialPhase == 1", "shaderlodBslSpatialPhase == 2"),
                SOLID.replace("shaderlodBslSpatialAlphaRef >= 0.0f", "shaderlodBslSpatialAlphaRef >= -1.0f"),
                SOLID.replace("albedo.a > shaderlodBslSpatialAlphaRef", "albedo.a < shaderlodBslSpatialAlphaRef"),
                SOLID + "\nvoid extra() { discard; }",
                SOLID.replace("return false;", "return finalAlpha <= 0.1f;"),
                SOLID.replace("iris_FragData0 = albedo;", "discard; iris_FragData0 = albedo;")})
            assertFalse(BslDepthPriming.invariantStage(changed, false, options));
    }
    @Test void sideEffectsAndVertexRestrictionsRemainRefused() throws Exception {
        var options = options("1");
        for (var sideEffect : new String[]{"gl_FragDepth", "imageStore", "imageAtomicAdd", "atomicAdd", "clrwl_custom"})
            assertFalse(BslDepthPriming.invariantStage(SOLID + "\n" + sideEffect, false, options));
        assertFalse(BslDepthPriming.invariantStage(SOLID.replace("invariant in", "in"), false, options));
        assertFalse(BslDepthPriming.invariantStage("#version 330 core\ninvariant out float mat;", true, options));
        assertTrue(BslDepthPriming.invariantStage("#version 330 core\ninvariant out float mat;\ninvariant gl_Position;", true, options));
    }
}
