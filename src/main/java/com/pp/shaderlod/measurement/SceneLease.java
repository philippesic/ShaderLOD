package com.pp.shaderlod.measurement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import java.util.concurrent.TimeUnit;
/** Reversible private singleplayer simulation ownership; never freezes a published/shared server. */
public final class SceneLease implements AutoCloseable {
    private final IntegratedServer server;
    private final java.util.concurrent.atomic.AtomicBoolean cancelled=new java.util.concurrent.atomic.AtomicBoolean();
    private final java.util.concurrent.atomic.AtomicReference<Boolean> previousFrozen=new java.util.concurrent.atomic.AtomicReference<>();
    private boolean started,closed;private java.util.concurrent.CompletableFuture<Void> restoring;
    private SceneLease(IntegratedServer server){this.server=server;}
    /** No simulation mutation until the caller has stored this exact owner. */
    public static SceneLease prepare(){
        var client=Minecraft.getInstance();var server=client==null?null:client.getSingleplayerServer();
        if(server==null||server.isPublished()||server.getPlayerList().getPlayerCount()!=1)
            throw new IllegalStateException("Paired fixed-scene mode requires a private singleplayer world; manual timing is available here");
        return new SceneLease(server);
    }
    public static SceneLease acquire(){
        var lease=prepare();try{lease.freeze();return lease;}catch(Throwable failure){
            try{lease.close();}catch(Throwable recovery){PairedMeasurement.append(failure,recovery);}
            PairedMeasurement.throwUnchecked(failure);throw new AssertionError("unreachable");
        }
    }
    public void freeze(){
        if(started||closed)throw new IllegalStateException("Scene lease already started");started=true;
        try{
            server.submit(()->{
                boolean frozen=server.tickRateManager().isFrozen();previousFrozen.set(frozen);
                if(!cancelled.get())server.tickRateManager().setFrozen(true);
                if(cancelled.get())server.tickRateManager().setFrozen(frozen);
                return frozen;
            }).get(2,TimeUnit.SECONDS);
        }catch(Throwable failure){
            cancelled.set(true);if(failure instanceof InterruptedException)Thread.currentThread().interrupt();
            Throwable primary=unwrap(failure);if(primary instanceof Error)PairedMeasurement.throwUnchecked(primary);
            throw new IllegalStateException("Could not freeze local simulation; retained owner requires restoration",primary);
        }
    }
    private static Throwable unwrap(Throwable failure){return failure instanceof java.util.concurrent.ExecutionException&&failure.getCause()!=null?failure.getCause():failure;}
    public boolean restoring(){return restoring!=null&&!closed;}
    public boolean restoreReady(){return restoring!=null&&restoring.isDone();}
    public boolean valid(){return started&&!closed&&!cancelled.get()&&restoring==null&&previousFrozen.get()!=null&&Minecraft.getInstance().getSingleplayerServer()==server&&!server.isPublished()&&server.getPlayerList().getPlayerCount()==1&&server.tickRateManager().isFrozen();}
    public String provenance(){return "private integrated server ticks frozen; priorFrozen="+previousFrozen.get()+"; identity="+System.identityHashCode(server);}
    @Override public void close(){
        if(closed)return;cancelled.set(true);
        if(restoring!=null&&restoring.isCompletedExceptionally())restoring=null;
        try{
            if(restoring==null)restoring=server.submit(()->{var prior=previousFrozen.get();if(prior!=null)server.tickRateManager().setFrozen(prior);});
            restoring.get(2,TimeUnit.SECONDS);closed=true;
        }catch(Throwable failure){
            if(failure instanceof InterruptedException)Thread.currentThread().interrupt();
            Throwable primary=unwrap(failure);if(primary instanceof Error)PairedMeasurement.throwUnchecked(primary);
            throw new IllegalStateException("Could not restore prior local simulation freeze state; owner retained",primary);
        }
    }
}
