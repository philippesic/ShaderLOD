package com.pp.shaderlod.optimization;

import com.pp.shaderlod.optimization.sourcecache.IrisCompiledSource;
import com.pp.shaderlod.optimization.sourcecache.LinkedCompilationProof;
import java.util.EnumMap;
import java.util.Map;
import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.shaderpack.properties.ProgramDirectives;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the real wrapper after raw certification; no game, GL or simulated replay. */
class LinkedCompilationProofTest {
    private static final class Attempt {
        final LinkedCompilationProof proof=new LinkedCompilationProof("complementary-reimagined","gbuffers_terrain");
        final ProgramSource source;
        final IrisCompiledSource.Certificate certificate;
        final EnumMap<PatchShaderType,String> output=new EnumMap<>(PatchShaderType.class);
        final Map<PatchShaderType,Object> initialShaders=shaders();
        Attempt() throws Exception {
            var constructor=ProgramSource.class.getDeclaredConstructor(String.class,String.class,String.class,
                String.class,String.class,String.class,ProgramDirectives.class,ProgramSet.class);
            constructor.setAccessible(true);
            source=constructor.newInstance("gbuffers_terrain","vertex",null,null,null,"fragment",null,null);
            certificate=new IrisCompiledSource.Certificate(source,null,null,"fixture.zip",IrisCompiledSource.Stages.of(source),1);
            for(var type:PatchShaderType.values())output.put(type,null);
            output.put(PatchShaderType.VERTEX,"transformed vertex");output.put(PatchShaderType.FRAGMENT,"transformed fragment");
            prepare();proof.transformed(source,null,AlphaTest.ALWAYS,output,true);
            proof.compiled(output,initialShaders);
            assertSame(certificate,proof.linked(source,AlphaTest.ALWAYS,initialShaders));
        }
        @SuppressWarnings("unchecked")
        void prepare() throws Exception {
            // Establish only the already-validated raw input boundary. Actual certification is
            // outside this unit fixture; the wrapper and compile/link joins are production code.
            var field=LinkedCompilationProof.class.getDeclaredField("PENDING");field.setAccessible(true);
            ((Map<ProgramSource,LinkedCompilationProof.Prepared>)field.get(proof)).put(source,
                new LinkedCompilationProof.Prepared(certificate,AlphaTest.ALWAYS));
        }
        void assertNewLinkRefused() {
            var fresh=shaders();proof.compiled(output,fresh);
            assertNull(proof.linked(source,AlphaTest.ALWAYS,fresh),
                "a rejected attempt must not reuse a cached map's previous raw certificate");
            // Old successfully compiled evidence describes consumed inputs and remains valid.
            assertSame(certificate,proof.linked(source,AlphaTest.ALWAYS,initialShaders));
        }
    }
    private static Map<PatchShaderType,Object> shaders() {
        return Map.of(PatchShaderType.VERTEX,new Object(),PatchShaderType.FRAGMENT,new Object());
    }
    @Test void missingRawPreparationRevokesAReusedTransformMap() throws Exception {
        var a=new Attempt();
        a.proof.discardPreparation(a.source);
        a.proof.transformed(a.source,null,AlphaTest.ALWAYS,a.output,true);
        a.assertNewLinkRefused();
    }
    @Test void explicitTransformedRejectionRevokesAReusedTransformMap() throws Exception {
        var a=new Attempt();a.prepare();
        a.proof.transformed(a.source,null,AlphaTest.ALWAYS,a.output,false);
        a.assertNewLinkRefused();
    }
    @Test void differentAlphaCannotRetainTheOldMapCertificate() throws Exception {
        var a=new Attempt();a.prepare();
        a.proof.transformed(a.source,null,new AlphaTest(net.irisshaders.iris.gl.blending.AlphaTestFunction.GREATER,0.1f),a.output,true);
        a.assertNewLinkRefused();
    }
    @Test void aFreshAcceptedPreparationMayCertifyTheCachedMapAgain() throws Exception {
        var a=new Attempt();
        a.proof.transformed(a.source,null,AlphaTest.ALWAYS,a.output,false);
        a.prepare();a.proof.transformed(a.source,null,AlphaTest.ALWAYS,a.output,true);
        var fresh=shaders();a.proof.compiled(a.output,fresh);
        assertSame(a.certificate,a.proof.linked(a.source,AlphaTest.ALWAYS,fresh));
    }
}
