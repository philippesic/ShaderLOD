package com.pp.shaderlod.integration;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipFile;
import net.irisshaders.iris.helpers.StringPair;
import net.irisshaders.iris.shaderpack.include.*;
import net.irisshaders.iris.shaderpack.option.ShaderPackOptions;
import net.irisshaders.iris.shaderpack.preprocessor.PropertiesPreprocessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Exercise the actual pinned properties preprocessor; GLSL alone misses property guards. */
class MakeUpCloudR8PropertiesTest {
    @TempDir Path directory;
    @Test void exactSelectedRawStorageAndIndependentReferenceAreActuallyParsed() throws Exception {
        Path output=directory.resolve("R8.zip");
        var build=new ProcessBuilder("python3","scripts/build_makeup_cloud_r8.py","--original",
            "dependencies/upstream/other-pack-research/MakeUp-UltraFast-9.5g.zip","--options",
            "dependencies/shortlist-test-inputs/g08/native100.txt",
            "--output",output.toString(),"--diagnostic").redirectErrorStream(true).start();
        String log=new String(build.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
        assertEquals(0,build.waitFor(),log);
        var nodes=new LinkedHashMap<AbsolutePackPath,FileNode>();String properties;
        try(var zip=new ZipFile(output.toFile())) {
            properties=new String(zip.getInputStream(zip.getEntry("shaders/shaders.properties")).readAllBytes(),StandardCharsets.UTF_8);
            for(var e:Collections.list(zip.entries()))if(e.getName().startsWith("shaders/")&&e.getName().matches(".*\\.(fsh|vsh|glsl)$")) {
                var path=AbsolutePackPath.fromAbsolutePath(e.getName().substring("shaders".length()));
                nodes.put(path,new FileNode(path,ImmutableList.copyOf(new String(zip.getInputStream(e).readAllBytes(),StandardCharsets.UTF_8).split("\\R"))));
            }
        }
        var constructor=IncludeGraph.class.getDeclaredConstructor(ImmutableMap.class,ImmutableMap.class);constructor.setAccessible(true);
        var graph=constructor.newInstance(ImmutableMap.copyOf(nodes),ImmutableMap.of());
        var defines=List.of(new StringPair("IS_IRIS","1"),new StringPair("IRIS_VERSION","10812"),new StringPair("MC_VERSION","12101"),new StringPair("MC_OS_MAC","1"));
        for(String mode:List.of("0","1","2")) {
            var options=new ShaderPackOptions(graph,Map.of("SHADERLOD_G08_CLOUD_R8",mode,"SHADERLOD_G08_CLOUD_R8_DEBUG","1","V_CLOUDS","2","CLOUD_VOL_STYLE","0","CLOUD_SPEED","0"));
            String parsed=PropertiesPreprocessor.preprocessSource(properties,options,defines);
            String expected=mode.equals("2")?"shaderlod_g08_cloud_r.bin":mode.equals("1")?"shaderlod_g08_cloud_rgba.bin":"clouds_natural_512_R_8bit.png";
            assertTrue(parsed.contains("texture.deferred.gaux2 = textures/"+expected),parsed);
            assertTrue(parsed.contains("customTexture.shaderlodG08ReferenceNoise = textures/clouds_natural_512_R_8bit.png"));
        }
    }
}
