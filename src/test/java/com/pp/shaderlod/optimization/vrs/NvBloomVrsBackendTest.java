package com.pp.shaderlod.optimization.vrs;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NvBloomVrsBackendTest {
    static VrsCapabilities supported() {
        var map=new HashMap<String,Boolean>();
        VrsCapabilities.REQUIRED_FUNCTIONS.forEach(f->map.put(f,true));
        return new VrsCapabilities(true,true,true,map);
    }
    static class Fake implements NvBloomVrsBackend.Driver {
        VrsCapabilities caps=supported();
        long context=101;
        int image=17,palette=0x9565,created,deleted,nv,width,height;
        boolean enabled=true,failBind,failRestore;
        public long context(){return context;}
        public VrsCapabilities capabilities(){return caps;}
        public int tileWidth(){nv++;return 16;}
        public int tileHeight(){nv++;return 16;}
        public int maxCoarseSamples(){nv++;return 16;}
        public int createZeroRateImage(int w,int h){width=w;height=h;return 100+(++created);}
        public void deleteImage(int id){deleted++;}
        public void imageBarrier(){nv++;}
        public int boundImage(){nv++;return image;}
        public int paletteZero(){nv++;return palette;}
        public boolean enabledZero(){nv++;return enabled;}
        public void bindImage(int value){nv++;image=value;if(failBind){failBind=false;throw new IllegalStateException("bind failure after mutation");}}
        public void paletteZero(int value){nv++;if(failRestore&&value==0x9565){failRestore=false;throw new IllegalStateException("restore failed once");}palette=value;}
        public void enabledZero(boolean value){nv++;enabled=value;}
        void original(){assertEquals(17,image);assertEquals(0x9565,palette);assertTrue(enabled);}
    }
    @Test void everyRequiredGateRefusesIndependentlyWithoutNvCalls() {
        var full=supported();
        var cases=new ArrayList<VrsCapabilities>();
        cases.add(new VrsCapabilities(false,true,true,full.functions()));
        cases.add(new VrsCapabilities(true,false,true,full.functions()));
        cases.add(new VrsCapabilities(true,true,false,full.functions()));
        for(String f:VrsCapabilities.REQUIRED_FUNCTIONS){
            var functions=new HashMap<>(full.functions());functions.put(f,false);
            cases.add(new VrsCapabilities(true,true,true,functions));
        }
        for(var caps:cases){
            var d=new Fake();d.caps=caps;var backend=new NvBloomVrsBackend(d);
            assertFalse(caps.supported());assertNull(backend.open(true,9,1920,1200));
            backend.release();assertEquals(0,d.nv);assertEquals(0,d.created);assertEquals(0,d.deleted);d.original();
        }
    }
    @Test void unqualifiedOrDefaultFramebufferNeverTouchesDriverState() {
        var d=new Fake();var b=new NvBloomVrsBackend(d);
        assertNull(b.open(false,9,1920,1200));assertNull(b.open(true,0,1920,1200));
        assertNull(b.open(true,9,0,1200));assertEquals(0,d.nv);assertEquals(0,d.created);
    }
    @Test void actualScopeRestoresPreviouslyEnabledForeignStateOnBodyFailure() {
        var d=new Fake();var b=new NvBloomVrsBackend(d);
        var thrown=assertThrows(IllegalArgumentException.class,()->{
            try(var scope=b.open(true,9,1920,1200)){
                assertNotNull(scope);assertEquals(101,d.image);assertEquals(NvBloomVrsBackend.RATE_2X2,d.palette);assertTrue(d.enabled);
                assertNull(b.open(true,9,1920,1200));
                throw new IllegalArgumentException("native draw failed");
            }
        });
        assertEquals("native draw failed",thrown.getMessage());d.original();
        b.release();assertEquals(1,d.deleted);d.original();
    }
    @Test void partialSetupFailureRestoresAndDisablesUntilRelease() {
        var d=new Fake();var b=new NvBloomVrsBackend(d);d.failBind=true;
        assertNull(b.open(true,9,1920,1200));d.original();
        int calls=d.nv;assertNull(b.open(true,9,1920,1200));assertEquals(calls,d.nv);
        b.release();assertEquals(1,d.deleted);
        try(var scope=b.open(true,9,1920,1200)){assertNotNull(scope);}d.original();b.release();
    }
    @Test void failedRestorationRetainsSnapshotForRecoveryInsteadOfContinuing() {
        var d=new Fake();var b=new NvBloomVrsBackend(d);var scope=b.open(true,9,1920,1200);
        d.failRestore=true;assertThrows(IllegalStateException.class,scope::close);
        b.release();d.original();assertEquals(1,d.deleted);
    }
    @Test void resizeUsesCeilFootprintAndRetiresOnlyOwnedImages() {
        var d=new Fake();var b=new NvBloomVrsBackend(d);
        try(var scope=b.open(true,9,1921,1201)){assertNotNull(scope);assertEquals(121,d.width);assertEquals(76,d.height);}
        try(var scope=b.open(true,9,1922,1202)){assertNotNull(scope);}assertEquals(1,d.created);
        try(var scope=b.open(true,9,1919,1199)){assertNotNull(scope);}assertEquals(2,d.created);assertEquals(1,d.deleted);
        b.release();assertEquals(2,d.deleted);d.original();
    }
    @Test void foreignContextNeverUsesOrDeletesOriginalContextObjects() {
        var d=new Fake();var b=new NvBloomVrsBackend(d);
        try(var scope=b.open(true,9,1920,1200)){assertNotNull(scope);}d.original();
        d.context=202;int calls=d.nv;assertNull(b.open(true,9,1920,1200));b.release();
        assertEquals(calls,d.nv);assertEquals(0,d.deleted);
        d.context=101;b.release();assertEquals(1,d.deleted);d.original();
    }
    @Test void disabledPriorEnableIsRestoredAsDisabled() {
        var d=new Fake();d.enabled=false;var b=new NvBloomVrsBackend(d);
        try(var scope=b.open(true,9,1920,1200)){assertNotNull(scope);assertTrue(d.enabled);}
        assertFalse(d.enabled);assertEquals(17,d.image);assertEquals(0x9565,d.palette);b.release();
    }
    @Test void aMidScopeContextSwitchRetainsRestorationUntilOwnerReturns() {
        var d=new Fake();var b=new NvBloomVrsBackend(d);var scope=b.open(true,9,1920,1200);
        d.context=202;int calls=d.nv;
        assertThrows(IllegalStateException.class,scope::close);assertEquals(calls,d.nv);
        d.context=101;b.release();d.original();assertEquals(1,d.deleted);
    }
}
