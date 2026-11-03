package com.pp.shaderlod.dev;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowyDirtBlock;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.storage.LevelResource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** One bounded G04 fixture inside the standard caller's backed-up managed save.
 * The standard caller restores the entire save after shutdown, including failure.
 */
final class StandardFeatureScene {
    private static final BlockPos POSITION = new BlockPos(0,64,-5);
    private boolean attempted;
    private Object world;
    private static void require(boolean ok,String reason) {
        if(!ok)throw new IllegalStateException(reason);
    }
    JsonObject prepare(Path report,JsonObject state)throws Exception {
        require(!attempted,"Standard feature fixture already attempted; restore before retry");
        var client=Minecraft.getInstance();var server=client.getSingleplayerServer();
        Path game=client.gameDirectory.toPath().toAbsolutePath().normalize();
        require(server!=null&&!server.isPublished()&&client.level!=null
            &&client.level.dimension()==net.minecraft.world.level.Level.OVERWORLD,"Private Overworld required");
        require(game.getParent().getParent().getFileName().toString().equals("world-pool")
            &&game.getFileName().toString().equals("client")&&game.toRealPath().equals(game),"Managed client required");
        Path save=game.resolve("saves/ShaderLOD-Benchmark-overworld-3-flat");
        require(server.getWorldPath(LevelResource.ROOT).toRealPath().equals(save),"Exact prepared save required");
        var operation=JsonParser.parseString(Files.readString(report.resolve("operation.json"))).getAsJsonObject();
        require(operation.get("profile").getAsString().equals(game.toString())
            &&operation.get("state").getAsString().equals("running"),"Active standard operation required");
        var record=operation.getAsJsonObject("iteration_profile");
        Path backup=report.resolve("world-before");
        require(record!=null&&record.get("world_backup").getAsString().equals(backup.toString())
            &&backup.toRealPath().equals(backup)&&!backup.startsWith(game)
            &&Files.isRegularFile(backup.resolve("ShaderLOD-Benchmark-overworld-3-flat/level.dat"))
            &&record.getAsJsonObject("original").has("saves"),"Independent standard whole-save backup required");
        var installed=state.getAsJsonObject("installed");
        require(!installed.get("master").getAsBoolean()
            &&installed.get("family").getAsString().equals("makeup-ultra-fast-shaders")
            &&installed.getAsJsonObject("flags").entrySet().stream().noneMatch(e->e.getValue().getAsBoolean()),"Native Off/empty MakeUp required");
        var options=new java.util.TreeMap<String,String>();
        state.getAsJsonObject("effective_options").entrySet().forEach(e->options.put(e.getKey(),e.getValue().getAsString()));
        // The main mod owns this resource; the dev mod's module cannot open it.
        require(com.pp.shaderlod.integration.makeupquad.MakeUpQuadEligibility.optionsMatch(options),"Pinned native100/AA required");
        require(client.player!=null&&client.player.getX()==.5&&client.player.getY()==65
            &&client.player.getZ()==-6.5&&client.player.getYRot()==0&&client.player.getXRot()==65,"Prepared close fixture pose required");
        attempted=true;world=client.level;
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
        var future=server.submit(()->{
            require(System.nanoTime()<deadline&&client.level==world&&client.getSingleplayerServer()==server
                &&server.tickRateManager().isFrozen(),"Fixture world changed or completion deadline expired");
            var level=server.overworld();
            require(level.getChunkSource().getChunk(0,-1,ChunkStatus.FULL,false)!=null,"Existing loaded chunk required");
            require(level.getBlockState(POSITION).equals(Blocks.GRASS_BLOCK.defaultBlockState().setValue(SnowyDirtBlock.SNOWY,false))
                &&level.getBlockState(POSITION.above()).isAir(),"Fixture grass/air preimage differs; preserve it");
            require(level.setBlock(POSITION,Blocks.HONEY_BLOCK.defaultBlockState(),3)
                &&level.getBlockState(POSITION).is(Blocks.HONEY_BLOCK),"Honey placement failed");
            var result=new JsonObject();result.addProperty("block","minecraft:honey_block");
            result.addProperty("position","0 64 -5");result.addProperty("server_completed",true);
            result.addProperty("restoration","Standard whole-save restoration required after shutdown");return result;
        });
        try{return future.get(2,TimeUnit.SECONDS);}
        catch(Exception failure){future.cancel(false);throw new IllegalStateException("Fixture completion uncertain; restore entire save before retry",failure);}
    }
    boolean ready() {
        var client=Minecraft.getInstance();
        return attempted&&client.level==world&&client.level!=null&&client.level.getBlockState(POSITION).is(Blocks.HONEY_BLOCK);
    }
}
