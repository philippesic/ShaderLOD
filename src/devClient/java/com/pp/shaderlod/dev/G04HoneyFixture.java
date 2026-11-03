package com.pp.shaderlod.dev;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowyDirtBlock;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.storage.LevelResource;
import net.irisshaders.iris.Iris;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** Private single-block experiment only. No registration, configure hook, or command channel. */
final class G04HoneyFixture {
    private static final Path ROOT=Path.of("/home/philip/ShaderLOD");
    private static final Path GAME=ROOT.resolve("run-workloads/world-pool/neoforge-1211-asahi-authored/client");
    private static final Path SAVE=GAME.resolve("saves/ShaderLOD-Benchmark-overworld-3-flat");
    private static final Path MANIFEST=ROOT.resolve("dev/experiments/g04-honey-fixture/live-ownership.json");
    private static final BlockPos POSITION=new BlockPos(0,64,-5);
    private static boolean attempted;
    private G04HoneyFixture() {}
    private static void require(boolean ok,String reason){if(!ok)throw new IllegalStateException(reason);}
    static JsonObject place(JsonObject state,String pin)throws Exception {
        require(Boolean.getBoolean("shaderlod.dev.g04HoneyFixture"),"Explicit private G04 opt-in required");
        require(!attempted,"One attempt only; restore after any uncertain completion");
        require(Files.isRegularFile(MANIFEST)&&!Files.isSymbolicLink(MANIFEST),"Separately allocated ownership/restore manifest required");
        require(pin.matches("[a-f0-9]{64}")&&pin.equals(LoadedArtifactEvidence.fileSha256(MANIFEST)),"Frozen ownership manifest required");
        var m=JsonParser.parseString(Files.readString(MANIFEST)).getAsJsonObject();
        require(m.get("parent_accepted").getAsBoolean()&&m.get("separately_allocated").getAsBoolean()
                &&m.get("full_restore_roundtrip_passed").getAsBoolean(),"Separate live allocation and qualified restore required");
        require(m.get("client_pid").getAsLong()==ProcessHandle.current().pid()
                &&m.get("client_birth").getAsString().equals(ProcessHandle.current().info().startInstant().orElseThrow().toString()),"Owned client identity mismatch");
        require(System.currentTimeMillis()<m.get("deadline_ms").getAsLong(),"Allocation expired");
        var c=Minecraft.getInstance();var server=c.getSingleplayerServer();
        require(c.gameDirectory.toPath().toAbsolutePath().normalize().equals(GAME)&&GAME.toRealPath().equals(GAME)
                &&server!=null&&!server.isPublished()&&server.getWorldPath(LevelResource.ROOT).toRealPath().equals(SAVE),"Exact private managed save required");
        require(Files.readAttributes(GAME,java.nio.file.attribute.BasicFileAttributes.class).fileKey().toString().equals(m.get("game_file_key").getAsString())
                &&Files.readAttributes(SAVE,java.nio.file.attribute.BasicFileAttributes.class).fileKey().toString().equals(m.get("save_file_key").getAsString()),"Replaced profile/save; old inode manifest refused");
        Path backup=Path.of(m.get("backup").getAsString());
        require(backup.startsWith(MANIFEST.getParent())&&!backup.startsWith(GAME)&&backup.toRealPath().equals(backup)
                &&Files.isRegularFile(backup.resolve("level.dat")),"Independent full-save recovery required");
        require("Linux".equals(System.getProperty("os.name"))&&"aarch64".equals(System.getProperty("os.arch"))
                &&state.get("gpu_renderer").getAsString().equals("Apple M1 (G13G B1)")
                &&state.get("gpu_driver").getAsString().equals("4.6 (Core Profile) Mesa 25.1.0-asahi20250221"),"Exact Asahi tuple required");
        var stack=state.getAsJsonObject("loaded_stack");
        for(var e:java.util.Map.of("minecraft","1.21.1","neoforge","21.1.253","iris","1.8.12-snapshot+mc1.21.1-local","sodium","0.6.13+mc1.21.1").entrySet())
            require(stack.has(e.getKey())&&e.getValue().equals(stack.get(e.getKey()).getAsString()),"Exact native loaded tuple required");
        for(String key:java.util.List.of("shaderlod.dev.cameraQuality","shaderlod.dev.pixelHistory","shaderlod.dev.pixelHistoryCapture","shaderlod.dev.measureWorkload","shaderlod.dev.benchmark","shaderlod.dev.physicalResolution"))
            require(System.getProperty(key)==null,"No competing diagnostic, including blank: "+key);

        for(var e:m.getAsJsonObject("loaded_stack").entrySet())require(e.getValue().equals(stack.get(e.getKey())),"Loaded tuple mismatch: "+e.getKey());
        require(m.getAsJsonObject("loaded_stack").size()>=4,"Explicit MC/loader/Iris/Sodium tuple required");
        require(c.level!=null&&c.player!=null&&c.level.dimension()==Level.OVERWORLD&&c.getCameraEntity()==c.player
                &&c.levelRenderer.hasRenderedAllSections()&&c.screen==null&&!c.isPaused()&&!Iris.isFallback()
                &&Iris.getCurrentPack().isPresent()&&Iris.getPipelineManager().getPipelineNullable()!=null
                &&Iris.getShaderPackOptionQueue().isEmpty(),"Settled native Overworld required");
        require(!com.pp.shaderlod.ShaderLodConfig.ENABLED.get()
                &&com.pp.shaderlod.optimization.OptimizationConfig.snapshot().flags().values().stream().noneMatch(Boolean::booleanValue),"Core/adapters must be Off");
        require(!com.pp.shaderlod.dev.timing.CpuTimers.recording()&&!com.pp.shaderlod.dev.timing.GpuTimers.recording(),"Untimed operation required");
        require(c.player.getX()==.5&&c.player.getY()==65&&c.player.getZ()==-6.5&&c.player.getYRot()==0&&c.player.getXRot()==65,"Frozen authored pose9 required");
        require(m.get("native_pack_sha256").getAsString().equals(LoadedArtifactEvidence.fileSha256(Iris.getShaderpacksDirectory().resolve(Iris.getCurrentPackName()))),"Pinned native archive required");
        var opts=state.getAsJsonObject("effective_options");
        require(opts.equals(m.getAsJsonObject("native100"))&&opts.size()==100,"Exact native100/AA2 required");
        require(opts.get("AA_TYPE").getAsString().equals("2"),"Native AA2 required");
        attempted=true;
        long expires=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
        var result=server.submit(()->{
            require(System.nanoTime()<expires,"Server completion deadline expired before placement");
            var level=server.overworld();
            require(server.tickRateManager().isFrozen()&&c.getSingleplayerServer()==server,"Owned frozen server required");
            // getChunk(..., false) never generates a missing chunk.
            require(level.getChunkSource().getChunk(0,-1,ChunkStatus.FULL,false)!=null,"Existing loaded full chunk required");
            require(level.getBlockState(POSITION).equals(Blocks.GRASS_BLOCK.defaultBlockState().setValue(SnowyDirtBlock.SNOWY,false))
                    &&level.getBlockState(POSITION.above()).isAir(),"Exact grass[snowy=false]/air preimage required");
            require(level.setBlock(POSITION,Blocks.HONEY_BLOCK.defaultBlockState(),3),"Server rejected one honey block");
            require(level.getBlockState(POSITION).equals(Blocks.HONEY_BLOCK.defaultBlockState()),"Server postimage mismatch");
            var ack=new JsonObject();ack.addProperty("server_completed",true);ack.addProperty("block","minecraft:honey_block");ack.addProperty("position","0 64 -5");ack.addProperty("restore_required",true);return ack;
        });
        try{return result.get(2,TimeUnit.SECONDS);}
        catch(Exception failure){result.cancel(false);throw new IllegalStateException("Completion uncertain; stop owned client and restore entire save; never retry",failure);}
    }
}
