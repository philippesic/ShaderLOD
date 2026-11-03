package com.pp.shaderlod.integration;

import com.pp.shaderlod.integration.makeupquad.VertexDependencyContract;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class VertexDependencyContractTest {
    private static byte[] bytes(String text){return text.getBytes(StandardCharsets.UTF_8);}
    private static Map<String,byte[]> source(String include){
        return Map.of("shaders/world1/gbuffers_weather.vsh",bytes("#include \""+include+"\"\n"),
            "shaders/program/gbuffers_weather.vsh",bytes("#include \"../lib/common.glsl\"\n"),
            "shaders/lib/common.glsl",bytes("// exact dependency\n"));
    }
    @Test void archiveAbsoluteAndRelativeTransitiveIncludesUseForwardSlashMembers(){
        for(String path:List.of("/program/gbuffers_weather.vsh","../program/gbuffers_weather.vsh")){
            var files=source(path);assertTrue(VertexDependencyContract.sameVertexContract(files,files),path);
            var changed=new HashMap<>(files);changed.put("shaders/lib/common.glsl",bytes("// changed dependency\n"));
            assertFalse(VertexDependencyContract.sameVertexContract(files,changed));
            changed.remove("shaders/lib/common.glsl");assertFalse(VertexDependencyContract.sameVertexContract(files,changed));
        }
    }
    @Test void unsafeMissingAndUnprovedIncludeOperandsStillRefuse(){
        for(String path:List.of("../../outside.glsl","/../outside.glsl","../program\\gbuffers_weather.vsh","/missing.glsl")){
            var files=source(path);assertFalse(VertexDependencyContract.sameVertexContract(files,files),path);
        }
        var macro=Map.of("shaders/gbuffers_terrain.vsh",bytes("#include UNKNOWN_DEPENDENCY\n"));
        assertFalse(VertexDependencyContract.sameVertexContract(macro,macro));
    }
}
