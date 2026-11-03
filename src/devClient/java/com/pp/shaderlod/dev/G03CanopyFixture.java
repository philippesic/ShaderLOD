package com.pp.shaderlod.dev;

import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Bounded CLIENT-ONLY canopy overlay. Never writes blocks on the saved server. */
final class G03CanopyFixture {
 private G03CanopyFixture() {}
 private record Entry(BlockPos pos,BlockState original) {}
 private static ClientLevel world;
 private static List<Entry> entries=List.of();
 private static final BlockState LEAVES=Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT,true).setValue(LeavesBlock.DISTANCE,1);
 private static void allowed() {
  var c=Minecraft.getInstance();var s=c.getSingleplayerServer();
  if(!Boolean.getBoolean("shaderlod.dev.g03CanopyFixture")||c.level==null||c.player==null||s==null||s.isPublished()
    ||c.level.dimension()!=net.minecraft.world.level.Level.OVERWORLD
    ||!s.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath().normalize().equals(c.gameDirectory.toPath().resolve("saves/ShaderLOD-Benchmark-overworld-3-flat").toAbsolutePath().normalize()))
   throw new IllegalStateException("Explicit owned saved-authoring client-only fixture required");
 }
 private static void serverOriginal(List<Entry> list)throws Exception {
  var server=Minecraft.getInstance().getSingleplayerServer();
  boolean same=server.submit(()->{
   var level=server.overworld();
   for(var e:list) {
    var chunk=level.getChunkSource().getChunkNow(e.pos.getX()>>4,e.pos.getZ()>>4);
    if(chunk==null||chunk.getBlockState(e.pos)!=e.original)return false;
   }
   return true;
  }).get(3,TimeUnit.SECONDS);
  if(!same)throw new IllegalStateException("Server original-block authority changed; no server repair permitted");
 }
 static JsonObject install()throws Exception {
  allowed();if(world!=null)throw new IllegalStateException("Canopy already owned");
  var level=Minecraft.getInstance().level;var list=new ArrayList<Entry>();
  for(int z=12;z<=36;z+=3)for(int x=-52;x<=-20;x++)for(int y=66;y<=81;y++) {
   var pos=new BlockPos(x,y,z);if(!level.hasChunkAt(pos))throw new IllegalStateException("Existing loaded client chunk required");
   var old=level.getBlockState(pos);if(old.isAir())list.add(new Entry(pos,old));
  }
  if(list.size()<4000||list.size()>5000)throw new IllegalStateException("Bounded air-only canopy opportunity differs");
  serverOriginal(list);world=level;entries=List.copyOf(list);
  try {
   for(var e:entries) {
    if(level.getBlockState(e.pos)!=e.original||!level.setBlock(e.pos,LEAVES,18))throw new IllegalStateException("Client overlay installation changed");
   }
   levelRendererChanged();return verify();
  }catch(Exception failure) {
   // Only the newly owned client overlay can be rolled back, never server data.
   for(var e:entries)if(level.getBlockState(e.pos)==LEAVES)level.setBlock(e.pos,e.original,18);
   entries=List.of();world=null;levelRendererChanged();throw failure;
  }
 }
 static JsonObject verify()throws Exception {
  allowed();if(world==null||world!=Minecraft.getInstance().level)throw new IllegalStateException("Current owned client canopy required");
  serverOriginal(entries);
  for(var e:entries)if(world.getBlockState(e.pos)!=LEAVES)throw new IllegalStateException("Canopy changed; no qualified workload");
  var digest=MessageDigest.getInstance("SHA-256");
  for(var e:entries)digest.update((e.pos.asLong()+":"+e.original+"\n").getBytes(StandardCharsets.UTF_8));
  var r=new JsonObject();r.addProperty("client_only",true);r.addProperty("server_original_blocks_verified",true);
  r.addProperty("installed_air_only_blocks",entries.size());r.addProperty("original_journal_sha256",HexFormat.of().formatHex(digest.digest()));
  r.addProperty("scope","Nine separated ordinary oak-leaf planes in existing loaded chunks. Bounded overdraw stress opportunity, not a representative forest claim. Server blocks remain original.");return r;
 }
 static JsonObject restore()throws Exception {
  if(world==null){var r=new JsonObject();r.addProperty("already_absent",true);return r;}
  var r=verify();for(var e:entries)if(!world.setBlock(e.pos,e.original,18))throw new IllegalStateException("Client overlay restoration failed");
  for(var e:entries)if(world.getBlockState(e.pos)!=e.original)throw new IllegalStateException("Client original state not restored");
  serverOriginal(entries);entries=List.of();world=null;levelRendererChanged();r.addProperty("client_original_blocks_restored",true);return r;
 }
 private static void levelRendererChanged(){Minecraft.getInstance().levelRenderer.allChanged();}
}
