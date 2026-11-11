import json
from pathlib import Path
import subprocess
import sys
import xml.etree.ElementTree as ET
from checks import policy, selftest, tooltests
from checks.contracts import require_tests

ROOT = Path(__file__).resolve().parents[1]
REPORTS = ROOT / "build/reports/foundation"


RULE_PREFIXES = tuple(rule + ":" for rule in policy.RULES)


def command(rule: str, args: list[str], fix: str = "") -> int:
    result = subprocess.run([sys.executable, "-m", *args], cwd=ROOT)
    if result.returncode:
        detail = fix or "read the tool output above"
        raise RuntimeError(f"{rule}: {args[0]} failed; {detail}")
    return 1


def test_counts(paths: list[Path], lane: str = "") -> int:
    count = 0
    found: set[str] = set()
    for path in paths:
        tree = ET.parse(path).getroot()
        suites = [tree] if tree.tag == "testsuite" else list(tree)
        for suite in suites:
            count += int(suite.attrib["tests"])
            found.update(
                case.attrib["name"].removesuffix("()")
                for case in suite.findall("testcase")
            )
            if any(
                int(suite.attrib.get(key, "0"))
                for key in ("failures", "errors", "skipped")
            ):
                raise ValueError("S2: failed, crashed or skipped tests")
    require_tests(count)
    if lane:
        expected = set(json.loads((ROOT / "checks/tests.json").read_text())[lane])
        if not expected <= found:
            raise ValueError("S2: required tests missing: " + str(expected - found))
    return count


def python_roots() -> list[str]:
    return [
        name for name in ("checks", "tests", "shaderlod_host") if (ROOT / name).is_dir()
    ]


def main() -> int:
    try:
        return run_step(sys.argv[1])
    except (ValueError, RuntimeError, AssertionError) as error:
        message = str(error)
        if not message.startswith(RULE_PREFIXES):
            raise
        print(message, file=sys.stderr)
        print("Fixes for each rule ID: docs/checks.md", file=sys.stderr)
        return 1


def run_step(step: str) -> int:
    REPORTS.mkdir(parents=True, exist_ok=True)
    count, rules = 0, 0
    if step == "pythonLint":
        command(
            "T-PFMT",
            ["ruff", "format", "--check", "--quiet", *python_roots()],
            "fix with: "
            + " ".join([sys.executable, "-m", "ruff", "format", *python_roots()]),
        )
        command("T-RUFF", ["ruff", "check", "--quiet", *python_roots()])
        count, rules = 2, 2
    elif step == "pythonTypes":
        count = command("T-MYPY", ["mypy", "--no-error-summary", *python_roots()])
        rules = 1
    elif step == "pythonTests":
        command(
            "T-PYTEST",
            ["pytest", "-q", "--no-header", f"--junitxml={REPORTS / 'pytest.xml'}"],
        )
        count = test_counts([REPORTS / "pytest.xml"], "python")
        rules = 1
    elif step in ("repoPolicy", "commentBan", "exceptionsAudit"):
        family = {
            "repoPolicy": "policy",
            "commentBan": "comments",
            "exceptionsAudit": "exceptions",
        }[step]
        errors = policy.validate(
            policy.files(ROOT),
            json.loads((ROOT / "checks/exceptions.json").read_text()),
            family,
        )
        if step == "exceptionsAudit":
            for entry in json.loads((ROOT / "checks/exceptions.json").read_text()):
                site = ROOT / entry["path"]
                if (
                    not site.is_file()
                    or entry["token"] not in site.read_text()
                    or not (ROOT / entry["decision"]).is_file()
                ):
                    errors.append("D7: exception site or decision missing")
        if step == "repoPolicy":
            required = {
                "AGENTS.md",
                "CLAUDE.md",
                "FINDINGS.md",
                "checks/self-tests.json",
                "checks/tests.json",
                "docs/architecture.md",
                "docs/development.md",
                "docs/results.md",
                "tasks/README.md",
                "skills/close-results/SKILL.md",
                ".githooks/pre-commit",
            }
            if not required <= set(policy.files(ROOT)):
                errors.append("S2: mandatory check configuration missing")
        if errors:
            raise ValueError("\n".join(errors))
        count = (
            len(json.loads((ROOT / "checks/exceptions.json").read_text()))
            if step == "exceptionsAudit"
            else len(policy.files(ROOT))
        )
        rules = {"repoPolicy": 16, "commentBan": 1, "exceptionsAudit": 1}[step]
    elif step == "toolSelfTest":
        tools = tooltests.main(ROOT)
        count, rules = len(tools) * 2, len(tools)
    elif step == "selfTest":
        tools = json.loads((REPORTS / "tool-self-tests.json").read_text())
        rows = selftest.execute(ROOT, tools)
        (REPORTS / "self-tests.json").write_text(json.dumps(rows, indent=2) + "\n")
        count, rules = (
            sum(2 for row in rows if row["engine"] not in ("tool", "bytecode")),
            len(policy.RULES),
        )
    elif step == "checkReport":
        junit = test_counts(
            list((ROOT / "build/test-results/test").glob("TEST-*.xml")), "java"
        )
        result = {
            p.stem: json.loads(p.read_text())
            for p in REPORTS.glob("step-*.json")
            if p.stem.removeprefix("step-")
            in {
                "pythonLint",
                "pythonTypes",
                "pythonTests",
                "repoPolicy",
                "commentBan",
                "exceptionsAudit",
                "toolSelfTest",
                "selfTest",
            }
        }
        result["Java tests"] = {"tests": junit, "execution": "see gradle-tasks.json"}
        result["selfTest summaries"] = {
            "note": "tool and bytecode rows reference their actual executions, not additional tests"
        }
        (REPORTS / "check.json").write_text(json.dumps(result, indent=2) + "\n")
        return 0
    else:
        raise ValueError("S2: unknown check step")
    result = {"step": step, "items": count, "result": "PASS"}
    (REPORTS / ("step-" + step + ".json")).write_text(json.dumps(result) + "\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
