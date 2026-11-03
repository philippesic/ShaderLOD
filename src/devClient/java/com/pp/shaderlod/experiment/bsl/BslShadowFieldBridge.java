package com.pp.shaderlod.experiment.bsl;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.TreeMap;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.features.FeatureFlags;
import net.irisshaders.iris.gl.program.Program;
import net.irisshaders.iris.gl.state.ValueUpdateNotifier;
import net.irisshaders.iris.gl.uniform.DynamicUniformHolder;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.shadows.ShadowRenderer;
import net.irisshaders.iris.shadows.ShadowRenderTargets;
import net.irisshaders.iris.uniforms.CommonUniforms;
import net.irisshaders.iris.uniforms.FrameUpdateNotifier;
import net.irisshaders.iris.uniforms.SystemTimeUniforms;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;

/** Private opt-in steady renderer plus separate bounded NEVER-TIME observation. No main-mod static
 * linkage. Missing mod, unknown state or certificate leaves the receiver native.
 * Producer authorization is independent from receiver support authorization. */
public final class BslShadowFieldBridge {
    private static final String ARCHIVE = "97310aef962c78d8b70c0e3a7af022fb4b8405ad432e2dfe8f6981c9b3ecfcdb";
    private static final String OPTIONS = "0aaa1276ea6e24106e6ed062a3bd149d1b53c09693d04a4637dcb19e6c43c394";
    private static final Map<FrameUpdateNotifier, State> STATES = new IdentityHashMap<>();
    private static final ThreadLocal<Boolean> PRODUCER_REGISTRATION = ThreadLocal.withInitial(() -> false);
    private static final BslFieldSourceRecords NATIVE_SOURCES=new BslFieldSourceRecords();
    private static final BslSteadyPrograms STEADY_PROGRAMS=new BslSteadyPrograms();
    private static final BslSteadyWitness WITNESS=BslSteadyWitness.fromProperties();
    private static State current;
    private static long receiptSequence;
    private BslShadowFieldBridge() {}
    public static boolean requested() { return Boolean.getBoolean("shaderlod.dev.bslFieldBridge"); }
    /** Opt-in request only. Actual source/state/epoch admission is independent. */
    private static boolean steadyRequested() { return Boolean.getBoolean("shaderlod.dev.bslFieldSteady"); }
    private static final class Signal implements ValueUpdateNotifier {
        final State state;
        final Object builder;
        final int program;
        final String name;
        final BslFieldSourceRecords.Proof registration;
        final BslBoundSignal callback=new BslBoundSignal();
        boolean listener;
        int finiteAttachCount,finiteFireCount;
        String finiteLastFire="NOT_FIRED";
        Signal(State state,Object builder,int program,String name) {
            this.state=state;this.builder=builder;this.program=program;this.name=name;
            var nativeProof=NATIVE_SOURCES.forRegistration(program);
            registration=nativeProof!=null?nativeProof:STEADY_PROGRAMS.forRegistration(program);
        }
        @Override public void setListener(Runnable next) {
            // Registration captures the actual builder's linked generation, not
            // whatever program happens to be current during callback attachment.
            listener=next!=null;callback.attach(next,registration,program);
            if(state.activeProbe&&!state.steady&&finiteAttachCount<64)finiteAttachCount++;
        }
        void fire(Object owner,int bound) {
            if(state.activeProbe&&!state.steady&&finiteFireCount<64){
                finiteFireCount++;
                finiteLastFire=!listener?"NO_NATIVE_LISTENER":registration==null?"NO_REGISTRATION_GENERATION"
                    :owner==null?"NO_LIVE_OWNER":owner!=registration?"OWNER_GENERATION_MISMATCH"
                    :bound!=program?"BOUND_PROGRAM_MISMATCH":"NATIVE_CALLBACK_ELIGIBLE";
            }
            callback.fire(owner,bound);
        }
    }
    private static final class State {
        final FrameUpdateNotifier notifier;
        final BslFieldEpochs epochs = new BslFieldEpochs();
        final BslSteadyMetadataCache metadata = new BslSteadyMetadataCache();
        final ArrayList<Signal> signals = new ArrayList<>();
        Object pipeline, pack;
        Map<String,String> expected;
        ShadowRenderTargets targets;
        Program producer;
        boolean pinned, pinAttempted, fieldValidated, consumerAuthorized, rawConsumerAuthorized, rawSameDomain, activeProbe, steady;
        Object glContext, targetObject, depthObject, resourceManager;
        java.util.List<net.minecraft.server.packs.PackResources> resourcePacks;
        String environment;
        JsonObject environmentDescription;
        net.neoforged.fml.ModList modList;
        java.util.List<net.neoforged.neoforgespi.language.IModInfo> modInfos;
        Object coreState;
        long coreLookupEpoch=-1;
        int observedFields, probeAttempts, sodiumRegistrationAttempts;
        final BslGpuValueEcho gpuEcho = new BslGpuValueEcho();
        BslGpuFieldValidation.Result gpuFieldValidation;
        String producerSource, producerLinkedSource, terrainSource, reason = "UNINITIALIZED";
        JsonObject producerPredicates;
        BslFieldReceiverSupport support;
        Binding rawBinding, hardwareBinding, fieldBinding, terrainRawBinding;
        int producerFbo, producerRawRedSwizzle;
        int[] fieldMainImage,fieldAltImage;
        int[] producerViewport;
        int depth, field;
        long gameTime, dayTime;
        float frameTime;
        double cameraX, cameraY, cameraZ;
        float cameraYaw, cameraPitch;
        final float[] model = new float[16], projection = new float[16];
        BslFieldSourceRecords.Proof producerProof;
        final java.util.Set<Integer> loggedTerrains = new java.util.HashSet<>();
        State(FrameUpdateNotifier notifier) { this.notifier = notifier; }
        void signal() {
            if(current!=this)return;
            int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            Object owner=signalOwner(this,program);
            for(var signal:signals)signal.fire(owner,program);
        }
        void reject(String why) { if(WITNESS!=null)WITNESS.fail(why); reason = why; consumerAuthorized = rawConsumerAuthorized = false; epochs.fail(); signal(); }
    }
    private static Object signalOwner(State s,int program) {
        if(s==null||program<=0)return null;
        if(s.producer!=null&&s.producer.getProgramId()==program) {
            var proof=NATIVE_SOURCES.forOwner(s.producer,program);
            return proof!=null&&proof==s.producerProof?proof:null;
        }
        return STEADY_PROGRAMS.boundOwner(s.pipeline,program,org.lwjgl.opengl.GL.getCapabilities());
    }
    private static boolean probeArmed() {
        String arm=System.getProperty("shaderlod.dev.bslFieldArm", "");
        return arm.isBlank() || Files.isRegularFile(Path.of(arm),java.nio.file.LinkOption.NOFOLLOW_LINKS);
    }
    private static int probeLimit() { return Math.max(1,Math.min(3,Integer.getInteger("shaderlod.dev.bslFieldFrames",1))); }
    private static State state(FrameUpdateNotifier n) { return STATES.computeIfAbsent(n, State::new); }
    private static final class UniformBuilderAccess {
        static final Field PROGRAM=programField();
        private static Field programField(){
            try{Field f=net.irisshaders.iris.gl.program.ProgramUniforms.Builder.class.getDeclaredField("program");
                f.setAccessible(true);return f;}catch(Exception|LinkageError unavailable){return null;}
        }
        static int program(UniformHolder holder){
            try{return holder instanceof net.irisshaders.iris.gl.program.ProgramUniforms.Builder&&PROGRAM!=null
                ?PROGRAM.getInt(holder):0;}catch(Exception|LinkageError unavailable){return 0;}
        }
    }
    /** Exact Sodium local builder seam; the pipeline/notifier and program ID
     * are actual constructor/method arguments. No additional core snapshot. */
    public static void registerSodium(UniformHolder holder,WorldRenderingPipeline pipeline,int actualProgram) {
        if(!requested()||pipeline==null)return;
        State s=state(pipeline.getFrameUpdateNotifier());
        if(!steadyRequested()&&s.sodiumRegistrationAttempts<64)s.sodiumRegistrationAttempts++;
        if(actualProgram<=0||UniformBuilderAccess.program(holder)!=actualProgram)return;
        register(holder,pipeline.getFrameUpdateNotifier());
    }
    public static void register(UniformHolder holder, FrameUpdateNotifier notifier) {
        if (!requested() || !(holder instanceof DynamicUniformHolder dynamic)) return;
        int actualProgram=UniformBuilderAccess.program(holder);
        if(actualProgram<=0)return; // Unknown/wrapped uniform owners stay native.
        State s = state(notifier);
        boolean producer = PRODUCER_REGISTRATION.get();
        Signal signal = new Signal(s,holder,actualProgram,"shaderlodBslShadowFieldQualified");
        s.signals.add(signal);
        dynamic.uniform1i("shaderlodBslShadowFieldQualified", () -> {
            if (current != s || !runtimeCurrent(s)) return 0;
            return producer ? (s.epochs.producerUniform(context(s)) ? 1 : 0)
                : (s.consumerAuthorized ? 1 : 0);
        }, signal);
        if (!producer) {
            Signal rawSignal=new Signal(s,holder,actualProgram,"shaderlodBslRawQueryQualified");s.signals.add(rawSignal);
            dynamic.uniform1i("shaderlodBslRawQueryQualified", () ->
                current == s && runtimeCurrent(s) && s.rawConsumerAuthorized ? 1 : 0, rawSignal);
        }
    }
    /** Wraps ONLY createProgram's existing dynamic call, after its ordinary
     * execution, before ProgramBuilder.build. getParent/directives and notifier
     * are actual pinned objects; no constructor argument substitution. */
    public static void registerProducer(UniformHolder holder, WorldRenderingPipeline pipeline,
            ProgramSource source, ShadowRenderTargets targets) {
        if (!requested() || !"shadowcomp1".equals(source.getName())
                || source.getFragmentSource().orElse("").indexOf("shaderlodBslShadowFieldEnabled") < 0) return;
        State s = state(pipeline.getFrameUpdateNotifier());
        s.pipeline = pipeline; s.targets = targets;
        s.producerSource = sha(source.getVertexSource().orElse("") + "\n" + source.getFragmentSource().orElse(""));
        PRODUCER_REGISTRATION.set(true);
        try {
            // generalCommonUniforms does not itself register frameCounter.
            CommonUniforms.generalCommonUniforms(holder, s.notifier, source.getParent().getPackDirectives());
            SystemTimeUniforms.addSystemTimeUniforms(holder);
        } finally { PRODUCER_REGISTRATION.remove(); }
    }
    public static void producerBuilt(WorldRenderingPipeline pipeline, ProgramSource source, Program result) {
        if (!requested() || !"shadowcomp1".equals(source.getName())) return;
        State s = STATES.get(pipeline.getFrameUpdateNotifier());
        if (s != null && s.pipeline == pipeline && s.producerSource != null) {
            s.producer=result; s.producerProof=NATIVE_SOURCES.claim(result,result.getProgramId());
            s.producerLinkedSource=s.producerProof==null?null:s.producerProof.sha256();
        }
    }
    private static String sha(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }
    private static String sha(String value) { return sha(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
    private static Map<String,String> actualOptions() {
        var options = Iris.getCurrentPack().orElseThrow().getShaderPackOptions();
        var values = options.getOptionValues();
        Map<String,String> actual = new TreeMap<>();
        options.getOptionSet().getBooleanOptions().keySet().forEach(k -> actual.put(k,
            Boolean.toString(values.getBooleanValueOrDefault(k))));
        options.getOptionSet().getStringOptions().keySet().forEach(k -> actual.put(k, values.getStringValueOrDefault(k)));
        return actual;
    }
    private static boolean optionsCurrent(State s) {
        var pack=Iris.getCurrentPack().orElse(null);
        if(pack!=s.pack||pack==null){s.metadata.invalidate();return false;}
        var options=pack.getShaderPackOptions();var values=options.getOptionValues();var set=options.getOptionSet();
        // The current pinned constructor actually uses MutableOptionValues.
        // Getter maps are exposed; no collection replacement or mutation hook.
        if(options.getClass()!=net.irisshaders.iris.shaderpack.option.ShaderPackOptions.class
                ||set.getClass()!=net.irisshaders.iris.shaderpack.option.OptionSet.class
                ||values.getClass()!=net.irisshaders.iris.shaderpack.option.values.MutableOptionValues.class
                &&values.getClass()!=net.irisshaders.iris.shaderpack.option.values.ImmutableOptionValues.class){
            s.metadata.invalidate();return false;
        }
        var key=new BslSteadyMetadataCache.Options(s.epochs.identity().renderEpoch(),s.pipeline,pack,options,values,set,
            org.lwjgl.opengl.GL.getCapabilities(),Minecraft.getInstance().getResourceManager(),s.targets.get(2),
            s.targets.getDepthTexture(),s.producerProof);
        return s.metadata.options(key,() -> {
            Map<String,String> actual=actualOptions();
            return "1".equals(actual.remove("SHADERLOD_BSL_SHADOW_FIELD"))
                && "1".equals(actual.remove("SHADERLOD_BSL_RAW_QUERY"))
                && java.util.Set.of("0","1").contains(actual.remove("SHADERLOD_BSL_RAW_FIELD"))
                && actual.equals(s.expected);
        });
    }
    private static boolean exactClasses() throws Exception {
        String[][] pins = {
            {"shadows/ShadowCompositeRenderer", "11f3aa7cb28b25e752d11df82b1a6a0cecaee35f5a09d8d7ccea06edfade00fc"},
            {"shadows/ShadowRenderer", "fb80f74fa044378605df7d9e7408574f1972709dac1ed17452b0e2f6f9372b59"},
            {"pipeline/IrisRenderingPipeline", "95b3eb4425306964328cf771c7cc79b8bfffdf90e15bce813ad1a7bd38f34579"},
            {"uniforms/CommonUniforms", "9cd8f45f63645cdaef6bbc6e1d5c43ae711078049b6a503fb80fcfb491cf5acf"}
        };
        for (var pin : pins) try (var in = Iris.class.getClassLoader().getResourceAsStream("net/irisshaders/iris/"+pin[0]+".class")) {
            if (in == null || !sha(in.readAllBytes()).equals(pin[1])) return false;
        }
        return true;
    }
    private static boolean pin(State s) {
        try {
            s.pack = Iris.getCurrentPack().orElse(null);
            if (s.pack == null || !exactClasses() || Iris.isFallback()
                    || !ModList.get().isLoaded("shaderlod") || s.producer == null || s.targets == null
                    || NATIVE_SOURCES.forOwner(s.producer,s.producer.getProgramId())==null) return false;
            if (!net.minecraft.SharedConstants.getCurrentVersion().getName().equals("1.21.1")
                    || !ModList.get().getModContainerById("neoforge").orElseThrow().getModInfo().getVersion().toString().equals("21.1.253")) return false;
            for (String mod : new String[]{"distanthorizons","voxy","create","flywheel"})
                if (ModList.get().isLoaded(mod)) return false;
            var directory = Iris.getShaderpacksDirectory().toAbsolutePath().normalize();
            var zip = directory.resolve(Iris.getCurrentPackName()).normalize();
            if (!zip.startsWith(directory) || zip.equals(directory) || !sha(Files.readAllBytes(zip)).equals(ARCHIVE)) return false;
            byte[] bytes = Files.readAllBytes(Path.of(System.getProperty("shaderlod.dev.bslFieldOptionsPin", "")));
            if (!sha(bytes).equals(OPTIONS)) return false;
            JsonObject options = new Gson().fromJson(new String(bytes, java.nio.charset.StandardCharsets.UTF_8), JsonObject.class);
            s.expected = new TreeMap<>();
            options.entrySet().forEach(e -> s.expected.put(e.getKey(), e.getValue().getAsString()));
            return s.expected.size() == 384 && optionsCurrent(s);
        } catch (Exception | LinkageError unavailable) { s.reason = "PIN_UNAVAILABLE:"+unavailable.getClass().getSimpleName(); return false; }
    }
    private static BslFieldEpochs.Context context(State s) {
        var mc = Minecraft.getInstance(); var target = mc.getMainRenderTarget();
        return new BslFieldEpochs.Context(s.pipeline, s.notifier, Iris.getCurrentPack().orElse(null),
            mc.level, mc.getCameraEntity(), target, SystemTimeUniforms.COUNTER.getAsInt(), target.width, target.height);
    }
    /** Lookup metadata once. Never create or replace the registered core. */
    private static final class CoreAccess {
        final Class<?> type;
        final Field frames,notified,previous,duplicate,current;
        final java.lang.reflect.Method core,settings,preset,enabled;
        CoreAccess() throws Exception {
            type=Class.forName("com.pp.shaderlod.CoreUniformFrame",false,CommonUniforms.class.getClassLoader());
            if(type.getField("BRIDGE_VERSION").getInt(null)!=2)throw new IllegalStateException("CORE_VERSION");
            frames=field(type,"FRAMES");notified=field(type,"notified");previous=field(type,"previousFrame");
            duplicate=field(type,"duplicateNotification");current=field(type,"current");
            core=current.getType().getMethod("core");settings=core.getReturnType().getMethod("settings");
            preset=core.getReturnType().getMethod("preset");enabled=settings.getReturnType().getMethod("enabled");
        }
        private static Field field(Class<?> type,String name)throws Exception {
            Field result=type.getDeclaredField(name);result.setAccessible(true);return result;
        }
    }
    private static final class CoreAccessHolder {
        static final CoreAccess ACCESS=load();
        private static CoreAccess load(){try{return new CoreAccess();}catch(Exception|LinkageError unavailable){return null;}}
    }
    private static boolean core(State s) {
        try {
            if(!ModList.get().isLoaded("shaderlod"))return false;
            CoreAccess a=CoreAccessHolder.ACCESS;if(a==null)return false;
            long epoch=s.epochs.identity().renderEpoch();
            if(s.coreLookupEpoch!=epoch){
                s.coreLookupEpoch=epoch;
                synchronized(a.type){s.coreState=((Map<?,?>)a.frames.get(null)).get(s.notifier);}
            }
            Object state=s.coreState;
            // These mutable native notification fields are LIVE on every call,
            // including a second notification without a changed frame snapshot.
            if(state==null||!a.notified.getBoolean(state)||a.previous.getInt(state)!=SystemTimeUniforms.COUNTER.getAsInt()
                    ||a.duplicate.getBoolean(state))return false;
            Object snapshot=a.current.get(state);
            return s.metadata.core(state,snapshot,epoch,() -> {
                try {Object c=a.core.invoke(snapshot);Object settings=a.settings.invoke(c);Object preset=a.preset.invoke(c);
                    return Boolean.TRUE.equals(a.enabled.invoke(settings))
                        &&preset instanceof Enum<?> e&&e.ordinal()>=0&&e.ordinal()<=2;
                }catch(Exception|LinkageError unavailable){return false;}
            });
        }catch(Exception|LinkageError unavailable){return false;}
    }
    /** Runtime inventory joins the reviewed source row. Pack bytes are pinned
     * once at creation; loaded pack/options/resource-pack/mod objects are checked
     * continuously. Active source review must explicitly exclude PBR paths. */
    /** Immutable startup admission inventory. ModList/ModInfo IDs and version
     * fields are pinned startup metadata; cheap live list/object checks reject
     * replacement/add/remove/reorder. Unknown concurrent reflective mutation of
     * loader metadata is outside the reviewed runtime inventory. */
    private static void captureEnvironment(State s) {
        s.modList=ModList.get();s.modInfos=java.util.List.copyOf(s.modList.getMods());
        Map<String,String> mods=new TreeMap<>();
        for(var mod:s.modInfos)mods.put(mod.getModId(),mod.getVersion().toString());
        var packs=s.resourcePacks.stream().map(net.minecraft.server.packs.PackResources::packId).toList();
        JsonObject result=new JsonObject();
        result.add("mods",new Gson().toJsonTree(mods));result.add("resource_packs",new Gson().toJsonTree(packs));
        s.environmentDescription=result;s.environment=sha(new Gson().toJson(result));
    }
    private static boolean inventoryCurrent(State s) {
        if(ModList.get()!=s.modList||s.modInfos==null)return false;
        var current=s.modList.getMods();if(current.size()!=s.modInfos.size())return false;
        for(int i=0;i<current.size();i++)if(current.get(i)!=s.modInfos.get(i))return false;
        return true;
    }
    private static boolean steadyScope(State s) {
        var manager=Minecraft.getInstance().getResourceManager();
        var packs=manager.listPacks().toList();
        if(manager!=s.resourceManager||s.resourcePacks==null||packs.size()!=s.resourcePacks.size())return false;
        for(int i=0;i<packs.size();i++)if(packs.get(i)!=s.resourcePacks.get(i))return false;
        return s.glContext==org.lwjgl.opengl.GL.getCapabilities()
            && s.targetObject==s.targets.get(2) && s.depthObject==s.targets.getDepthTexture()
            && s.environment!=null && inventoryCurrent(s);
    }
    private static boolean runtimeCurrent(State s) {
        try {
            var mc = Minecraft.getInstance();
            return requested() && s.steady==steadyRequested() && (s.activeProbe || s.steady) && s.pinned && s.producer!=null && s.producerProof!=null
                && NATIVE_SOURCES.forOwner(s.producer,s.producer.getProgramId())==s.producerProof && !Iris.isFallback() && Iris.getCurrentPack().orElse(null) == s.pack
                && mc.level != null && mc.level.dimension().equals(Level.OVERWORLD)
                && core(s) && optionsCurrent(s) && (!s.steady || steadyScope(s));
        } catch (RuntimeException | LinkageError unavailable) { return false; }
    }
    public static void begin(WorldRenderingPipeline pipeline) {
        if (!requested()) return;
        current = STATES.get(pipeline.getFrameUpdateNotifier());
        if (current == null || current.pipeline != pipeline) { current = null; return; }
        State s = current;
        if (!s.pinAttempted) {s.pinAttempted=true;s.pinned=pin(s);}
        s.consumerAuthorized = s.rawConsumerAuthorized = false; s.fieldValidated = s.rawSameDomain = false; s.support = null;
        s.steady=steadyRequested();
        // Finite observer cannot run in steady frames, even when arm exists.
        s.activeProbe=!s.steady && probeArmed() && s.observedFields<probeLimit() && s.probeAttempts<3;
        if(s.glContext==null){s.glContext=org.lwjgl.opengl.GL.getCapabilities();
            s.targetObject=s.targets.get(2);s.depthObject=s.targets.getDepthTexture();
            s.resourceManager=Minecraft.getInstance().getResourceManager();
            s.resourcePacks=Minecraft.getInstance().getResourceManager().listPacks().toList();captureEnvironment(s);} s.loggedTerrains.clear();
        s.epochs.begin(context(s)); s.signal();
        s.reason = s.pinned ? "WAIT_NATIVE_SHADOW" : "NATIVE_UNPINNED";
        if(WITNESS!=null)WITNESS.begin(s.pipeline,s.glContext,s.epochs.identity(),context(s).width(),context(s).height(),
            s.steady&&!s.activeProbe&&s.pinned&&!Boolean.getBoolean("shaderlod.dev.bslGpuValueEcho"));
    }
    public static void shadowBegin(WorldRenderingPipeline pipeline) {
        State s = current;
        if (s == null || s.pipeline != pipeline) return;
        s.consumerAuthorized = s.rawConsumerAuthorized = false; s.epochs.shadowBegin(context(s)); s.signal();
    }
    public static void nativeBeforeField(WorldRenderingPipeline pipeline) {
        State s = current;
        if (s == null || s.pipeline != pipeline) return;
        s.epochs.nativeBeforeField(context(s));
        var mc = Minecraft.getInstance(); var cam = mc.gameRenderer.getMainCamera(); var pos = cam.getPosition();
        if (mc.level == null || ShadowRenderer.MODELVIEW == null || ShadowRenderer.PROJECTION == null) { s.reject("NATIVE_INPUTS_UNKNOWN"); return; }
        s.gameTime = mc.level.getGameTime(); s.dayTime = mc.level.getDayTime();
        s.frameTime = SystemTimeUniforms.TIMER.getFrameTimeCounter();
        s.cameraX = pos.x; s.cameraY = pos.y; s.cameraZ = pos.z; s.cameraYaw=cam.getYRot(); s.cameraPitch=cam.getXRot();
        ShadowRenderer.MODELVIEW.get(s.model); ShadowRenderer.PROJECTION.get(s.projection);
    }
    private static boolean unchangedInputs(State s) {
        var mc = Minecraft.getInstance(); var cam = mc.gameRenderer.getMainCamera(); var pos = cam.getPosition();
        if (mc.level == null || ShadowRenderer.MODELVIEW == null || ShadowRenderer.PROJECTION == null) return false;
        float[] m = new float[16], p = new float[16];
        ShadowRenderer.MODELVIEW.get(m); ShadowRenderer.PROJECTION.get(p);
        return mc.level.getGameTime()==s.gameTime && mc.level.getDayTime()==s.dayTime
            && SystemTimeUniforms.TIMER.getFrameTimeCounter()==s.frameTime
            && pos.x==s.cameraX && pos.y==s.cameraY && pos.z==s.cameraZ && cam.getYRot()==s.cameraYaw && cam.getXRot()==s.cameraPitch
            && java.util.Arrays.equals(m,s.model) && java.util.Arrays.equals(p,s.projection);
    }
    private static int uniform(int program, String name) {
        // Program 0 is native unbound state, never an object for GL queries.
        // In particular shadow-return receipts must not create GL_INVALID_VALUE.
        if(program<=0)return Integer.MIN_VALUE;
        int loc=GL20.glGetUniformLocation(program,name);
        return loc<0 ? Integer.MIN_VALUE : GL20.glGetUniformi(program,loc);
    }
    private static boolean linkedCore(int program) {
        int preset=uniform(program,"shaderlodPreset");
        return uniform(program,"shaderlodBridgeVersion")==2 && uniform(program,"shaderlodEnabled")==1
            && preset>=0 && preset<=2 && uniform(program,"frameCounter")==SystemTimeUniforms.COUNTER.getAsInt()
            && (GL20.glGetUniformLocation(program,"shaderlodBslShadowFieldQualified")>=0
                || GL20.glGetUniformLocation(program,"shaderlodBslRawQueryQualified")>=0);
    }
    private record Binding(int texture, int sampler, int compare, int compareFunction,
            int minFilter, int magFilter, int width, int height, int format,
            int baseLevel,int maxLevel,int depthBits,int depthType,int wrapS,int wrapT,
            float[] border,boolean anisotropySupported,Float anisotropy) {}
    private static Binding binding(int program, String name) {
        int unit=uniform(program,name);
        if (unit<0 || unit>=GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS)) return null;
        int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);
        try {
            int texture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            int sampler=GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
            if(texture<=0)return null;
            int compare=sampler==0?GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL14_COMPARE_MODE):GL33.glGetSamplerParameteri(sampler,GL14_COMPARE_MODE);
            int fn=sampler==0?GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL14_COMPARE_FUNC):GL33.glGetSamplerParameteri(sampler,GL14_COMPARE_FUNC);
            int min=sampler==0?GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER):GL33.glGetSamplerParameteri(sampler,GL11.GL_TEXTURE_MIN_FILTER);
            int mag=sampler==0?GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER):GL33.glGetSamplerParameteri(sampler,GL11.GL_TEXTURE_MAG_FILTER);
            float[] border=new float[4];
            if(sampler==0)GL11.glGetTexParameterfv(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_BORDER_COLOR,border);
            else GL33.glGetSamplerParameterfv(sampler,GL11.GL_TEXTURE_BORDER_COLOR,border);
            boolean anisotropic=org.lwjgl.opengl.GL.getCapabilities().GL_EXT_texture_filter_anisotropic||org.lwjgl.opengl.GL.getCapabilities().GL_ARB_texture_filter_anisotropic;
            Float anisotropy=anisotropic?(sampler==0?GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D,0x84FE):GL33.glGetSamplerParameterf(sampler,0x84FE)):null;
            return new Binding(texture,sampler,compare,fn,min,mag,
                GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH),
                GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT),
                GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_INTERNAL_FORMAT),
                GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,0x813C),GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,0x813D),
                GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,0x884A),GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,0x8C16),
                sampler==0?GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S):GL33.glGetSamplerParameteri(sampler,GL11.GL_TEXTURE_WRAP_S),
                sampler==0?GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T):GL33.glGetSamplerParameteri(sampler,GL11.GL_TEXTURE_WRAP_T),border,anisotropic,anisotropy);
        } finally { GL13.glActiveTexture(active); }
    }
    private static int[] imageInfo(int texture) {
        int previous=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try{
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
            return new int[]{texture,GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH),
                GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT),
                GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_INTERNAL_FORMAT),
                GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,0x813C),GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,0x813D),
                GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,0x884A),GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,0x8C16),
                GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,0x805C),
                GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,0x8C10)};
        }finally{GL11.glBindTexture(GL11.GL_TEXTURE_2D,previous);}
    }
    private static boolean knownDepth(Binding b){return b.depthBits>=16&&(b.format==0x81A5||b.format==0x81A6||b.format==0x81A7||b.format==GL30.GL_DEPTH_COMPONENT32F);}
    private static boolean exactFieldImage(int[] info){return info!=null&&info[0]>0&&info[1]==2048&&info[2]==2048&&info[3]==GL30.GL_R32F&&info[4]==0&&info[5]>=0&&info[8]==32&&info[9]==GL11.GL_FLOAT;}
    private static final int GL14_COMPARE_MODE=0x884C, GL14_COMPARE_FUNC=0x884D;
    /** Exact producer program is already bound with its actual samplers and
     * all core/frame uniforms uploaded. Called after native Program.use. */
    public static void afterProducerUse(WorldRenderingPipeline pipeline, Program program) {
        State s=current;
        if(s==null||!(s.activeProbe||s.steady)||s.pipeline!=pipeline||s.producer!=program)return;
        if(!s.steady && s.probeAttempts>=3){s.activeProbe=false;s.reject("PROBE_ATTEMPT_LIMIT");return;}
        if(!s.steady)s.probeAttempts++;
        try {
            requireNoGlError("PRODUCER_START");
            int id=program.getProgramId();
            var proof=NATIVE_SOURCES.forOwner(program,id);
            if(proof==null||proof!=s.producerProof||proof.sha256()==null){s.reject("MISSING_NATIVE_PRE_DETACH_SOURCE_PROOF");write(s,"producer-source-rejected");return;}
            s.producerLinkedSource=proof.sha256();
            Binding raw=binding(id,"shadowtex0");
            requireNoGlError("PRODUCER_RAW_BINDING");
            int[] viewport=new int[4]; GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);
            int fbo=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            int field=GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
            var target=s.targets.get(2);
            s.fieldMainImage=target==null?null:imageInfo(target.getMainTexture());
            s.fieldAltImage=target==null?null:imageInfo(target.getAltTexture());
            requireNoGlError("PRODUCER_FIELD_IMAGE_QUERY");
            if(!s.steady){
                s.producerPredicates=new JsonObject();
                s.producerPredicates.addProperty("pinned",s.pinned);
                s.producerPredicates.addProperty("active_probe",s.activeProbe);
                s.producerPredicates.addProperty("runtime_current",runtimeCurrent(s));
                s.producerPredicates.addProperty("core",core(s));
                s.producerPredicates.addProperty("options",optionsCurrent(s));
                s.producerPredicates.addProperty("unchanged_inputs",unchangedInputs(s));
                s.producerPredicates.addProperty("linked_core",linkedCore(id));
                s.producerPredicates.addProperty("options_class",Iris.getCurrentPack().orElseThrow().getShaderPackOptions().getClass().getName());
                s.producerPredicates.addProperty("values_class",Iris.getCurrentPack().orElseThrow().getShaderPackOptions().getOptionValues().getClass().getName());
                s.producerPredicates.addProperty("core_access_present",CoreAccessHolder.ACCESS!=null);
                s.producerPredicates.addProperty("field_main_image_exact",exactFieldImage(s.fieldMainImage));
                s.producerPredicates.addProperty("field_alt_image_exact",exactFieldImage(s.fieldAltImage));
                s.producerPredicates.addProperty("fbo_complete",GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER)==GL30.GL_FRAMEBUFFER_COMPLETE);
                s.producerPredicates.addProperty("raster_depth",GL11.glIsEnabled(GL11.GL_DEPTH_TEST));
                s.producerPredicates.addProperty("raster_blend",GL11.glIsEnabled(GL11.GL_BLEND));
                s.producerPredicates.addProperty("raster_cull",GL11.glIsEnabled(GL11.GL_CULL_FACE));
            }
            boolean exact=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)==id&&runtimeCurrent(s)&&unchangedInputs(s)&&linkedCore(id)
                && ((WorldRenderingPipeline)s.pipeline).hasFeature(FeatureFlags.HIGHER_SHADOWCOLOR)
                && ((WorldRenderingPipeline)s.pipeline).hasFeature(FeatureFlags.SEPARATE_HARDWARE_SAMPLERS)
                && raw!=null&&raw.texture==s.targets.getDepthTexture().getTextureId()
                && raw.compare==GL11.GL_NONE&&raw.width==2048&&raw.height==2048&&raw.baseLevel==0&&knownDepth(raw)
                && s.targets.getResolution()==2048&&target!=null&&target.getWidth()==2048&&target.getHeight()==2048
                && target.getInternalFormat().name().equals("R32F")&&exactFieldImage(s.fieldMainImage)&&exactFieldImage(s.fieldAltImage)
                && field>0&&(field==target.getMainTexture()||field==target.getAltTexture())
                && GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE)==GL11.GL_TEXTURE
                && GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL30.GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL)==0
                && fbo>0&&GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER)==GL30.GL_FRAMEBUFFER_COMPLETE
                && GL11.glGetInteger(GL20.GL_DRAW_BUFFER0)==GL30.GL_COLOR_ATTACHMENT0
                && java.util.Arrays.equals(viewport,new int[]{0,0,128,128})
                && !GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)&&!GL11.glIsEnabled(GL11.GL_STENCIL_TEST)
                && !GL11.glIsEnabled(GL11.GL_DEPTH_TEST)&&!GL11.glIsEnabled(GL11.GL_BLEND)
                && !GL11.glIsEnabled(GL11.GL_CULL_FACE)&&!GL11.glIsEnabled(GL11.GL_COLOR_LOGIC_OP)
                && !GL11.glIsEnabled(GL30.GL_RASTERIZER_DISCARD)&&GL11.glGetInteger(GL13.GL_SAMPLES)==0;
            var mask=BufferUtils.createByteBuffer(4); GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK,mask);
            exact=exact&&mask.get(0)!=0;
            s.depth=raw==null?0:raw.texture; s.field=field; s.rawBinding=raw; s.producerRawRedSwizzle=redSwizzle(raw); s.producerFbo=fbo; s.producerViewport=viewport;
            if(s.steady)exact=exact&&BslRawQueryContract.steadyFiniteDomain(rawState(raw,s.producerRawRedSwizzle))
                && BslRawQueryContract.resourcesMatch(rawState(raw,s.producerRawRedSwizzle),rawState(raw,s.producerRawRedSwizzle))
                && BslRawQueryContract.steadyProducerReviewed(driver(),s.producerLinkedSource,s.environment);
            requireNoGlError("PRODUCER_COMPLETE");
            if(!s.epochs.producer(context(s),s.depth,s.field,exact)) { s.reject("PRODUCER_RESOURCE_OR_EPOCH_REJECTED"); write(s,"producer-rejected"); return; }
            s.signal();
            if(uniform(id,"shaderlodBslShadowFieldQualified")!=1){s.reject("PRODUCER_LINKED_CERT_NOT_ONE");return;}
            s.reason="PRODUCER_ACTIVE_RECEIVER_NATIVE";
            write(s,"producer-bound");
            witness(s,"producer-bound");
        } catch(RuntimeException|LinkageError unavailable){s.reject("PRODUCER_UNAVAILABLE:"+unavailable.getClass().getSimpleName()+":"+unavailable.getMessage());write(s,"producer-error");}
    }
    public static boolean isProducer(WorldRenderingPipeline pipeline, Program program) {
        return current!=null&&current.pipeline==pipeline&&current.producer==program;
    }
    /** Untimed full current depth/field join after the actual quad returns.
     * Every useful cell including frame stamp is checked; stamp alone is never
     * coverage proof. No history/minimum estimate, no 4x4 census substitution. */
    public static void fieldReturned(WorldRenderingPipeline pipeline) {
        State s=current;
        if(s==null||!(s.activeProbe||s.steady)||s.pipeline!=pipeline)return;
        try {
            if(!s.epochs.producerUniform(context(s)))return;
            requireNoGlError("FIELD_QUAD_RETURNED");
            // Steady coverage is the reviewed complete producer source + exact
            // native quad normal return/current state. The GLSL receiver still
            // checks the exact stamp every invocation; never read it on CPU.
            boolean valid=s.epochs.producerUniform(context(s))&&runtimeCurrent(s)&&unchangedInputs(s)
                && (s.steady || validateField(s));
            if(s.steady)s.rawSameDomain=BslRawQueryContract.steadyFiniteDomain(rawState(s.rawBinding,s.producerRawRedSwizzle));
            s.fieldValidated=valid; if(!s.steady)s.observedFields++;
            s.epochs.fieldReturned(context(s),valid);
            s.signal(); s.reason=valid?"COMPLETE_FIELD_SUPPORT_BOUNDS_PENDING":"FIELD_VALIDATION_FAILED";
            write(s,"field-returned");
            if(WITNESS!=null)WITNESS.producerReturned(s.epochs.identity(),valid&&s.steady);
            witness(s,"field-returned");
        }catch(RuntimeException|LinkageError unavailable){s.reject("FIELD_READBACK_UNAVAILABLE:"+unavailable.getClass().getSimpleName()+":"+unavailable.getMessage());write(s,"field-error");}
    }
    private static void requireNoGlError(String operation) {
        int error=GL11.glGetError();
        if(error!=GL11.GL_NO_ERROR)throw new IllegalStateException(operation+"_GL_ERROR_"+error);
    }
    private static float[] readTexture(int texture, int format) {
        if(WITNESS!=null)WITNESS.forbidden("texture_readback");
        requireNoGlError("BEFORE_READBACK_TEXTURE_"+texture);
        int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE), previous=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int pbo=GL11.glGetInteger(GL21_PIXEL_PACK_BUFFER_BINDING), alignment=GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
        int rowLength=GL11.glGetInteger(GL11.GL_PACK_ROW_LENGTH), skipRows=GL11.glGetInteger(GL11.GL_PACK_SKIP_ROWS), skipPixels=GL11.glGetInteger(GL11.GL_PACK_SKIP_PIXELS);
        boolean swap=GL11.glGetBoolean(GL11.GL_PACK_SWAP_BYTES), lsb=GL11.glGetBoolean(GL11.GL_PACK_LSB_FIRST);
        int imageHeight=GL11.glGetInteger(0x806C),skipImages=GL11.glGetInteger(0x806B);
        try {
            GL15_bindPack(0); GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT,4);
            GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH,0); GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS,0); GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS,0); GL11.glPixelStorei(GL11.GL_PACK_SWAP_BYTES,0); GL11.glPixelStorei(GL11.GL_PACK_LSB_FIRST,0); GL11.glPixelStorei(0x806C,0); GL11.glPixelStorei(0x806B,0);
            requireNoGlError("READBACK_PACK_SETUP");
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
            requireNoGlError("READBACK_BIND_TEXTURE_"+texture);
            var buffer=BufferUtils.createFloatBuffer(2048*2048);
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,format,GL11.GL_FLOAT,buffer);
            int error=GL11.glGetError();if(error!=GL11.GL_NO_ERROR)throw new IllegalStateException("READBACK_TEXTURE_"+texture+"_FORMAT_"+format+"_GL_ERROR_"+error);
            float[] values=new float[2048*2048]; buffer.get(values); return values;
        }finally{
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,previous); GL13.glActiveTexture(active);
            GL15_bindPack(pbo); GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT,alignment);
            GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH,rowLength); GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS,skipRows); GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS,skipPixels); GL11.glPixelStorei(GL11.GL_PACK_SWAP_BYTES,swap?1:0); GL11.glPixelStorei(GL11.GL_PACK_LSB_FIRST,lsb?1:0); GL11.glPixelStorei(0x806C,imageHeight); GL11.glPixelStorei(0x806B,skipImages);
        }
    }
    private static final int GL21_PIXEL_PACK_BUFFER_BINDING=0x88ED;
    private static void GL15_bindPack(int pbo){org.lwjgl.opengl.GL15.glBindBuffer(0x88EB,pbo);}
    private static void saveReadback(State s,String name,float[] data) {
        String root=System.getProperty("shaderlod.dev.bslFieldEvidence",""); if(root.isBlank())return;
        try{
            var b=java.nio.ByteBuffer.allocate(data.length*4).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            for(float v:data)b.putFloat(v);
            Path dir=Path.of(root);Files.createDirectories(dir);
            var id=s.epochs.identity();
            Files.write(dir.resolve("render-"+id.renderEpoch()+"-shadow-"+id.shadowEpoch()+"-"+name+".f32le"),b.array());
        }catch(Exception unavailable){/* readback validation remains separate from saving */}
    }
    private static void saveEchoReadback(State s,String name,float[] values) {
        try {
            var bytes=java.nio.ByteBuffer.allocate(values.length*4).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            for(float value:values)bytes.putFloat(value);
            var id=s.epochs.identity();
            Path dir=Path.of(System.getProperty("shaderlod.dev.bslFieldEvidence"));
            Path path=dir.resolve("render-"+id.renderEpoch()+"-shadow-"+id.shadowEpoch()+"-"+name+".f32le");
            Files.write(path,bytes.array());
            s.gpuEcho.receipt().put(name+"_readback",Map.of("path",path.toAbsolutePath().toString(),
                "bytes",bytes.array().length,"sha256",sha(bytes.array())));
        }catch(Exception failure){throw new IllegalStateException("ECHO_READBACK_EXPORT_FAILED_"+name,failure);}
    }
    private static boolean validateField(State s) {
        if(s.steady)throw new IllegalStateException("STEADY_READBACK_FORBIDDEN");
        if(Boolean.getBoolean("shaderlod.dev.bslGpuValueEcho")) {
            // Finite diagnostic only. Actual native producer has returned normally;
            // its raw image/level/sampler and frame identity remain unchanged here.
            var identity=s.epochs.identity();
            if(!runtimeCurrent(s)||!unchangedInputs(s)||s.producerProof==null
                || NATIVE_SOURCES.forOwner(s.producer,s.producer.getProgramId())!=s.producerProof
                || GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)!=s.producer.getProgramId())
                throw new IllegalStateException("ECHO_CURRENT_SOURCE_EPOCH_REJECTED");
            String output=System.getProperty("shaderlod.dev.bslFieldEvidence","");
            if(output.isBlank())throw new IllegalStateException("ECHO_EVIDENCE_PATH_REQUIRED");
            BslGpuValueEcho.check("ECHO_BEFORE_FRESH_RAW_BINDING");
            Binding raw=binding(s.producer.getProgramId(),"shadowtex0");
            int rawRed=redSwizzle(raw);
            BslGpuValueEcho.check("ECHO_FRESH_RAW_QUERY");
            if(!BslRawQueryContract.resourcesMatch(rawState(s.rawBinding,s.producerRawRedSwizzle),rawState(raw,rawRed)))
                throw new IllegalStateException("ECHO_FRESH_RAW_BINDING_MISMATCH");
            if(WITNESS!=null)WITNESS.forbidden("gpu_echo");
            float[] echo=s.gpuEcho.run(s.producerProof,s.depth,raw.sampler,
                uniform(s.producer.getProgramId(),"shadowtex0"),s.producer.getProgramId(),Path.of(output));
            BslGpuValueEcho.check("ECHO_BRIDGE_RESTORED");
            if(!identity.equals(s.epochs.identity())||!runtimeCurrent(s)||!unchangedInputs(s))
                throw new IllegalStateException("ECHO_IDENTITY_CHANGED");
            float[] field=readTexture(s.field,GL11.GL_RED);
            float[] cpu=readTexture(s.depth,GL11.GL_DEPTH_COMPONENT); // Auxiliary only.
            s.gpuFieldValidation=BslGpuFieldValidation.compare(echo,field,cpu,identity.frame());
            s.rawSameDomain=s.gpuFieldValidation.normalDomain();
            float[] useful=new float[128*128];
            for(int y=0;y<128;y++)System.arraycopy(field,y*2048,useful,y*128,128);
            saveEchoReadback(s,"gpu-echo-2048",echo);saveEchoReadback(s,"depth-cpu-auxiliary-2048",cpu);
            saveEchoReadback(s,"field-useful-128",useful);
            return s.gpuFieldValidation.valid();
        }
        float[] depth=readTexture(s.depth,GL11.GL_DEPTH_COMPONENT), field=readTexture(s.field,GL11.GL_RED);
        // Separate finite normal-value gate; CPU field equality alone is not a
        // cross-sampler conversion certificate. Normal values avoid denorm/NaN.
        s.rawSameDomain=true;
        for(float v:depth) if(!BslRawQueryContract.normalDepth(v)){s.rawSameDomain=false;break;}
        float[] useful=new float[128*128];
        for(int y=0;y<128;y++)System.arraycopy(field,y*2048,useful,y*128,128);
        saveReadback(s,"depth-2048",depth);saveReadback(s,"field-useful-128",useful);
        int frame=SystemTimeUniforms.COUNTER.getAsInt();
        if(field[127*2048+127]!=-(float)(frame+1))return false;
        for(int cy=0;cy<128;cy++)for(int cx=0;cx<128;cx++) {
            if(cx==127&&cy==127)continue;
            float min=1;
            for(int y=0;y<16;y++)for(int x=0;x<16;x++){
                float z=depth[(cy*16+y)*2048+cx*16+x]; min=z>=0&&z<=1?Math.min(min,z):0;
            }
            if(field[cy*2048+cx]!=min)return false;
        }
        return true;
    }
    public static void shadowReturned(WorldRenderingPipeline pipeline) {
        if(current==null||!(current.activeProbe||current.steady)||current.pipeline!=pipeline)return;
        current.epochs.shadowReturned(context(current)); current.signal();
        write(current,"shadow-returned");
        witness(current,"shadow-returned");
    }
    /** Actual opaque Sodium draw seam, after uniforms AND sampler binding.
     * Record linked field/HW joins. The stage deliberately refuses to turn
     * finite scene examples into a universal texture-coordinate/error bound. */
    public static void beforeTerrainDraw() {
        State s=current;
        if(!requested()||s==null||!(s.activeProbe||s.steady)||!s.pinned)return;
        // Reset even for unrecognized/nonterrain programs. A previous draw's
        // qualification must never leak to a different or shadow pass.
        s.rawConsumerAuthorized=s.consumerAuthorized=false;s.signal();
        int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        if(program<=0||(GL20.glGetUniformLocation(program,"shaderlodBslShadowFieldQualified")<0
                && GL20.glGetUniformLocation(program,"shaderlodBslRawQueryQualified")<0)
                ||s.producer!=null&&program==s.producer.getProgramId())return;
        if ("1".equals(Iris.getCurrentPack().orElseThrow().getShaderPackOptions().getOptionValues()
                .getStringValueOrDefault("SHADERLOD_BSL_RAW_QUERY"))) {
            beforeRawTerrainDraw(s,program); return;
        }
        if(s.steady)return; // Hardware support unresolved.
        try {
            Binding hardware=binding(program,"shadowtex0HW"), field=binding(program,"shadowcolor2");
            boolean resource=s.fieldValidated&&linkedCore(program)&&runtimeCurrent(s)&&unchangedInputs(s)
                &&hardware!=null&&hardware.texture==s.depth&&hardware.compare==0x884E&&hardware.compareFunction==GL11.GL_LEQUAL
                &&hardware.minFilter==GL11.GL_LINEAR&&hardware.magFilter==GL11.GL_LINEAR
                &&hardware.width==2048&&hardware.height==2048&&hardware.baseLevel==0&&knownDepth(hardware)
                &&hardware.wrapS==0x812F&&hardware.wrapT==0x812F
                &&(!hardware.anisotropySupported||hardware.anisotropy!=null&&hardware.anisotropy==1.0f)
                &&field!=null&&field.texture==s.field
                &&field.compare==GL11.GL_NONE&&field.width==2048&&field.height==2048&&field.baseLevel==0&&field.format==GL30.GL_R32F;
            // A runtime bound certificate must be tied to this linked terrain
            // source and native9 support. No system-property "qualified=1" route.
            s.terrainSource=receiverAttachedSource(program);
            resource=resource&&s.terrainSource!=null; s.hardwareBinding=hardware; s.fieldBinding=field;
            boolean support=s.support!=null&&s.support.bounded()&&s.support.identity().equals(s.epochs.identity())
                &&s.support.producerSourceSha256().equals(s.producerLinkedSource)&&s.support.terrainSourceSha256().equals(s.terrainSource)
                &&s.support.driver().equals(driver())&&hardware!=null&&s.support.nativeDepthFormat()==hardware.format
                &&s.support.compareFunction()==hardware.compareFunction&&s.support.minFilter()==hardware.minFilter&&s.support.magFilter()==hardware.magFilter;
            s.consumerAuthorized=resource&&s.epochs.consumer(context(s),hardware.texture,field.texture,support);
            s.reason=s.consumerAuthorized?"RECEIVER_RESOURCE_AND_BOUND_CERT_JOINED"
                :resource&&s.epochs.consumer(context(s),hardware.texture,field.texture,true)
                ?"RECEIVER_RESOURCE_JOIN_OK_DRIVER_SUPPORT_BOUND_MISSING":"RECEIVER_RESOURCE_OR_EPOCH_REJECTED";
            s.signal(); if(s.loggedTerrains.add(program))write(s,"terrain-before-draw");
        }catch(RuntimeException|LinkageError unavailable){s.reject("RECEIVER_UNAVAILABLE:"+unavailable.getClass().getSimpleName());write(s,"receiver-error");}
    }
    private static int redSwizzle(Binding b) {
        if(b==null)return -1;
        int previous=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try { GL11.glBindTexture(GL11.GL_TEXTURE_2D,b.texture);
            return GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,0x8E42);
        } finally { GL11.glBindTexture(GL11.GL_TEXTURE_2D,previous); }
    }
    private static BslRawQueryContract.Raw rawState(Binding b,int swizzle) {
        return b==null?null:new BslRawQueryContract.Raw(b.texture,b.sampler,b.compare,b.minFilter,b.magFilter,
            b.width,b.height,b.format,b.baseLevel,b.maxLevel,b.depthBits,b.depthType,b.wrapS,b.wrapT,
            b.anisotropySupported,b.anisotropy,swizzle);
    }
    private static void beforeRawTerrainDraw(State s,int program) {
        // Reset before every actual consuming draw, including rejection.
        s.rawConsumerAuthorized=false; s.consumerAuthorized=false;
        try {
            requireNoGlError("RAW_RECEIVER_START");
            Binding raw=binding(program,"shadowtex0"), hardware=binding(program,"shadowtex0HW"),
                field=binding(program,"shadowcolor2");
            var draw=s.steady?STEADY_PROGRAMS.current(s.pipeline,program,org.lwjgl.opengl.GL.getCapabilities()):null;
            s.terrainSource=s.steady?(draw==null?null:draw.proof().sha256()):receiverAttachedSource(program);
            s.terrainRawBinding=raw;s.hardwareBinding=hardware;s.fieldBinding=field;
            boolean resource=s.fieldValidated && s.rawSameDomain && linkedCore(program)
                && GL20.glGetUniformLocation(program,"shaderlodBslRawQueryQualified")>=0
                && runtimeCurrent(s) && unchangedInputs(s)
                && BslRawQueryContract.resourcesMatch(rawState(s.rawBinding,s.producerRawRedSwizzle),rawState(raw,redSwizzle(raw)))
                && raw.texture==s.depth
                && hardware!=null && hardware.texture==s.depth && hardware.compare==0x884E
                && hardware.compareFunction==GL11.GL_LEQUAL && hardware.minFilter==GL11.GL_LINEAR
                && hardware.magFilter==GL11.GL_LINEAR && hardware.width==2048 && hardware.height==2048
                && hardware.baseLevel==0 && knownDepth(hardware)
                && hardware.wrapS==0x812F && hardware.wrapT==0x812F
                && (!hardware.anisotropySupported || hardware.anisotropy!=null && hardware.anisotropy==1.0f)
                && field!=null && field.texture==s.field && field.compare==GL11.GL_NONE
                && field.width==2048 && field.height==2048 && field.baseLevel==0
                && field.format==GL30.GL_R32F && redSwizzle(field)==0x1903
                && uniform(program,"shadowtex0")!=uniform(program,"shadowtex0HW")
                && BslGpuValueEcho.simple(field.minFilter)
                && BslGpuValueEcho.simple(field.magFilter);
            boolean source=s.steady
                ? BslRawQueryContract.steadySourcesReviewed(driver(),s.producerLinkedSource,s.terrainSource,s.environment)
                : BslRawQueryContract.sourcesReviewed(driver(),s.producerLinkedSource,s.terrainSource);
            s.rawConsumerAuthorized=resource && source
                && s.epochs.consumer(context(s),raw.texture,field.texture,true);
            s.reason=s.rawConsumerAuthorized?"EXPLICIT_RAW_QUERY_CURRENT_RESOURCE_AND_SOURCE_JOINED"
                :resource?"EXPLICIT_RAW_QUERY_SOURCE_VALUE_CONTRACT_REVIEW_PENDING"
                :"EXPLICIT_RAW_QUERY_RESOURCE_VALUE_OR_EPOCH_REJECTED";
            requireNoGlError("RAW_RECEIVER_COMPLETE");
            s.signal();if(!s.steady&&s.loggedTerrains.add(program))write(s,"raw-terrain-before-draw");
            witness(s,"raw-terrain-before-draw");
        } catch(RuntimeException|LinkageError unavailable) {
            s.reject("RAW_RECEIVER_UNAVAILABLE:"+unavailable.getClass().getSimpleName()+":"+unavailable.getMessage());write(s,"raw-receiver-error");
        }
    }
    /** Called by root's serial untimed support qualification after actual field,
     * linked source and hardware sampler evidence. This cannot be granted from
     * JVM options or a shader uniform value. Every new render resets it. */
    public static boolean acceptReceiverSupport(BslFieldReceiverSupport proof, Path actualBoundEvidence) {
        State s=current;
        try {
            if(s==null||proof==null||!proof.bounded()||!s.fieldValidated
                    ||!proof.identity().equals(s.epochs.identity())||!runtimeCurrent(s)
                    ||!sha(Files.readAllBytes(actualBoundEvidence)).equals(proof.fullSupportBoundEvidenceSha256()))return false;
            s.support=proof; // Actual source/sampler/driver join is repeated before each terrain draw.
            return true;
        }catch(Exception|LinkageError unavailable){return false;}
    }
    public static long sodiumBeforeLink(int program) {
        return requested()?STEADY_PROGRAMS.beforeLink(program):-1;
    }
    public static void sodiumLinked(long token,int program) {
        if(!requested())return;
        try {requireNoGlError("SODIUM_LINK_SOURCE_START");
            var stages=driverAttachedStages(program);requireNoGlError("SODIUM_LINK_SOURCE_END");
            STEADY_PROGRAMS.linked(token,program,true,stages);}
        catch(RuntimeException|LinkageError unavailable){STEADY_PROGRAMS.linked(token,program,false,null);}
    }
    public static void sodiumLinkFailed(long token,int program) {STEADY_PROGRAMS.linked(token,program,false,null);}
    public static void sodiumLinkReturned(long token,net.caffeinemc.mods.sodium.client.gl.shader.GlProgram<?> program) {
        if(token>=0&&program!=null)STEADY_PROGRAMS.linkReturned(token,program,program.handle());
    }
    public static void sodiumLinkFinished(long token,int program) {
        if(token>=0)STEADY_PROGRAMS.linkFinished(token,program);
    }
    public static void sodiumBuilt(WorldRenderingPipeline pipeline,net.irisshaders.iris.pipeline.programs.SodiumPrograms.Pass pass,
            net.caffeinemc.mods.sodium.client.gl.shader.GlProgram<?> program) {
        if(!requested()||program==null)return;
        STEADY_PROGRAMS.built(pipeline,program,program.getInterface(),program.handle(),pass.name(),org.lwjgl.opengl.GL.getCapabilities());
    }
    public static void sodiumSetupBegin(){if(requested()){STEADY_PROGRAMS.setupBegin();clearReceiver();}}
    public static void sodiumSetupReturned(Object shader){if(requested())STEADY_PROGRAMS.setupReturned(shader);}
    public static void sodiumReset(Object shader){if(requested()){STEADY_PROGRAMS.reset(shader);clearReceiver();}}
    public static void sodiumDeleted(net.caffeinemc.mods.sodium.client.gl.shader.GlProgram<?> program) {
        if(requested()){STEADY_PROGRAMS.deleted(program,program.handle());clearReceiver();}
    }
    /** HEAD/delete phases revoke CPU eligibility only. A prior program's
     * native callback may still be installed while Sodium binds the next one.
     * Native update pushes zero after setup HEAD; draw/producer safe phases
     * notify only an exact live owner with its actual program bound. */
    private static void clearReceiver(){if(current!=null)current.consumerAuthorized=current.rawConsumerAuthorized=false;}
    private static String driver(){return GL11.glGetString(GL11.GL_VENDOR)+"|"+GL11.glGetString(GL11.GL_RENDERER)+"|"+GL11.glGetString(GL11.GL_VERSION);}
    public static long nativeBeforeLink(int program) {
        return requested()?NATIVE_SOURCES.beforeLink(program):-1;
    }
    public static void nativeLinkFailed(long token,int program){NATIVE_SOURCES.linked(token,program,false,null);}
    public static void nativeLinkedSources(long token,int program,String name,net.irisshaders.iris.gl.shader.GlShader[] shaders) {
        if(!requested())return;
        try{
            boolean exact="shadowcomp1".equals(name)&&shaders!=null&&shaders.length==2
                &&GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)==GL11.GL_TRUE;
            var stages=exact?driverAttachedStages(program):null;
            if(stages!=null){
                int[] count={0}, ids=new int[2];GL20.glGetAttachedShaders(program,count,ids);
                java.util.Set<Integer> actual=new java.util.HashSet<>();for(int id:ids)actual.add(id);
                for(var shader:shaders)exact=exact&&shader!=null&&actual.contains(shader.getHandle());
            }
            var proof=NATIVE_SOURCES.linked(token,program,exact,stages);
            if(proof!=null)exportStages(program,"native-link-"+proof.generation(),proof.stages());
        }catch(RuntimeException|LinkageError unavailable){NATIVE_SOURCES.linked(token,program,false,null);}
    }
    public static void nativeProgramDestroyed(Program program) {
        if(!requested())return;
        int id=program.getProgramId();NATIVE_SOURCES.remove(program,id);
        for(State s:STATES.values())if(s.producer==program){
            s.producerProof=null;s.producerLinkedSource=null;s.pinned=false;s.fieldValidated=false;
            s.reject("NATIVE_PROGRAM_DESTROYED");
        }
    }
    /** Driver inventory with complete byte-length guard. Empty, unexpected,
     * duplicated or truncated stages fail closed; never hash an empty string. */
    private static Map<Integer,String> driverAttachedStages(int program) {
        if(WITNESS!=null)WITNESS.forbidden("driver_source_query");
        if(program<=0||!GL20.glIsProgram(program)||GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)!=GL11.GL_TRUE)return null;
        int attached=GL20.glGetProgrami(program,GL20.GL_ATTACHED_SHADERS);
        if(attached!=2)return null;
        int[] count={0},ids=new int[2];GL20.glGetAttachedShaders(program,count,ids);
        if(count[0]!=2||ids[0]<=0||ids[0]==ids[1])return null;
        Map<Integer,String> stages=new TreeMap<>();
        for(int id:ids){
            int type=GL20.glGetShaderi(id,GL20.GL_SHADER_TYPE),length=GL20.glGetShaderi(id,GL20.GL_SHADER_SOURCE_LENGTH);
            if(length<=1||length>1_048_576||stages.containsKey(type))return null;
            String text=GL20.glGetShaderSource(id);
            if(text==null||text.isBlank()||text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length+1!=length)return null;
            stages.put(type,text);
        }
        return BslFieldSourceRecords.completeDigest(stages)==null?null:stages;
    }
    /** Sodium keeps linked stages attached after delete requests; query the
     * actually bound program fresh on every consuming draw. No numeric-ID
     * cache can survive deletion, relink, reload or GL name reuse. */
    private static String receiverAttachedSource(int program) {
        var stages=driverAttachedStages(program);
        String hash=BslFieldSourceRecords.completeDigest(stages);
        if(hash==null)return null;
        State s=current;
        exportStages(program,"sodium-source-"+hash,stages);
        return hash;
    }
    private static void exportStages(int program,String lifetime,Map<Integer,String> stages) {
        if(steadyRequested())return;
        String output=System.getProperty("shaderlod.dev.bslFieldEvidence","");if(output.isBlank())return;
        if(WITNESS!=null)WITNESS.forbidden("source_export");
        try{
            Path dir=Path.of(output);Files.createDirectories(dir);
            for(var stage:stages.entrySet()){
                Path p=dir.resolve(lifetime+"-program-"+program+"-stage-"+stage.getKey()+".glsl");
                if(!Files.exists(p))Files.writeString(p,stage.getValue(),java.nio.file.StandardOpenOption.CREATE_NEW);
            }
        }catch(Exception unavailable){/* Missing export does not create source evidence. */}
    }
    private static JsonObject signalDiagnostics(State s,int program) {
        JsonObject result=new JsonObject();int count=0;
        for(Signal signal:s.signals)if(signal.program==program&&count++<4) {
            JsonObject r=new JsonObject();r.addProperty("builder_class",signal.builder.getClass().getName());
            r.addProperty("registered_program",signal.program);r.addProperty("listener_present",signal.listener);
            r.addProperty("registration_generation",signal.registration==null?-1:signal.registration.generation());
            r.addProperty("live_owner_present",signalOwner(s,program)!=null);
            r.addProperty("registration_matches_live_owner",signal.registration!=null&&signal.registration==signalOwner(s,program));
            r.addProperty("finite_attach_count",signal.finiteAttachCount);r.addProperty("finite_fire_count",signal.finiteFireCount);
            r.addProperty("finite_last_fire",signal.finiteLastFire);result.add(signal.name,r);
        }
        return result;
    }
    private static void write(State s,String event) {
        if(s.steady)return;
        String output=System.getProperty("shaderlod.dev.bslFieldEvidence","");
        if(output.isBlank() || receiptSequence>=64)return;
        try {
            JsonObject r=new JsonObject(); var identity=s.epochs.identity();
            r.addProperty("event",event); r.addProperty("reason",s.reason); r.addProperty("never_time",true);
            r.add("producer_predicates",s.producerPredicates);
            r.addProperty("probe_field_limit",probeLimit()); r.addProperty("fields_observed",s.observedFields);
            r.addProperty("probe_attempts",s.probeAttempts); r.addProperty("probe_attempt_limit",3); r.addProperty("receipt_limit",64);
            r.addProperty("archive_sha256",ARCHIVE); r.addProperty("native_options_sha256",OPTIONS);
            r.addProperty("pipeline_epoch",identity.pipelineEpoch()); r.addProperty("render_epoch",identity.renderEpoch());
            r.addProperty("shadow_epoch",identity.shadowEpoch()); r.addProperty("frame_counter",identity.frame());
            r.addProperty("producer_program",s.producer==null?0:s.producer.getProgramId());
            r.addProperty("current_program",GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));
            r.add("uniform_signal_diagnostics",signalDiagnostics(s,GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)));
            r.addProperty("uniform_builder_access_available",UniformBuilderAccess.PROGRAM!=null);
            r.addProperty("sodium_registration_attempts",s.sodiumRegistrationAttempts);
            r.addProperty("depth_texture",s.depth); r.addProperty("field_texture",s.field);
            r.addProperty("producer_program_source_sha256",s.producerSource); r.addProperty("producer_linked_driver_source_sha256",s.producerLinkedSource);
            JsonObject sourceProof=new JsonObject();
            if(s.producerProof!=null){
                sourceProof.addProperty("generation",s.producerProof.generation());sourceProof.addProperty("program",s.producerProof.program());
                sourceProof.addProperty("route",s.producerProof.route());sourceProof.addProperty("sha256",s.producerProof.sha256());
                JsonObject stages=new JsonObject();for(var stage:s.producerProof.stages().entrySet()){
                    JsonObject item=new JsonObject();item.addProperty("sha256",sha(stage.getValue()));
                    item.addProperty("utf8_bytes",stage.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8).length);stages.add(String.valueOf(stage.getKey()),item);
                }sourceProof.add("stages",stages);
            }r.add("producer_pre_detach_source_proof",sourceProof); r.addProperty("terrain_attached_source_sha256",s.terrainSource);
            r.addProperty("driver",driver()); r.addProperty("steady_environment_sha256",s.environment);
            r.add("runtime_environment",s.environmentDescription); r.addProperty("producer_fbo",s.producerFbo);
            r.add("producer_viewport",new Gson().toJsonTree(s.producerViewport));
            r.add("producer_raw_binding",new Gson().toJsonTree(s.rawBinding));
            r.add("field_main_actual_image",new Gson().toJsonTree(s.fieldMainImage));
            r.add("field_alt_actual_image",new Gson().toJsonTree(s.fieldAltImage));
            r.addProperty("field_pair_physical_bytes",33554432);
            r.add("terrain_hardware_binding",new Gson().toJsonTree(s.hardwareBinding));
            r.add("terrain_field_binding",new Gson().toJsonTree(s.fieldBinding));
            r.add("terrain_raw_binding",new Gson().toJsonTree(s.terrainRawBinding));
            r.add("gpu_value_echo",new Gson().toJsonTree(s.gpuEcho.receipt()));
            r.add("gpu_field_validation",new Gson().toJsonTree(s.gpuFieldValidation));
            r.addProperty("raw_finite_normal_value_domain",s.rawSameDomain);
            r.addProperty("explicit_raw_receiver_authorized",s.rawConsumerAuthorized);
            r.addProperty("explicit_raw_qualified_uniform",uniform(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),"shaderlodBslRawQueryQualified"));
            r.add("receiver_support",new Gson().toJsonTree(s.support));
            r.addProperty("complete_field_validated",s.fieldValidated); r.addProperty("receiver_authorized",s.consumerAuthorized);
            r.addProperty("qualified_uniform",uniform(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),"shaderlodBslShadowFieldQualified"));
            Path dir=Path.of(output); Files.createDirectories(dir);
            Files.writeString(dir.resolve(String.format("%06d-%s.json",++receiptSequence,event)),new Gson().toJson(r)+"\n");
        }catch(Exception|LinkageError unavailable){/* output unavailable never grants authorization */}
    }
    /** Untimed snapshot only; source hashes come from existing live object proofs.
     * binding/imageInfo/redSwizzle restore their only mutations. No pixel/source query. */
    private static int[] witnessGlState() {
        int[] viewport=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);
        return new int[]{GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),
            GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D),GL11.glGetInteger(GL33.GL_SAMPLER_BINDING),
            GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),
            GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),viewport[0],viewport[1],viewport[2],viewport[3]};
    }
    private static void witness(State s,String phase) {
        if(WITNESS==null||!WITNESS.sampling()||("raw-terrain-before-draw".equals(phase)&&!WITNESS.wantsDraw(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM))))return;
        try {
            requireNoGlError("STEADY_WITNESS_BEFORE");int[] before=witnessGlState();
            int program=before[0];boolean drawPhase="raw-terrain-before-draw".equals(phase);
            var draw=drawPhase?STEADY_PROGRAMS.current(s.pipeline,program,org.lwjgl.opengl.GL.getCapabilities()):null;
            var proof=drawPhase?(draw==null?null:draw.proof()):s.producerProof;
            Object owner=drawPhase?(draw==null?null:draw.program()):s.producer;
            boolean sourceLive=proof!=null&&owner!=null&&signalOwner(s,proof.program())==proof;
            boolean eligible=s.steady&&!s.activeProbe&&runtimeCurrent(s)&&core(s)&&unchangedInputs(s)
                &&sourceLive&&(drawPhase?s.rawConsumerAuthorized&&uniform(program,"shaderlodBslRawQueryQualified")==1
                    :"shadow-returned".equals(phase)?s.epochs.fieldComplete():program==s.producer.getProgramId()&&(!"producer-bound".equals(phase)||uniform(program,"shaderlodBslShadowFieldQualified")==1));
            JsonObject r=new JsonObject();
            r.addProperty("phase",phase);r.addProperty("reason",s.reason);r.addProperty("current_program",program);
            r.addProperty("owner_class",owner==null?null:owner.getClass().getName());
            r.addProperty("owner_identity",owner==null?0:System.identityHashCode(owner));
            r.addProperty("typed_pass",draw==null?null:draw.pass());
            r.addProperty("program",proof==null?0:proof.program());r.addProperty("generation",proof==null?-1:proof.generation());
            r.addProperty("cached_source_sha256",proof==null?null:proof.sha256());r.addProperty("source_route",proof==null?null:proof.route());
            r.addProperty("live_owner_generation_matches",sourceLive);
            r.addProperty("registered_core_current",core(s));r.addProperty("core_state_class",s.coreState==null?null:s.coreState.getClass().getName());
            JsonObject values=new JsonObject();for(String name:new String[]{"shaderlodBridgeVersion","shaderlodEnabled","shaderlodPreset","frameCounter","shaderlodBslShadowFieldQualified","shaderlodBslRawQueryQualified","shadowtex0","shadowtex0HW","shadowcolor2"})values.addProperty(name,uniform(program,name));
            if(program>0)for(String name:new String[]{"shaderlodNear","shaderlodFar"}) {
                int location=GL20.glGetUniformLocation(program,name);if(location>=0)values.addProperty(name,GL20.glGetUniformf(program,location));
            }
            r.add("actual_linked_uniform_values",values);r.add("dynamic_registrations",signalDiagnostics(s,proof==null?0:proof.program()));
            r.addProperty("environment_sha256",s.environment);r.addProperty("driver",driver());
            r.addProperty("archive_sha256",ARCHIVE);r.addProperty("native_options_sha256",OPTIONS);
            Binding actualRaw=binding(program,"shadowtex0"),actualHardware=binding(program,"shadowtex0HW"),actualField=binding(program,"shadowcolor2");
            r.add("actual_raw_binding",new Gson().toJsonTree(actualRaw));r.addProperty("actual_raw_red_swizzle",redSwizzle(actualRaw));
            r.add("actual_hardware_binding",new Gson().toJsonTree(actualHardware));
            r.add("actual_field_binding",new Gson().toJsonTree(actualField));r.addProperty("actual_field_red_swizzle",redSwizzle(actualField));
            var target=s.targets.get(2);
            r.add("actual_field_main_image",new Gson().toJsonTree(imageInfo(target.getMainTexture())));
            r.add("actual_field_alt_image",new Gson().toJsonTree(imageInfo(target.getAltTexture())));
            r.addProperty("depth_texture",s.depth);r.addProperty("current_field_texture",s.field);
            r.addProperty("producer_fbo",s.producerFbo);r.add("producer_viewport",new Gson().toJsonTree(s.producerViewport));
            r.addProperty("draw_fbo",before[4]);
            int colorType=before[4]==0?-1:GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
            int colorName=before[4]==0?-1:GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
            r.addProperty("color0_type",colorType);r.add("color0_actual_image",new Gson().toJsonTree(colorType==GL11.GL_TEXTURE?imageInfo(colorName):null));r.addProperty("fbo_status",GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER));
            r.addProperty("color0_object",before[4]==0?-1:GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME));
            r.addProperty("color0_level",colorType!=GL11.GL_TEXTURE?-1:GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL30.GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL));
            r.addProperty("field_complete_source_contract",s.fieldValidated);r.addProperty("raw_finite_domain",s.rawSameDomain);
            r.addProperty("stamp_expected_from_source",-(s.epochs.identity().frame()+1));
            r.addProperty("stamp_cpu_read",false);r.addProperty("stamp_route","reviewed producer R32F store / receiver texelFetch equality on frameCounter; invocation unproved");
            int[] after=witnessGlState();requireNoGlError("STEADY_WITNESS_AFTER");
            boolean restored=java.util.Arrays.equals(before,after);r.add("state_before",new Gson().toJsonTree(before));r.add("state_after",new Gson().toJsonTree(after));r.addProperty("state_restored",restored);r.addProperty("gl_errors_clean",true);
            WITNESS.phase(phase,s.epochs.identity(),owner,proof,r,eligible&&restored);
        }catch(RuntimeException|LinkageError unavailable){WITNESS.fail("SNAPSHOT_UNAVAILABLE:"+unavailable);}
    }
    public static void finish(WorldRenderingPipeline pipeline) {
        if(WITNESS!=null&&current!=null&&current.pipeline==pipeline)WITNESS.finish();
        if(current!=null&&current.pipeline==pipeline){current.consumerAuthorized=current.rawConsumerAuthorized=false;current.epochs.end();current.signal();}
    }
    public static void dispose(WorldRenderingPipeline pipeline) {
        STEADY_PROGRAMS.dispose(pipeline);
        State s=STATES.remove(pipeline.getFrameUpdateNotifier());
        if(s!=null){
            s.metadata.invalidate();s.coreState=null;
            try{s.gpuEcho.close();}catch(RuntimeException|LinkageError failure){
                s.reject("ECHO_DISPOSE_FAILED:"+failure);write(s,"echo-dispose-error");
            }
            if(s.producer!=null)NATIVE_SOURCES.remove(s.producer,s.producer.getProgramId());s.producerProof=null;s.producerLinkedSource=null;s.epochs.dispose(pipeline);s.signal();s.signals.clear();}
        if(current==s)current=null;
    }
}
