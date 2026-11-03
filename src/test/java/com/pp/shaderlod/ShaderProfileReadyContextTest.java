package com.pp.shaderlod;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShaderProfileReadyContextTest {
    @Test void unavailableAdmissionGetsOneAttemptPerReadyContext(){
        var ready=new ShaderOptimizationProfiles.ReadyContext();
        var world=new Object();var pipeline=new Object();var pack=new Object();
        assertFalse(ready.claim(null,pipeline,pack));assertFalse(ready.claim(world,null,pack));
        assertTrue(ready.claim(world,pipeline,pack));
        for(int tick=0;tick<100;tick++)assertFalse(ready.claim(world,pipeline,pack));
        assertTrue(ready.claim(world,new Object(),pack));
        assertTrue(ready.claim(new Object(),pipeline,pack));
        assertTrue(ready.claim(world,pipeline,new Object()));
        ready.clear();assertTrue(ready.claim(world,pipeline,pack));
    }
}
