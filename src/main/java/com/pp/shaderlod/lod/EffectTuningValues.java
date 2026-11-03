package com.pp.shaderlod.lod;

import java.util.*;

/** Complete portable effect-policy validation, independent of Minecraft/config initialization. */
public final class EffectTuningValues {
    private EffectTuningValues() {}
    public static Map<String,String> canonical(Map<String,String> input) {
        validate(input);var values=new TreeMap<>(input);
        for(var effect:Effect.values())for(String suffix:List.of("near","far","transition","medium","low")) {
            String key=effect.name().toLowerCase(Locale.ROOT)+"."+suffix;double value=Double.parseDouble(values.get(key));
            values.put(key,Double.toString(value==0?0.0:value));
        }
        return Map.copyOf(values);
    }
    public static void validate(Map<String,String> values) {
        Objects.requireNonNull(values);var expected=new HashSet<String>();
        for(var effect:Effect.values()) {
            String p=effect.name().toLowerCase(Locale.ROOT)+".";
            for(String suffix:List.of("mode","near","far","transition","medium","low","protect"))expected.add(p+suffix);
            if(!Set.of("INHERIT","FULL","ADAPTIVE").contains(values.get(p+"mode"))||!Set.of("true","false").contains(values.get(p+"protect")))throw new IllegalArgumentException("Invalid effect tuning mode/protection");
            double near=Double.parseDouble(values.get(p+"near")),far=Double.parseDouble(values.get(p+"far")),width=Double.parseDouble(values.get(p+"transition"));
            if(near<1||near>4095||far<2||far>4096||width>512)throw new IllegalArgumentException("Effect tuning distances exceed limits");
            var bands=new DistanceBands(near,far,width);
            new EffectPolicy("ADAPTIVE".equals(values.get(p+"mode")),Boolean.parseBoolean(values.get(p+"protect")),bands,Double.parseDouble(values.get(p+"medium")),Double.parseDouble(values.get(p+"low")));
        }
        if(!values.keySet().equals(expected))throw new IllegalArgumentException("Incomplete or unknown effect tuning fields");
    }
    public static Map<String,String> defaults() {
        var out=new TreeMap<String,String>();for(var effect:Effect.values()) {
            String k=effect.name().toLowerCase(Locale.ROOT)+".";var p=EffectPolicy.defaultsFor(effect);
            out.put(k+"mode","INHERIT");out.put(k+"near",Double.toString(p.bands().nearBlocks()));out.put(k+"far",Double.toString(p.bands().farBlocks()));
            out.put(k+"transition",Double.toString(p.bands().transitionBlocks()));out.put(k+"medium",Double.toString(p.mediumWorkRatio()));out.put(k+"low",Double.toString(p.farWorkRatio()));out.put(k+"protect",Boolean.toString(p.preserveSalientLighting()));
        }return Map.copyOf(out);
    }
}
