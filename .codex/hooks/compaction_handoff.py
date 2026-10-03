"""Emit durable project context without launching chats or controlling jobs."""

import json
import sys


def main() -> None:
    try:
        event = json.load(sys.stdin)
    except (ValueError, OSError):
        return
    if not isinstance(event, dict):
        return
    source = event.get("source")
    if source not in {"startup", "resume", "compact"}:
        return
    message = (
        "ShaderLOD durable state is docs/STATUS.md and its linked docs/goals/ file. "
        "Read the active checkpoint before substantial work. Follow "
        "docs/AGENT_OPERATIONS.md for worker ownership and runtime handoffs. "
        "Iris is required; Photon is the explicit shader target; DH is optional."
    )
    if source == "compact":
        message += (
            " After compaction, verify live agents and client/GPU jobs before "
            "resuming. Refresh the checkpoint at the next milestone with actual "
            "checks and next actions. A hook cannot transfer running work or "
            "authorize creation of a new chat."
        )
    print(json.dumps({"hookSpecificOutput": {
        "hookEventName": "SessionStart", "additionalContext": message
    }}))


if __name__ == "__main__":
    main()
