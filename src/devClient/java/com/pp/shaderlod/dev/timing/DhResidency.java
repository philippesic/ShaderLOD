package com.pp.shaderlod.dev.timing;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.irisshaders.iris.shadows.ShadowRenderer;

/** Bounded, once-per-second render-list residency census. No GL getters or pixel claims. */
public final class DhResidency {
    private static long at;
    private static Object level;
    private static JsonObject evidence=new JsonObject();
    public static void observe(Object handler){
        if(!CostMapSession.active()||ShadowRenderer.ACTIVE)return;
        var c=Minecraft.getInstance();long now=System.nanoTime();if(c.level==level&&now-at<1_000_000_000L)return;
        level=c.level;at=now;var out=new JsonObject();out.addProperty("at_ns",now);
        try{
            var pos=Class.forName("com.seibel.distanthorizons.core.pos.DhSectionPos");
            var camera=c.gameRenderer.getMainCamera().getPosition();int n=0,resident=0,far=0;
            Object buffers=handler.getClass().getMethod("getColumnRenderBuffers").invoke(handler);
            int size=(Integer)buffers.getClass().getMethod("size").invoke(buffers);
            for(int index=0;index<size;index++){
                Object buffer=buffers.getClass().getMethod("get",int.class).invoke(buffers,index);
                if(++n>65536)throw new IllegalStateException("residency census cap");
                Class<?> cls=buffer.getClass();
                if(!cls.getField("buffersUploaded").getBoolean(buffer)||!(Boolean)cls.getMethod("hasNonNullVbos").invoke(buffer))continue;
                resident++;long p=cls.getField("pos").getLong(buffer);
                int x=(Integer)pos.getMethod("getMinCornerBlockX",long.class).invoke(null,p),z=(Integer)pos.getMethod("getMinCornerBlockZ",long.class).invoke(null,p),w=(Integer)pos.getMethod("getBlockWidth",long.class).invoke(null,p);
                if(DhResidencyBounds.beyond(x,z,w,camera.x,camera.z,c.options.renderDistance().get()))far++;
            }
            out.addProperty("camera",camera.toString());out.addProperty("vanilla_chunks",c.options.renderDistance().get());out.addProperty("dimension",c.level.dimension().location().toString());out.addProperty("status","observed");out.addProperty("listed",n);out.addProperty("uploaded_containers",resident);out.addProperty("beyond_vanilla_uploaded",far);
            out.addProperty("meaning","uploaded non-null VBO containers in world render list, wholly beyond vanilla square; residency not pixel visibility or complete horizon coverage");
        }catch(Exception e){out.addProperty("status","unknown");out.addProperty("reason",e.toString());}
        evidence=out;
    }
    public static JsonObject snapshot(){return level==Minecraft.getInstance().level?evidence.deepCopy():new JsonObject();}
    public static boolean ready(){var e=snapshot();return System.nanoTime()-at<2_000_000_000L&&e.has("beyond_vanilla_uploaded")&&e.get("beyond_vanilla_uploaded").getAsInt()>0;}
}
