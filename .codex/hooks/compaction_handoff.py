"""Emit durable project context without launching chats or controlling jobs."""

import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "scripts"))
from agent_runtime.checkpoint import view


def main() -> None:
    try:
        event = json.load(sys.stdin)
    except (ValueError, OSError):
        return
    if not isinstance(event, dict):
        return
    source = event.get("source")
    if source not in {"startup", "resume", "compact", "fork"}:
        return
    message = "ShaderLOD checkpoint: run `python3 scripts/project_checkpoint.py show docs/STATUS.md`; linked goal and operating guidance are in that record and AGENTS.md. "
    try:
        facts = view(ROOT / "docs/STATUS.md").get("facts")
        if facts:
            owner = " ".join(facts["owner"].split())
            message += f"Last observation {facts['observed_at']}; recorded owner: {owner[:260]}. "
    except (ValueError, OSError, TypeError, KeyError):
        message += "Checkpoint unavailable or invalid; inspect it before relying on recorded state. "
    message += "This is a recorded observation, not a live job census or continuation owner."
    print(json.dumps({"hookSpecificOutput": {
        "hookEventName": "SessionStart", "additionalContext": message
    }}))


if __name__ == "__main__":
    main()
