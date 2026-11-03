package com.pp.shaderlod.dev;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import java.security.MessageDigest;
import net.irisshaders.iris.Iris;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;

/** Explicitly untimed proof of the actual consuming native cloud and bound atlas. */
final class G08BoundSamplerProof {
 private G08BoundSamplerProof() {}
 static JsonObject marker(String digest)throws Exception{
  String name=Iris.getCurrentPackName();
  if(!name.matches("[A-Za-z0-9_.-]+\\.zip")||digest==null||!digest.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Pinned private cloud archive required");
  Path file=Iris.getShaderpacksDirectory().resolve(name);
  if(Files.isSymbolicLink(file)||!LoadedArtifactEvidence.fileSha256(file).equals(digest))throw new IllegalStateException("Cloud archive changed");
  JsonObject marker;
  try(var z=new ZipFile(file.toFile())){var e=z.getEntry("SHADERLOD_G08_NATIVE100_BOUND.json");if(e==null||e.getSize()<0||e.getSize()>16384)throw new IllegalStateException("Bounded cloud marker absent");marker=JsonParser.parseString(new String(z.getInputStream(e).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();}
  if(!marker.get("native100").getAsBoolean()||marker.get("tail_steps").getAsInt()!=(marker.get("layout").getAsString().equals("alu")?0:4)||!marker.get("native_queries").getAsBoolean()||!marker.get("original_sha256").getAsString().equals("0036da17e0c7158e1c669593e6322d53fce3f5a42fb5124b935e9e21a5719db8"))throw new IllegalStateException("Unknown cloud law");
  marker.addProperty("actual_archive_sha256",digest);marker.addProperty("actual_pack",name);return marker;
 }
 private static int parameter(int sampler,int name){return sampler==0?GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,name):GL33.glGetSamplerParameteri(sampler,name);}
 private static float parameterf(int sampler,int name){return sampler==0?GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D,name):GL33.glGetSamplerParameterf(sampler,name);}
 static JsonObject capture(int program,JsonObject marker)throws Exception{
  if(!G08BoundProbe.observing()||marker==null||GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)!=program)throw new IllegalStateException("Actual armed cloud draw required");
  int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),pbo=GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
  int[] keys={GL11.GL_PACK_ALIGNMENT,GL11.GL_PACK_ROW_LENGTH,GL11.GL_PACK_SKIP_ROWS,GL11.GL_PACK_SKIP_PIXELS,GL11.GL_PACK_SWAP_BYTES,GL11.GL_PACK_LSB_FIRST,GL12.GL_PACK_IMAGE_HEIGHT,GL12.GL_PACK_SKIP_IMAGES};
  int[] prior=Arrays.stream(keys).map(GL11::glGetInteger).toArray();var images=new JsonArray();
  try{
   GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,0);for(int key:keys)GL11.glPixelStorei(key,key==GL11.GL_PACK_ALIGNMENT?1:0);
   for(String name:marker.get("layout").getAsString().equals("alu")?List.of("gaux2"):List.of("gaux2","noisetex")){
    String actualName=name;int location=GL20.glGetUniformLocation(program,name);if(location<0&&name.equals("gaux2")){actualName="colortex5";location=GL20.glGetUniformLocation(program,actualName);}if(location<0)throw new IllegalStateException("Actual cloud sampler absent: "+name);
    int unit=GL20.glGetUniformi(program,location);if(unit<0||unit>=GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS))throw new IllegalStateException("Cloud unit invalid");
    GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);int texture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D),sampler=GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
    if(texture<=0||!GL11.glIsTexture(texture))throw new IllegalStateException("Actual cloud image absent");
    int extent=name.equals("gaux2")?512:256,filter=name.equals("gaux2")?GL11.GL_LINEAR:GL11.GL_NEAREST,wrap=name.equals("gaux2")?GL11.GL_REPEAT:GL12.GL_CLAMP_TO_EDGE;
    int w=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH),h=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT),format=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_INTERNAL_FORMAT);
    int redBits=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_RED_SIZE),redType=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL30.GL_TEXTURE_RED_TYPE);
    float aniso=(GL.getCapabilities().GL_EXT_texture_filter_anisotropic||GL.getCapabilities().GL_ARB_texture_filter_anisotropic)?parameterf(sampler,0x84FE):1f;
    if(w!=extent||h!=extent||redBits!=8||redType!=GL30.GL_UNSIGNED_NORMALIZED||GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL33.GL_TEXTURE_SWIZZLE_R)!=GL11.GL_RED||GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL12.GL_TEXTURE_BASE_LEVEL)!=0||parameter(sampler,GL11.GL_TEXTURE_MIN_FILTER)!=filter||parameter(sampler,GL11.GL_TEXTURE_MAG_FILTER)!=filter||parameter(sampler,GL11.GL_TEXTURE_WRAP_S)!=wrap||parameter(sampler,GL11.GL_TEXTURE_WRAP_T)!=wrap||parameter(sampler,GL14.GL_TEXTURE_COMPARE_MODE)!=GL11.GL_NONE||aniso!=1f)throw new IllegalStateException("Native cloud/atlas sampling law differs: "+name);
    if(name.equals("noisetex")&&(GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_GREEN_SIZE)!=8||GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_BLUE_SIZE)!=8||GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_ALPHA_SIZE)!=8||GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL33.GL_TEXTURE_SWIZZLE_G)!=GL11.GL_GREEN||GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL33.GL_TEXTURE_SWIZZLE_B)!=GL11.GL_BLUE||GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL33.GL_TEXTURE_SWIZZLE_A)!=GL11.GL_ALPHA))throw new IllegalStateException("Atlas channels differ");
    var pixels=MemoryUtil.memAlloc(w*h*4);String hash;
    try{
     GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
     byte[] data=new byte[name.equals("gaux2")?w*h:w*h*4];if(name.equals("gaux2")){for(int i=0;i<data.length;i++)data[i]=pixels.get(4*i);}else pixels.get(data);
     hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }finally{MemoryUtil.memFree(pixels);}
    if(!hash.equals(marker.get(name.equals("gaux2")?"cloud_red_sha256":"atlas_rgba_sha256").getAsString()))throw new IllegalStateException("Actual uploaded cloud/atlas bytes differ: "+name);
    var row=new JsonObject();row.addProperty("sampler_name",name);row.addProperty("actual_uniform",actualName);row.addProperty("unit",unit);row.addProperty("texture",texture);row.addProperty("sampler",sampler);row.addProperty("width",w);row.addProperty("height",h);row.addProperty("internal_format",format);row.addProperty("red_bits",redBits);row.addProperty("red_type",redType);row.addProperty("min_filter",filter);row.addProperty("mag_filter",filter);row.addProperty("wrap",wrap);row.addProperty("anisotropy",aniso);row.addProperty("gpu_bytes_sha256",hash);images.add(row);
   }
  }finally{GL13.glActiveTexture(active);GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,pbo);for(int i=0;i<keys.length;i++)GL11.glPixelStorei(keys[i],prior[i]);}
  if(GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)!=active||GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING)!=pbo||GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)!=program)throw new IllegalStateException("Cloud sampler proof state restoration differs");for(int i=0;i<keys.length;i++)if(GL11.glGetInteger(keys[i])!=prior[i])throw new IllegalStateException("Cloud pack state differs");
  var result=new JsonObject();result.add("images",images);result.addProperty("state_restored",true);result.addProperty("scope","Actual completed deferred program's sampler uniforms/unit bindings/effective filtering and immutable full level0 GPU bytes. Native cloud linear-repeat nonmip sampling, nearest-clamp directional atlas. No rendering/economics inference.");return result;
 }
}
