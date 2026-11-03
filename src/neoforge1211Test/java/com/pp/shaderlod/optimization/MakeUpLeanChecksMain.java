package com.pp.shaderlod.optimization;

/** Separate JVM, matching the existing BSL Iris static-initialization contract. */
public final class MakeUpLeanChecksMain {
    public static void main(String[] args) throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("Owned fixture receiver required");
        checkInactiveDraws();
        var fixture=new MakeUpLeanGraphTest();fixture.dir=java.nio.file.Path.of(args[0]);
        fixture.checkProducer();
        System.out.println("PASS actual MakeUp lean producer/parser/recovery");
    }

    private static void checkInactiveDraws() throws Exception {
        // No GL context exists in this JVM. Owner-free entry decisions and
        // the four plain wrappers must avoid all GL calls and retain the draw.
        String[] families={"makeup","bsl","bliss","photon","sildur","solas","astra"};
        String[] names={"MakeUp","Bsl","Bliss","Photon","Sildur","Solas","Astra"};
        for(int i=0;i<families.length;i++) {
            var runtime=Class.forName("com.pp.shaderlod.optimization."+families[i]+"earlyalpha."+names[i]+"EarlyAlphaRuntime");
            if(!Boolean.TRUE.equals(runtime.getMethod("inactive").invoke(null)))throw new AssertionError(names[i]);
        }
        Object[] wrappers={
            new com.pp.shaderlod.optimization.makeupearlyalpha.mixin.MakeUpAlphaDrawMixin(){},
            new com.pp.shaderlod.optimization.blissearlyalpha.mixin.BlissAlphaDrawMixin(){},
            new com.pp.shaderlod.optimization.sildurearlyalpha.mixin.SildurAlphaDrawMixin(){},
            new com.pp.shaderlod.optimization.photonearlyalpha.mixin.PhotonAlphaDrawMixin(){}
        };
        int[] draws={0};
        com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> nativeCall=values->{draws[0]++;return null;};
        for(Object wrapper:wrappers) {
            var method=wrapper.getClass().getSuperclass().getDeclaredMethod("draw",int.class,long.class,int.class,long.class,int.class,long.class,
                com.llamalad7.mixinextras.injector.wrapoperation.Operation.class);
            method.setAccessible(true);method.invoke(wrapper,0,0L,0,0L,0,0L,nativeCall);
        }
        if(draws[0]!=4)throw new AssertionError("Native call was not retained exactly once per wrapper");
        if(!com.pp.shaderlod.optimization.sourcecache.InactiveDrawElision.elide(true)
                ||com.pp.shaderlod.optimization.sourcecache.InactiveDrawElision.elide(false))throw new AssertionError("Owner guard");
        try {
            com.pp.shaderlod.optimization.sourcecache.InactiveDrawElision.configure(false);
            throw new AssertionError("Ungated diagnostic accepted");
        } catch(IllegalStateException expected) {}
        System.setProperty("shaderlod.dev.inactiveDrawElision","true");
        try {
            com.pp.shaderlod.optimization.sourcecache.InactiveDrawElision.configure(false);
            if(com.pp.shaderlod.optimization.sourcecache.InactiveDrawElision.elide(true))throw new AssertionError("Causal control");
        } finally {
            com.pp.shaderlod.optimization.sourcecache.InactiveDrawElision.configure(true);
            System.clearProperty("shaderlod.dev.inactiveDrawElision");
        }
        System.out.println("PASS seven owner-free alpha decisions and four plain native wrappers without GL");
    }
}
