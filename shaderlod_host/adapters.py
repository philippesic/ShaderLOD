import hashlib
import json
from pathlib import Path

from shaderlod_host.benchmark import object_fields


def semantic_mapping(
    observed: dict[str, object], definitions: Path
) -> dict[str, object]:
    activation = observed.get("activation")
    if not isinstance(activation, dict):
        return {
            "status": "unmapped",
            "reason": "no actual archive activation; raw stage boundaries retained",
        }
    digest = activation.get("archiveSha256")
    for path in sorted(definitions.glob("*.json")):
        record = object_fields(
            json.loads(path.read_bytes()),
            {
                "schema",
                "archive_sha256",
                "dimension",
                "required_options",
                "labels",
                "source",
                "limits",
            },
            "mapping",
        )
        if (
            record["schema"] != "shaderlod-stage-mapping-v1"
            or not isinstance(record["required_options"], dict)
            or not isinstance(record["labels"], list)
        ):
            raise ValueError("invalid semantic mapping")
        if record["archive_sha256"] != digest or record["dimension"] != observed.get(
            "dimension"
        ):
            continue
        options = observed.get("options")
        if not isinstance(options, dict) or any(
            options.get(k) != v for k, v in record["required_options"].items()
        ):
            continue
        return {
            "status": "mapped",
            "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
            "definition": record,
            "meaning": "semantic membership only; no causal attribution or invented effect split",
        }
    return {
        "status": "unmapped",
        "reason": "archive/dimension/options do not match a qualified definition; raw boundaries retained",
    }
