import hashlib
import json
from pathlib import Path

root = Path(__file__).resolve().parent
folder = root / "evidence"
index = json.loads((folder / "index.json").read_text())
records = {}
for item in index["files"]:
    assert Path(item["file"]).name == item["file"]
    raw = (folder / item["file"]).read_bytes()
    assert len(raw) == item["bytes"]
    assert hashlib.sha256(raw).hexdigest() == item["sha256"]
    for record in json.loads(raw)["records"]:
        assert record["id"] not in records
        records[record["id"]] = record


def decode_identities(value):
    if isinstance(value, dict):
        if set(value) == {"_identity_rows"}:
            return {
                index["identity_rows"][key]["path"]: index["identity_rows"][key][
                    "sha256"
                ]
                for key in value["_identity_rows"]
            }
        return {key: decode_identities(item) for key, item in value.items()}
    if isinstance(value, list):
        return [decode_identities(item) for item in value]
    return value


def value(identity):
    record = records[identity]
    if "data" in record:
        return decode_identities(record["data"])
    form = record["representation"]
    if form["type"] == "dict":
        return {item["key"]: value(item["id"]) for item in form["items"]}
    if form["type"] == "list":
        return [value(key) for key in form["items"]]
    assert form["type"] == "chunks"
    return [item for key in form["items"] for item in value(key)]


streams = 0
rows = 0
for record in records.values():
    if "semantic_sha256" not in record:
        continue
    data = value(record["id"])
    if record["kind"] in {"frame-or-numerical-stream", "telemetry-stream"}:
        if data["encoding"] == "columns":
            data = [
                dict(zip(data["columns"], row, strict=True)) for row in data["rows"]
            ]
        else:
            assert data["encoding"] == "records"
            data = data["rows"]
        streams += 1
        rows += len(data)
    raw = json.dumps(
        data, ensure_ascii=False, sort_keys=True, separators=(",", ":")
    ).encode()
    assert hashlib.sha256(raw).hexdigest() == record["semantic_sha256"], record["paths"]
    if record["kind"] == "authored-source":
        assert hashlib.sha256(data.encode()).hexdigest() == record["id"]

for source in index["source_recovery"]:
    if "record" in source:
        record = records[source["record"]]
        text = record.get("source_text", value(record["id"]))
        assert isinstance(text, str)
        assert hashlib.sha256(text.encode()).hexdigest() == source["sha256"]
    else:
        assert len(source["blob"]) == 40


figure = json.loads((root / "result.json").read_text())
for session in figure["sensitivity"]["sessions"]:
    assert session["result_sha256"] in records
    assert value(session["result_sha256"])["state"] == "complete"
    observations = value(session["result_sha256"])["observations"]
    for arm in session["arms"]:
        observed = next(row for row in observations if row["arm"] == arm["arm"])
        assert observed["summary"]["fps"] == arm["fps"]
        assert observed["summary"]["bins"] == arm["bins"]


print(
    f"PASS: closed benchmark evidence, {streams} numerical/telemetry streams and {rows} exact rows, retained hashes, source text and plotted outcomes"
)
