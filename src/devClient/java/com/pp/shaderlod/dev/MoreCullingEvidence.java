package com.pp.shaderlod.dev;

import com.google.gson.*;
import java.lang.reflect.*;
import java.security.*;
import java.util.*;
import net.neoforged.fml.ModList;

/** Explicit untimed read-only peer configuration/class provenance; no GL calls or writes. */
final class MoreCullingEvidence {
    private MoreCullingEvidence() {}
    static JsonObject read() throws Exception {
        if (!ModList.get().isLoaded("moreculling") || !ModList.get().isLoaded("cloth_config"))
            throw new IllegalStateException("Actual loaded MoreCulling/Cloth peer required");
        ClassLoader loader = com.pp.shaderlod.ShaderLod.class.getClassLoader();
        Class<?> peer = Class.forName("ca.fxco.moreculling.MoreCulling", false, loader);
        Object config = peer.getField("CONFIG").get(null);
        if (config == null) throw new IllegalStateException("Resolved MoreCulling CONFIG absent");
        var out = new JsonObject();var fields = new JsonObject();
        for (Field field : config.getClass().getFields()) {
            if (!Modifier.isStatic(field.getModifiers())) fields.add(field.getName(), value(field.get(config)));
        }
        out.add("resolved_config", fields);out.addProperty("config_class", config.getClass().getName());
        var classes = new JsonObject();
        for (String name : List.of("ca.fxco.moreculling.MoreCulling", "ca.fxco.moreculling.config.MoreCullingConfig",
                "me.shedaniel.clothconfig2.api.ConfigBuilder", "net.neoforged.fml.loading.VersionSupportMatrix")) {
            var row = new JsonObject();
            try {
                Class<?> type = Class.forName(name, false, loader);
                var resource = type.getResource(type.getSimpleName() + ".class");
                if (resource == null) throw new IllegalStateException("Class resource absent");
                try (var in = resource.openStream()) {
                    row.addProperty("raw_class_sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())));
                }
                row.addProperty("resource", resource.toString());
                var code = type.getProtectionDomain().getCodeSource();
                row.addProperty("code_source", code == null ? "unavailable" : code.getLocation().toString());
            } catch (Exception | LinkageError unavailable) { row.addProperty("unavailable", unavailable.toString()); }
            classes.add(name, row);
        }
        out.add("classes", classes);
        out.addProperty("scope", "Read resolved public CONFIG fields and raw class resources; not transformed executable identity or culling-work evidence. DEFAULT leaves mode alone proves no leaf-face removal.");
        return out;
    }
    private static JsonElement value(Object value) {
        if (value == null) return JsonNull.INSTANCE;
        if (value instanceof Boolean b) return new JsonPrimitive(b);
        if (value instanceof Number n) return new JsonPrimitive(n);
        if (value instanceof String s) return new JsonPrimitive(s);
        if (value instanceof Enum<?> e) return new JsonPrimitive(e.name());
        if (value instanceof Map<?,?> map) { var out = new JsonObject();map.forEach((k,v) -> out.add(String.valueOf(k), value(v)));return out; }
        if (value instanceof Iterable<?> list) { var out = new JsonArray();list.forEach(v -> out.add(value(v)));return out; }
        throw new IllegalArgumentException("Unproved config field type " + value.getClass().getName());
    }
}
