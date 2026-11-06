package com.pp.shaderlod.optimization.framecopy;

import com.pp.shaderlod.optimization.CompileOptionControl;
import com.pp.shaderlod.optimization.OptimizationId;
import com.pp.shaderlod.optimization.OptimizationRuntimeContext;
import com.pp.shaderlod.optimization.mips.DeadMipAdmission;
import com.pp.shaderlod.packs.PackSelectionService;
import net.irisshaders.iris.Iris;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;

/** NeoForge1.21.1-only setup join. Archive verification is called by the compile transaction only. */
public final class MakeUpDeadCopySetup {
    private static String status = "not inspected; qualification pending";
    private MakeUpDeadCopySetup() {}
    public static String status() { return status; }
    public static void initialize() {
        CompileOptionControl.configureMakeUpDeadCopyAdmission(MakeUpDeadCopySetup::context);
        OptimizationRuntimeContext.configureImplementation(OptimizationId.DEAD_FRAME_COPY,
            () -> "Pinned MakeUp dead persistence-copy graph; " + status + "; performance/quality qualification remains scoped to recorded tests");
    }
    private static MakeUpDeadCopyAdmission.Context context() {
        try {
            if (!MakeUpDeadCopyAdmission.platformQualified(System.getProperty("os.name"),System.getProperty("os.arch"),
                    org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER),
                    org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_VERSION)))
                return rejected("unqualified platform/renderer/driver; native graph retained");
            var client=Minecraft.getInstance();var pack=Iris.getCurrentPack().orElse(null);
            String selected=Iris.getIrisConfig().getShaderPackName().orElse(null);
            if(client.level==null||pack==null||selected==null||!selected.equals(Iris.getCurrentPackName())||Iris.isFallback())return rejected("world/selected pack unavailable");
            var directory=Iris.getShaderpacksDirectory().toAbsolutePath().normalize();var archive=directory.resolve(selected).normalize();
            if(!archive.startsWith(directory)||archive.equals(directory))return rejected("selected source path refused");
            boolean generated="makeup-ultra-fast-shaders".equals(PackSelectionService.verifyGeneratedRendererGraph(archive,DeadMipAdmission.effective(pack)));
            var mods=ModList.get();String rawIris=mods.getModContainerById("iris").map(m->m.getModInfo().getVersion().toString()).orElse("");
            // This local build has a snapshot label; the exact raw engine class pins remain mandatory.
            String iris=MakeUpDeadCopyAdmission.canonicalIrisMetadata(rawIris);
            status=(generated?"actual generated recipe/state verified":"selected generated recipe/state refused")+"; actual Iris="+rawIris;
            return new MakeUpDeadCopyAdmission.Context(SharedConstants.getCurrentVersion().getName(),"neoforge",iris,
                FrameCopyPins.exactApi(),generated,client.level.dimension().location().toString(),mods.isLoaded("distanthorizons"),mods.isLoaded("voxy"));
        }catch(Exception|LinkageError unavailable){return rejected("setup unavailable: "+unavailable.getClass().getSimpleName());}
    }
    private static MakeUpDeadCopyAdmission.Context rejected(String reason){status=reason;return null;}
}
