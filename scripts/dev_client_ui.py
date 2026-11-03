#!/usr/bin/env python3
"""Retained, explicitly authorized AppleScript fallback for one owned Mac client."""
import argparse
import json
import hashlib
import re
from datetime import datetime, timedelta, timezone
from urllib.parse import unquote
from pathlib import Path
import subprocess
import sys

import dev_client as dc


MAIN_JAR_SHA256 = "7ecf471e1750c1ed933718480b3224b5038e2c073a7cad3a856f00ad877e3428"
ORIGINAL_PACK = "MakeUp-UltraFast-9.5g.zip"
ORIGINAL_PACK_SHA256 = "0036da17e0c7158e1c669593e6322d53fce3f5a42fb5124b935e9e21a5719db8"
INTERACTION_LABEL = "PACKAGED_NATIVE_WORLD_JOIN_INTERACTION"


def plain_arg_tokens(text):
    """Only the generated subset: whitespace, full-line comments, whole quoted tokens.

    No shell interpretation; backslash escapes/continuations/inline comments are
    deliberately unsupported. Unknown Java syntax is refusal, never guessing.
    """
    tokens = []
    for line in text.splitlines():
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        dc.require(not any(c in line for c in ("\\", "\x00", "#")), "Unsupported argfile syntax")
        while line.strip():
            line = line.lstrip()
            if line[0] in ("'", '"'):
                quote = line[0]
                end = line.find(quote, 1)
                dc.require(end > 1 and (end+1 == len(line) or line[end+1].isspace()),
                           "Unsupported argfile quoting")
                token, line = line[1:end], line[end+1:]
                dc.require(not any(c in token for c in ("'", '"')), "Unsupported argfile quoting")
            else:
                pieces = line.split(None, 1)
                token, line = pieces[0], pieces[1] if len(pieces) > 1 else ""
                dc.require(not any(c in token for c in ("'", '"')), "Unsupported argfile quoting")
            dc.require(not token.startswith("@") and token != "--disable-@files", "Nested/disabled argfile")
            tokens.append(token)
    dc.require(bool(tokens), "Empty argfile")
    return tokens


def regular_unlinked(path):
    dc.require(path.is_absolute() and path == path.resolve() and path.is_file() and
               not any(p.is_symlink() for p in (path, *path.parents)), "Argfile path/symlink refused")


def resolved_packaged_args(admission, session, owner, proof, prepath, pre, launch, now):
    """Only the two actual ModDev packagedMain argfiles and a pre-Java receipt.

    A locally recorded snapshot does not establish VM consumed bytes. The caller
    still requires current native loader/class/world evidence separately.
    """
    pin = owner["plan"].get("launchArgfiles")
    dc.require(isinstance(pin, dict) and pin == admission.get("launchArgfiles"), "Argfile owner/admission pin mismatch")
    manifest_path = session / "launch-argfiles/manifest.json"
    dc.require(pin.get("path") == str(manifest_path), "Wrong argfile manifest session")
    regular_unlinked(manifest_path)
    sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
    manifest_before = manifest_path.stat()
    manifest_bytes = manifest_path.read_bytes()
    manifest_after = manifest_path.stat()
    dc.require((manifest_before.st_ino, manifest_before.st_size, manifest_before.st_mtime_ns) ==
               (manifest_after.st_ino, manifest_after.st_size, manifest_after.st_mtime_ns) and
               hashlib.sha256(manifest_bytes).hexdigest() == pin.get("sha256"), "Argfile manifest pin mismatch")
    manifest = json.loads(manifest_bytes)
    contract_path = session / "launch-contract.json"
    regular_unlinked(contract_path)
    contract_bytes = contract_path.read_bytes()
    dc.require(hashlib.sha256(contract_bytes).hexdigest() == manifest.get("launchContractSha256"),
               "Launch contract pin mismatch")
    contract = json.loads(contract_bytes)
    dc.require(contract.get("schema") == "packaged-launch-contract-r1" and
               contract.get("session") == str(session) and contract.get("profile") == str(pre["profile"]) and
               contract.get("launchInstant") == admission["launchInstant"] and
               contract.get("stop_at_utc") == owner["plan"]["stop_at_utc"], "Wrong launch contract")
    capture = datetime.fromisoformat(manifest["captureInstant"])
    dc.require(manifest.get("schema") == "packaged-launch-argfiles-r1" and
               manifest.get("phase") == "fresh-prelaunch-before-java" and
               manifest.get("launchInstant") == admission["launchInstant"] and
               manifest.get("session") == str(session) and manifest.get("profile") == str(pre["profile"]) and
               manifest.get("prelaunchSha256") == admission["prelaunchSha256"] and
               capture.tzinfo is not None and launch <= capture <= now and
               launch.timestamp() <= prepath.stat().st_mtime <= capture.timestamp()+0.001 and
               launch.timestamp() <= contract_path.stat().st_mtime <= capture.timestamp()+0.001 and
               capture.timestamp()-0.001 <= manifest_path.stat().st_mtime <= now.timestamp()+1,
               "Stale/wrong pre-Java argfile provenance")
    # ps argv is flattened. Support only this actual launcher with no ambiguous
    # quoting/whitespace in paths; richer argv requires an authoritative vector.
    raw = proof["argv"]
    dc.require(not any(c in raw for c in ("'", '"', "\\", "\n", "\x00")), "Ambiguous process argv")
    raw_tokens = raw.split()
    main = "net.neoforged.devlaunch.Main"
    dc.require(raw_tokens.count(main) == 1, "Wrong packaged launcher main")
    pivot = raw_tokens.index(main)
    expected = {role: dc.ROOT / ("build/moddev/packagedMainRun" + suffix + "Args.txt")
                for role, suffix in (("vm", "Vm"), ("program", "Program"))}
    refs = [t for t in raw_tokens if t.startswith("@")]
    dc.require(refs == ["@"+str(expected["vm"]), "@"+str(expected["program"])] and
               raw_tokens.index(refs[0]) < pivot and raw_tokens[pivot+1:] == [refs[1]],
               "Unknown/conflicting argfile launch shape")
    entries = manifest.get("files", [])
    dc.require(len(entries) == 2 and [e.get("role") for e in entries] == ["vm", "program"],
               "Wrong argfile manifest entries")
    expanded = {}
    for entry in entries:
        role = entry["role"]
        original = expected[role]
        snapshot = manifest_path.parent / original.name
        dc.require(entry.get("original") == str(original) and entry.get("snapshot") == str(snapshot),
                   "Wrong argfile provenance paths")
        for path in (original, snapshot):
            regular_unlinked(path)
            before = path.stat()
            data = path.read_bytes()
            after = path.stat()
            dc.require((before.st_ino, before.st_size, before.st_mtime_ns) ==
                       (after.st_ino, after.st_size, after.st_mtime_ns) and
                       hashlib.sha256(data).hexdigest() == entry.get("sha256") and
                       len(data) == entry.get("size"), "Modified/racing argfile")
            if path == original:
                dc.require(after.st_mtime_ns == entry.get("originalMtimeNs") and
                           launch.timestamp() <= after.st_mtime <= capture.timestamp()+0.001,
                           "Stale/replaced generated argfile")
            else:
                dc.require(capture.timestamp()-0.001 <= after.st_mtime <= now.timestamp()+1,
                           "Stale argfile snapshot")
                expanded[role] = plain_arg_tokens(data.decode("utf-8", errors="strict"))
    vm = raw_tokens[1:pivot]
    vm[vm.index(refs[0]):vm.index(refs[0])+1] = expanded["vm"]
    # Only the observed -cp and optional single -p forms are supported. Java
    # long options also accept =value; splitting the option name catches these
    # overrides without trying to interpret an alternate launcher grammar.
    alternate_paths = ("-classpath", "--class-path", "-jar", "--module-path",
                       "--upgrade-module-path", "--patch-module", "-m", "--module")
    dc.require(vm.count("-cp") == 1 and vm.count("-p") <= 1 and not any(
               t.split("=", 1)[0] in alternate_paths or t.startswith(("-cp=", "-p=")) for t in vm),
               "Conflicting VM classpath/module arguments")
    cp = vm[vm.index("-cp")+1].split(":")
    dc.require(cp == pre["classpath"], "Resolved VM/prelaunch classpath mismatch")
    program = expanded["program"]
    dc.require(program[0] == "cpw.mods.bootstraplauncher.BootstrapLauncher" and
               program.count("--gameDir") == 1 and program[program.index("--gameDir")+1] == ".",
               "Wrong packaged program/profile arguments")
    return vm, program


def validate_packaged_interaction(admission, session, owner, table, pid, proof, now=None):
    """Read-only admission, never timing/frame/viewport/focus/backend readiness."""
    now = now or datetime.now(timezone.utc)
    dc.require(admission.get("schema") == "packaged-interaction-r2", "Wrong packaged admission schema")
    dc.require(Path(admission["session"]).resolve() == session, "Wrong packaged session")
    profile = Path(admission["profile"]).resolve()
    dc.require(profile == Path(owner["plan"]["profile"]).resolve(), "Wrong packaged profile")
    launch = datetime.fromisoformat(admission["launchInstant"])
    end = datetime.fromisoformat(owner["plan"]["stop_at_utc"])
    dc.require(launch.tzinfo is not None and end.tzinfo is not None, "Timezone required")
    dc.require(owner["plan"].get("launchInstant") == admission["launchInstant"], "Wrong launch session")
    dc.require(launch <= now < end and 0 < (end-launch).total_seconds() <= 120,
               "Packaged interaction requires an active finite deadline of at most 120 seconds")
    dc.require(owner.get("status") == "running" and not any(k in owner for k in
               ("exit_code", "deadline_stop_requested_utc", "deadline_stop_error", "launcher_exit_timeout")),
               "Packaged client is not running or stop/exit was requested")
    dc.require(len(owner.get("clients", {})) == 1 and owner["clients"].get(str(pid)) == proof
               and pid in table and dc.identity(table[pid]) == proof and list(dc.clients(table)) == [pid],
               "Packaged PID/birth/argv/single-client mismatch")
    jar = profile / "mods/shaderlod-0.1.0-dev.jar"
    sha = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()
    dc.require(admission.get("mainJarSha256") == MAIN_JAR_SHA256 and sha(jar) == MAIN_JAR_SHA256,
               "Current packaged main JAR mismatch")
    dc.require(admission.get("selectedPack") == ORIGINAL_PACK and
               admission.get("selectedPackSha256") == ORIGINAL_PACK_SHA256 and
               sha(profile / "shaderpacks" / ORIGINAL_PACK) == ORIGINAL_PACK_SHA256,
               "Original shader pack mismatch")
    iris = dc.properties((profile / "config/iris.properties").read_text())
    dc.require(iris.get("shaderPack") == ORIGINAL_PACK and iris.get("enableShaders") == "true",
               "Original shaders are not selected")
    prepath = profile.parent / "prelaunch.json"
    dc.require(sha(prepath) == admission["prelaunchSha256"], "Prelaunch pin mismatch")
    pre = json.loads(prepath.read_text())
    dc.require(pre["sourceSet"] == "packagedMain" and pre["loadedMods"] == [] and
               pre["systemProperties"] == {} and Path(pre["profile"]).resolve() == profile and
               Path(pre["expectedModJar"]).resolve() == jar, "Packaged prelaunch mismatch")
    forbidden = ("/build/classes/java/main", "/build/resources/main", "/build/classes/java/devClient",
                 "/build/resources/devClient", "-Dshaderlod.dev.", "-Dfml.modFolders=")
    dc.require(not any(x in proof["argv"] for x in forbidden), "Development argv detected")
    dc.require(bool(pre["classpath"]) and not any(any(x in p for x in forbidden) or
               re.search(r'/shaderlod[^/]*\.jar$', p) for p in pre["classpath"]), "Development classpath detected")
    vm, program = resolved_packaged_args(admission, session, owner, proof, prepath, pre, launch, now)
    dc.require(not any(any(x in t for x in forbidden) for t in vm + program), "Development resolved arguments detected")
    dc.require(not any(re.search(r'/shaderlod[^/]*\.jar(?:$|:)', t) for t in vm),
               "Development resolved classpath detected")
    classpath = profile.parent / "runtime-class-load.log"
    dc.require(Path(admission["loaderClassOriginLog"]).resolve() == classpath and
               [t for t in vm if t.startswith("-Xlog")] == ["-Xlog:class+load=info:file=" + str(classpath)], "Wrong class log session")
    paths = (session / "console.log", profile / "logs/debug.log", classpath, prepath)
    dc.require(all(p.is_file() and not p.is_symlink() and
               launch.timestamp() <= p.stat().st_mtime <= now.timestamp()+1 for p in paths),
               "Missing or stale current-session logs/prelaunch")
    console, debug, classes = (p.read_text(errors="replace") for p in paths[:3])
    dc.require(not dc.FATAL.search(console + debug) and not re.search(
               r'\bFATAL\b|Stopping!|Stopping server|Disconnecting|Process .* exited|BUILD FAILED', console + debug),
               "Terminal or fatal runtime evidence")
    # Saved-world missing-dev warning is allowed only with its exact loader context.
    for text in (console, debug):
        lines = text.splitlines()
        for i, line in enumerate(lines):
            if "shaderlod_dev" in line:
                dc.require(line == "shaderlod_dev (version 0.1.0 -> MISSING)" and i > 0 and
                           "CommonHooks/WP" in lines[i-1] and
                           "The following mods have version differences that were not resolved:" in lines[i-1] and
                           i+1 < len(lines) and lines[i+1] == "Things may not work well.", "Loaded/unexplained dev module")
        dc.require(not any(t in text for t in ("com.pp.shaderlod.dev.", "shaderlod.dev.", "mod/shaderlod_dev",
                                               "shaderlod-dev.mixins", "{shaderlod_dev}", "(shaderlod_dev)")),
                   "Development runtime evidence")
    classes = unquote(classes)
    direct = [line for line in classes.splitlines() if "com.pp.shaderlod." in line and "$$Lambda/" not in line]
    origin = " source: union:" + str(jar) + "#"
    dc.require(any("com.pp.shaderlod.ShaderLod source:" in line for line in direct) and
               "com.pp.shaderlod.dev." not in classes and all(origin in line for line in direct),
               "Current private main class origin missing/mismatched")
    dc.require("Found valid mod file " + jar.name + " with {shaderlod} mods - versions {0.1.0-dev}" in debug and
               "Considering mod file candidate " + str(jar) in debug and
               "ShaderLOD 0.1.0-dev (shaderlod)" in debug, "Current main loader discovery missing")
    lines = console.splitlines()
    def current_line(line):
        stamp = re.match(r'^\[(\d\d):(\d\d):(\d\d)\]', line)
        if not stamp:
            return False
        local = launch.astimezone()
        t = local.replace(hour=int(stamp[1]), minute=int(stamp[2]), second=int(stamp[3]), microsecond=0)
        return any(launch.timestamp()-1 <= (t+timedelta(days=d)).timestamp() <= now.timestamp()+1
                   for d in (-1, 0, 1))
    joins = [i for i, line in enumerate(lines) if "Dev joined the game" in line and current_line(line)]
    pipelines = [i for i, line in enumerate(lines) if "Creating pipeline for dimension minecraft:overworld" in line
                 and current_line(line)]
    runtime = [(i, line) for i, line in enumerate(lines) if "ShaderLOD runtime: " in line and current_line(line)]
    dc.require(joins and pipelines and runtime, "Fresh world/pipeline/main-runtime evidence missing")
    i, line = runtime[-1]
    dc.require(any(j <= p <= i for j in joins for p in pipelines), "World/pipeline/runtime ordering mismatch")
    fields = dict(re.findall(r'(\w+)=([^,;]+)', line.split("ShaderLOD runtime: ", 1)[1]))
    dc.require(fields.get("pack") == ORIGINAL_PACK and fields.get("shadersInUse") == "true" and
               fields.get("fallback") == "false" and fields.get("enabled") in ("true", "false") and
               fields.get("preset") in ("QUALITY", "PERFORMANCE", "BALANCED"), "Current main runtime mismatch")
    return {"label": INTERACTION_LABEL, "runtimeLine": line, "enabled": fields["enabled"],
            "preset": fields["preset"], "renderedFrame": "UNVERIFIED", "viewport": "UNVERIFIED",
            "focus": "UNVERIFIED", "backend": "UNVERIFIED"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--session", required=True, type=Path)
    parser.add_argument("--packaged-interaction", type=Path, help="Pinned packaged admission JSON; interaction only")
    parser.add_argument("--all-owned-windows", action="store_true",
                        help="One startup request using ActivateAllWindows for this exact owned application")
    parser.add_argument("action", choices=("focus", "inspect", "key", "click"))
    parser.add_argument("values", nargs="*", type=int)
    args = parser.parse_args()
    if sys.platform != "darwin":
        parser.error("This helper is for the owned Mac development client")
    session = args.session.resolve()
    owner = json.loads((session / "owner.json").read_text())
    table = dc.process_table()
    live = [(int(pid), proof) for pid, proof in owner.get("clients", {}).items()
            if int(pid) in table and dc.identity(table[int(pid)]) == proof]
    dc.require(len(live) == 1 and len(dc.clients(table)) == 1,
               "Exactly one live, identity-matched owned Minecraft client is required")
    pid, proof = live[0]
    dc.require(pid in dc.clients(table), "Owned process is not the Minecraft client")
    interaction = None
    if args.packaged_interaction:
        dc.require(not args.all_owned_windows, "Packaged startup focus requires activation options 0")
        dc.require(dc.associated(table[pid], Path(owner["plan"]["profile"])), "Owned packaged profile mismatch")
        interaction = validate_packaged_interaction(json.loads(args.packaged_interaction.read_text()),
                                                    session, owner, table, pid, proof)
    else:
        dc.require(owner.get("status") == "ready", "Client readiness is required")
    expected_args = {"focus": 0, "inspect": 0, "key": 1, "click": 2}
    dc.require(len(args.values) == expected_args[args.action], "Wrong action arguments")
    dc.require(not args.all_owned_windows or args.action == "focus", "Window option is focus-only")
    dc.require(args.action != "focus" or not owner.get("plan", {}).get("background_launch", False),
               "Focus refused by background launch preference")
    focus_receipt = session / "ui-focus-once.json"
    dc.require(args.action != "focus" or not focus_receipt.exists(),
               "Focus was already attempted for this session; no refocus loop")
    if args.packaged_interaction:
        current = dc.process_table()
        validate_packaged_interaction(json.loads(args.packaged_interaction.read_text()),
                                      session, json.loads((session / "owner.json").read_text()), current, pid, proof)
    if args.action == "focus":
        dc.write_json(focus_receipt, {"status": "attempted", "client": proof})
    if args.action == "focus":
        command = ["/usr/bin/osascript", str(Path(__file__).with_name("dev_client_focus.applescript")),
                   str(pid), "1" if args.all_owned_windows else "0"]
    else:
        command = ["/usr/bin/osascript", str(Path(__file__).with_suffix(".applescript")),
                   str(pid), args.action, *(str(v) for v in args.values)]
    try:
        result = subprocess.run(command, capture_output=True, text=True, timeout=12)
    except subprocess.TimeoutExpired:
        if args.action == "focus":
            dc.write_json(focus_receipt, {"action": args.action, "client": proof,
                          "status": "timed-out-outcome-unverified",
                          "error": "AppleScript did not return within 12 seconds"})
        raise SystemExit("AppleScript timed out; UI outcome remains unverified; no automatic retry")
    current = dc.process_table()
    dc.require(pid in current and dc.identity(current[pid]) == proof,
               "Owned client identity changed during UI operation")
    receipt = {"action": args.action, "client": proof, "activationOptions": 1 if args.all_owned_windows else 0,
               "interaction": interaction, "returncode": result.returncode,
               "output": result.stdout.strip(), "error": result.stderr.strip()}
    if args.action == "focus":
        dc.write_json(focus_receipt, receipt)
    print(json.dumps({"action": args.action, "pid": pid, "interaction": interaction, "returncode": result.returncode,
                      "output": receipt["output"], "error": receipt["error"]}, indent=2))
    dc.require(result.returncode == 0, "Owned-client AppleScript action failed")


if __name__ == "__main__":
    main()
