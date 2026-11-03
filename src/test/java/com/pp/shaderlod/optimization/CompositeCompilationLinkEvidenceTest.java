package com.pp.shaderlod.optimization;

import com.pp.shaderlod.optimization.sourcecache.CompositeCompilationLinkEvidence;
import com.pp.shaderlod.optimization.sourcecache.CompositeCompilationLinkEvidence.Stage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CompositeCompilationLinkEvidenceTest {
    private final Object vertex=new Object(),fragment=new Object(),builder=new Object(),program=new Object(),context=new Object();
    private CompositeCompilationLinkEvidence<Object> shaders(){
        var proof=new CompositeCompilationLinkEvidence<>(context);proof.transformed("vertex",null,"fragment");proof.begin("vertex",null,"fragment");
        proof.shader(Stage.VERTEX,"vertex",vertex);proof.shader(Stage.FRAGMENT,"fragment",fragment);return proof;
    }
    @Test void successfulLinkRequiresTheExactBuilderAndReturnedProgram(){
        var proof=shaders();proof.linked(new Object[]{vertex,fragment},7);proof.builder(builder,7);proof.built(builder,program,7);
        assertSame(context,proof.complete(program,7));assertNull(proof.complete(program,7));
    }
    @Test void foreignOrReorderedShaderObjectsCannotQualify(){
        for(Object[] inputs:new Object[][]{{fragment,vertex},{vertex,new Object()},{vertex},{vertex,fragment,new Object()}}){
            var proof=shaders();proof.linked(inputs,7);assertTrue(proof.poisoned());proof.builder(builder,7);proof.built(builder,program,7);assertNull(proof.complete(program,7));
        }
    }
    @Test void substitutedStageTextAndDuplicateCompilationPoisonTheAttempt(){
        var proof=new CompositeCompilationLinkEvidence<>(context);proof.transformed("vertex",null,"fragment");proof.begin("vertex",null,"foreign");assertTrue(proof.poisoned());
        proof=shaders();proof.shader(Stage.FRAGMENT,"fragment",fragment);assertTrue(proof.poisoned());
        proof=new CompositeCompilationLinkEvidence<>(context);proof.transformed("vertex",null,"fragment");proof.begin("vertex",null,"fragment");proof.shader(Stage.VERTEX,"foreign",vertex);assertTrue(proof.poisoned());
    }
    @Test void failedOrPartialLinkAndReusedNumericIdsNeverPublish(){
        var proof=shaders();assertNull(proof.complete(program,7));
        proof=shaders();proof.linked(new Object[]{vertex,fragment},0);assertTrue(proof.poisoned());
        proof=shaders();proof.linked(new Object[]{vertex,fragment},7);proof.builder(builder,8);assertTrue(proof.poisoned());
        proof=shaders();proof.linked(new Object[]{vertex,fragment},7);proof.builder(builder,7);proof.built(new Object(),program,7);assertNull(proof.complete(program,7));
        proof=shaders();proof.linked(new Object[]{vertex,fragment},7);proof.builder(builder,7);proof.built(builder,program,7);assertNull(proof.complete(new Object(),7));
    }
    @Test void explicitInvalidationCannotBeRevivedByMatchingOldInputs(){
        var proof=shaders();proof.poison();proof.linked(new Object[]{vertex,fragment},7);proof.builder(builder,7);proof.built(builder,program,7);assertNull(proof.complete(program,7));
    }
}
