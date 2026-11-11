import hashlib
import json
from pathlib import Path
import time

from shaderlod_host.benchmark import object_fields, positive, session_command
from shaderlod_host.functional_contract import verify_functional
from shaderlod_host.storage import publish


def validate_session(
    session: Path, output: Path, surface: tuple[int, int], dh_enabled: bool
) -> dict[str, object]:
    from shaderlod_host.runtime_live import preserve_outputs

    if output.exists():
        raise ValueError("fresh validation output required")
    metadata = json.loads((session / "session.json").read_bytes())
    profile = Path(metadata["profile"])
    for name in (
        "shaderlod-functional.json",
        "shaderlod-functional.png",
        "shaderlod-functional-identity.png",
    ):
        if (profile / name).exists():
            raise ValueError(
                "earlier functional outputs require preservation before validation"
            )
    initial = session_command(session, "status", 0, 5)
    if initial.get("pack") != "ComplementaryReimagined_r5.9.3.zip":
        raise ValueError(
            "prepared functional profile must start with original primary pack"
        )
    output.mkdir(parents=True)
    started = time.monotonic()
    failure = None
    observed = None
    restored = False
    try:
        session_command(session, "prepare", 0, 10)
        session_command(session, "validate", 0, 60)
        pin = json.loads(
            (
                Path(__file__).resolve().parents[1] / "dependencies/primary-pack.json"
            ).read_bytes()
        )
        observed = verify_functional(
            json.loads((profile / "shaderlod-functional.json").read_bytes()),
            profile,
            pin["filename"],
            pin["sha256"],
            dh_enabled,
            surface,
        )
        if observed.get("outcome") != "PASS":
            raise ValueError("functional producer failed")
        restored = True
    except (OSError, ValueError, RuntimeError) as error:
        failure = str(error)
    preserved = preserve_outputs(profile, output / "outputs")
    result: dict[str, object] = {
        "schema": "shaderlod-validation-v1",
        "outcome": "PASS" if failure is None else "FAIL",
        "elapsed_seconds": time.monotonic() - started,
        "restored": restored,
        "failure": failure,
        "functional": observed,
        "outputs": preserved,
        "session_identity": initial.get("session_identity"),
        "surface": list(surface),
        "scope": "live loading, actual activation, Off/Cancel/failed Apply restoration; no perceptual or performance verdict",
    }
    publish(output / "result.json", result)
    publish(
        output / "receipt.json",
        {
            "schema": "shaderlod-validation-receipt-v1",
            "sha256": hashlib.sha256((output / "result.json").read_bytes()).hexdigest(),
        },
    )
    return result


def validation_spec(value: object) -> tuple[tuple[int, int], bool]:
    record = object_fields(value, {"schema", "surface", "dh_enabled"}, "validation")
    surface = record["surface"]
    if (
        record["schema"] != "shaderlod-validation-spec-v1"
        or not isinstance(surface, list)
        or len(surface) != 2
        or type(record["dh_enabled"]) is not bool
    ):
        raise ValueError("explicit native surface and DH mode required")
    return (positive(surface[0], "width"), positive(surface[1], "height")), record[
        "dh_enabled"
    ]
