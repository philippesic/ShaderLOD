package com.pp.shaderlod.optimization.makeupprime;

import net.caffeinemc.mods.sodium.client.gl.device.MultiDrawBatch;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;
import static org.junit.jupiter.api.Assertions.*;

class C01DeferredBatchTest {
 @Test void snapshotsCommandsAndPartitionsBeforeNativeBatchReuse(){
  var source=new MultiDrawBatch(8);
  try(var saved=new C01DeferredBatch()){
   source.size=4;
   for(int i=0;i<4;i++){
    MemoryUtil.memPutAddress(source.pElementPointer+8L*i,16L*i);
    MemoryUtil.memPutInt(source.pElementCount+4L*i,6*(i+1));
    MemoryUtil.memPutInt(source.pBaseVertex+4L*i,40*i);
   }
   int[] from={0,2},to={2,4};boolean[] valid={true,false};
   C01Bounds.Replay callback=groups->{};
   saved.capture(source,2,12,8,from,to,valid,callback);
   source.clear();MemoryUtil.memSet(source.pElementCount,0,32);from[1]=0;to[0]=0;valid[0]=false;
   assertEquals(4,saved.batch.size);assertEquals(2,saved.groups);assertEquals(12,saved.firstSlot);assertEquals(8,saved.region);
   assertSame(callback,saved.replay);assertEquals(2,saved.starts[1]);assertEquals(2,saved.ends[0]);assertTrue(saved.eligible[0]);assertFalse(saved.eligible[1]);
   for(int i=0;i<4;i++){
    assertEquals(16L*i,MemoryUtil.memGetAddress(saved.batch.pElementPointer+8L*i));
    assertEquals(6*(i+1),MemoryUtil.memGetInt(saved.batch.pElementCount+4L*i));
    assertEquals(40*i,MemoryUtil.memGetInt(saved.batch.pBaseVertex+4L*i));
   }
   saved.release();assertNull(saved.replay);
  }finally{source.delete();}
 }
 @Test void refusesPartialOrOverlappingPartitionsAndPreservesUngroupedFallback(){
  var source=new MultiDrawBatch(8);
  try(var saved=new C01DeferredBatch()){
   source.size=4;C01Bounds.Replay callback=groups->{};
   assertThrows(IllegalArgumentException.class,()->saved.capture(source,2,0,1,new int[]{0,1},new int[]{2,4},new boolean[2],callback));
   assertThrows(IllegalArgumentException.class,()->saved.capture(source,1,0,1,new int[]{0},new int[]{3},new boolean[1],callback));
   assertThrows(IllegalArgumentException.class,()->saved.capture(source,0,0,1,new int[0],new int[0],new boolean[0],null));
   saved.capture(source,0,0,1,new int[0],new int[0],new boolean[0],callback);
   assertEquals(4,saved.batch.size);assertEquals(0,saved.groups);
  }finally{source.delete();}
 }
}
