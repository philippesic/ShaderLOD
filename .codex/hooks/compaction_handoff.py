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
        "Current scope spans nine shader families and NeoForge/Fabric; Iris is required, DH/PBR need compatibility evidence. Historical assignments are not live work."
        " For an explicitly requested Goal, verify native Goal state with get_goal; "
        "a file or this reminder is not continuation. Use checkpoint.py show for "
        "the compact view and show --json for typed updates. Follow the current "
        "adoption receipt for actual compiled consumers rather than an old staged patch."
    )
    if source == "compact":
        message += (
            " After compaction, verify live agents and client/GPU jobs before "
            "resuming; an idle recorded lane is not launch authorization. Refresh the checkpoint at the next milestone with actual "
            "checks and next actions. A hook cannot transfer running work or "
            "authorize creation of a new chat."
        )
    print(json.dumps({"hookSpecificOutput": {
        "hookEventName": "SessionStart", "additionalContext": message
    }}))


if __name__ == "__main__":
    main()
