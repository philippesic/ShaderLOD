package com.pp.shaderlod.dev.bindingfix;
import java.util.*;
/** Two explicit experiment modes; no inferred pack/loader/default promotion. */
public final class BindingInputModes {
    public enum Mode { ORIGINAL_ONLY, RENDERER_SPEED, MAKEUP_CONTROL, REFUSED }
    private BindingInputModes() {}
    private static final Set<String> BOOLEAN_KEYS=Set.of("shaderlod.dev.portBindingInputFix","shaderlod.dev.portOriginalOnly",
        "shaderlod.dev.portRendererSpeedProbe","shaderlod.dev.portRoundtrip","shaderlod.dev.portRoundtripUi",
        "shaderlod.dev.portQualityRoundtrip","shaderlod.dev.portRoundtripDh","shaderlod.experimentalRendererPort092",
        "shaderlod.experimentalRendererResearch","shaderlod.photonQuadOriginalGraphR1");
    private static boolean on(Properties props,String key) { return "true".equals(props.getProperty(key)); }
    public static Mode mode(Properties props) {
        for(String key:BOOLEAN_KEYS) {
            String value=props.getProperty(key);if(value!=null && !value.equals("true") && !value.equals("false"))return Mode.REFUSED;
        }
        for(Object item:props.keySet())
            if(item instanceof String key && key.startsWith("shaderlod.dev.port") && !BOOLEAN_KEYS.contains(key) && !key.equals("shaderlod.dev.portRoundtripOriginal") && !key.equals("shaderlod.dev.portNativeFamily"))return Mode.REFUSED;
        String family=props.getProperty("shaderlod.dev.portNativeFamily", "");
        if(!family.isBlank()){
            if(!family.equals("makeup-volume") || !on(props,"shaderlod.dev.portBindingInputFix") || !on(props,"shaderlod.dev.portRoundtrip") || !on(props,"shaderlod.dev.portRoundtripUi"))return Mode.REFUSED;
            for(String flag:List.of("shaderlod.dev.portOriginalOnly","shaderlod.dev.portRendererSpeedProbe","shaderlod.dev.portQualityRoundtrip","shaderlod.dev.portRoundtripDh","shaderlod.experimentalRendererPort092","shaderlod.experimentalRendererResearch","shaderlod.photonQuadOriginalGraphR1"))if(on(props,flag))return Mode.REFUSED;
            return Mode.MAKEUP_CONTROL;
        }
        if(!on(props,"shaderlod.dev.portBindingInputFix") || !on(props,"shaderlod.dev.portRoundtrip") ||
            on(props,"shaderlod.dev.portRoundtripUi") || on(props,"shaderlod.dev.portQualityRoundtrip") || on(props,"shaderlod.dev.portRoundtripDh") ||
            on(props,"shaderlod.experimentalRendererResearch") || on(props,"shaderlod.photonQuadOriginalGraphR1"))return Mode.REFUSED;
        boolean original=on(props,"shaderlod.dev.portOriginalOnly"),speed=on(props,"shaderlod.dev.portRendererSpeedProbe"),research=on(props,"shaderlod.experimentalRendererPort092");
        if(original && !speed && !research)return Mode.ORIGINAL_ONLY;
        if(!original && speed && research)return Mode.RENDERER_SPEED;
        return Mode.REFUSED;
    }
    public static boolean coreAllows(Mode mode,boolean loaded,boolean enabled,boolean debug,String preset){
        if(mode==Mode.MAKEUP_CONTROL)return loaded&&!debug&&(preset.equals("BALANCED")||enabled&&preset.equals("QUALITY"));
        return coreAllows(mode,loaded,enabled,debug,preset.equals("PERFORMANCE"));
    }
    public static boolean coreAllows(Mode mode,boolean loaded,boolean enabled,boolean debug,boolean performance) {
        return mode!=Mode.REFUSED && mode!=Mode.MAKEUP_CONTROL && loaded && !debug && performance && (mode==Mode.RENDERER_SPEED || !enabled);
    }
}
