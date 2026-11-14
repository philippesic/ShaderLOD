import argparse
import json
from pathlib import Path
import sys

from shaderlod_host.benchmark import (
    CommandIncomplete,
    WorkflowPlan,
    import_capture,
    session_command,
)
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
            "session-recover",
            "prepare-windows",
            "prepare-windows-dh",
            "prepare-dh-dataset",
            "sync-windows",
            "prepare-profile",
            "prepare-world",
            "freeze-windows-runtime",
            "qualify",
            "validate",
            "history",
            "installation",
            "display-publish",
            "display-check",
            "display-units",
        ],
    )
    parser.add_argument("--request", type=Path)
    parser.add_argument("--spec", type=Path)
    parser.add_argument("--policy", type=Path)
    parser.add_argument("--runs", type=Path, nargs="+")
    parser.add_argument("--review", type=Path)
    parser.add_argument("--plan", action="store_true")
    parser.add_argument("--resume", action="store_true")
    parser.add_argument("--ancestor-pid", type=int, help=argparse.SUPPRESS)
    parser.add_argument("--ancestor-birth", help=argparse.SUPPRESS)
    parser.add_argument("--probe-worker", action="store_true")
    parser.add_argument("--frames", type=Path)
    parser.add_argument("--session", type=Path)
    parser.add_argument("--launch", type=Path)
    parser.add_argument("--profile", type=Path)
    parser.add_argument("--world", default="ShaderLOD-Smoke")
    parser.add_argument("--display", type=Path)
    parser.add_argument("--timeout", type=int)
    parser.add_argument("--wait-ready", action="store_true")
    parser.add_argument("--telemetry", action="store_true")
    parser.add_argument("--without-shaderlod", action="store_true")
    parser.add_argument("--dh-generation", action="store_true")
    parser.add_argument("--without-dh-witness", action="store_true")
    parser.add_argument("--source-root", type=Path)
    parser.add_argument("--output-root", type=Path)
    parser.add_argument("--ownership-root", type=Path)
    parser.add_argument("--result", type=Path)
    parser.add_argument("--control", type=Path)
    parser.add_argument("--reserve-bytes", type=int, default=67108864)
    args = parser.parse_args()
    if args.timeout is None:
        args.timeout = 30 if args.mode == "display-check" else 180
    if args.mode in {"display-publish", "display-check", "display-units"}:
        from shaderlod_host.config import project_root
        from shaderlod_host.runtime_live import (
            publish_display_environment,
            check_display,
            write_display_units,
        )

        root = project_root()
        if args.mode == "display-publish":
            publish_display_environment(
                args.display or root / ".local/display/environment.json"
            )
        elif args.mode == "display-check":
            print(
                json.dumps(
                    check_display(
                        args.display or root / ".local/display/environment.json",
                        args.timeout,
                    )
                )
            )
        else:
            print(
                json.dumps(
                    write_display_units(args.output_root or root / ".local/services")
                )
            )
        return 0
    if (args.ancestor_pid is None) != (args.ancestor_birth is None):
        parser.error("internal ancestry requires both process ID and birth identity")
    if args.resume and (args.mode != "qualify" or args.spec is None):
        parser.error(
            "--resume requires qualify --spec for an unfinished frozen campaign"
        )
    if (
        args.mode in {"qualify", "history"}
        and args.result is not None
        and not args.runs
    ):
        from shaderlod_host.campaign import campaign_results

        args.runs = campaign_results(args.result.parent)
    if args.mode == "report" and args.result is not None and args.request is None:
        if args.output_root is None:
            parser.error("report --result requires fresh --output-root")
        from shaderlod_host.reports import rebuild

        try:
            print(json.dumps(rebuild(args.result, args.output_root), sort_keys=True))
            return 0
        except (OSError, ValueError) as error:
            print(json.dumps({"status": "refused", "reason": str(error)}))
            return 2
    if args.mode == "installation":
        from shaderlod_host.comparison import ComparisonPlan, installation_plan
        from shaderlod_host.runtime_live import execute_installation

        if args.spec is None:
            parser.error("installation requires --spec")
        installation = ComparisonPlan.decode(json.loads(args.spec.read_bytes()))
        resolved_installation = installation_plan(installation)
        if args.plan:
            print(json.dumps(resolved_installation, sort_keys=True))
            return 0
        if (
            args.launch is None
            or args.profile is None
            or args.display is None
            or args.ownership_root is None
            or args.output_root is None
        ):
            parser.error(
                "installation requires --launch --profile --display --ownership-root --output-root"
            )
        result = execute_installation(
            installation,
            args.launch,
            args.profile,
            args.display,
            args.ownership_root,
            args.output_root,
            args.world,
        )
        print(
            json.dumps(
                {key: value for key, value in result.items() if key != "observations"},
                sort_keys=True,
            )
        )
        return 0 if result["state"] == "complete" else 1
    if args.mode == "history":
        from shaderlod_host.reports import history

        if not args.runs or args.output_root is None:
            parser.error("history requires --runs --output-root")
        result = history(args.runs, args.output_root)
        groups = result["groups"]
        failures = result["failures"]
        if not isinstance(groups, dict) or not isinstance(failures, list):
            raise ValueError("invalid history output")
        print(json.dumps({"series": len(groups), "failures": len(failures)}))
        return 0
    if args.mode == "validate":
        from shaderlod_host.validation import validation_spec, validate_session

        if args.spec is None:
            parser.error("validate requires --spec")
        surface, dh_enabled = validation_spec(json.loads(args.spec.read_bytes()))
        if args.plan:
            print(
                json.dumps(
                    {
                        "schema": "shaderlod-validation-plan-v1",
                        "surface": list(surface),
                        "dh_enabled": dh_enabled,
                        "maximum_control_wait_seconds": 75,
                        "scope": "ready-session functional lifecycle, no speed or perceptual verdict",
                    },
                    sort_keys=True,
                )
            )
            return 0
        if args.session is None or args.output_root is None:
            parser.error("validate requires --session --output-root")
        result = validate_session(args.session, args.output_root, surface, dh_enabled)
        print(
            json.dumps(
                {
                    k: v
                    for k, v in result.items()
                    if k not in {"functional", "session_identity"}
                },
                sort_keys=True,
            )
        )
        return 0 if result["outcome"] == "PASS" else 1
    if args.mode == "qualify":
        from shaderlod_host.assessment import QualificationPolicy, assess
        from shaderlod_host.storage import publish

        if args.spec is not None:
            from shaderlod_host.campaign import Campaign
            from shaderlod_host.runtime_live import execute_campaign

            campaign = Campaign.decode(json.loads(args.spec.read_bytes()))
            if args.plan:
                print(json.dumps(campaign.describe(), sort_keys=True))
                return 0
            if (
                args.launch is None
                or args.profile is None
                or args.display is None
                or args.ownership_root is None
                or args.output_root is None
            ):
                parser.error(
                    "campaign requires --launch --profile --display --ownership-root --output-root"
                )
            result = execute_campaign(
                campaign,
                args.launch,
                args.profile,
                args.display,
                args.ownership_root,
                args.output_root,
                args.world,
                args.resume,
                args.control,
            )
            print(json.dumps(result, sort_keys=True))
            return (
                0
                if result["state"] == "complete"
                else 3
                if result["state"] == "paused"
                else 1
            )
        if args.policy is None or not args.runs or args.output_root is None:
            parser.error("qualify requires --policy --runs --output-root")
        policy = QualificationPolicy.decode(json.loads(args.policy.read_bytes()))
        review = None if args.review is None else json.loads(args.review.read_bytes())
        result = assess(policy, args.runs, review)
        publish(args.output_root / "assessment.json", result)
        print(json.dumps(result, sort_keys=True))
        return 0 if result["recommendation"] == "meets_declared_policy" else 1
    if args.mode == "freeze-windows-runtime":
        if args.source_root is None or args.output_root is None:
            parser.error("freeze-windows-runtime requires --source-root --output-root")
        from shaderlod_host.runtime_live import freeze_windows_runtime

        print(
            json.dumps(
                freeze_windows_runtime(
                    args.source_root,
                    args.output_root,
                    Path(__file__).resolve().parents[1],
                ),
                sort_keys=True,
            )
        )
        return 0
    if args.mode == "prepare-world":
        from shaderlod_host.storage import BusyRefusal, prepare_world
        import socket

        if (
            args.source_root is None
            or args.profile is None
            or args.output_root is None
            or args.spec is None
            or args.ownership_root is None
        ):
            parser.error(
                "prepare-world requires --source-root --profile --world --spec --output-root --ownership-root"
            )
        if (
            not args.world
            or args.world in {".", ".."}
            or "/" in args.world
            or "\\" in args.world
        ):
            raise ValueError("single prepared world name required")
        with BusyRefusal(args.ownership_root, socket.gethostname() + ":gpu:0"):
            prepared = prepare_world(
                args.source_root,
                args.profile / "saves" / args.world,
                args.output_root,
                json.loads(args.spec.read_bytes()),
            )
        print(
            json.dumps(
                {key: value for key, value in prepared.items() if key != "files"},
                sort_keys=True,
            )
        )
        return 0
    if args.mode == "prepare-dh-dataset":
        from shaderlod_host.storage import BusyRefusal, prepare_dh_dataset
        import socket

        if args.profile is None or args.ownership_root is None:
            parser.error(
                "prepare-dh-dataset requires --profile --world --ownership-root"
            )
        with BusyRefusal(args.ownership_root, socket.gethostname() + ":gpu:0"):
            print(
                json.dumps(prepare_dh_dataset(args.profile, args.world), sort_keys=True)
            )
        return 0
    if args.mode == "prepare-profile":
        from shaderlod_host.storage import BusyRefusal, prepare_primary_profile
        import socket

        if (
            args.profile is None
            or args.output_root is None
            or args.ownership_root is None
        ):
            parser.error(
                "prepare-profile requires --profile --output-root --ownership-root"
            )
        pin = json.loads(
            (
                args.spec
                if args.spec is not None
                else Path(__file__).resolve().parents[1]
                / "dependencies/primary-pack.json"
            ).read_bytes()
        )
        with BusyRefusal(args.ownership_root, socket.gethostname() + ":gpu:0"):
            print(
                json.dumps(
                    prepare_primary_profile(
                        args.profile, pin["filename"], pin["sha256"], args.output_root
                    ),
                    sort_keys=True,
                )
            )
        return 0
    if args.mode == "sync-windows":
        if args.output_root is None:
            parser.error(
                "sync-windows requires --output-root, such as /mnt/c/ShaderLOD"
            )
        if args.output_root.parts[:2] != ("/", "mnt"):
            parser.error("sync-windows writes to a Windows drive under /mnt")
        from shaderlod_host.runtime_live import sync_windows

        print(
            json.dumps(
                sync_windows(Path(__file__).resolve().parents[1], args.output_root),
                sort_keys=True,
            )
        )
        return 0
    if args.mode == "prepare-windows-dh":
        if args.profile is None:
            parser.error("--profile canonical profile required")
        if sys.platform != "win32":
            parser.error("native Windows preparation required")
        from shaderlod_host.runtime_live import prepare_windows_dh

        print(
            json.dumps(
                prepare_windows_dh(args.profile, Path(__file__).resolve().parents[1]),
                sort_keys=True,
            )
        )
        return 0
    if args.mode == "prepare-windows":
        if args.source_root is None or args.profile is None:
            parser.error(
                "--source-root legacy runtime and --profile canonical profile required"
            )
        from shaderlod_host.runtime_live import prepare_windows_runtime

        print(
            json.dumps(
                prepare_windows_runtime(
                    args.source_root, args.profile, Path(__file__).resolve().parents[1]
                ),
                sort_keys=True,
            )
        )
        return 0
    if args.mode == "doctor":
        if args.probe_worker:
            if args.output_root is None:
                parser.error("--output-root required for watchdog probe")
            from shaderlod_host.worker import probe_worker

            result = probe_worker(args.output_root)
            print(json.dumps(result, sort_keys=True))
            return 0 if result["pass"] else 1
        print(
            json.dumps(
                {
                    "schema": "shaderlod-doctor-v1",
                    "cpu_runner": "implemented"
                    if sys.platform == "linux"
                    else "unavailable",
                    "platform": sys.platform,
                    "live_runner": "Linux/native Windows; explicit allocation and runtime receipts required",
                    "current_renderer_qualification": "not inspected by doctor",
                    "benchmark_methodology": "implemented; qualification is result- and configuration-scoped",
                    "python": sys.version.split()[0],
                }
            )
        )
        return 0
    if args.mode in {
        "session-open",
        "session-status",
        "session-close",
        "session-recover",
    }:
        try:
            if args.session is None:
                parser.error("--session required")
            if args.mode in {"session-status", "session-close", "session-recover"}:
                if args.mode == "session-status" and args.wait_ready:
                    from shaderlod_host.benchmark import wait_ready

                    observed = wait_ready(args.session, args.timeout)
                    print(json.dumps(observed, sort_keys=True))
                    return 0
                print(
                    json.dumps(
                        session_command(
                            args.session,
                            {
                                "session-close": "close",
                                "session-status": "status",
                                "session-recover": "recover",
                            }[args.mode],
                            0,
                            5
                            if args.mode == "session-status"
                            else min(65, args.timeout),
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
                args.world,
                None
                if args.ancestor_pid is None
                else (args.ancestor_pid, args.ancestor_birth),
                args.telemetry,
                args.without_shaderlod,
                args.dh_generation,
                not args.without_dh_witness,
            )
        except CommandIncomplete as error:
            print(json.dumps({"status": "incomplete", "reason": str(error)}))
            return 1
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
            specification = json.loads(args.spec.read_bytes())
            if (
                isinstance(specification, dict)
                and specification.get("schema") == "shaderlod-comparison-v1"
            ):
                from shaderlod_host.comparison import ComparisonPlan, execute_comparison

                comparison = ComparisonPlan.decode(specification)
                if args.mode != "compare":
                    raise ValueError(
                        "comparison specification requires compare command"
                    )
                if args.plan:
                    print(json.dumps(comparison.describe(), sort_keys=True))
                    return 0
                if args.session is None or args.output_root is None:
                    parser.error("comparison requires --session --output-root")
                comparison_policy = (
                    None
                    if args.policy is None
                    else json.loads(args.policy.read_bytes())
                )
                result = execute_comparison(
                    comparison, args.session, args.output_root, policy=comparison_policy
                )
                print(
                    json.dumps(
                        {
                            key: value
                            for key, value in result.items()
                            if key not in {"observations", "baselines"}
                        },
                        sort_keys=True,
                    )
                )
                return 0 if result["state"] == "complete" else 1
            plan = WorkflowPlan.decode(specification)
            if (args.mode == "profile") != (plan.scope == "profile"):
                raise ValueError("command disagrees with workflow scope")
            if (
                not args.plan
                and args.mode == "profile"
                and args.session is not None
                and args.output_root is not None
            ):
                if len(plan.arms) != 1 or plan.blocks != 1:
                    raise ValueError(
                        "only single fixed-scene frame captures are currently supported"
                    )
                if args.output_root.exists():
                    raise ValueError("fresh profile output required before capture")
                payload = None
                if plan.instrument == "diagnostic":
                    status = session_command(args.session, "status", 0, 5)
                    pose = status.get("pose")
                    if not isinstance(pose, list) or len(pose) != 5:
                        raise ValueError(
                            "ready player pose required for diagnostic profile"
                        )
                    payload = {
                        "from": pose,
                        "to": pose,
                        "delay_ms": 0,
                        "diagnostic": True,
                    }
                observed = session_command(
                    args.session,
                    "capture",
                    plan.window_ms,
                    min(310, plan.budget_ms / 1000),
                    payload,
                )
                from shaderlod_host.benchmark import save_live_capture

                try:
                    summary = save_live_capture(observed, args.output_root)
                except (OSError, ValueError) as error:
                    from shaderlod_host.storage import publish

                    publish(
                        args.output_root / "failure.json",
                        {
                            "schema": "shaderlod-profile-failure-v1",
                            "state": "incomplete",
                            "reason": str(error),
                            "session": str(args.session),
                            "sequence": observed.get("sequence"),
                        },
                    )
                    raise CommandIncomplete(
                        "capture rejected; raw acknowledgement and failure receipt preserved"
                    ) from error
                print(json.dumps(summary, sort_keys=True))
                return 0 if summary["valid"] is True else 1
            if not args.plan:
                raise ValueError(
                    "live comparison requires a shaderlod-comparison-v1 specification"
                )
            print(json.dumps(plan.describe(), sort_keys=True))
            return 0
        except CommandIncomplete as error:
            print(json.dumps({"status": "incomplete", "reason": str(error)}))
            return 1
        except (OSError, ValueError) as error:
            print(json.dumps({"status": "refused", "reason": str(error)}))
            return 2
    if args.mode == "verify" and args.request is None and args.result is not None:
        from shaderlod_host.benchmark import verify_frame_receipt

        try:
            kind = json.loads(args.result.read_bytes()).get("schema")
            if kind == "shaderlod-comparison-result-v1":
                from shaderlod_host.comparison import verify_comparison

                verified = verify_comparison(args.result)
                print(json.dumps({"integrity": "PASS", "state": verified["state"]}))
                return 0
            if kind == "shaderlod-campaign-result-v1":
                from shaderlod_host.campaign import campaign_results
                from shaderlod_host.comparison import verify_comparison

                record = json.loads(args.result.read_bytes())
                paths = campaign_results(args.result.parent)
                for path in paths:
                    verify_comparison(path)
                names = record.get("runs")
                if (
                    not isinstance(names, list)
                    or not all(isinstance(name, str) for name in names)
                    or [name.replace("\\", "/") for name in names]
                    != [
                        path.relative_to(args.result.parent).as_posix()
                        for path in paths
                    ]
                ):
                    raise ValueError("campaign run coverage disagreement")
                print(
                    json.dumps(
                        {
                            "integrity": "PASS",
                            "state": record["state"],
                            "verified_runs": len(paths),
                        }
                    )
                )
                return 0
            if kind == "shaderlod-installation-result-v1":
                from shaderlod_host.comparison import verify_installation

                verified = verify_installation(args.result)
                print(json.dumps({"integrity": "PASS", "state": verified["state"]}))
                return 0
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
    try:
        sys.exit(main())
    except CommandIncomplete as error:
        print(json.dumps({"status": "incomplete", "reason": str(error)}))
        sys.exit(1)
    except (OSError, ValueError, RuntimeError) as error:
        print(json.dumps({"status": "refused", "reason": str(error)}))
        sys.exit(2)
