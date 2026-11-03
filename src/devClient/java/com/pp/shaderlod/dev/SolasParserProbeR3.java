package com.pp.shaderlod.dev;

import com.google.common.collect.ImmutableList;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.features.FeatureFlags;
import net.irisshaders.iris.gl.shader.StandardMacros;
import net.irisshaders.iris.helpers.StringPair;
import net.irisshaders.iris.shaderpack.IrisDefines;
import net.irisshaders.iris.shaderpack.include.*;
import net.irisshaders.iris.shaderpack.option.*;
import net.irisshaders.iris.shaderpack.option.values.MutableOptionValues;
import net.irisshaders.iris.shaderpack.parsing.BooleanParser;
import net.irisshaders.iris.shaderpack.preprocessor.*;
import net.irisshaders.iris.shaderpack.properties.ShaderProperties;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import java.nio.file.*;
import java.net.URI;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import java.security.MessageDigest;
import java.util.*;

/** STAGED, UNBUILT: one native-parser diagnostic, never installs a shader pack. */
final class SolasParserProbeR3 {
    private boolean consumed;
    static boolean register() {
        String pack=System.getProperty("shaderlod.dev.solasParserPack", "");
        String out=System.getProperty("shaderlod.dev.solasParserOut", "");
        if (pack.isBlank() && out.isBlank()) return false;
        if (pack.isBlank() || out.isBlank() || !System.getProperty("shaderlod.dev.compare", "").isBlank()
                || Boolean.getBoolean("shaderlod.dev.smoke"))
            throw new IllegalArgumentException("Require exactly pack/out inputs with compare/smoke off");
        NeoForge.EVENT_BUS.addListener(new SolasParserProbeR3()::tick);
        return true; // isolated diagnostic skips other SmokeClient probe/autoload registration
    }
    private void tick(ClientTickEvent.Post event) {
        var client = Minecraft.getInstance();
        if (consumed || !(client.screen instanceof TitleScreen)) return;
        consumed = true;
        Path out = null;
        boolean outputOwned = false;
        try {
            out=Path.of(System.getProperty("shaderlod.dev.solasParserOut")).toAbsolutePath();
            Files.createDirectory(out); // atomic refusal if any previous/partial output exists
            outputOwned = true;
            if (client.level != null || ModList.get() == null || !ModList.get().isLoaded("iris"))
                throw new IllegalStateException("Require initialized Iris title screen without world");
            if (Iris.getIrisConfig() == null || Iris.getIrisConfig().areDebugOptionsEnabled())
                throw new IllegalStateException("Require initialized config and debug=false; do not change config");
            if (Iris.getCurrentPack().isPresent())
                throw new IllegalStateException("Require existing shader selection disabled; do not change it here");
            Path pack=Path.of(System.getProperty("shaderlod.dev.solasParserPack")).toAbsolutePath();
            if (!sha(pack).equals("b7dfe4c7353d677a92cc4c86b87bc0633f1014305d927ca6a482c303427bf825"))
                throw new IllegalStateException("Wrong Solas archive");
            var origin=Iris.class.getProtectionDomain().getCodeSource();
            if (origin==null || origin.getLocation()==null) throw new IllegalStateException("UNKNOWN Iris codeSource");
            URI originUri=origin.getLocation().toURI();
            write(out.resolve("loaded-iris-code-source-uri.txt"),originUri.toString());
            Path originJar=physicalJar(originUri).toRealPath();
            String originSha=sha(originJar);
            var loadedIris=ModList.get().getModFiles().stream()
                .filter(f->f.getMods().stream().anyMatch(m->m.getModId().equals("iris"))).toList();
            if (loadedIris.size()!=1) throw new IllegalStateException("UNKNOWN/ambiguous loaded Iris artifact");
            Path modJar=loadedIris.getFirst().getFile().getFilePath().toRealPath();
            String modSha=sha(modJar);
            String originReceipt="codeSource="+originUri+"\nphysicalJar="+originJar+"\nphysicalSha256="+originSha
                +"\nmodListJar="+modJar+"\nmodListSha256="+modSha+"\n";
            write(out.resolve("loaded-iris-origin.txt"),originReceipt);
            if (!Files.isSameFile(originJar,modJar) || !originSha.equals("3e3f94d2d5cdcb8145c4385d521974aa2d8669b2cc212351f70cf06488182e83") || !modSha.equals(originSha))
                throw new IllegalStateException("Loaded Iris origin/pin mismatch");
            long context=GLFW.glfwGetCurrentContext(), window=client.getWindow().getWindow();
            if (!RenderSystem.isOnRenderThread() || context==0 || context!=window)
                throw new IllegalStateException("Require actual current Minecraft render context");
            var caps=GL.getCapabilities(); // actual existing context, no allocation/initialization override
            write(out.resolve("initialized-context.txt"),"renderThread=true\ncontext="+context+"\nwindow="+window
                +"\nversion="+GL11.glGetString(GL11.GL_VERSION)+"\nrenderer="+GL11.glGetString(GL11.GL_RENDERER)
                +"\nvendor="+GL11.glGetString(GL11.GL_VENDOR)+"\nglsl="+GL11.glGetString(35724)
                +"\nglBindImageTexture="+caps.glBindImageTexture+"\nglDispatchCompute="+caps.glDispatchCompute
                +"\nglClearTexImage="+caps.glClearTexImage+"\n");
            var standard=StandardMacros.createStandardEnvironmentDefines();
            if (standard.stream().noneMatch(p->p.key().equals("MC_OS_MAC")))
                throw new IllegalStateException("Actual environment is not MC_OS_MAC");
            var env=new ArrayList<StringPair>(standard);
            env.addAll(IrisDefines.createIrisReplacements()); // same append as ShaderPack
            var propertiesEnv=new ArrayList<StringPair>(env);
            var featureReceipt=new StringBuilder();
            for (var flag:FeatureFlags.values()) {
                boolean usable=flag.isUsable();
                featureReceipt.append(flag.name()).append(" usable=").append(usable).append('\n');
                if (usable) propertiesEnv.add(new StringPair("IRIS_FEATURE_"+flag.name(), ""));
            }
            write(out.resolve("actual-feature-flags.txt"),featureReceipt);
            write(out.resolve("actual-environment.txt"),propertiesEnv.toString());
            try (var zip=FileSystems.newFileSystem(pack)) {
                Path root=zip.getPath("/shaders");
                if (Files.exists(root.resolve("dimension.properties")))
                    throw new IllegalStateException("Unexpected dimension override");
                var starts=ImmutableList.<AbsolutePackPath>builder();
                for (String d:List.of("/","/world0/","/world-1/","/world1/"))
                    ShaderPackSourceNames.findPresentSources(starts,root,AbsolutePackPath.fromAbsolutePath(d),ShaderPackSourceNames.POTENTIAL_STARTS);
                var roots=starts.build();
                var graph=new IncludeGraph(root,roots,false);
                if (!graph.getFailures().isEmpty()) throw new IllegalStateException(graph.getFailures().toString());
                String raw=Files.readString(root.resolve("shaders.properties"));
                var defaults=new ShaderPackOptions(graph,Map.of());
                var defaultProperties=new ShaderProperties(raw,defaults,propertiesEnv);
                var profiles=ProfileSet.fromTree(defaultProperties.getProfiles(),defaults.getOptionSet());
                var high=new LinkedHashMap<String,String>();
                profiles.forEach((name,p)-> { if (name.equals("HIGH")) high.putAll(p.optionValues); });
                if (high.isEmpty()) throw new IllegalStateException("HIGH profile absent");
                var options=new ShaderPackOptions(graph,high); // native HIGH; zero user overrides
                var properties=new ShaderProperties(raw,options,propertiesEnv);
                var finalProfiles=ProfileSet.fromTree(properties.getProfiles(),options.getOptionSet());
                var scanned=finalProfiles.scan(options.getOptionSet(),options.getOptionValues());
                var current=scanned.current.orElseThrow();
                int delta=options.getOptionValues().getOptionsChanged()-new MutableOptionValues(options.getOptionSet(),current.optionValues).getOptionsChanged();
                if (!current.name.equals("HIGH") || delta!=0) throw new IllegalStateException("Not native HIGH/+0");
                StringBuilder result=new StringBuilder("PROFILE=HIGH DELTA=0 USER_OVERRIDES=0\n");
                result.append("ROOTS=").append(roots).append('\n');
                result.append("HIGH_MAP=").append(high).append('\n');
                result.append("FINAL_PROFILE_DISABLED_PROGRAMS=").append(current.disabledPrograms).append('\n');
                result.append("VX_DISCOVERED=").append(options.getOptionSet().getBooleanOptions().containsKey("VX_SUPPORT")).append('\n');
                result.append("VX_DEFAULT=").append(defaults.getOptionValues().getBooleanValueOrDefault("VX_SUPPORT")).append('\n');
                result.append("VX_EFFECTIVE=").append(options.getOptionValues().getBooleanValueOrDefault("VX_SUPPORT")).append('\n');
                result.append("VOXEL_VOLUME_SIZE=").append(options.getOptionValues().getStringValueOrDefault("VOXEL_VOLUME_SIZE")).append('\n');
                result.append("IMAGE_INFORMATION=").append(properties.getIrisCustomImages()).append('\n');
                for (String d:List.of("world0","world-1","world1")) {
                    String name=d+"/shadowcomp";
                    String expression=properties.getConditionallyEnabledPrograms().get(name);
                    if (expression==null) throw new IllegalStateException("Missing predicate "+name);
                    boolean predicate=BooleanParser.parse(expression,options.getOptionValues());
                    result.append(name).append(" expression=").append(expression).append(" predicate=").append(predicate)
                        .append(" profileDisabled=").append(current.disabledPrograms.contains(name))
                        .append(" enabled=").append(predicate&&!current.disabledPrograms.contains(name)).append('\n');
                }
                String nativePreprocessed=PropertiesPreprocessor.preprocessSource(raw,options,propertiesEnv);
                write(out.resolve("native-high.preprocessed.properties"),nativePreprocessed);
                // Option-transformed common from the actual IncludeGraph, expanded by actual IncludeProcessor.
                String common=String.join("\n",new IncludeProcessor(options.getIncludes()).getIncludedFile(AbsolutePackPath.fromAbsolutePath("/lib/common.glsl")));
                var glslEnv=new ArrayList<StringPair>(env);
                for (String feature:properties.getOptionalFeatureFlags())
                    if (!FeatureFlags.isInvalid(feature)) glslEnv.add(new StringPair("IRIS_FEATURE_"+feature,""));
                write(out.resolve("actual-glsl-environment.txt"),glslEnv.toString());
                String witness=JcppProcessor.glslPreprocessSource(common+"\n#ifdef VX_SUPPORT\nSOLAS_R3_VX_TRUE\n#else\nSOLAS_R3_VX_FALSE\n#endif\n",glslEnv);
                write(out.resolve("native-high.common-witness.glsl"),witness);
                result.append("GLSL_VX_TRUE=").append(witness.contains("SOLAS_R3_VX_TRUE")).append('\n');
                result.append("GLSL_VX_FALSE=").append(witness.contains("SOLAS_R3_VX_FALSE")).append('\n');
                write(out.resolve("result.txt"),result);
                // R4: preserve the native HIGH map; align only this ephemeral parser input.
                write(out.resolve("input-archive.txt"),"path="+pack+"\nsha256="+sha(pack)+"\n");
                var correctedInput=new LinkedHashMap<String,String>(high);
                correctedInput.put("VX_SUPPORT","false");
                var correctedOptions=new ShaderPackOptions(graph,correctedInput);
                var correctedProperties=new ShaderProperties(raw,correctedOptions,propertiesEnv);
                var correctedProfiles=ProfileSet.fromTree(correctedProperties.getProfiles(),correctedOptions.getOptionSet());
                var correctedScan=correctedProfiles.scan(correctedOptions.getOptionSet(),correctedOptions.getOptionValues());
                var correctedCurrent=correctedScan.current;
                var correctedProfileMap=correctedCurrent.map(p->p.optionValues).orElse(Map.of());
                int correctedDelta=correctedOptions.getOptionValues().getOptionsChanged()
                    -new MutableOptionValues(correctedOptions.getOptionSet(),correctedProfileMap).getOptionsChanged();
                var correctedDisabled=correctedCurrent.map(p->p.disabledPrograms).orElse(List.of());
                var baselineEffective=effectiveMap(options);
                var correctedEffective=effectiveMap(correctedOptions);
                write(out.resolve("native-effective-options.txt"),baselineEffective.toString());
                write(out.resolve("corrected-effective-options.txt"),correctedEffective.toString());
                var baselineUnrelated=new TreeMap<>(baselineEffective); baselineUnrelated.remove("VX_SUPPORT");
                var correctedUnrelated=new TreeMap<>(correctedEffective); correctedUnrelated.remove("VX_SUPPORT");
                String correctedPreprocessed=PropertiesPreprocessor.preprocessSource(raw,correctedOptions,propertiesEnv);
                write(out.resolve("corrected-high.preprocessed.properties"),correctedPreprocessed);
                var nativeFields=new Properties(); nativeFields.load(new java.io.StringReader(nativePreprocessed));
                var correctedFields=new Properties(); correctedFields.load(new java.io.StringReader(correctedPreprocessed));
                var propertyKeys=new TreeSet<String>(nativeFields.stringPropertyNames());
                propertyKeys.addAll(correctedFields.stringPropertyNames());
                var propertyDeltas=new StringBuilder(); var unexpectedPropertyDeltas=new ArrayList<String>();
                var permittedPropertyKeys=Set.of("image.voxel_img","image.floodfill_img","image.floodfill_img_copy",
                    "iris.features.optional","voxelizeLightBlocks","program.world0/shadowcomp.enabled",
                    "program.world-1/shadowcomp.enabled","program.world1/shadowcomp.enabled");
                for (String key:propertyKeys) if (!Objects.equals(nativeFields.getProperty(key),correctedFields.getProperty(key))) {
                    propertyDeltas.append(key).append(" native=").append(nativeFields.getProperty(key))
                        .append(" corrected=").append(correctedFields.getProperty(key)).append('\n');
                    if (!permittedPropertyKeys.contains(key)) unexpectedPropertyDeltas.add(key);
                }
                write(out.resolve("properties-deltas.txt"),propertyDeltas);
                var correctedResult=new StringBuilder("PROFILE_INTENT=HIGH USER_OVERRIDES=0 ALIGNMENT_OVERRIDES={VX_SUPPORT=false}\n");
                correctedResult.append("ACTUAL_PROFILE=").append(correctedCurrent.map(p->p.name).orElse("Custom"))
                    .append(" ACTUAL_DELTA=").append(correctedDelta).append('\n');
                correctedResult.append("VX_DISCOVERED=").append(correctedOptions.getOptionSet().getBooleanOptions().containsKey("VX_SUPPORT")).append('\n');
                correctedResult.append("VX_DEFAULT=").append(defaults.getOptionValues().getBooleanValueOrDefault("VX_SUPPORT")).append('\n');
                correctedResult.append("VX_EFFECTIVE=").append(correctedOptions.getOptionValues().getBooleanValueOrDefault("VX_SUPPORT")).append('\n');
                correctedResult.append("INPUT_MAP=").append(correctedInput).append('\n');
                correctedResult.append("FINAL_PROFILE_DISABLED_PROGRAMS=").append(correctedDisabled).append('\n');
                correctedResult.append("IMAGE_INFORMATION=").append(correctedProperties.getIrisCustomImages()).append('\n');
                boolean allProgramsDisabled=true;
                for (String d:List.of("world0","world-1","world1")) {
                    String name=d+"/shadowcomp";
                    String expression=correctedProperties.getConditionallyEnabledPrograms().get(name);
                    if (expression==null) throw new IllegalStateException("Missing corrected predicate "+name);
                    boolean predicate=BooleanParser.parse(expression,correctedOptions.getOptionValues());
                    boolean enabled=predicate&&!correctedDisabled.contains(name);
                    allProgramsDisabled &= !enabled && !predicate;
                    correctedResult.append(name).append(" expression=").append(expression).append(" predicate=").append(predicate)
                        .append(" profileDisabled=").append(correctedDisabled.contains(name)).append(" enabled=").append(enabled).append('\n');
                }
                var correctedGlslEnv=new ArrayList<StringPair>(env);
                for (String feature:correctedProperties.getOptionalFeatureFlags())
                    if (!FeatureFlags.isInvalid(feature)) correctedGlslEnv.add(new StringPair("IRIS_FEATURE_"+feature,""));
                write(out.resolve("corrected-glsl-environment.txt"),correctedGlslEnv.toString());
                String correctedCommon=String.join("\n",new IncludeProcessor(correctedOptions.getIncludes())
                    .getIncludedFile(AbsolutePackPath.fromAbsolutePath("/lib/common.glsl")));
                String correctedWitness=JcppProcessor.glslPreprocessSource(correctedCommon
                    +"\n#ifdef VX_SUPPORT\nSOLAS_R3_VX_TRUE\n#else\nSOLAS_R3_VX_FALSE\n#endif\n",correctedGlslEnv);
                write(out.resolve("corrected-high.common-witness.glsl"),correctedWitness);
                boolean sameUnrelatedOptions=baselineUnrelated.equals(correctedUnrelated);
                boolean sameCommon=witness.equals(correctedWitness);
                var nativeTokens=glslCodeTokens(witness); var correctedTokens=glslCodeTokens(correctedWitness);
                boolean sameCodeTokens=nativeTokens.equals(correctedTokens);
                write(out.resolve("native-common-code-tokens.txt"),nativeTokens.toString());
                write(out.resolve("corrected-common-code-tokens.txt"),correctedTokens.toString());
                var nativeLines=witness.split("\n",-1); var correctedLines=correctedWitness.split("\n",-1);
                var rawDiff=new StringBuilder();
                for (int i=0;i<Math.max(nativeLines.length,correctedLines.length);i++) {
                    String a=i<nativeLines.length?nativeLines[i]:"<ABSENT>", b=i<correctedLines.length?correctedLines[i]:"<ABSENT>";
                    if (!a.equals(b)) rawDiff.append("line=").append(i+1).append(" native=").append(a).append(" corrected=").append(b).append('\n');
                }
                write(out.resolve("common-raw-line-differences.txt"),rawDiff);
                boolean macVxFalse=correctedWitness.contains("SOLAS_R3_VX_FALSE")&&!correctedWitness.contains("SOLAS_R3_VX_TRUE");
                boolean aligned=!correctedOptions.getOptionValues().getBooleanValueOrDefault("VX_SUPPORT")
                    &&correctedProperties.getIrisCustomImages().isEmpty()&&allProgramsDisabled&&macVxFalse;
                correctedResult.append("UNRELATED_EFFECTIVE_OPTIONS_UNCHANGED=").append(sameUnrelatedOptions).append('\n');
                correctedResult.append("UNEXPECTED_PROPERTIES_DELTAS=").append(unexpectedPropertyDeltas).append('\n');
                correctedResult.append("COMMON_PREPROCESSED_BYTE_EQUAL=").append(sameCommon).append('\n');
                correctedResult.append("COMMON_GLSL_CODE_TOKENS_EQUAL=").append(sameCodeTokens).append('\n');
                correctedResult.append("GLSL_VX_TRUE=").append(correctedWitness.contains("SOLAS_R3_VX_TRUE")).append('\n');
                correctedResult.append("GLSL_VX_FALSE=").append(macVxFalse).append('\n');
                correctedResult.append("ALIGNED_METADATA=").append(aligned).append('\n');
                write(out.resolve("corrected-result.txt"),correctedResult);
                if (!sameUnrelatedOptions || !unexpectedPropertyDeltas.isEmpty() || !sameCodeTokens || !aligned)
                    throw new IllegalStateException("R4 alignment/unchanged-behavior checks failed; preserve results, stop");
            }
        } catch (Throwable failure) {
            // Only write in the directory actually created by THIS invocation.
            // Creation refusal leaves prior evidence untouched; raw console retains that refusal.
            if (out != null && outputOwned) {
                try {
                    var text=new java.io.StringWriter();
                    failure.printStackTrace(new java.io.PrintWriter(text));
                    write(out.resolve("failure.txt"),text.toString());
                } catch (Exception receiptFailure) { failure.addSuppressed(receiptFailure); }
            }
            Iris.logger.error("Solas parser R3 failed; no pack/pipeline was installed",failure);
        }
        // No world open, pack selection, reload, pipeline constructor, config setter or GL allocation.
    }
    // Predeclared R4 comparison: existing JCPP lexer, not regex comment deletion.
    private static List<String> glslCodeTokens(String source) throws Exception {
        source=source.replaceAll("(?m)^\\h*#\\h*line\\h+[0-9]+(?:\\h+(?:[0-9]+|\"[^\"\\r\\n]*\"))?\\h*\\r?$","");
        var result=new ArrayList<String>();
        try (var preprocessor=new org.anarres.cpp.Preprocessor();
             var lexer=new org.anarres.cpp.StringLexerSource(source,true)) {
            preprocessor.addInput(lexer);
            for (;;) {
                var token=lexer.token(); int type=token.getType();
                if (type==org.anarres.cpp.Token.EOF) break;
                if (type==org.anarres.cpp.Token.INVALID) throw new IllegalStateException("Invalid GLSL comparison token");
                if (type==org.anarres.cpp.Token.CCOMMENT || type==org.anarres.cpp.Token.CPPCOMMENT
                        || type==org.anarres.cpp.Token.WHITESPACE || type==org.anarres.cpp.Token.NL) continue;
                result.add(type+":"+token.getText());
            }
        }
        return result;
    }
    private static Map<String,String> effectiveMap(ShaderPackOptions options) {
        var result=new TreeMap<String,String>();
        for (String name:options.getOptionSet().getBooleanOptions().keySet())
            result.put(name,Boolean.toString(options.getOptionValues().getBooleanValueOrDefault(name)));
        for (String name:options.getOptionSet().getStringOptions().keySet())
            result.put(name,options.getOptionValues().getStringValueOrDefault(name));
        return result;
    }
    private static void write(Path path, CharSequence contents) throws Exception {
        Files.writeString(path,contents,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
    }
    private static Path physicalJar(URI origin) {
        if ("file".equals(origin.getScheme())) return Path.of(origin);
        if (!"union".equals(origin.getScheme()) || origin.getRawAuthority()!=null || origin.getRawQuery()!=null)
            throw new IllegalStateException("UNKNOWN Iris origin URI: "+origin);
        // URI.getPath percent-decodes path bytes; a literal '+' stays '+'. Never URLDecoder.
        String path=origin.getPath(), fragment=origin.getFragment();
        if (path==null || !path.startsWith("/")) throw new IllegalStateException("UNKNOWN union path");
        if (fragment!=null) {
            if (!fragment.matches("[0-9]+!/") || !path.endsWith(".jar"))
                throw new IllegalStateException("UNKNOWN union fragment: "+origin);
        } else {
            var suffix=java.util.regex.Pattern.compile("^(.*\\.jar)#[0-9]+!/$").matcher(path);
            if (!suffix.matches()) throw new IllegalStateException("UNKNOWN union suffix: "+origin);
            path=suffix.group(1);
        }
        return Path.of(path);
    }
    private static String sha(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
}
