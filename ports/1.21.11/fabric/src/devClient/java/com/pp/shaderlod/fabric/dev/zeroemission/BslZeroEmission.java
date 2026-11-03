package com.pp.shaderlod.fabric.dev.zeroemission;

import com.pp.shaderlod.fabric.FabricLodConfig;
import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.integration.bslquad.BslQuadEligibility;
import com.pp.shaderlod.integration.makeupquad.*;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.program.ProgramUniforms;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.pipeline.programs.SodiumPrograms;
import net.minecraft.client.Minecraft;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Finite, pinned dev arithmetic prototype. No per-draw GL queries or sampler changes. */
public final class BslZeroEmission {
 public static final boolean REQUESTED=Boolean.getBoolean("shaderlod.dev.portBslZeroEmission");
 private static final String CONTROL="c521e68e25b120da34600507d9a2497cf758eb5d6098ec102a0e0759705d6881";
 private static final String CANDIDATE="7889f6f44c444c415ad212e985be21fbb3caef5b6720273a41eab91baee1ae81";
 private static Object certifiedPack,certifiedPipeline;
 private static String certifiedName;
 private static long certifiedGeneration=-1,checkedFrame=-1;
 private static boolean checkedEnabled;
 private BslZeroEmission(){}
 public static String expectedArchive(String arm){return switch(arm){
  case "stock" -> BslQuadEligibility.ARCHIVE_SHA;
  case "control" -> CONTROL;
  case "candidate" -> CANDIDATE;
  default -> throw new IllegalArgumentException("Unknown zero-emission arm: "+arm);
 };}
 public static void register(ProgramUniforms.Builder builder,SodiumPrograms.Pass pass){
  if(!REQUESTED)return;
  builder.uniform1b(UniformUpdateFrequency.PER_FRAME,"shaderlodBslZeroEmissionEnabled",BslZeroEmission::enabled);
  builder.uniform1i(UniformUpdateFrequency.PER_FRAME,"shaderlodBslZeroEmissionVersion",()->1);
  builder.uniform1i(UniformUpdateFrequency.PER_FRAME,"shaderlodBslZeroEmissionPreset",()->FabricLodConfig.snapshot().preset()==LodPreset.PERFORMANCE?1:0);
 }
 public static boolean enabled(){
  if(!REQUESTED||!BslZeroEmissionObserver.REQUESTED||!FabricLodConfig.loaded()||MakeUpQuadLifecycle.suspended()||Iris.isFallback())return false;
  var cfg=FabricLodConfig.snapshot();
  if(!cfg.enabled()||cfg.preset()!=LodPreset.PERFORMANCE||cfg.debug())return false;
  try{
   if(Long.parseLong(System.getProperty("shaderlod.dev.startupDeadlineEpochMillis","0"))<=System.currentTimeMillis())return false;
   var c=Minecraft.getInstance();
   if(c.level==null||c.level.dimension()!=net.minecraft.world.level.Level.OVERWORLD||c.getMainRenderTarget().width!=1920||c.getMainRenderTarget().height!=1200||c.getWindow().getWidth()!=1920||c.getWindow().getHeight()!=1200)return false;
   if(!Iris.getShaderPackOptionQueue().isEmpty()||Iris.getIrisConfig().areDebugOptionsEnabled()||!Iris.getCurrentPackName().equals(Iris.getIrisConfig().getShaderPackName().orElse("")))return false;
   if(Boolean.getBoolean(RendererRolloutPolicy.RESEARCH_ROUTES)||MakeUpQuadRuntime.enabled()||Boolean.getBoolean("shaderlod.rendererSpeedPortR1"))return false;
   for(String flag:List.of("portBslEarlyAlpha","portBslEarlyAlphaObserver","portBslOriginalSource","portBslAlphaAdmission","portBslGpuBudget","portSildurOriginalSource","portRoundtrip","portRendererSpeedProbe"))if(Boolean.getBoolean("shaderlod.dev."+flag))return false;
   var pack=Iris.getCurrentPack().orElseThrow();var pipeline=Iris.getPipelineManager().getPipelineNullable();var name=Iris.getCurrentPackName();long generation=MakeUpQuadLifecycle.generation();
   if(pipeline==null||pipeline.getClass()!=net.irisshaders.iris.pipeline.IrisRenderingPipeline.class)return false;
   if(pack!=certifiedPack||pipeline!=certifiedPipeline||generation!=certifiedGeneration||!name.equals(certifiedName)){
    certifiedPack=null;certifiedPipeline=null;checkedFrame=-1;
    if(!com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread()||!"OpenGL".equals(com.mojang.blaze3d.systems.RenderSystem.getDevice().getBackendName())||!org.lwjgl.opengl.GL.getCapabilities().OpenGL41)return false;
    if(((net.irisshaders.iris.pipeline.IrisRenderingPipeline)pipeline).getSodiumPrograms().getClass()!=SodiumPrograms.class)return false;
    var root=Iris.getShaderpacksDirectory().toAbsolutePath().normalize();var path=root.resolve(name).normalize();
    if(!path.startsWith(root)||path.equals(root)||Files.isSymbolicLink(path))return false;
    String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    if(!Set.of(CONTROL,CANDIDATE).contains(hash)||!MakeUpQuadApiPins.exactApi())return false;
    var mods=new TreeMap<String,String>();net.fabricmc.loader.api.FabricLoader.getInstance().getAllMods().forEach(m->{if(!m.getMetadata().getId().equals("java"))mods.put(m.getMetadata().getId(),m.getMetadata().getVersion().getFriendlyString());});
    if(!MakeUpQuadRuntime.stackMatches(mods)||!MakeUpQuadRuntime.resourcesMatch(c.getResourceManager().listPacks().map(net.minecraft.server.packs.PackResources::packId).toList()))return false;
    certifiedPack=pack;certifiedPipeline=pipeline;certifiedGeneration=generation;certifiedName=name;
   }
   // Certify mutable effective options once per observed render, never once per multidraw.
   long frame=BslZeroEmissionObserver.renderOrdinal();
   if(checkedFrame!=frame){
    var o=pack.getShaderPackOptions();var values=o.getOptionValues();var map=new TreeMap<String,String>();
    o.getOptionSet().getBooleanOptions().keySet().forEach(k->map.put(k,Boolean.toString(values.getBooleanValueOrDefault(k))));
    o.getOptionSet().getStringOptions().keySet().forEach(k->map.put(k,values.getStringValueOrDefault(k)));
    checkedEnabled=BslQuadEligibility.optionsMatch(map)&&MakeUpQuadRuntime.resourcesMatch(c.getResourceManager().listPacks().map(net.minecraft.server.packs.PackResources::packId).toList());checkedFrame=frame;
   }
   return checkedEnabled;
  }catch(Exception|LinkageError e){return false;}
 }
}
