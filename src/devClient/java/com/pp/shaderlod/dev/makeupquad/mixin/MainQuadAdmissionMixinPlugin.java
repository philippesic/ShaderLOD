package com.pp.shaderlod.dev.makeupquad.mixin;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;
/** Opt-in, resource-only target pin; no typed main/Iris/Sodium dependency. */
public final class MainQuadAdmissionMixinPlugin implements IMixinConfigPlugin {
    public static final String PROPERTY="shaderlod.dev.makeupQuadMainAdmissionR1";
    public static final String TARGET="com/pp/shaderlod/integration/makeupquad/AdjacentQuadCoalescer.class";
    public static final String SHA="22a646d0138e24dc0109a2509a8b9226bcc74421d5aea25a9f55d456e3a69572";
    public static boolean allowed(ClassLoader loader) {
        if (!Boolean.getBoolean(PROPERTY)) return false;
        try (var in=loader.getResourceAsStream(TARGET)) {
            return in!=null && SHA.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())));
        } catch(Exception | LinkageError unavailable) { return false; }
    }
    public void onLoad(String p) {}
    public String getRefMapperConfig() { return null; }
    public boolean shouldApplyMixin(String target,String mixin) { return allowed(getClass().getClassLoader()); }
    public void acceptTargets(Set<String> mine,Set<String> others) {}
    public List<String> getMixins() { return null; }
    public void preApply(String t,ClassNode n,String m,IMixinInfo i) {}
    public void postApply(String t,ClassNode n,String m,IMixinInfo i) {}
}
