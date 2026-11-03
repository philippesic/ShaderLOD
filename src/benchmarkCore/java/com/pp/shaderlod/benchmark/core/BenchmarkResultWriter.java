package com.pp.shaderlod.benchmark.core;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Post-capture JSON export only. No IO or hashing is added to sampling. */
public final class BenchmarkResultWriter {
    private BenchmarkResultWriter() {}
    public static String toJson(BenchmarkResult.Manifest manifest) {
        Objects.requireNonNull(manifest);
        var out = new StringBuilder();
        out.append("{\"manifest\":"); append(out, manifest);
        out.append(",\"qualificationBlockers\":"); append(out, manifest.qualificationBlockers());
        out.append("}\n");
        return out.toString();
    }
    /** Complete serialization precedes replacement; an unsuccessful write preserves the prior result. */
    public static void write(Path destination, BenchmarkResult.Manifest manifest) throws IOException {
        String json = toJson(manifest);
        Path target = destination.toAbsolutePath();
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), ".benchmark-result-", ".tmp");
        try {
            Files.writeString(temporary, json, StandardCharsets.UTF_8);
            // Refuse replacement on filesystems lacking atomic moves; don't risk truncating prior evidence.
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }
    private static void append(StringBuilder out, Object value) {
        if (value == null) { out.append("null"); return; }
        if (value instanceof String s) { quote(out, s); return; }
        if (value instanceof Enum<?> e) { quote(out, e.name()); return; }
        if (value instanceof Boolean b) { out.append(b); return; }
        if (value instanceof Number n) {
            if (n instanceof Double d && !Double.isFinite(d) || n instanceof Float f && !Float.isFinite(f)) out.append("null");
            else out.append(n);
            return;
        }
        if (value instanceof Map<?, ?> map) {
            out.append('{'); boolean first = true;
            var entries = new ArrayList<>(map.entrySet());
            entries.sort(Comparator.comparing(e -> (String)e.getKey()));
            for (var entry : entries) {
                if (!(entry.getKey() instanceof String key)) throw new IllegalArgumentException("JSON key must be string");
                if (!first) out.append(','); first = false;
                quote(out, key); out.append(':'); append(out, entry.getValue());
            }
            out.append('}'); return;
        }
        if (value instanceof List<?> list) {
            out.append('['); boolean first = true;
            for (var item : list) { if (!first) out.append(','); first = false; append(out, item); }
            out.append(']'); return;
        }
        if (value.getClass().isRecord()) {
            out.append('{'); boolean first = true;
            for (var component : value.getClass().getRecordComponents()) {
                if (!first) out.append(','); first = false;
                quote(out, component.getName()); out.append(':');
                try { append(out, component.getAccessor().invoke(value)); }
                catch (IllegalAccessException | InvocationTargetException error) { throw new IllegalArgumentException("record export failed", error); }
            }
            out.append('}'); return;
        }
        throw new IllegalArgumentException("unsupported JSON value: " + value.getClass().getName());
    }
    private static void quote(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\""); case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b"); case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n"); case '\r' -> out.append("\\r"); case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20 || Character.isSurrogate(c)) {
                        out.append("\\u");
                        for (int shift = 12; shift >= 0; shift -= 4) out.append(Character.forDigit((c >>> shift) & 15, 16));
                    } else out.append(c);
                }
            }
        }
        out.append('"');
    }
}
