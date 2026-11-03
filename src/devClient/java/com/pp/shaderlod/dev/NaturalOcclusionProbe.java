package com.pp.shaderlod.dev;
import com.google.gson.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
/** Read existing loaded natural terrain only; never constructs blocks or chunks. */
final class NaturalOcclusionProbe {
 private NaturalOcclusionProbe(){}
 record Site(double x,double y,double z,int roof,int surrounding){}
 private static Object serverIdentity;
 private static List<Site> sites=List.of();
 static JsonObject scan()throws Exception {
  var c=Minecraft.getInstance();var server=c.getSingleplayerServer();
  if(!Boolean.getBoolean("shaderlod.dev.c01Bounds")||server==null||server.isPublished()||c.player==null)throw new IllegalStateException("Explicit private natural C01 diagnostic required");
  int centerX=(int)Math.floor(c.player.getX()),centerZ=(int)Math.floor(c.player.getZ());
  var found=server.submit(()->{
   var level=server.overworld();var result=new ArrayList<Site>();
   for(int x=centerX-128;x<=centerX+128;x+=8)for(int z=centerZ-128;z<=centerZ+128;z+=8){
    var chunk=level.getChunkSource().getChunkNow(x>>4,z>>4);if(chunk==null)continue;
    int top=chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x&15,z&15);
    for(int y=Math.max(level.getMinBuildHeight()+4,top-64);y<=top-4;y++){
     var p=new BlockPos(x,y,z);
     if(!chunk.getBlockState(p).isAir()||!chunk.getBlockState(p.above()).isAir()||!chunk.getBlockState(p.above(2)).isAir()||!chunk.getBlockState(p.below()).canOcclude())continue;
     int roof=0;for(int dy=3;dy<=10;dy++)if(chunk.getBlockState(p.above(dy)).canOcclude()){roof=dy;break;}
     if(roof==0)continue;
     int surrounding=0;for(int dx:new int[]{-3,0,3})for(int dz:new int[]{-3,0,3}){
      var neighbor=level.getChunkSource().getChunkNow((x+dx)>>4,(z+dz)>>4);
      if(neighbor!=null&&neighbor.getBlockState(p.offset(dx,1,dz)).canOcclude())surrounding++;
     }
     result.add(new Site(x+.5,y,z+.5,roof,surrounding));
    }
   }
   result.sort(Comparator.comparingInt(Site::surrounding).reversed().thenComparingInt(Site::roof));
   var selected=new ArrayList<Site>();for(var site:result){if(selected.stream().anyMatch(old->Math.abs(old.x-site.x)<24&&Math.abs(old.z-site.z)<24))continue;selected.add(site);if(selected.size()==16)break;}
   return selected;
  }).get(4,TimeUnit.SECONDS);
  serverIdentity=server;sites=List.copyOf(found);var out=new JsonObject();out.addProperty("scope","Existing loaded chunks within128blocks,8blockgrid; natural air3high with solidfloor androof3..10. No new chunks or blocks; bounded opportunity search, not exhaustive terrain census.");out.add("sites",new Gson().toJsonTree(sites));return out;
 }
 static Site site(int index){if(Minecraft.getInstance().getSingleplayerServer()!=serverIdentity||index<0||index>=sites.size())throw new IllegalStateException("Current census site required");return sites.get(index);}
}
