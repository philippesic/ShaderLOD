package com.pp.shaderlod.optimization.reimaginedspatial;
import java.util.*;import java.security.*;
/** Only the extra normal-program/quad interfaces consumed by this private SOLID scope. */
public final class ReimaginedSpatialPins{private static final boolean EXACT=check();private ReimaginedSpatialPins(){}public static boolean exact(){return EXACT;}private static boolean check(){try{for(var e:Map.ofEntries(Map.entry("net/irisshaders/iris/shaderpack/loading/ProgramArrayId.class","335c41c14058b5a151638b434b01c2a697e87768c03fce1a0174cb7e3072aff1"),
Map.entry("net/irisshaders/iris/pathways/FullScreenQuadRenderer.class","7595d1408a17a4203af0c66b7d8a7f66c7f28e4a7b9ac7243893800e9d5a4d22"),
Map.entry("net/irisshaders/iris/gl/program/Program.class","8252cffe8d7322a3d64fad8036938e07b871cbd5667c0dafec0a3d48fd610d49"),
Map.entry("net/irisshaders/iris/gl/program/ProgramUniforms.class","92d557456c18f45abf2ef7895e484bf0ebca648b331ed70fc84567982adb878f"),
Map.entry("net/irisshaders/iris/gl/sampler/SamplerBinding.class","f3b15536d1c4edb697a1c686bccb939223a17adbc428f3b8c548b849fc355fd9"),
Map.entry("net/irisshaders/iris/gl/sampler/GlSampler.class","75a3f4b37c1b2ed65573eb8d070106902cc2b7205189c8f971748fff8635d33f"),
Map.entry("net/irisshaders/iris/pipeline/transform/transformer/CompositeTransformer.class","e67965a33e2c78ab57b95a2bfbcd118297fe2b0e7e530c5bc4d58482ca2f53fd")).entrySet())try(var in=ReimaginedSpatialPins.class.getClassLoader().getResourceAsStream(e.getKey())){if(in==null||!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())).equals(e.getValue()))return false;}return true;}catch(Exception|LinkageError unavailable){return false;}}}
