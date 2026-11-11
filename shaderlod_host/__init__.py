from pathlib import Path

from shaderlod_host.contracts import Request, decode_request

__all__ = ["Request", "decode_request", "execute"]


def execute(
    request: Request,
    source_root: Path,
    output_root: Path,
    ownership_root: Path,
    control: Path | None = None,
    reserve_bytes: int = 0,
) -> dict[str, object]:
    from shaderlod_host.runtime import execute as run

    return run(
        request, source_root, output_root, ownership_root, control, reserve_bytes
    )
