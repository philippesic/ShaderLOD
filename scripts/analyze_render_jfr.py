#!/usr/bin/env python3
"""Analyze a completed JDK JFR export; stack sample counts are not execution times."""
import argparse
from collections import Counter, defaultdict
from datetime import datetime, timezone
import hashlib
import json
import math
from pathlib import Path
import re
import shutil
import statistics
import subprocess
import tempfile

EVENTS = (
    "jdk.ExecutionSample", "jdk.NativeMethodSample", "jdk.CPULoad", "jdk.ThreadCPULoad",
    "jdk.GCPhasePause", "jdk.GarbageCollection", "jdk.Compilation", "jdk.ThreadPark",
    "jdk.JavaMonitorWait", "jdk.JavaMonitorEnter", "jdk.ThreadSleep", "jdk.CPUInformation",
)
SAMPLE_TYPES = {"jdk.ExecutionSample", "jdk.NativeMethodSample"}
WAIT_TYPES = {"jdk.ThreadPark", "jdk.JavaMonitorWait", "jdk.JavaMonitorEnter", "jdk.ThreadSleep"}
LIMITS = [
    "Java and native sample counts are separate; their sampling periods may differ. Counts are not source execution time or GPU busy time.",
    "A native GL sample identifies the Java/JNI call boundary, not the internal driver instruction or whether it is executing versus waiting.",
    "Inclusive stack counts overlap; recursive occurrences of a method count once per sample. Truncated/missing stacks reduce attribution.",
    "JFR profile settings threshold lock/park events and slow compilations; absence does not exclude shorter waits or JIT activity.",
    "CPU load events summarize an earlier sampling interval and may straddle warmup/measurement boundaries. JDK thread load is normalized by active processor count.",
    "GC pause and render wait durations may overlap. GC collection elapsed time and compiler-thread durations are not render-thread pause time.",
    "One profiled JVM cannot explain variability across other runs or establish a causal shader, instrumentation, DVFS, or driver regression.",
]


def timestamp(value):
    if isinstance(value, (float, int)):
        return float(value)
    if not isinstance(value, str):
        raise ValueError("Missing/invalid JFR startTime")
    return datetime.fromisoformat(value.replace("Z", "+00:00")).timestamp()


def utc(value):
    return datetime.fromtimestamp(value, timezone.utc).isoformat().replace("+00:00", "Z")


def duration_seconds(value):
    if value is None:
        return 0.0
    if isinstance(value, (float, int)):
        # Fixtures may supply seconds explicitly; JDK JSON emits ISO-8601 durations.
        result = float(value)
    else:
        match = re.fullmatch(r"PT(?:(\d+(?:\.\d+)?)H)?(?:(\d+(?:\.\d+)?)M)?(?:(\d+(?:\.\d+)?)S)?", value)
        if not match or not any(match.groups()):
            raise ValueError("Invalid JFR ISO duration: " + str(value))
        hours, minutes, seconds = (float(part or 0) for part in match.groups())
        result = hours * 3600 + minutes * 60 + seconds
    if not math.isfinite(result) or result < 0:
        raise ValueError("Invalid negative/nonfinite duration")
    return result


def thread_name(values, sample=False):
    thread = values.get("sampledThread" if sample else "eventThread") or {}
    return thread.get("javaName") or thread.get("osName") or "<unknown>"


def frames(values):
    result = []
    for frame in (values.get("stackTrace") or {}).get("frames", []):
        method = frame.get("method") or {}
        klass = method.get("type") or {}
        name = klass.get("name", "<unknown>").replace("/", ".") + "." + method.get("name", "<unknown>")
        result.append(name)
    return result


def classify_top(name, native, trace=()):
    lower = name.lower()
    if native:
        if name.startswith("org.lwjgl.system.JNI.") and any(frame.startswith("org.lwjgl.glfw.") for frame in trace):
            return "native_window_swap_or_events"
        if "getthreadcputime" in lower:
            return "native_cpu_clock"
        if name.startswith("org.lwjgl.opengl."):
            if "query" in lower:
                return "native_opengl_query_api"
            if "draw" in lower:
                return "native_opengl_draw_api"
            return "native_opengl_other_api"
        if name.startswith("org.lwjgl.glfw."):
            return "native_window_swap_or_events"
        if any(part in lower for part in (".park", ".wait", ".sleep")):
            return "native_wait_api"
        if any(part in lower for part in ("copymemory", "setmemory", "memcopy", "memcpy")):
            return "native_memory_api"
        return "native_other"
    if name.startswith("com.pp.shaderlod.dev."):
        return "java_development_instrumentation"
    if "DefaultChunkRenderer" in name and any(part in name for part in ("fillCommandBuffer", "addIndexedDrawCommands", "addNonIndexedDrawCommands", "getVisibleFaces")):
        return "java_sodium_command_assembly"
    if name.startswith("net.caffeinemc.mods.sodium."):
        return "java_sodium_other"
    if name.startswith(("net.irisshaders.", "org.lwjgl.opengl.")):
        return "java_iris_or_gl_wrapper"
    if name.startswith("net.minecraft."):
        return "java_minecraft"
    return "java_other"


def ranked(counter, total=None, limit=30):
    return [{"name": name, "samples": count,
             "share_of_this_stream": count / total if total else None}
            for name, count in sorted(counter.items(), key=lambda item: (-item[1], item[0]))[:limit]]


def union_seconds(intervals):
    total = 0.0
    last_start = last_end = None
    for start, end in sorted(intervals):
        if last_end is None:
            last_start, last_end = start, end
        elif start <= last_end:
            last_end = max(last_end, end)
        else:
            total += last_end - last_start
            last_start, last_end = start, end
    return total + (last_end - last_start if last_end is not None else 0)


def average(values):
    return statistics.mean(values) if values else None


def contexts(trace):
    anchors = {
        "sodium_visibility_or_prepare": "net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer.setupTerrain",
        "sodium_region_command_fill": "net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer.fillCommandBuffer",
        "sodium_draw_submission": "net.caffeinemc.mods.sodium.client.gl.device.GLRenderDevice$ImmediateDrawCommandList.multiDrawElementsBaseVertex",
        "iris_shadow_render": "net.irisshaders.iris.shadows.ShadowRenderer.renderShadows",
        "iris_final_pass": "net.irisshaders.iris.pipeline.FinalPassRenderer.renderFinalPass",
        "iris_composite_pass": "net.irisshaders.iris.pipeline.CompositeRenderer.renderAll",
        "window_swap": "org.lwjgl.glfw.GLFW.glfwSwapBuffers",
        "window_events": "org.lwjgl.glfw.GLFW.glfwPollEvents",
    }
    present = set(trace)
    return [label for label, method in anchors.items() if method in present]


def infer_render_thread(events):
    counts = Counter()
    for event in events:
        if event.get("type") not in SAMPLE_TYPES:
            continue
        values = event.get("values") or {}
        if any(name in {"net.minecraft.client.renderer.GameRenderer.render", "net.minecraft.client.Minecraft.runTick"}
               for name in frames(values)):
            counts[thread_name(values, True)] += 1
    if counts:
        selected = counts.most_common(1)[0][0]
        return "^" + re.escape(selected) + "$", {"method": "render-entrypoint stack presence", "candidates": dict(counts)}
    return "^Render thread$", {"method": "fallback thread name; no rendering stack evidence", "candidates": {}}


def analyze(payload, render_thread="auto", bin_seconds=5.0, start=None, end=None):
    if bin_seconds <= 0:
        raise ValueError("bin_seconds must be positive")
    events = payload.get("recording", {}).get("events") if isinstance(payload, dict) else payload
    if not isinstance(events, list):
        raise ValueError("Expected JDK jfr print --json recording.events array")
    timed = [(timestamp(event["values"]["startTime"]), event) for event in events
             if event.get("values", {}).get("startTime") is not None]
    if not timed:
        raise ValueError("Recording has no timestamped events")
    observed_start = min(time for time, event in timed)
    observed_end = max(time + duration_seconds(event["values"].get("duration")) for time, event in timed)
    requested_start = timestamp(start) if start is not None else observed_start
    requested_end = timestamp(end) if end is not None else observed_end
    begin, finish = max(observed_start, requested_start), min(observed_end, requested_end)
    if finish <= begin:
        raise ValueError("Requested measurement window does not overlap recording")
    if render_thread == "auto":
        render_thread, selection = infer_render_thread(events)
    else:
        selection = {"method": "explicit thread-name regular expression", "candidates": {}}
    match_thread = re.compile(render_thread).search
    totals = Counter()
    all_thread_samples = defaultdict(Counter)
    samples = {kind: {"top": Counter(), "inclusive": Counter(), "categories": Counter(),
                      "contexts": Counter(), "states": Counter(), "stacks": Counter(), "truncated": 0, "missing": 0, "total": 0}
               for kind in SAMPLE_TYPES}
    bins = {}
    cpu_load = []
    thread_load = []
    all_thread_load = defaultdict(list)
    waits = defaultdict(list)
    wait_intervals = []
    gc_pauses = []
    gc_intervals = []
    collections = []
    compilations = []
    cpu_information = [event["values"] for _, event in timed if event["type"] == "jdk.CPUInformation"]

    def bin_for(time):
        last_index = max(0, math.ceil((finish - begin) / bin_seconds) - 1)
        index = min(last_index, int(max(0, time - begin) / bin_seconds))
        return bins.setdefault(index, {"sample_counts": Counter(), "top_native": Counter(), "top_java": Counter(),
                                       "categories_native": Counter(), "categories_java": Counter(),
                                       "cpu_load": [], "render_thread_load": [], "gc_pause_ms": 0.0,
                                       "render_wait_ms": 0.0, "compilation_ms_all_threads": 0.0})

    def split_duration(time, stop, key):
        cursor = max(time, begin)
        stop = min(stop, finish)
        while cursor < stop:
            index = int(math.floor(max(0, cursor - begin) / bin_seconds + 1e-9))
            edge = min(stop, begin + (index + 1) * bin_seconds)
            bin_for(cursor)[key] += (edge - cursor) * 1000
            cursor = edge

    for time, event in timed:
        kind, values = event["type"], event["values"]
        seconds = duration_seconds(values.get("duration"))
        point_in_window = begin <= time <= finish
        interval_start, interval_end = max(begin, time), min(finish, time + seconds)
        interval_in_window = seconds > 0 and interval_end > interval_start
        if not point_in_window and not interval_in_window:
            continue
        totals[kind] += 1
        if kind in SAMPLE_TYPES and point_in_window:
            name = thread_name(values, True)
            all_thread_samples[kind][name] += 1
            if not match_thread(name):
                continue
            stream = samples[kind]
            stream["total"] += 1
            trace = frames(values)
            stream["states"][values.get("state", "<unknown>")] += 1
            stream["truncated"] += bool((values.get("stackTrace") or {}).get("truncated"))
            bucket = bin_for(time)
            bucket["sample_counts"][kind] += 1
            if not trace:
                stream["missing"] += 1
                continue
            top = trace[0]
            category = classify_top(top, kind == "jdk.NativeMethodSample", trace)
            stream["top"][top] += 1
            stream["inclusive"].update(set(trace))
            stream["categories"][category] += 1
            stream["contexts"].update(contexts(trace))
            stream["stacks"][" <- ".join(trace[:12])] += 1
            bucket["top_native" if kind == "jdk.NativeMethodSample" else "top_java"][top] += 1
            bucket["categories_native" if kind == "jdk.NativeMethodSample" else "categories_java"][category] += 1
        elif kind == "jdk.CPULoad" and point_in_window:
            record = {key: float(values[key]) for key in ("jvmUser", "jvmSystem", "machineTotal") if key in values}
            record["at_utc"] = utc(time)
            cpu_load.append(record)
            bin_for(time)["cpu_load"].append(record)
        elif kind == "jdk.ThreadCPULoad" and point_in_window:
            name = thread_name(values)
            record = {"at_utc": utc(time), "user": float(values.get("user", 0)), "system": float(values.get("system", 0))}
            all_thread_load[name].append(record["user"] + record["system"])
            if match_thread(name):
                thread_load.append(record)
                bin_for(time)["render_thread_load"].append(record)
        elif kind in WAIT_TYPES and interval_in_window and match_thread(thread_name(values)):
            record = {"at_utc": utc(time), "clipped_ms": (interval_end - interval_start) * 1000,
                      "recorded_ms": seconds * 1000, "stack": frames(values)[:12]}
            waits[kind].append(record)
            wait_intervals.append((interval_start, interval_end))
            split_duration(interval_start, interval_end, "render_wait_ms")
        elif kind == "jdk.GCPhasePause" and interval_in_window:
            gc_pauses.append({"at_utc": utc(time), "clipped_ms": (interval_end - interval_start) * 1000,
                              "name": values.get("name"), "gcId": values.get("gcId")})
            gc_intervals.append((interval_start, interval_end))
            split_duration(interval_start, interval_end, "gc_pause_ms")
        elif kind == "jdk.GarbageCollection" and point_in_window:
            collections.append({"at_utc": utc(time), "elapsed_ms": seconds * 1000,
                                "sum_of_pauses_ms": duration_seconds(values.get("sumOfPauses")) * 1000,
                                "longest_pause_ms": duration_seconds(values.get("longestPause")) * 1000,
                                "name": values.get("name"), "cause": values.get("cause")})
        elif kind == "jdk.Compilation" and interval_in_window:
            method = values.get("method") or {}
            klass = method.get("type") or {}
            compilations.append({"at_utc": utc(time), "clipped_ms": (interval_end - interval_start) * 1000,
                                 "recorded_ms": seconds * 1000, "thread": thread_name(values),
                                 "compiler": values.get("compiler"), "level": values.get("compileLevel"),
                                 "method": klass.get("name", "<unknown>").replace("/", ".") + "." + method.get("name", "<unknown>")})
            split_duration(interval_start, interval_end, "compilation_ms_all_threads")

    hardware_threads = cpu_information[0].get("hwThreads") if cpu_information else None
    result = {
        "observed_window_utc": [utc(observed_start), utc(observed_end)],
        "requested_window_utc": [utc(requested_start), utc(requested_end)],
        "analyzed_window_utc": [utc(begin), utc(finish)], "analyzed_seconds": finish - begin,
        "requested_window_coverage_fraction": (finish - begin) / (requested_end - requested_start),
        "render_thread_pattern": render_thread, "events_in_window": dict(totals),
        "render_thread_selection": selection,
        "cpu_information": cpu_information,
        "samples": {}, "time_bins": [],
        "cpu_load": {"records": cpu_load, "mean_jvm_fraction": average([r.get("jvmUser", 0) + r.get("jvmSystem", 0) for r in cpu_load]),
                     "mean_machine_fraction": average([r.get("machineTotal", 0) for r in cpu_load])},
        "render_thread_cpu_load": {"records": thread_load, "mean_machine_fraction": average([r["user"] + r["system"] for r in thread_load]),
                                   "hardware_threads_for_reference": hardware_threads,
                                   "core_fraction_if_hardware_count_equals_active_count": average([(r["user"] + r["system"]) * hardware_threads for r in thread_load]) if hardware_threads else None},
        "thread_load_means_machine_fraction": sorted([{"thread": name, "mean": average(load), "records": len(load)} for name, load in all_thread_load.items()], key=lambda item: item["mean"], reverse=True),
        "render_wait_events": {kind: {"count": len(records), "sum_clipped_ms": sum(r["clipped_ms"] for r in records),
                                      "largest": sorted(records, key=lambda r: r["clipped_ms"], reverse=True)[:10]} for kind, records in waits.items()},
        "render_wait_union_ms": union_seconds(wait_intervals) * 1000,
        "gc_pause_events": gc_pauses, "gc_pause_union_ms": union_seconds(gc_intervals) * 1000,
        "garbage_collections": collections,
        "slow_compilations_all_threads": sorted(compilations, key=lambda r: r["clipped_ms"], reverse=True)[:30],
        "limits": LIMITS,
    }
    for kind, stream in samples.items():
        result["samples"][kind] = {"render_samples": stream["total"], "truncated_stacks": stream["truncated"],
                                   "missing_stacks": stream["missing"], "states": dict(stream["states"]),
                                   "all_thread_counts": dict(all_thread_samples[kind]),
                                   "top_methods": ranked(stream["top"], stream["total"]),
                                   "inclusive_methods": ranked(stream["inclusive"], stream["total"]),
                                   "categories": ranked(stream["categories"], stream["total"]),
                                   "overlapping_stack_contexts": ranked(stream["contexts"], stream["total"]),
                                   "top_stacks": ranked(stream["stacks"], stream["total"], 15)}
    for index, bucket in sorted(bins.items()):
        result["time_bins"].append({"start_offset_seconds": index * bin_seconds,
                                    "start_utc": utc(begin + index * bin_seconds),
                                    "sample_counts": dict(bucket["sample_counts"]),
                                    "top_native": ranked(bucket["top_native"], limit=5),
                                    "top_java": ranked(bucket["top_java"], limit=5),
                                    "categories_native": dict(bucket["categories_native"]),
                                    "categories_java": dict(bucket["categories_java"]),
                                    "cpu_load_mean_machine_fraction": average([r.get("machineTotal", 0) for r in bucket["cpu_load"]]),
                                    "render_thread_load_mean_machine_fraction": average([r["user"] + r["system"] for r in bucket["render_thread_load"]]),
                                    "gc_pause_ms": bucket["gc_pause_ms"], "render_wait_ms": bucket["render_wait_ms"],
                                    "compilation_ms_all_threads": bucket["compilation_ms_all_threads"]})
    process_mean = result["cpu_load"]["mean_jvm_fraction"]
    render_mean = result["render_thread_cpu_load"]["mean_machine_fraction"]
    result["counter_warnings"] = []
    if process_mean is not None and render_mean is not None and process_mean < render_mean * .5:
        result["counter_warnings"].append(
            "Mean JVM CPU load is less than half the mean render-thread CPU load. Different trailing windows/counter accounting do not corroborate one another; do not infer whole-process utilization from this aggregate event.")
    return result


def find_jfr():
    tool = shutil.which("jfr")
    if tool:
        return tool
    resolver = Path("/usr/libexec/java_home")
    if resolver.exists():
        jdk = subprocess.run([str(resolver), "-v", "21"], check=True, capture_output=True, text=True).stdout.strip()
        candidate = Path(jdk) / "bin/jfr"
        if candidate.exists():
            return str(candidate)
    raise ValueError("JDK jfr tool not found; pass --jfr /path/to/jdk/bin/jfr")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("recording", type=Path, help="Completed .jfr or already-exported .json")
    parser.add_argument("--recording-closed", action="store_true", help="Confirm owning JVM exited/recording completed before exporting .jfr")
    parser.add_argument("--jfr")
    parser.add_argument("--export-json", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--measurement", type=Path, help="Completed WorkloadSession JSON for absolute capture-window alignment")
    parser.add_argument("--start", help="ISO-8601 absolute start")
    parser.add_argument("--end", help="ISO-8601 absolute end")
    parser.add_argument("--bin-seconds", type=float, default=5)
    parser.add_argument("--render-thread", default="auto", help="auto derives name from GameRenderer/Minecraft stacks; otherwise regular expression")
    args = parser.parse_args()
    source = args.recording.resolve()
    before = source.stat()
    export = source
    temporary = None
    if source.suffix == ".jfr":
        if not args.recording_closed:
            parser.error("Refusing to export .jfr before --recording-closed confirmation")
        if args.export_json:
            export = args.export_json
            export.parent.mkdir(parents=True, exist_ok=True)
        else:
            temporary = tempfile.TemporaryDirectory(prefix="shaderlod-jfr-")
            export = Path(temporary.name) / "recording.json"
        with export.open("w") as stream:
            subprocess.run([args.jfr or find_jfr(), "print", "--json", "--events", ",".join(EVENTS),
                            "--stack-depth", "64", str(source)], stdout=stream, check=True)
        after = source.stat()
        if (before.st_size, before.st_mtime_ns) != (after.st_size, after.st_mtime_ns):
            raise ValueError("Recording changed during export; refuse incomplete evidence")
    payload = json.loads(export.read_text())
    start, end = args.start, args.end
    measurement = None
    if args.measurement:
        measurement = json.loads(args.measurement.read_text())
        if measurement.get("status") != "complete":
            raise ValueError("Workload measurement not complete")
        start = start or measurement["measurement_started_at_utc"]
        end = end or timestamp(start) + measurement["elapsed_seconds"]
    result = analyze(payload, args.render_thread, args.bin_seconds, start, end)
    result["source"] = str(source)
    result["source_sha256"] = hashlib.sha256(source.read_bytes()).hexdigest()
    if args.export_json:
        result["export_json"] = str(args.export_json.resolve())
    if args.measurement:
        result["measurement_source"] = str(args.measurement.resolve())
        result["measurement_metadata"] = {key: measurement.get(key) for key in
                                            ("profile", "render_frames", "elapsed_seconds", "measurement_started_at_utc", "sampling_policy", "backend", "loaded_mods")}
    content = json.dumps(result, indent=2, allow_nan=False) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(content)
    else:
        print(content, end="")
    if temporary:
        temporary.cleanup()


if __name__ == "__main__":
    main()
