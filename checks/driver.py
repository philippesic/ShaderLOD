import json
from pathlib import Path
import subprocess
import sys
import xml.etree.ElementTree as ET
from checks import policy, selftest, tooltests
from checks.contracts import require_tests

ROOT = Path(__file__).resolve().parents[1]
REPORTS = ROOT / "build/reports/foundation"


def command(rule: str, args: list[str]) -> int:
    result = subprocess.run([sys.executable, "-m", *args], cwd=ROOT)
    if result.returncode:
        raise RuntimeError(f"{rule}: tool failed ({result.returncode})")
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
        if found != expected:
            raise ValueError(
                "S2: required tests missing or unexpected: " + str(found ^ expected)
            )
    return count


def main() -> int:
    REPORTS.mkdir(parents=True, exist_ok=True)
    step = sys.argv[1]
    count, rules = 0, 0
    if step == "discoverProviders":
        providers = policy.discover(policy.files(ROOT))
        (ROOT / "build/providers.json").write_text(json.dumps(providers) + "\n")
        count, rules = 1, 1
        print(
            json.dumps(
                {
                    "providers": len(providers),
                    "discovery_control": "test_discovery_no_shared_edit",
                }
            )
        )
    elif step == "pythonLint":
        command("T-PFMT", ["ruff", "format", "--check", "checks", "tests"])
        command("T-RUFF", ["ruff", "check", "checks", "tests"])
        count, rules = 2, 2
    elif step == "pythonTypes":
        count = command("T-MYPY", ["mypy", "checks", "tests"])
        rules = 1
    elif step == "pythonTests":
        command("T-PYTEST", ["pytest", f"--junitxml={REPORTS / 'pytest.xml'}"])
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
                "checks/entrypoints.json",
                "docs/SYSTEM_MAP.md",
                ".github/CODEOWNERS",
                ".github/workflows/check.yml",
                ".githooks/pre-commit",
            }
            if not required <= set(policy.files(ROOT)):
                errors.append("S2: mandatory check configuration missing")
            print(
                "Protected check configuration requires Philip review; remote protection unconfigured."
            )
        if errors:
            raise ValueError("\n".join(errors))
        count = (
            len(json.loads((ROOT / "checks/exceptions.json").read_text()))
            if step == "exceptionsAudit"
            else len(policy.files(ROOT))
        )
        rules = {"repoPolicy": 16, "commentBan": 1, "exceptionsAudit": 1}[step]
    elif step == "selfTest":
        tools = tooltests.main(ROOT)
        rows = selftest.execute(ROOT, tools)
        (REPORTS / "self-tests.json").write_text(json.dumps(rows, indent=2) + "\n")
        count, rules = len(rows) * 2, len(policy.RULES)
    elif step == "checkReport":
        junit = test_counts(
            list((ROOT / "build/test-results/test").glob("TEST-*.xml")), "java"
        )
        result = {
            p.stem: json.loads(p.read_text()) for p in REPORTS.glob("step-*.json")
        }
        result["Java tests"] = {"rules": 7, "tests": junit}
        result["Java compile / Error Prone"] = {
            "rules": 2,
            "tests": len(list((ROOT / "src").rglob("*.java"))),
        }
        result["Spotless Java / Groovy"] = {
            "rules": 2,
            "tests": len(list((ROOT / "src").rglob("*.java"))) + 2,
        }
        result["Checkstyle"] = {
            "rules": 1,
            "tests": len(list((ROOT / "src").rglob("*.java"))),
        }
        result["Dependency analysis"] = {"rules": 1, "tests": 1}
        (REPORTS / "check.json").write_text(json.dumps(result, indent=2) + "\n")
        print(json.dumps(result, indent=2))
        return 0
    else:
        raise ValueError("S2: unknown check step")
    result = {"step": step, "rules": rules, "tests": count, "result": "PASS"}
    (REPORTS / ("step-" + step + ".json")).write_text(json.dumps(result) + "\n")
    print(json.dumps(result))
    return 0


if __name__ == "__main__":
    sys.exit(main())
