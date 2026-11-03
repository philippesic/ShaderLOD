package com.pp.shaderlod.blissprefix;
import com.google.gson.*;
import java.util.function.IntSupplier;
/** One finite error sample per named checkpoint. Every consumed GLenum is recorded.
 * Stop at any nonzero code. Never drain, clear-and-continue, or qualify failed data. */
public final class BlissPrefixGlErrorReceipt {
 public static void checkpoint(JsonObject receipt,String phase,IntSupplier getError){
  int code=getError.getAsInt();var event=new JsonObject();event.addProperty("phase",phase);
  event.addProperty("glenum",code);event.addProperty("glenum_hex",String.format("0x%04X",code));
  event.addProperty("name",name(code));event.addProperty("single_error_sample",true);
  JsonArray events;if(receipt.has("GPU_GL_error_checkpoints"))events=receipt.getAsJsonArray("GPU_GL_error_checkpoints");
  else{events=new JsonArray();receipt.add("GPU_GL_error_checkpoints",events);}events.add(event);
  if(code!=0){if(!receipt.has("GPU_GL_failed_phase")){receipt.addProperty("GPU_GL_failed_phase",phase);receipt.addProperty("GPU_GL_failed_glenum",code);
   receipt.addProperty("GPU_GL_failed_glenum_hex",String.format("0x%04X",code));}receipt.addProperty("certificate_active",false);
   throw new IllegalStateException("Bliss prefix GL error phase="+phase+" GLenum="+String.format("0x%04X",code)+" "+name(code));}
 }
 private static String name(int code){return switch(code){case 0->"GL_NO_ERROR";case 0x0500->"GL_INVALID_ENUM";case 0x0501->"GL_INVALID_VALUE";case 0x0502->"GL_INVALID_OPERATION";case 0x0505->"GL_OUT_OF_MEMORY";case 0x0506->"GL_INVALID_FRAMEBUFFER_OPERATION";default->"UNKNOWN_GL_ENUM";};}
 private BlissPrefixGlErrorReceipt(){}
}
