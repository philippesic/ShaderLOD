import json
from pathlib import Path
import sys
import tempfile
from checks.contracts import runtime_status
from checks.publisher import publish
from checks.contracts import Evidence, Health, Measured, Missing, decode_sample, receipt


def cpu() -> dict[str, object]:
    measured = decode_sample({"kind": "measured", "value": 0, "count": 1})
    missing = decode_sample({"kind": "missing", "reason": "unscheduled"})
    assert measured == Measured(0, 1)
    assert missing == Missing("unscheduled")
    value: dict[str, object] = dict(receipt(Evidence.SYNTHETIC, "synthetic"))
    value.update(
        health=Health.PASS,
        measured={"kind": "measured", "value": measured.value, "count": measured.count},
        missing={"kind": "missing", "reason": missing.reason},
    )
    with tempfile.TemporaryDirectory(prefix="shaderlod-cpu-") as temp:
        path = Path(temp) / "receipt.json"
        publish(path, value)
        assert json.loads(path.read_text()) == value
        try:
            publish(path, value)
        except FileExistsError:
            pass
        else:
            raise AssertionError("D3: duplicate publication accepted")
    return value


def main() -> int:
    if sys.argv[1:] == ["--runtime"]:
        print(
            "D1: runtime needs explicit --profile, --display, --receipt, --ownership-root and --timeout within an allocated hardware window",
            file=sys.stderr,
        )
        return runtime_status(2)
    if sys.argv[1:] == ["--cpu"]:
        print(json.dumps(cpu(), sort_keys=True))
        return 0
    print("usage: ./verify --cpu | --runtime", file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main())
