package com.pp.shaderlod.optimization;

import com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpAlphaInterface;
import com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpOpaqueAlphaSpecialization;
import java.nio.charset.StandardCharsets;
import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.gl.blending.AlphaTestFunction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MakeUpOpaqueAlphaSpecializationTest {
    private static String authenticHelper() throws Exception {
        try(var input=MakeUpOpaqueAlphaSpecializationTest.class.getResourceAsStream(
                "/assets/shaderlod/adapters/patches/adapters/shared/makeup_early_alpha.glsl")) {
            assertNotNull(input);
            return new String(input.readAllBytes(),StandardCharsets.UTF_8).replace("#if SHADERLOD_MAKEUP_EARLY_ALPHA == 1\n", "").replace("#else\n    return false;\n#endif\n}", "}")
                + "\nvoid main(){vec4 blockColor=vec4(1.0);if(shaderlodMakeupNativeAlphaReject(clamp(blockColor.a,0.0,1.0)))discard;}\n";
        }
    }
    @Test void opaqueChangesExactlyTheActiveHelperAndNeverGrantsAlpha() throws Exception {
        String before=authenticHelper();
        String after=MakeUpOpaqueAlphaSpecialization.specialize(before,AlphaTest.ALWAYS,true);
        assertNotNull(after);
        String old="bool shaderlodMakeupEarlyAlphaActive() {\n    return shaderlod_optimization_enabled(shaderlodOptEarlyAlpha) && shaderlodPreset != 2 && shaderlodMakeupEarlyAlphaPermission == 1;\n}";
        assertEquals(before.replace(old,"bool shaderlodMakeupEarlyAlphaActive() { return false; }"),after,
            "Reject stub, samples, coordinates and every other byte must remain unchanged");
        assertTrue(after.contains("bool shaderlodMakeupNativeAlphaReject(float finalAlpha) { return false; }"));
        assertTrue(MakeUpOpaqueAlphaSpecialization.transformed(after));
        assertFalse(MakeUpAlphaInterface.transformed(after,AlphaTest.ALWAYS));
    }
    @Test void missingProofOrUnfamiliarAndDuplicateDefinitionsLeaveInputUntouched() throws Exception {
        String before=authenticHelper();
        assertNull(MakeUpOpaqueAlphaSpecialization.specialize(before,AlphaTest.ALWAYS,false));
        assertNull(MakeUpOpaqueAlphaSpecialization.specialize(before.replace("shaderlodPreset != 2","shaderlodPreset == 2"),AlphaTest.ALWAYS,true));
        assertNull(MakeUpOpaqueAlphaSpecialization.specialize(before+"\nbool shaderlodMakeupEarlyAlphaActive(){return true;}",AlphaTest.ALWAYS,true));
        assertNull(MakeUpOpaqueAlphaSpecialization.specialize("void main(){}",AlphaTest.ALWAYS,true));
        assertNull(MakeUpOpaqueAlphaSpecialization.specialize(null,AlphaTest.ALWAYS,true));
    }
    @Test void greaterAndOtherPoliciesKeepTheirExistingRewrite() throws Exception {
        String before=authenticHelper();var greater=new AlphaTest(AlphaTestFunction.GREATER,.1f);
        assertNull(MakeUpOpaqueAlphaSpecialization.specialize(before,greater,true));
        assertNull(MakeUpOpaqueAlphaSpecialization.specialize(before,new AlphaTest(AlphaTestFunction.LESS,.1f),true));
        assertNull(MakeUpOpaqueAlphaSpecialization.specialize(before,null,true));
        String prepared=MakeUpAlphaInterface.prepare(before,greater);
        assertNotNull(prepared);assertTrue(prepared.contains("!(finalAlpha > shaderlodMakeupEarlyAlphaReference)"));
        assertFalse(MakeUpOpaqueAlphaSpecialization.transformed(prepared));
    }
}
