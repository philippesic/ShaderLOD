import argparse
import json
from pathlib import Path
import sys

from shaderlod_host.benchmark import WorkflowPlan, import_capture, session_command
from shaderlod_host.contracts import decode_request, verify_source
from shaderlod_host.runtime import execute, verify_result, recover_execution


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Bounded execution, frame capture and offline analysis; qualification is explicit"
    )
    parser.add_argument(
        "mode",
        choices=[
            "plan",
            "run",
            "verify",
            "report",
            "doctor",
            "recover",
            "profile",
            "compare",
            "session-open",
            "session-status",
            "session-close",
        ],
    )
    parser.add_argument("--request", type=Path)
    parser.add_argument("--spec", type=Path)
    parser.add_argument("--plan", action="store_true")
    parser.add_argument("--frames", type=Path)
    parser.add_argument("--session", type=Path)
    parser.add_argument("--launch", type=Path)
    parser.add_argument("--profile", type=Path)
    parser.add_argument("--display", type=Path)
    parser.add_argument("--timeout", type=int, default=180)
    parser.add_argument("--source-root", type=Path)
    parser.add_argument("--output-root", type=Path)
    parser.add_argument("--ownership-root", type=Path)
    parser.add_argument("--result", type=Path)
    parser.add_argument("--control", type=Path)
    parser.add_argument("--reserve-bytes", type=int, default=67108864)
    args = parser.parse_args()
    if args.mode == "doctor":
        print(
            json.dumps(
                {
                    "schema": "shaderlod-doctor-v1",
                    "cpu_runner": "implemented"
                    if sys.platform == "linux"
                    else "unavailable",
                    "platform": sys.platform,
                    "live_runner": "linux-only; explicit invocation and runtime receipt required",
                    "current_renderer_qualification": "not inspected by doctor",
                    "benchmark_methodology": "unavailable",
                    "python": sys.version.split()[0],
                }
            )
        )
        return 0
    if args.mode in {"session-open", "session-status", "session-close"}:
        try:
            if args.session is None:
                parser.error("--session required")
            if args.mode in {"session-status", "session-close"}:
                print(
                    json.dumps(
                        session_command(
                            args.session,
                            "close" if args.mode == "session-close" else "status",
                            0,
                            5,
                        ),
                        sort_keys=True,
                    )
                )
                return 0
            if (
                args.launch is None
                or args.profile is None
                or args.display is None
                or args.ownership_root is None
            ):
                parser.error(
                    "session-open requires --launch --profile --display --ownership-root"
                )
            from shaderlod_host.runtime_live import benchmark_session

            return benchmark_session(
                args.launch,
                args.profile,
                args.display,
                args.session,
                args.ownership_root,
                args.timeout,
            )
        except (OSError, ValueError, RuntimeError) as error:
            print(json.dumps({"status": "refused", "reason": str(error)}))
            return 2
    if args.mode in {"profile", "compare"} or args.frames is not None:
        try:
            if args.frames is not None:
                if args.mode != "report" or args.output_root is None:
                    parser.error("frame import requires report --frames --output-root")
                print(
                    json.dumps(
                        import_capture(args.frames, args.output_root), sort_keys=True
                    )
                )
                return 0
            if args.spec is None:
                parser.error("--spec required")
            plan = WorkflowPlan.decode(json.loads(args.spec.read_bytes()))
            if (args.mode == "profile") != (plan.scope == "profile"):
                raise ValueError("command disagrees with workflow scope")
            if (
                not args.plan
                and args.mode == "profile"
                and args.session is not None
                and args.output_root is not None
            ):
                if (
                    len(plan.arms) != 1
                    or plan.blocks != 1
                    or plan.instrument != "frames"
                ):
                    raise ValueError(
                        "only single fixed-scene frame captures are currently supported"
                    )
                observed = session_command(
                    args.session,
                    "capture",
                    plan.window_ms,
                    min(65, plan.budget_ms / 1000),
                )
                from shaderlod_host.benchmark import save_live_capture

                summary = save_live_capture(observed, args.output_root)
                print(json.dumps(summary, sort_keys=True))
                return 0 if summary["valid"] is True else 1
            if not args.plan:
                raise ValueError(
                    "live benchmark execution is not yet implemented; use --plan"
                )
            print(json.dumps(plan.describe(), sort_keys=True))
            return 0
        except (OSError, ValueError) as error:
            print(json.dumps({"status": "refused", "reason": str(error)}))
            return 2
    if args.mode == "verify" and args.request is None and args.result is not None:
        from shaderlod_host.benchmark import verify_frame_receipt

        try:
            print(json.dumps(verify_frame_receipt(args.result), sort_keys=True))
            return 0
        except (OSError, ValueError) as error:
            print(json.dumps({"status": "refused", "reason": str(error)}))
            return 2
    if args.request is None:
        parser.error("--request required")
    try:
        request = decode_request(json.loads(args.request.read_bytes()))
        if args.mode == "recover":
            if args.output_root is None or args.ownership_root is None:
                parser.error("--output-root and --ownership-root required")
            print(
                json.dumps(
                    recover_execution(request, args.output_root, args.ownership_root),
                    sort_keys=True,
                )
            )
            return 0
        if args.mode in {"verify", "report"}:
            if args.result is None:
                parser.error("--result required")
            print(
                json.dumps(
                    verify_result(json.loads(args.result.read_bytes()), request),
                    sort_keys=True,
                )
            )
            return 0
        if args.source_root is None:
            parser.error("--source-root required")
        verify_source(args.source_root, request.source)
        if args.mode == "plan":
            print(
                json.dumps(
                    {
                        "schema": "shaderlod-plan-v1",
                        "request": request.identity,
                        "purpose": request.purpose,
                        "argv": request.argv,
                        "budget_ms": request.cumulative_ms,
                        "activation": "not observed",
                        "evidence_scope": "CPU process integration only",
                    }
                )
            )
            return 0
        if args.output_root is None or args.ownership_root is None:
            parser.error("--output-root and --ownership-root required")
        result = execute(
            request,
            args.source_root,
            args.output_root,
            args.ownership_root,
            args.control,
            args.reserve_bytes,
        )
        print(json.dumps(result, sort_keys=True))
        return 0 if result["state"] == "complete" else 1
    except (OSError, ValueError, RuntimeError) as error:
        print(json.dumps({"status": "refused", "reason": str(error)}))
        return 2


if __name__ == "__main__":
    sys.exit(main())
