package com.pp.shaderlod.dev;
import java.util.*;

public final class ObservedPackReloadTest {
    static void check(boolean value){if(!value)throw new AssertionError();}
    public static void main(String[] args)throws Exception {
        check(!ObservedPackReload.permitsSelection(false,false,true,"minecraft:overworld"));
        check(!ObservedPackReload.permitsSelection(false,true,false,"minecraft:overworld"));
        check(ObservedPackReload.permitsSelection(false,true,true,"minecraft:overworld"));
        check(ObservedPackReload.permitsSelection(false,true,true,"minecraft:the_nether"));
        check(!ObservedPackReload.permitsSelection(false,true,true,"minecraft:the_end"));
        check(!ObservedPackReload.permitsSelection(false,true,true,null));
        check(ObservedPackReload.permitsSelection(true,false,true,"minecraft:overworld"));
        check(!ObservedPackReload.permitsSelection(true,false,true,"minecraft:the_nether"));
        var events=new ArrayList<String>();
        ObservedPackReload.run(()->events.add("arm"),()->events.add("reload"),()->events.add("disarm"),()->events.add("rollback"));
        check(events.equals(List.of("arm","reload")));
        for(boolean rollbackFails:List.of(false,true)){
            events.clear();
            try {
                ObservedPackReload.run(()->events.add("arm"),()->{events.add("failed-reload");throw new Exception("original");},
                    ()->events.add("disarm"),()->{events.add("rollback");if(rollbackFails)throw new Exception("rollback");});
                throw new AssertionError("Failure must propagate");
            }catch(Exception e){check(e.getMessage().equals("original"));check(e.getSuppressed().length==(rollbackFails?1:0));}
            check(events.equals(List.of("arm","failed-reload","disarm","rollback")));
        }
        events.clear();
        try {ObservedPackReload.run(()->{throw new Exception("arm");},()->events.add("reload"),()->events.add("disarm"),()->events.add("rollback"));throw new AssertionError();}
        catch(Exception e){check(e.getMessage().equals("arm"));check(events.isEmpty());}
        System.out.println("Observed reload success, rejection, rollback and rollback-failure checks passed");
    }
}
