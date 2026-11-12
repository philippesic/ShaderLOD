import hashlib
import json
from pathlib import Path

root = Path(__file__).resolve().parent / "retirement-supplement"
index = json.loads((root / "index.json").read_text())
records = {}
for item in index["files"]:
    assert Path(item["file"]).name == item["file"]
    raw = (root / item["file"]).read_bytes()
    assert len(raw) == item["bytes"]
    assert hashlib.sha256(raw).hexdigest() == item["sha256"]
    for record in json.loads(raw)["records"]:
        assert record["id"] not in records
        records[record["id"]] = record


def decode(data):
    if isinstance(data, dict):
        if set(data) == {"_shared"}:
            return value(data["_shared"])
        return {key: decode(item) for key, item in data.items()}
    if isinstance(data, list):
        return [decode(item) for item in data]
    return data


def value(identity):
    record = records[identity]
    if "data" in record:
        return decode(record["data"])
    form = record["representation"]
    if form["type"] == "dict":
        return {item["key"]: value(item["id"]) for item in form["items"]}
    rows = [value(item["id"]) for item in form["items"]]
    if form["type"] == "text":
        return "".join(rows)
    assert form["type"] == "list"
    return rows


for record in records.values():
    if "data_sha256" not in record:
        continue
    data = value(record["id"])
    raw = json.dumps(
        data, ensure_ascii=False, sort_keys=True, separators=(",", ":")
    ).encode()
    assert hashlib.sha256(raw).hexdigest() == record["data_sha256"], record["path"]
    if record["kind"] in {"historical-authored-source", "historical-report"}:
        assert hashlib.sha256(data.encode()).hexdigest() == record["id"]
assert index["counts"]["unclassified"] == 0
print("PASS: retirement supplement projections, original reports and source text")
