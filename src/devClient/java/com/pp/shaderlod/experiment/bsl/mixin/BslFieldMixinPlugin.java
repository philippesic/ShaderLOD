package com.pp.shaderlod.experiment.bsl.mixin;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
/** No controller or main-mod classes linked during mixin applicability. */
public final class BslFieldMixinPlugin implements IMixinConfigPlugin {
 public void onLoad(String p){}
 public String getRefMapperConfig(){return null;}
 private static Boolean pinned;
 private static boolean exactApi(){
  if(pinned!=null)return pinned;
  String[][] pins={
            {"net/irisshaders/iris/gl/program/ProgramUniforms$Builder.class", "f6f2708036ef724cd55149981025aebc3caf02cb344d19e0551d6374b36f03b4"},
            {"net/irisshaders/iris/gl/uniform/DynamicLocationalUniformHolder.class", "090a908ed2446f25554724b127046ed15425f51b19115762230b57b3d6dfe003"},
            {"net/irisshaders/iris/gl/uniform/IntUniform.class", "70d87451b04c6626345b91243d432c700032476a97cb033c66bc85fcfc8c1041"},
            {"net/irisshaders/iris/shaderpack/option/ShaderPackOptions.class", "a6126e1e10013467efd930676756e1fd883f331217bfffd184a5ddb4670334c3"},
            {"net/irisshaders/iris/shaderpack/option/OptionSet.class", "469a1d42a80898bd67bbc83b07bf447dcf4463bdc295b3879f10f26dc0e2e1eb"},
            {"net/irisshaders/iris/shaderpack/option/values/MutableOptionValues.class", "8b49420d63b778bc1295db53881694114b8a98cff3f026499c4175cdb125bc9e"},
            {"net/irisshaders/iris/shaderpack/option/values/ImmutableOptionValues.class", "fab8380dd940634177b006d89d62bf41153327e4dc5d6c0c80d5326b3f33eea2"},
            {"net/neoforged/fml/ModList.class", "8c00f6c48eb4ab4ec3ecc5e65db8d95d30aae6ddd43b8a8b064001e386acc88f"},
            {"net/neoforged/fml/loading/moddiscovery/ModInfo.class", "58b2bca36bc98b96ab09c88699a0aeeca767b17adb2fc98ed79f14478296c68b"},
            {"net/irisshaders/iris/pathways/FullScreenQuadRenderer.class", "7595d1408a17a4203af0c66b7d8a7f66c7f28e4a7b9ac7243893800e9d5a4d22"},
            {"net/caffeinemc/mods/sodium/client/gl/GlObject.class", "f1894a8169341fa22fd3823ddc79056a8775bf91957716fdfb72392e53ad5ba5"},
            {"net/irisshaders/iris/pipeline/programs/SodiumShader.class", "33e49ddf69f01a86a64ad2e2d33595b81a22a859ba1feda0dda65813e663f356"},
            {"net/irisshaders/iris/targets/DepthTexture.class", "f9f7af036d89221ab874ca6158983606429ba245f8c8e243bfdf8ac151086ce0"},
            {"net/irisshaders/iris/shadows/ShadowRenderTargets.class", "4d6f1b3294365738dbcacac691021105a8141b2893441d919aff8b148ff093c5"},
            {"net/irisshaders/iris/gl/sampler/GlSampler.class", "75a3f4b37c1b2ed65573eb8d070106902cc2b7205189c8f971748fff8635d33f"},
            {"net/caffeinemc/mods/sodium/client/gl/shader/GlProgram.class", "f4439d38f4733c65c08f5b575a387835725605c43d8860f3e652cb4fd92c7ec0"},
            {"net/irisshaders/iris/gl/program/Program.class", "8252cffe8d7322a3d64fad8036938e07b871cbd5667c0dafec0a3d48fd610d49"},
            {"net/irisshaders/iris/gl/program/ProgramBuilder.class", "f2b09d4fe167409349529666ffb33864ac8d473321a3bb271234011cd5afa6aa"},
            {"net/irisshaders/iris/gl/shader/GlShader.class", "3c53fa0c3d704ae0383c56b95d1c653eac152069d646b95444c32f46e6b998db"},
            {"net/irisshaders/iris/gl/shader/ProgramCreator.class", "0fe4f9c64a4bdd8f544c23099cee9117ad91b0a73caed6429c38e2c560f18e2e"},
            {"net/caffeinemc/mods/sodium/client/gl/shader/GlProgram$Builder.class", "80e9edbf32bf34171bdafcfb94e79a3c764a11b81d0a1183788220c64b8b7758"},
            {"net/irisshaders/iris/pipeline/programs/SodiumPrograms.class", "9fe2bf60a9c6140c90bcf09aeae23e38365f029660385d4dae919fbe6e7481cf"},
            {"net/irisshaders/iris/gl/program/ProgramUniforms.class", "92d557456c18f45abf2ef7895e484bf0ebca648b331ed70fc84567982adb878f"},
            {"net/irisshaders/iris/pipeline/IrisRenderingPipeline.class", "95b3eb4425306964328cf771c7cc79b8bfffdf90e15bce813ad1a7bd38f34579"},
            {"net/irisshaders/iris/pipeline/WorldRenderingPipeline.class", "58cdb6fe06983558ffec27a41fe5105d2f5b5cbf28dfcb1df85afa20ce03f151"},
            {"net/irisshaders/iris/shaderpack/programs/ProgramSet.class", "7d0ee9f3c134126775182f23ba085f97d1d3d28a78a2a5ee115413317f47b495"},
            {"net/irisshaders/iris/shaderpack/programs/ProgramSource.class", "a1d142aac769ad65f77761f5f8275c7f22972f2e967c09a19074433eea117893"},
            {"net/irisshaders/iris/shadows/ShadowCompositeRenderer.class", "11f3aa7cb28b25e752d11df82b1a6a0cecaee35f5a09d8d7ccea06edfade00fc"},
            {"net/irisshaders/iris/shadows/ShadowRenderer.class", "fb80f74fa044378605df7d9e7408574f1972709dac1ed17452b0e2f6f9372b59"},
            {"net/irisshaders/iris/uniforms/CommonUniforms.class", "9cd8f45f63645cdaef6bbc6e1d5c43ae711078049b6a503fb80fcfb491cf5acf"},
            {"net/caffeinemc/mods/sodium/client/gl/device/GLRenderDevice$ImmediateDrawCommandList.class", "60d7958e257a4ee5c22e9ca299dd8ca124c2f113d0d970b731526f510dae538b"}
  };
  try{
   for(var pin:pins)try(var in=BslFieldMixinPlugin.class.getClassLoader().getResourceAsStream(pin[0])){
    if(in==null||!java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())).equals(pin[1]))return pinned=false;
   }
   return pinned=true;
  }catch(Exception|LinkageError unknown){return pinned=false;}
 }
 public boolean shouldApplyMixin(String t,String m){return Boolean.getBoolean("shaderlod.dev.bslFieldBridge")&&exactApi();}
 public void acceptTargets(Set<String> mine,Set<String> other){}
 public List<String> getMixins(){return null;}
 public void preApply(String t,ClassNode n,String m,IMixinInfo i){}
 public void postApply(String t,ClassNode n,String m,IMixinInfo i){}
}
