import json
import os
from pathlib import Path
import tempfile


def publish(path: Path, record: dict[str, object]) -> None:
    payload = json.dumps(record, sort_keys=True, allow_nan=False).encode()
    descriptor, temporary = tempfile.mkstemp(prefix=".publish-", dir=path.parent)
    staged = Path(temporary)
    try:
        with os.fdopen(descriptor, "wb") as stream:
            stream.write(payload)
            stream.flush()
            os.fsync(stream.fileno())
        os.link(staged, path)
    finally:
        staged.unlink(missing_ok=True)
