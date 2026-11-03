package com.pp.shaderlod.optimization.sourcecache;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.shaderpack.ShaderPack;
import java.util.*;
/** Exact existing renderer certificates, evaluated only for observed inputs in the pipeline build. */
public final class LoadedRendererSources {
 private LoadedRendererSources(){}
 public static boolean allows(String route){return IrisSourceLifecycle.proof(List.of(LoadedRendererSources.class,route),()->false);}
 public static void qualifyLoadedSource(ShaderPack pack){
  var options=EffectiveOptions.current(pack.getShaderPackOptions());
  String name=Iris.getIrisConfig().getShaderPackName().orElse(null);if(name==null)return;
  var archive=Iris.getShaderpacksDirectory().resolve(name);
  IrisSourceLifecycle.proof(List.of(LoadedRendererSources.class,"MAKEUP"),()->com.pp.shaderlod.integration.makeupquad.MakeUpQuadEligibility.validate(archive,options).eligible());
  IrisSourceLifecycle.proof(List.of(LoadedRendererSources.class,"UNBOUND"),()->com.pp.shaderlod.integration.unboundquad.UnboundQuadEligibility.validate(archive,options).eligible());
  IrisSourceLifecycle.proof(List.of(LoadedRendererSources.class,"REIMAGINED"),()->com.pp.shaderlod.integration.reimaginedquad.ReimaginedQuadEligibility.validate(archive,options).eligible());
  IrisSourceLifecycle.proof(List.of(LoadedRendererSources.class,"SILDUR"),()->com.pp.shaderlod.integration.sildurquad.SildurQuadEligibility.validate(archive,options).eligible());
  IrisSourceLifecycle.proof(List.of(LoadedRendererSources.class,"BLISS"),()->com.pp.shaderlod.integration.blissquad.BlissQuadEligibility.validate(archive,options).eligible());
  IrisSourceLifecycle.proof(List.of(LoadedRendererSources.class,"BSL"),()->com.pp.shaderlod.integration.bslquad.BslQuadEligibility.validate(archive,options).eligible());
  IrisSourceLifecycle.proof(List.of(LoadedRendererSources.class,"PHOTON"),()->com.pp.shaderlod.integration.photonquad.PhotonQuadEligibility.validate(archive,options).eligible());
  IrisSourceLifecycle.proof(List.of(LoadedRendererSources.class,"ASTRA"),()->com.pp.shaderlod.integration.astraquad.AstraQuadEligibility.validate(archive,options).eligible());
  IrisSourceLifecycle.proof(List.of(LoadedRendererSources.class,"SOLAS"),()->com.pp.shaderlod.integration.solasquad.SolasQuadEligibility.validate(archive,options).eligible());

  IrisSourceLifecycle.proof(List.of(LoadedRendererSources.class,"GENERATED"),()->com.pp.shaderlod.integration.makeupquad.GeneratedRendererEligibility.validate(archive,options));
 }
}
