package com.pp.shaderlod.optimization.framecopy;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.irisshaders.iris.helpers.StringPair;
import net.irisshaders.iris.shaderpack.preprocessor.PropertiesPreprocessor;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FrameCopyPropertyGuardTest {
    private static String properties() throws Exception {
        var recipe=JsonParser.parseString(Files.readString(Path.of("patches/adapters/makeup-ultra-fast-shaders.json"))).getAsJsonObject();
        for(var item:recipe.getAsJsonArray("patches")) {
            var patch=item.getAsJsonObject();
            if(patch.get("path").getAsString().equals("shaders/shaders.properties")) {
                String text=patch.get("after").getAsString();
                return text.substring(text.lastIndexOf("# ShaderLOD G01:"));
            }
        }
        throw new AssertionError("Missing actual G01 properties patch");
    }
    @Test void absentMixinOnColdLoadCannotActivatePersistedOne() throws Exception {
        // No Java capability helper at all: the unsupported engine only sees persisted G01=1.
        var persisted=List.of(new StringPair("SHADERLOD_MAKEUP_DEAD_FRAME_COPY","1"));
        assertFalse(PropertiesPreprocessor.preprocessSource(properties(),persisted).contains("flip.composite2"));
        var supported=FrameCopyPropertyGuard.defines(persisted,true);
        String on=PropertiesPreprocessor.preprocessSource(properties(),supported);
        assertTrue(on.contains("flip.composite2.colortex0=true"));
        assertTrue(on.contains("flip.composite2.colortex7=true"));
        assertEquals(2,on.lines().filter(l->l.startsWith("flip.")).count());
        var off=FrameCopyPropertyGuard.defines(List.of(new StringPair("SHADERLOD_MAKEUP_DEAD_FRAME_COPY","0")),true);
        assertFalse(PropertiesPreprocessor.preprocessSource(properties(),off).contains("flip.composite2"));
    }
    @Test void capabilityCannotSurviveARejectedApiOrMutateTheCaller() throws Exception {
        var input=List.of(new StringPair("SHADERLOD_MAKEUP_DEAD_FRAME_COPY","1"),new StringPair(FrameCopyPropertyGuard.DEFINE,"1"));
        var denied=FrameCopyPropertyGuard.defines(input,false);
        assertEquals(2,input.size());assertEquals(1,denied.size());
        assertFalse(PropertiesPreprocessor.preprocessSource(properties(),denied).contains("flip.composite2"));
        var admitted=FrameCopyPropertyGuard.defines(input,true);
        assertEquals(1,admitted.stream().filter(p->p.key().equals(FrameCopyPropertyGuard.DEFINE)).count());
        assertThrows(UnsupportedOperationException.class,()->admitted.clear());
    }
    @Test void constructorCapabilityJoinsGlslAndPropertiesAndFailsClosed() {
        var input=com.google.common.collect.ImmutableList.of(new StringPair("SHADERLOD_MAKEUP_DEAD_FRAME_COPY","1"),
                new StringPair(FrameCopyPropertyGuard.DEFINE,"0"));
        String glsl="#if defined SHADERLOD_FRAMECOPY_RUNTIME_GUARD && SHADERLOD_MAKEUP_DEAD_FRAME_COPY == 1\nfloat scalarRoute;\n#else\nfloat nativeRoute;\n#endif\n";
        for(boolean exact:List.of(false,true)) {
            var constructor=FrameCopyPropertyGuard.constructorDefines(input,exact);
            String shader=net.irisshaders.iris.shaderpack.preprocessor.JcppProcessor.glslPreprocessSource(glsl,constructor);
            String properties=PropertiesPreprocessor.preprocessSource(glsl,FrameCopyPropertyGuard.defines(constructor,exact));
            assertEquals(exact,shader.contains("scalarRoute"));
            assertEquals(exact,properties.contains("scalarRoute"));
            assertEquals(!exact,shader.contains("nativeRoute"));
            assertEquals(constructor,FrameCopyPropertyGuard.defines(constructor,exact));
        }
        assertEquals("0",input.get(1).value());
    }

}
