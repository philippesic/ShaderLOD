package com.pp.shaderlod.dev.timing;

import com.google.gson.*;
import java.util.HashSet;
import java.util.Set;

/** Pure bounded G06 evidence contract; receives actual getters, never performs GL or source IO. */
final class QuadSamplerWindow {
    static final int PROGRAM_LIMIT = 64;
    static final long DURATION_NS = 3_000_000_000L;
    private final long started;
    private final JsonObject report = new JsonObject();
    private final JsonArray events = new JsonArray();
    private final Set<Integer> programs = new HashSet<>();
    private String status = "ARMED";
    private int frame = -1, texture, producerProgram, consumerProgram;
    private boolean producerReturned;

    QuadSamplerWindow(long now, JsonObject binding) {
        started = now;
        report.addProperty("schema", 1);
        report.add("binding", binding.deepCopy());
        report.addProperty("quad_only", true);
        report.addProperty("program_limit", PROGRAM_LIMIT);
        report.addProperty("duration_limit_ms", 3000);
        report.addProperty("source_evidence", "Not requested; native Iris pass label and current GL/FBO/sampler getters only. Detached shader objects confer no source claim.");
        report.addProperty("scope", "Untimed pre-quad state and native quad RETURN in one frame; no image contents, GPU completion, fetch/iteration counts, quality or speed proof.");
        report.add("events", events);
    }

    boolean active(long now) {
        if (status.equals("ARMED") && now - started > DURATION_NS) refuse("DEADLINE");
        return status.equals("ARMED");
    }

    void refuse(String reason) {
        if (!status.equals("ARMED")) return;
        status = "REFUSED";
        report.addProperty("reason", reason);
    }

    void before(String name, int program, int currentFrame, JsonObject event, long now) {
        if (!active(now)) return;
        try {
            require(program > 0 && event.get("linked").getAsBoolean(), "UNLINKED_PROGRAM");
            programs.add(program);
            require(programs.size() <= PROGRAM_LIMIT, "PROGRAM_LIMIT");
            event.addProperty("native_pass", name);
            event.addProperty("frame", currentFrame);
            event.addProperty("phase", "quad_HEAD");
            events.add(event);
            var draw = event.getAsJsonObject("draw_state");
            require(integer(draw, "draw_framebuffer") > 0, "DEFAULT_FBO");
            if (name.equals("deferred")) {
                require(texture == 0, "REPEATED_PRODUCER");
                viewport(draw, 960, 600);
                var output = draw.getAsJsonArray("color_attachments").get(0).getAsJsonObject();
                require(integer(output, "object_type") == 5890 && integer(output, "texture_level") == 0, "PRODUCER_ATTACHMENT_LEVEL");
                allocation(output.getAsJsonObject("allocation"));
                texture = integer(output, "texture");
                require(texture > 0, "PRODUCER_TEXTURE");
                frame = currentFrame;
                producerProgram = program;
            } else if (name.equals("deferred1")) {
                require(texture > 0 && producerReturned && frame == currentFrame, "PRODUCER_NOT_RETURNED_IN_SAME_FRAME");
                require(program != producerProgram, "PROGRAM_IDENTITY_COLLISION");
                viewport(draw, 1920, 1200);
                var sampler = event.getAsJsonObject("samplers").getAsJsonObject("colortex8");
                require(integer(sampler, "active_uniform_size") == 1 && integer(sampler, "active_uniform_type") == 35678
                    && integer(sampler, "uniform_location") >= 0, "INACTIVE_RT8_SAMPLER");
                require(integer(sampler, "texture_target") == 3553 && integer(sampler, "bound_texture") == texture, "RT8_TEXTURE_IDENTITY");
                allocation(sampler.getAsJsonObject("texture_level_zero"));
                require(integer(sampler.getAsJsonObject("texture_parameters"), "base_level") == 0, "RT8_BASE_LEVEL");
                for (var item : draw.getAsJsonArray("color_attachments")) {
                    var output = item.getAsJsonObject();
                    if (integer(output, "object_type") == 5890)
                        require(integer(output, "texture") != texture, "RT8_FEEDBACK");
                }
                consumerProgram = program;
            } else throw new IllegalStateException("UNEXPECTED_PASS");
        } catch (RuntimeException invalid) { refuse(invalid.toString()); }
    }

    void returned(String name, int program, int currentFrame, long now) {
        if (!active(now)) return;
        if (frame != currentFrame) { refuse("FRAME_CHANGED_BEFORE_RETURN"); return; }
        if (name.equals("deferred") && program == producerProgram && texture > 0) producerReturned = true;
        else if (name.equals("deferred1") && program == consumerProgram && producerReturned) {
            status = "COMPLETE_MATCHED_RT8";
            report.addProperty("physical_join", true);
            report.addProperty("texture", texture);
            report.addProperty("frame", frame);
            report.addProperty("producer_quad_returned", true);
            report.addProperty("consumer_quad_returned", true);
        } else refuse("QUAD_RETURN_IDENTITY");
    }

    JsonObject snapshot(long now) {
        active(now);
        report.addProperty("status", status);
        report.addProperty("captured_programs", programs.size());
        report.addProperty("observing", status.equals("ARMED"));
        if (!report.has("physical_join")) report.addProperty("physical_join", false);
        return report.deepCopy();
    }

    private static int integer(JsonObject value, String key) { return value.get(key).getAsInt(); }
    private static void require(boolean predicate, String reason) { if (!predicate) throw new IllegalStateException(reason); }
    private static void viewport(JsonObject draw, int width, int height) {
        var v = draw.getAsJsonArray("viewport");
        require(v.size() == 4 && v.get(0).getAsInt() == 0 && v.get(1).getAsInt() == 0
            && v.get(2).getAsInt() == width && v.get(3).getAsInt() == height, "VIEWPORT");
    }
    private static void allocation(JsonObject value) {
        require(value.get("status").getAsString().equals("queried_without_gl_error_consumption")
            && integer(value, "level") == 0 && integer(value, "width") == 960
            && integer(value, "height") == 600 && integer(value, "internal_format") == 33328, "RT8_LEVEL0_RG32F_EXTENT");
    }
}
