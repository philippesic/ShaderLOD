#!/usr/bin/env python3
"""Bounded stdlib reader/writer for facts embedded in the authoritative STATUS."""
import argparse
from datetime import datetime, timezone, timedelta
import hashlib
import json
import os
import math
from pathlib import Path
import re
import sys
import tempfile
from urllib.parse import quote, unquote

BEGIN = "<!-- codex-current-state:v1\n"
END = "\n-->"
LIMIT = 1048576
FACT_LIMIT = 32768
REASONS = {"no_next_owner", "preparation_overdue", "same_cause_failures", "review_overdue"}


class CheckpointError(ValueError):
    pass


def utcnow():
    return datetime.now(timezone.utc)


def stamp(value, field, now):
    if not isinstance(value, str) or not re.fullmatch(
            r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:\d{2})", value):
        raise CheckpointError(f"{field}: require RFC3339 timestamp with timezone")
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as exc:
        raise CheckpointError(f"{field}: invalid timestamp") from exc
    if parsed > now + timedelta(minutes=5):
        raise CheckpointError(f"{field}: observation/event is in the future")
    return parsed


def text_field(value, field, nullable=False, limit=2000):
    if nullable and value is None:
        return
    if not isinstance(value, str) or not value.strip() or len(value) > limit:
        raise CheckpointError(f"{field}: require nonempty text, at most {limit} characters")
    if any(ord(c) < 32 and c not in "\n\t" for c in value) or "codex-current-state" in value or "-->" in value:
        raise CheckpointError(f"{field}: unsupported control/marker text")


def keys(value, required, field):
    if not isinstance(value, dict) or set(value) != set(required):
        raise CheckpointError(f"{field}: expected exactly {', '.join(required)}")


def validate(facts, now=None):
    now = now or utcnow()
    required = ("schema_version", "objective", "baseline", "result", "hypothesis", "owner",
                "stage", "status", "process_health", "authority", "goal", "resources",
                "next_action", "observed_at", "event_at", "evidence", "review")
    version = facts.get("schema_version") if isinstance(facts, dict) else None
    if type(version) is not int or version not in {1, 2}:
        raise CheckpointError("schema_version must be 1 or 2")
    keys(facts, required + (("progress",) if version == 2 else ()), "facts")
    for field in ("objective", "baseline", "result", "hypothesis", "owner", "authority", "goal"):
        text_field(facts[field], field)
    choices = {"stage": {"preparation", "execution", "evaluation", "integration", "idle", "completed"},
               "status": {"active", "paused", "blocked", "complete"},
               "process_health": {"healthy", "none", "unknown", "paused", "blocked"}}
    for field, allowed in choices.items():
        if not isinstance(facts[field], str) or facts[field] not in allowed:
            raise CheckpointError(f"{field}: expected one of {sorted(allowed)}")
    observed = stamp(facts["observed_at"], "observed_at", now)
    if facts["event_at"] is not None and stamp(facts["event_at"], "event_at", now) > observed:
        raise CheckpointError("event_at must not follow observed_at")
    resources = facts["resources"]
    if not isinstance(resources, list) or not resources or len(resources) > 12:
        raise CheckpointError("resources: require 1–12 explicit resource/allocation/restriction records")
    for entry in resources:
        text_field(entry, "resources entry")
    action = facts["next_action"]
    keys(action, ("action", "owner", "milestone"), "next_action")
    for field in ("action", "milestone"):
        text_field(action[field], f"next_action.{field}")
    text_field(action["owner"], "next_action.owner", nullable=True)
    evidence = facts["evidence"]
    if not isinstance(evidence, list) or not evidence or len(evidence) > 20:
        raise CheckpointError("evidence: require 1–20 explicit evidence records")
    for entry in evidence:
        keys(entry, ("summary", "path", "observed_at", "event_at"), "evidence entry")
        text_field(entry["summary"], "evidence.summary")
        text_field(entry["path"], "evidence.path")
        eo = stamp(entry["observed_at"], "evidence.observed_at", now)
        if eo > observed:
            raise CheckpointError("evidence observation must not follow checkpoint observation")
        if entry["event_at"] is not None and stamp(entry["event_at"], "evidence.event_at", now) > eo:
            raise CheckpointError("evidence event must not follow its observation")
    review = facts["review"]
    keys(review, ("attempt", "trigger_at", "same_cause_failures", "failure_cause", "decision"), "review")
    text_field(review["attempt"], "review.attempt")
    # A review deadline may be in the future; still require a valid zoned timestamp.
    stamp(review["trigger_at"], "review.trigger_at", datetime.max.replace(tzinfo=timezone.utc)-timedelta(days=1))
    count = review["same_cause_failures"]
    if type(count) is not int or count < 0 or count > 100000:
        raise CheckpointError("review.same_cause_failures: require nonnegative integer")
    text_field(review["failure_cause"], "review.failure_cause", nullable=count == 0)
    decision = review["decision"]
    if decision is not None:
        decision_fields = ("choice", "rationale", "decided_at", "addresses", "next_action")
        keys(decision, decision_fields + (("remaining_blocker", "change", "review_at") if version == 2 else ()), "review.decision")
        decided = stamp(decision["decided_at"], "decision.decided_at", now)
        if version == 2:
            for field in ("remaining_blocker", "change"):
                text_field(decision[field], "decision." + field, limit=700)
            if decision["review_at"] != review["trigger_at"]:
                raise CheckpointError("decision.review_at must equal bounded review trigger")
            if datetime.fromisoformat(decision["review_at"].replace("Z", "+00:00")) <= decided:
                raise CheckpointError("decision requires a bounded review after decided_at")
            if decision["next_action"] != action["action"]:
                raise CheckpointError("decision next action must match current next action")
        if decision["choice"] not in {"simplify", "extend", "switch", "blocked"}:
            raise CheckpointError("decision choice must be simplify, extend, switch or blocked")
        for field in ("rationale", "next_action"):
            text_field(decision[field], "decision." + field)
        if stamp(decision["decided_at"], "decision.decided_at", now) > observed:
            raise CheckpointError("decision must not follow checkpoint observation")
        addresses = decision["addresses"]
        if not isinstance(addresses, list) or not addresses or any(
                not isinstance(a, str) or a not in REASONS for a in addresses) or len(set(addresses)) != len(addresses):
            raise CheckpointError("decision.addresses: require distinct review reason codes")
        if decision["choice"] == "blocked" and facts["status"] != "blocked":
            raise CheckpointError("blocked decision requires status=blocked")
    if version == 2:
        p = facts["progress"]
        keys(p, ("activity", "process", "completed_updates", "execution_seconds", "allocation_seconds",
                 "last_progress_at", "blocker", "limitations"), "progress")
        if p["activity"] not in {"preparation", "allocated_execution", "idle"}:
            raise CheckpointError("progress.activity: preparation, allocated_execution or idle required")
        for field in ("process", "blocker"):
            text_field(p[field], "progress." + field, nullable=True, limit=600)
        text_field(p["limitations"], "progress.limitations", limit=700)
        updates = p["completed_updates"]
        if updates is not None and (type(updates) is not int or updates < 0):
            raise CheckpointError("progress.completed_updates: nonnegative integer or null")
        for field in ("execution_seconds", "allocation_seconds"):
            value = p[field]
            if value is not None and (type(value) not in {int, float} or not math.isfinite(value) or value < 0):
                raise CheckpointError("progress." + field + ": finite nonnegative number or null")
        if p["allocation_seconds"] is not None and (p["allocation_seconds"] <= 0 or
                (p["execution_seconds"] is not None and p["execution_seconds"] > p["allocation_seconds"])):
            raise CheckpointError("progress: execution cannot exceed positive agreed allocation")
        if p["last_progress_at"] is not None and stamp(p["last_progress_at"], "progress.last_progress_at", now) > observed:
            raise CheckpointError("progress time cannot follow observation")
        if p["activity"] == "allocated_execution" and (facts["status"] != "active" or
                facts["process_health"] != "healthy" or not p["process"] or not p["last_progress_at"] or
                not ((updates or 0) > 0 or (p["execution_seconds"] or 0) > 0)):
            raise CheckpointError("allocated_execution requires active healthy owned process and positive observed progress")
    if len(json.dumps(facts).encode()) > FACT_LIMIT:
        raise CheckpointError("facts exceed compact checkpoint limit")
    return facts


def review_due(facts, now=None):
    now = now or utcnow()
    if facts["status"] != "active":
        return []
    due = []
    if facts["stage"] in {"idle", "completed"} and facts["next_action"]["owner"] is None:
        due.append("no_next_owner")
    # Healthy execution is not an administrative timeout; its explicit owner remains responsible.
    deadline = datetime.fromisoformat(facts["review"]["trigger_at"].replace("Z", "+00:00"))
    if facts["schema_version"] == 2:
        healthy_allocation = facts["progress"]["activity"] == "allocated_execution"
    else:
        # Compatibility: v1 has no typed progress. Never exempt broad evaluation.
        healthy_allocation = facts["process_health"] == "healthy" and facts["stage"] == "execution"
    if now >= deadline and not healthy_allocation:
        preparing = facts["stage"] == "preparation" or (facts["schema_version"] == 2 and facts["progress"]["activity"] == "preparation")
        due.append("preparation_overdue" if preparing else "review_overdue")
    if facts["review"]["same_cause_failures"] >= 2:
        due.append("same_cause_failures")
    return due


def safe_path(path):
    path = Path(os.path.abspath(path))
    if any(p.is_symlink() for p in (path, *path.parents)):
        raise CheckpointError("symlink path/ancestor refused")
    if not path.is_file():
        raise CheckpointError("checkpoint must be an existing regular file")
    return path


def read_bytes(path):
    path = safe_path(path)
    with path.open("rb") as stream:
        data = stream.read(LIMIT + 1)
    if len(data) > LIMIT:
        raise CheckpointError("checkpoint exceeds 1 MiB read limit")
    data.decode("utf-8")
    return data


def sha(data):
    return hashlib.sha256(data).hexdigest()


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise CheckpointError(f"duplicate JSON field: {key}")
        result[key] = value
    return result


def extract(data, now=None, canonical=True):
    value = data.decode("utf-8")
    if "codex-current-state" not in value:
        return None
    if value.count(BEGIN) != 1 or value.count("codex-current-state") != 1:
        raise CheckpointError("malformed or duplicate current-state block")
    start = value.index(BEGIN) + len(BEGIN)
    end = value.find(END, start)
    if end < 0:
        raise CheckpointError("unterminated current-state block")
    raw = value[start:end]
    if len(raw.encode()) > FACT_LIMIT:
        raise CheckpointError("embedded facts exceed limit")
    facts = validate(json.loads(raw, object_pairs_hook=unique_object), now)
    if canonical:
        history = re.search(r"\n\[Exact previous checkpoint\]\(([^\n)]+)\)", value)
        if history is None:
            raise CheckpointError("missing exact-history link in managed checkpoint")
        archive = unquote(history.group(1))
        if Path(archive).name != archive or not re.search(r"\.history\.[a-f0-9]{64}(?:\.[^/]*)?$", archive):
            raise CheckpointError("invalid same-directory exact-history link")
        if render(facts, archive) != data:
            raise CheckpointError("visible checkpoint drift/extra content; regenerate from verified facts with expected SHA")
    return facts


def view(path, now=None):
    now = now or utcnow()
    data = read_bytes(path)
    facts = extract(data, now)
    if facts is None:
        return {"path": str(path), "sha256": sha(data), "managed": False}
    age = max(0, int((now - datetime.fromisoformat(facts["observed_at"].replace("Z", "+00:00"))).total_seconds()))
    return {"path": str(path), "sha256": sha(data), "managed": True, "facts": facts,
            "observation_age_seconds": age, "stale_observation": age > 3600,
            "review_due": review_due(facts, now)}


def line(value):
    return " ".join(value.split())


def render_v1(facts, archive):
    f = facts
    action = f["next_action"]
    result = ["# Current project status", "", BEGIN + json.dumps(f, ensure_ascii=False, sort_keys=True, indent=2) + END,
              "", "## Current checkpoint", "", f"**Objective:** {line(f['objective'])}",
              f"**Matched baseline:** {line(f['baseline'])}", f"**Result / evidence:** {line(f['result'])}",
              f"**Hypothesis:** {line(f['hypothesis'])}",
              f"**State:** {f['status']}; stage {f['stage']}; owner {line(f['owner'])}; process health {f['process_health']}.",
              f"**Authority:** {line(f['authority'])}", f"**Goal:** {line(f['goal'])}",
              f"**Resources and restrictions:** {'; '.join(line(r) for r in f['resources'])}",
              f"**Next action:** {line(action['action'])}",
              f"**Next owner / milestone:** {line(action['owner']) if action['owner'] else 'unassigned'} / {line(action['milestone'])}",
              f"**Observed:** {f['observed_at']}; event time: {f['event_at'] or 'unknown (do not infer from observation time)' }.",
              f"**Attempt / review trigger:** {line(f['review']['attempt'])} / {f['review']['trigger_at']}.",
              f"**Same-cause failures:** {f['review']['same_cause_failures']}; cause: {line(f['review']['failure_cause']) if f['review']['failure_cause'] else 'none recorded'}."]
    d = f["review"]["decision"]
    result.append("**Review decision:** " + (f"{d['choice']} at {d['decided_at']}: {line(d['rationale'])}; next: {line(d['next_action'])}." if d else "none recorded."))
    result += ["", "## Evidence", ""]
    for e in f["evidence"]:
        result.append(f"- {line(e['summary'])}: {line(e['path'])}; observed {e['observed_at']}; event {e['event_at'] or 'unknown'}.")
    archive_sha = re.search(r"\.history\.([a-f0-9]{64})", archive).group(1)
    result += ["", f"[Exact previous checkpoint]({quote(archive, safe='._-')}) (SHA-256 `{archive_sha}`).", ""]
    return "\n".join(result).encode("utf-8")


def compact(value, limit=500):
    value = line(value)
    return value if len(value) <= limit else value[:limit - 1] + "… [detail omitted; use --json]"


def duration(value):
    if value is None:
        return "unknown"
    return f"{value:,.4f}".rstrip("0").rstrip(".") if value % 1 else f"{value:,.0f}"


def execution_label(f):
    if f["status"] != "active":
        state = f["status"] + "; process health " + f["process_health"]
    elif f["schema_version"] == 2:
        state = f["progress"]["activity"].replace("_", " ") + "; process health " + f["process_health"]
    else:
        state = "active " + f["stage"] + "; process health " + f["process_health"] + "; typed progress unavailable (v1)"
    if f["schema_version"] == 2:
        p = f["progress"]
        state += "; cumulative " + (str(p["completed_updates"]) if p["completed_updates"] is not None else "unknown") + " updates"
        state += "; " + duration(p["execution_seconds"]) + " execution seconds"
        if p["allocation_seconds"] is not None:
            state += " / " + duration(p["allocation_seconds"]) + " allocated"
            if p["execution_seconds"] is not None:
                state += "; " + duration(p["allocation_seconds"] - p["execution_seconds"]) + " seconds remaining"
    return state


def current_lines(f):
    out = ["**Objective:** " + compact(f["objective"], 450),
           "**Matched baseline:** " + compact(f["baseline"], 450),
           "**Outcome:** " + compact(f["result"], 750),
           "**Execution (last observation):** " + execution_label(f),
           "**Owner:** " + compact(f["owner"], 200),
           "**Authority:** " + compact(f["authority"], 350),
           "**Goal:** " + compact(f["goal"], 400),
           "**Next action:** " + compact(f["next_action"]["action"], 500),
           "**Next owner / milestone:** " + compact(f["next_action"]["owner"] or "unassigned", 180) + " / " + compact(f["next_action"]["milestone"], 350),
           "**Observed:** " + f["observed_at"] + "; event " + (f["event_at"] or "unknown; do not infer")]
    if f["status"] == "active":
        out += ["**Chosen hypothesis (authored):** " + compact(f["hypothesis"], 350)]
    if f["schema_version"] == 2:
        out += ["**Blocker:** " + (f["progress"]["blocker"] or "none recorded"),
                "**Limitations:** " + line(f["progress"]["limitations"])]
    else:
        out += ["**Limitations:** v1 authored prose is unverified; typed counters/limitations require explicit migration. Full facts and linked evidence remain authoritative inputs."]
    out += ["**Review:** " + f["review"]["trigger_at"] + "; " + str(f["review"]["same_cause_failures"]) + " same-cause failures; cause " + compact(f["review"]["failure_cause"] or "none recorded", 250),
            "**Evidence:** " + "; ".join(compact(e["path"], 250) for e in sorted(f["evidence"], key=lambda e: datetime.fromisoformat(e["observed_at"].replace("Z", "+00:00")), reverse=True)[:3]) + ("; earlier receipts in --json and exact history" if len(f["evidence"]) > 3 else "")]
    return out


def render(facts, archive):
    if facts["schema_version"] == 1:
        return render_v1(facts, archive)
    archive_sha = re.search(r"\.history\.([a-f0-9]{64})", archive).group(1)
    lines = ["# Current project status", "", BEGIN + json.dumps(facts, ensure_ascii=False, sort_keys=True, indent=2) + END,
             "", "## Current checkpoint", "", *current_lines(facts), "",
             f"[Exact previous checkpoint]({quote(archive, safe='._-')}) (SHA-256 `{archive_sha}`).", ""]
    return "\n".join(lines).encode("utf-8")


def human_view(report, full=False):
    if not report["managed"]:
        return f"Unmanaged checkpoint: {report['path']}\nSHA-256: {report['sha256']}"
    f = report["facts"]
    # Keep the persisted canonical rendering stable; this is a read-only display.
    if full:
        fields = current_lines(f)
    else:
        fields = ["**Objective:** " + compact(f["objective"], 450),
                  "**Result:** " + compact(f["result"], 750),
                  "**Matched baseline:** " + compact(f["baseline"], 450),
                  "**Last-observed state:** " + execution_label(f) + "; stage " + f["stage"],
                  "**Owner:** " + compact(f["owner"], 200),
                  "**Authority:** " + line(f["authority"]),
                  "**Next action:** " + compact(f["next_action"]["action"], 500),
                  "**Next owner:** " + compact(f["next_action"]["owner"] or "unassigned", 180)]
        if f["schema_version"] == 2:
            p = f["progress"]
            if p["allocation_seconds"] is None:
                fields.append("**Allocation:** typed allocation unspecified; see --json resources for original allocations/restrictions.")
            if p["blocker"]:
                fields.append("**Blocker:** " + line(p["blocker"]))
            fields.append("**Limitations:** " + line(p["limitations"]))
        else:
            fields.append("**Limitations:** v1 has no typed progress/allocation; see --json resources and linked evidence. Authored facts are not live verification.")
        fields += ["**Observed:** " + f["observed_at"] + "; event " + (f["event_at"] or "unknown; do not infer"),
                   "**Goal:** " + line(f["goal"]),
                   "**Recent evidence:** " + "; ".join(line(e["path"]) for e in sorted(f["evidence"], key=lambda e: datetime.fromisoformat(e["observed_at"].replace("Z", "+00:00")), reverse=True)[:3])]
    text = ("\n" if full else "\n\n").join(fields)
    text += f"\n\n**Review due:** {', '.join(report['review_due']) or 'none'}; observation age {report['observation_age_seconds']} seconds"
    if report["stale_observation"]:
        text += "; stale observation, verify live state"
    text += f"\n\n**Full facts / allocations / history:** {report['path']} (show --json or --full)\n**Current SHA-256 for updates:** {report['sha256']}"
    if full:
        text += "\n\n" + json.dumps(f, ensure_ascii=False, sort_keys=True, indent=2)
    return text


def decision_covers(facts, reasons, previous=None):
    if not reasons:
        return True
    d = facts["review"]["decision"]
    if d is None or not set(reasons).issubset(d["addresses"]):
        return False
    if previous and datetime.fromisoformat(d["decided_at"].replace("Z", "+00:00")) < datetime.fromisoformat(previous["observed_at"].replace("Z", "+00:00")):
        return False
    return True


def update(path, facts, expected, apply=False, now=None):
    now = now or utcnow()
    path = safe_path(path)
    previous_bytes = read_bytes(path)
    if not re.fullmatch(r"[a-f0-9]{64}", expected) or sha(previous_bytes) != expected:
        raise CheckpointError("expected previous SHA-256 mismatch; reread before updating")
    # Recovery may archive visible-body drift, but malformed machine facts are refused.
    previous = extract(previous_bytes, now, canonical=False)
    validate(facts, now)
    if previous and previous["schema_version"] == 2 and facts["schema_version"] == 1:
        raise CheckpointError("v2 checkpoint cannot be downgraded by an in-flight v1 writer; reread show --json, retain progress/schema_version=2 and update using the new SHA")
    due = review_due(facts, now)
    old_due = review_due(previous, now) if previous and facts["status"] == "active" else []
    if not decision_covers(facts, list(set(due + old_due)), previous):
        raise CheckpointError("review response required: " + ", ".join(sorted(set(due + old_due))))
    if previous:
        old = previous["review"]
        new = facts["review"]
        d = new["decision"]
        fresh_decision = bool(d and d != old["decision"] and
            datetime.fromisoformat(d["decided_at"].replace("Z", "+00:00")) >=
            datetime.fromisoformat(previous["observed_at"].replace("Z", "+00:00")))
        paused_migration = (previous["schema_version"] == 1 and facts["schema_version"] == 2 and
                            previous["status"] == facts["status"] == "paused" and
                            new["trigger_at"] == old["trigger_at"])
        if new["attempt"] != old["attempt"] and not paused_migration and (not fresh_decision or d["choice"] != "switch"):
            raise CheckpointError("attempt change requires fresh explicit switch decision; subtask does not reset budget")
        distinct_attempt_switch = bool(new["attempt"] != old["attempt"] and fresh_decision and d["choice"] == "switch")
        if facts["schema_version"] == 2 and distinct_attempt_switch and new["attempt"] not in d["change"]:
            raise CheckpointError("new attempt switch must name the new experiment/attempt in decision.change; timestamp alone does not reset accounting")
        if previous["schema_version"] == facts["schema_version"] == 2 and not distinct_attempt_switch:
            for field in ("completed_updates", "execution_seconds"):
                before = previous["progress"][field]
                after = facts["progress"][field]
                if before is not None and (after is None or after < before):
                    raise CheckpointError("same-attempt cumulative progress cannot be erased or move backwards; retain actual paid counters")
            if previous["progress"]["allocation_seconds"] != facts["progress"]["allocation_seconds"]:
                raise CheckpointError("same-attempt agreed allocation cannot change silently; retain original allocation or explicitly switch to the named new experiment")
        if datetime.fromisoformat(new["trigger_at"].replace("Z", "+00:00")) > datetime.fromisoformat(old["trigger_at"].replace("Z", "+00:00")) and (not fresh_decision or d["choice"] not in {"extend", "switch"}):
            raise CheckpointError("later review trigger requires fresh explicit extend/switch decision")
        if facts["schema_version"] == 2 and fresh_decision:
            if d["remaining_blocker"] != (facts["progress"]["blocker"] or "none recorded"):
                raise CheckpointError("decision remaining blocker must match current progress blocker or 'none recorded'")
            old_d = old["decision"] or {}
            # Observation refreshes are metadata, not milestone evidence.
            receipt_content = lambda records: [(e["summary"], e["path"]) for e in records]
            progress_content = lambda p: {k: v for k, v in (p or {}).items() if k != "last_progress_at"}
            changed = (facts["result"] != previous["result"] or
                       receipt_content(facts["evidence"]) != receipt_content(previous["evidence"]) or
                       progress_content(facts["progress"]) != progress_content(previous.get("progress")))
            if not changed and d["rationale"] == old_d.get("rationale") and d["change"] == old_d.get("change"):
                raise CheckpointError("fresh decision needs changed evidence/progress or changed substantive rationale; timestamp alone is insufficient")
        if datetime.fromisoformat(facts["observed_at"].replace("Z", "+00:00")) < datetime.fromisoformat(previous["observed_at"].replace("Z", "+00:00")):
            raise CheckpointError("checkpoint observation cannot move backwards")
    archive = path.with_name(f"{path.stem}.history.{expected}{path.suffix}")
    reused = archive.exists() or archive.is_symlink()
    if reused and read_bytes(archive) != previous_bytes:
        raise CheckpointError("archive destination differs from exact previous bytes; never overwritten")
    candidate = render(facts, archive.name)
    report = {"applied": False, "path": str(path), "previous_sha256": expected,
              "next_sha256": sha(candidate), "archive": str(archive), "archive_reused": reused, "review_due": due,
              "text": candidate.decode()}
    if not apply:
        return report
    lock = path.with_name(f".{path.name}.checkpoint.lock")
    temp = None
    lock_fd = None
    try:
        lock_fd = os.open(lock, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
        os.write(lock_fd, f"pid={os.getpid()} expected={expected}\n".encode())
        # Re-read under cooperative writer lock; preserve uncoordinated edits seen here too.
        if read_bytes(path) != previous_bytes:
            raise CheckpointError("checkpoint changed while preparing update")
        try:
            fd = os.open(archive, os.O_CREAT | os.O_EXCL | os.O_WRONLY, path.stat().st_mode & 0o777)
        except FileExistsError:
            if read_bytes(archive) != previous_bytes:
                raise CheckpointError("existing archive differs; never overwritten")
            report["archive_reused"] = True
        else:
            with os.fdopen(fd, "wb") as stream:
                stream.write(previous_bytes)
                stream.flush()
                os.fsync(stream.fileno())
        if read_bytes(archive) != previous_bytes:
            raise CheckpointError("archive verification failed; checkpoint left unchanged")
        fd, temp = tempfile.mkstemp(prefix=f".{path.name}.", dir=path.parent)
        with os.fdopen(fd, "wb") as stream:
            os.fchmod(stream.fileno(), path.stat().st_mode & 0o777)
            stream.write(candidate)
            stream.flush()
            os.fsync(stream.fileno())
        if read_bytes(path) != previous_bytes:
            raise CheckpointError("checkpoint changed before replacement; archive preserved")
        safe_path(path)
        os.replace(temp, path)
        temp = None
        directory = os.open(path.parent, os.O_RDONLY)
        try:
            os.fsync(directory)
        finally:
            os.close(directory)
        report["applied"] = True
        report.pop("text")
        return report
    except FileExistsError as exc:
        raise CheckpointError("exclusive lock/archive exists; inspect before recovery, never overwritten") from exc
    finally:
        if temp is not None:
            os.unlink(temp)
        if lock_fd is not None:
            os.close(lock_fd)
            os.unlink(lock)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("show", "lint", "update"))
    parser.add_argument("checkpoint")
    parser.add_argument("--input", help="full JSON facts (no defaults/invented facts)")
    parser.add_argument("--expected-sha")
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--json", action="store_true", help="full machine-readable show (lint/update remain JSON)")
    parser.add_argument("--full", action="store_true", help="human view plus full facts")
    args = parser.parse_args()
    try:
        if args.action == "update":
            if not args.input or not args.expected_sha:
                raise CheckpointError("update requires --input and --expected-sha")
            with open(args.input, "rb") as stream:
                raw = stream.read(FACT_LIMIT + 1)
            if len(raw) > FACT_LIMIT:
                raise CheckpointError("input facts exceed 32 KiB")
            result = update(args.checkpoint, json.loads(raw, object_pairs_hook=unique_object), args.expected_sha, args.apply)
        else:
            if args.apply or args.input or args.expected_sha:
                raise CheckpointError("write options require update")
            result = view(args.checkpoint)
            if args.action == "lint" and not result["managed"]:
                raise CheckpointError("no current-state block: migration requires factual full input")
        if args.action == "show" and not args.json:
            print(human_view(result, args.full))
        else:
            print(json.dumps(result, ensure_ascii=False, sort_keys=True))
    except (CheckpointError, ValueError, OSError, TypeError, OverflowError) as exc:
        print(json.dumps({"error": str(exc)}), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
