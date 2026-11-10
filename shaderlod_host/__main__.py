import argparse
import json
from pathlib import Path
import sys

from shaderlod_host.contracts import decode_request, verify_source
from shaderlod_host.runtime import execute, verify_result


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Bounded CPU development execution; no benchmark methodology"
    )
    parser.add_argument("mode", choices=["plan", "run", "verify", "report", "doctor"])
    parser.add_argument("--request", type=Path)
    parser.add_argument("--source-root", type=Path)
    parser.add_argument("--output-root", type=Path)
    parser.add_argument("--ownership-root", type=Path)
    parser.add_argument("--result", type=Path)
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
                    "live_renderer": "unavailable",
                    "benchmark_methodology": "unavailable",
                    "python": sys.version.split()[0],
                }
            )
        )
        return 0
    if args.request is None:
        parser.error("--request required")
    try:
        request = decode_request(json.loads(args.request.read_bytes()))
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
            request, args.source_root, args.output_root, args.ownership_root
        )
        print(json.dumps(result, sort_keys=True))
        return 0 if result["state"] == "complete" else 1
    except (OSError, ValueError, RuntimeError) as error:
        print(json.dumps({"status": "refused", "reason": str(error)}))
        return 2


if __name__ == "__main__":
    sys.exit(main())
