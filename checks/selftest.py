import json
from pathlib import Path
import tempfile
import xml.etree.ElementTree as ET
from checks.contracts import (
    Health,
    decode_sample,
    receipt,
    runtime_status,
    require_tests,
)
from checks.policy import RULES, validate
from checks.publisher import publish


def mapping(value: object) -> dict[str, object]:
    if not isinstance(value, dict):
        raise ValueError("S2: expected object")
    return {str(key): item for key, item in value.items()}


def entries_of(value: object) -> list[dict[str, object]]:
    if not isinstance(value, list):
        raise ValueError("S2: expected list")
    return [mapping(item) for item in value]


def outcome(spec: dict[str, object], root: Path, engine: str) -> list[str]:
    if "action" not in spec:
        tree = {
            str(k): str(v).encode() for k, v in mapping(spec.get("files", {})).items()
        }
        entries = entries_of(spec.get("entries", []))
        return validate(tree, entries, "comments" if engine == "comments" else "all")
    action = spec["action"]
    if action == "receipt":
        receipt(str(spec["level"]), "synthetic")
    elif action == "runtime":
        status = spec["exit"]
        if not isinstance(status, int):
            raise ValueError("S2: invalid exit code")
        runtime_status(status)
    elif action == "sample":
        decode_sample(mapping(spec["data"]))
    elif action == "health":
        try:
            Health(str(spec["health"]))
        except ValueError:
            return ["D5: unknown health"]
    elif action == "counts":
        count = spec["count"]
        if not isinstance(count, int):
            raise ValueError("S2: invalid test count")
        require_tests(count)
    elif action == "publish":
        with tempfile.TemporaryDirectory(prefix="shaderlod-publish-specimen-") as temp:
            path = Path(temp) / "receipt.json"
            publish(path, {"value": 1})
            if spec["duplicate"]:
                try:
                    publish(path, {"value": 2})
                except FileExistsError:
                    assert json.loads(path.read_text()) == {"value": 1}
                    return ["D3: second publication refused"]
                raise AssertionError("S2: publisher overwrote a result")
    elif action == "manifest":
        return validate(
            {"checks/self-tests.json": (root / "checks/self-tests.json").read_bytes()},
            [],
        )
    elif action == "findings":
        return validate({"FINDINGS.md": (root / "FINDINGS.md").read_bytes()}, [])
    else:
        raise ValueError("S2: unknown specimen action")
    return []


def execute(root: Path, tools: dict[str, dict[str, object]]) -> list[dict[str, object]]:
    manifest = json.loads((root / "checks/self-tests.json").read_text())
    if set(manifest) != RULES:
        raise ValueError("S2: manifest/validator rule mismatch")
    methods = {
        case.attrib["name"]
        for path in (root / "build/test-results/test").glob("TEST-*.xml")
        for case in ET.parse(path).getroot().findall("testcase")
        if case.find("failure") is None and case.find("skipped") is None
    }
    rows: list[dict[str, object]] = []
    control: list[str]
    for rule, cases in manifest.items():
        if not cases:
            raise ValueError("S2: missing pair " + rule)
        for case in cases:
            engine = case["engine"]
            if engine == "tool":
                tool = tools[case["bad"]["tool"]]
                if tool["control"] != "PASS":
                    raise ValueError("S2: tool control failed")
                bad, control = [rule + ": real tool rejected specimen"], []
            elif engine == "bytecode":
                method = case["bad"]["java"]
                if method + "()" not in methods and method not in methods:
                    raise ValueError("S2: missing bytecode pair " + method)
                bad, control = [rule + ": compiled bad specimen rejected"], []
            else:

                def invoke(spec: dict[str, object]) -> list[str]:
                    try:
                        return outcome(spec, root, engine)
                    except ValueError as error:
                        if not str(error).startswith(tuple(r + ":" for r in RULES)):
                            raise
                        return [str(error)]

                bad, control = invoke(case["bad"]), invoke(case["control"])
            if not any(message.startswith(rule + ":") for message in bad) or control:
                raise AssertionError(
                    f"S2: {rule}/{case['name']}: bad={bad}, control={control}"
                )
            rows.append(
                {
                    "rule": rule,
                    "name": case["name"],
                    "specimen": "FAIL " + rule,
                    "control": "PASS",
                }
            )
    return rows
