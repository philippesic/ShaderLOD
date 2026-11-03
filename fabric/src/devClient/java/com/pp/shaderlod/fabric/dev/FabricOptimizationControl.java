package com.pp.shaderlod.fabric.dev;
import com.google.gson.JsonParser;
import java.nio.file.*;
import java.util.Arrays;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.slf4j.LoggerFactory;
/** Explicit dev main-thread bridge to existing normal APIs, no scene or timing implementation. */
final class FabricOptimizationControl {
    private byte[] consumed;
    static boolean requested(){return Boolean.getBoolean("shaderlod.dev.fabricControl");}
    static void register(){ClientTickEvents.END_CLIENT_TICK.register(new FabricOptimizationControl()::tick);}
    void tick(Minecraft client){
        if(client.level==null||client.player==null)return;
        Path file=client.gameDirectory.toPath().toAbsolutePath().normalize().getParent().resolve("control.json");
        try {poll(file,FabricOptimizationControl::requireInspection,null);}
        catch(Exception failure){LoggerFactory.getLogger("shaderlod-dev").warn("Fabric control rejected: {}",failure.toString());}
    }
    Path poll(Path file,FabricOptimizationMeasurementControl.Inspection inspection,FabricOptimizationMeasurementControl.Action action)throws Exception{
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))return null;
        byte[] bytes;try(var in=Files.newInputStream(file)){bytes=in.readNBytes(65537);}
        if(Arrays.equals(bytes,consumed))return null;
        consumed=bytes; // Never replay unchanged content, even a rejection. Receipt owns valid ID permanently.
        if(bytes.length>65536)throw new IllegalArgumentException("control.json exceeds64KiB");
        var control=JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        return action==null?FabricOptimizationMeasurementControl.consume(control,file,inspection):FabricOptimizationMeasurementControl.consume(control,file,inspection,action);
    }
    static void requireInspection(){
        if(!FabricLoader.getInstance().isModLoaded("shaderlod")||!requested()
                ||Boolean.getBoolean("shaderlod.dev.fabricRoundtrip")||Boolean.getBoolean("shaderlod.dev.fabricRendererSpeedProbe")
                ||Boolean.getBoolean("shaderlod.dev.fabricRendererSourceProbe")||Boolean.getBoolean("shaderlod.dev.fabricOriginalSourceProbe")
                ||Boolean.getBoolean("shaderlod.dev.benchmark")||Boolean.getBoolean("shaderlod.dev.measureWorkload")
                ||Boolean.getBoolean("shaderlod.dev.benchmarkOnly")||Boolean.getBoolean("shaderlod.dev.measureOnly")
                ||Boolean.parseBoolean(System.getProperty("shaderlod.dev.gpuTiming","true"))
                ||Boolean.parseBoolean(System.getProperty("shaderlod.dev.cpuTiming","true")))
            throw new IllegalArgumentException("Fabric control requires isolated untimed dev inspection; normal RuntimeMeasurement remains opt-in");
    }
}
