import json
from pathlib import Path


def publish(path: Path, record: dict[str, object]) -> None:
    with path.open("x") as stream:
        json.dump(record, stream, sort_keys=True)
