#if !defined SHADERLOD_WORK_TRACE_INCLUDED
#define SHADERLOD_WORK_TRACE_INCLUDED

// ShaderLOD diagnostic mechanism: explicitly instrumented operations only.
// Caller owns storage, option declaration, output attachments and adapter tags.
// No transport, samplers, pack policy or automatic source instrumentation.
#ifdef SHADERLOD_WORK_TRACE

// Every integer in this closed range is exactly representable in binary32.
const uint SHADERLOD_TRACE_SCHEMA = 1u;
const uint SHADERLOD_TRACE_MAX_EXACT = 16777215u;
const uint SHADERLOD_TRACE_PATH_NONE = 0u;
const uint SHADERLOD_TRACE_PATH_ORIGINAL = 1u;
const uint SHADERLOD_TRACE_PATH_ADAPTED = 2u;
const uint SHADERLOD_TRACE_PATH_FULL_TARGET = 3u;

struct ShaderlodWorkTrace {
    uint primary_iterations;
    uint density_evaluations;
    uint secondary_visits;
    uint texture_evaluations;
    uint original_work;
    float requested_work; // Continuous request, not an executed counter.
    float selected_work; // Continuous selected budget, not an executed counter.
    uint path_tag;
    uint reason_tag; // Adapter-declared mapping, required in capture metadata.
    bool valid;
};

ShaderlodWorkTrace shaderlod_trace_init() {
    ShaderlodWorkTrace trace;
    trace.primary_iterations = 0u;
    trace.density_evaluations = 0u;
    trace.secondary_visits = 0u;
    trace.texture_evaluations = 0u;
    trace.original_work = 0u;
    trace.requested_work = 0.0;
    trace.selected_work = 0.0;
    trace.path_tag = SHADERLOD_TRACE_PATH_NONE;
    trace.reason_tag = 0u;
    trace.valid = true;
    return trace;
}

bool shaderlod_trace_budget_valid(float budget) {
    return !isnan(budget) && !isinf(budget) &&
        budget >= 0.0 && budget <= float(SHADERLOD_TRACE_MAX_EXACT);
}

// Set tags when the actual execution path/budget is known. Does not reset work.
// Invalid metadata is sticky, like overflow; a later selection cannot repair it.
void shaderlod_trace_select(inout ShaderlodWorkTrace trace, uint original,
    float requested, float selected, uint path, uint reason) {
    trace.original_work = original;
    trace.requested_work = requested;
    trace.selected_work = selected;
    trace.path_tag = path;
    trace.reason_tag = reason;
    trace.valid = trace.valid && original <= SHADERLOD_TRACE_MAX_EXACT &&
        shaderlod_trace_budget_valid(requested) && shaderlod_trace_budget_valid(selected) &&
        path <= SHADERLOD_TRACE_PATH_FULL_TARGET && reason <= SHADERLOD_TRACE_MAX_EXACT;
}

// Increment exactly once at an actual reached operation. Saturation never wraps;
// overflow invalidates the entire record rather than reporting an invented cap.
void shaderlod_trace_increment(inout uint counter, inout bool valid) {
    if (counter >= SHADERLOD_TRACE_MAX_EXACT) {
        valid = false;
        return;
    }
    counter += 1u;
}

bool shaderlod_trace_valid(ShaderlodWorkTrace trace) {
    return trace.valid &&
        trace.primary_iterations <= SHADERLOD_TRACE_MAX_EXACT &&
        trace.density_evaluations <= SHADERLOD_TRACE_MAX_EXACT &&
        trace.secondary_visits <= SHADERLOD_TRACE_MAX_EXACT &&
        trace.texture_evaluations <= SHADERLOD_TRACE_MAX_EXACT &&
        trace.original_work <= SHADERLOD_TRACE_MAX_EXACT &&
        shaderlod_trace_budget_valid(trace.requested_work) &&
        shaderlod_trace_budget_valid(trace.selected_work) &&
        trace.path_tag <= SHADERLOD_TRACE_PATH_FULL_TARGET &&
        trace.reason_tag <= SHADERLOD_TRACE_MAX_EXACT;
}

// Raw, unnormalized RGBA32F pages. Unknown page/overflow/nonfinite -> all -1.
// No blending, filtering, tone mapping, gamma conversion or half-float storage.
vec4 shaderlod_trace_encode(ShaderlodWorkTrace trace, uint page) {
    if (!shaderlod_trace_valid(trace) || page > 2u) return vec4(-1.0);
    if (page == 0u) return vec4(float(trace.primary_iterations),
        float(trace.density_evaluations), float(trace.secondary_visits),
        float(trace.texture_evaluations));
    if (page == 1u) return vec4(float(trace.original_work), trace.requested_work,
        trace.selected_work, float(trace.path_tag));
    return vec4(float(SHADERLOD_TRACE_SCHEMA), float(trace.reason_tag), 1.0, 0.0);
}

// Declaration owns its semicolon: invoke without a trailing semicolon, so the
// disabled global declaration leaves no GLSL410-invalid empty declaration.
#define SHADERLOD_TRACE_DECLARE(trace) ShaderlodWorkTrace trace;
#define SHADERLOD_TRACE_INIT(trace) trace = shaderlod_trace_init()
#define SHADERLOD_TRACE_SELECT(trace, original, requested, selected, path, reason) shaderlod_trace_select(trace, original, requested, selected, path, reason)
#define SHADERLOD_TRACE_PRIMARY(trace) shaderlod_trace_increment(trace.primary_iterations, trace.valid)
#define SHADERLOD_TRACE_DENSITY(trace) shaderlod_trace_increment(trace.density_evaluations, trace.valid)
#define SHADERLOD_TRACE_SECONDARY(trace) shaderlod_trace_increment(trace.secondary_visits, trace.valid)
#define SHADERLOD_TRACE_TEXTURE(trace) shaderlod_trace_increment(trace.texture_evaluations, trace.valid)
#define SHADERLOD_TRACE_INVALIDATE(trace) trace.valid = false

#else // SHADERLOD_WORK_TRACE

// Empty macros erase storage, calls AND argument evaluation from ordinary GLSL.
// Encoding/output must also sit inside the caller's diagnostic-only guard.
#define SHADERLOD_TRACE_DECLARE(trace)
#define SHADERLOD_TRACE_INIT(trace)
#define SHADERLOD_TRACE_SELECT(trace, original, requested, selected, path, reason)
#define SHADERLOD_TRACE_PRIMARY(trace)
#define SHADERLOD_TRACE_DENSITY(trace)
#define SHADERLOD_TRACE_SECONDARY(trace)
#define SHADERLOD_TRACE_TEXTURE(trace)
#define SHADERLOD_TRACE_INVALIDATE(trace)

#endif // SHADERLOD_WORK_TRACE
#endif // SHADERLOD_WORK_TRACE_INCLUDED
