import hashlib
import json
from pathlib import Path

root = Path(__file__).resolve().parents[2]
sources = root / "results/sources/history"
manifest = json.loads((sources / "files.json").read_text())
catalog = json.loads((sources / "catalog.json").read_text())["records"]
records = {}
locations = {}

for item in manifest["files"]:
    assert Path(item["file"]).name == item["file"]
    raw = (sources / item["file"]).read_bytes()
    assert len(raw) == item["bytes"], item["file"]
    assert hashlib.sha256(raw).hexdigest() == item["sha256"], item["file"]
    for record in json.loads(raw)["records"]:
        assert record["id"] not in records, record["id"]
        records[record["id"]] = record
        locations[record["id"]] = item["file"]


def resolve(identity):
    record = records[identity]
    if "parts" not in record:
        return record.get("data")
    pieces = [records[key] for key in record["parts"]]
    if any("key" in piece for piece in pieces):
        value = {}
        for piece in pieces:
            data = resolve(piece["id"])
            if "start" in piece:
                target = value.setdefault(piece["key"], [])
                if piece.get("single_list_element"):
                    target.append(data)
                else:
                    target.extend(data)
            else:
                value[piece["key"]] = data
        return value
    value = []
    for piece in pieces:
        data = resolve(piece["id"])
        if piece.get("single_list_element"):
            value.append(data)
        else:
            value.extend(data)
    return value


def check_options(value):
    if isinstance(value, dict):
        if "retained_options_id" in value:
            assert records[value["retained_options_id"]]["kind"] == "tested-options"
        for child in value.values():
            check_options(child)
    elif isinstance(value, list):
        for child in value:
            check_options(child)


for entry in catalog:
    record = records[entry["id"]]
    assert locations[entry["id"]] == entry["file"]
    assert record["origins"] == entry["origins"]
    if "text" in record:
        raw = record["text"].encode()
        assert hashlib.sha256(raw).hexdigest() == record["id"], entry
        assert len(raw) == record["original_bytes"], entry
    value = resolve(record["id"])
    if "data_sha256" in record:
        raw = json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode()
        assert hashlib.sha256(raw).hexdigest() == record["data_sha256"], record["id"]
    check_options(value)

summary = json.loads(Path(__file__).with_name("result.json").read_text())
assert len(catalog) == summary["retained_record_count"]
assert sum(v["entries"] for v in summary["file_dispositions"].values()) == summary["snapshot_regular_files"] + summary["snapshot_symlinks"]
assert sum(summary["earlier_archive_dispositions"].values()) == summary["earlier_archive_entries"]
assert summary["unclassified_file_groups"] == 0

source_map = json.loads(Path(__file__).with_name("source-recovery.json").read_text())
assert len(source_map["sources"]) == source_map["unique_experiment_sources"]
for source in source_map["sources"]:
    if source["recovery"] == "retained-source-record":
        record = records[source["record"]]
        assert hashlib.sha256(record["text"].encode()).hexdigest() == source["sha256"]
    else:
        assert source["recovery"] == "git-main-blob"
        assert len(source["blob"]) == 40

capture = resolve("6839a764129cae84d99c98856b8218c5817163735f8d40bd1a8520868cecb305")
frames = capture["retained_frame_intervals"]
intervals = {frame["id"]["frameId"] for frame in frames if frame["nextStartNs"] is not None}
gpu_frames = {event["invocation"]["frame"]["frameId"] for event in capture["retained_gpu_events"] if event["gpuOutcome"]["status"] == "MEASURED"}
assert len(intervals) == 162
assert len(intervals & gpu_frames) == 39
assert capture["health"]["unresolvedTail"] == 43
assert capture["health"]["imbalancedScopes"] == 1
assert capture["health"]["outcome"]["status"] == "UNKNOWN"

rows = json.loads((root / "results/asahi-null-calibration/result.json").read_text())
assert len(rows) == 6
effects = [row["apparent_gain_percent"] for row in rows]
assert round(min(effects), 2) == -9.70
assert round(max(effects), 2) == 10.24

campaigns = {}
for name in ["asahi-four-target-audit-20261104", "rtx5080-four-target-audit-20261106"]:
    path = "dev/experiments/" + name + "/results.json"
    identity = next(entry["id"] for entry in catalog if any(origin["container"] == "old-checkout" and origin["path"] == path for origin in entry["origins"]))
    cells = resolve(identity)["cells"]
    assert len(cells) == 64
    campaigns[name] = {(cell["goal"], cell["shader"]): cell for cell in cells}
    assert len(campaigns[name]) == 64

cards = list((root / "results").glob("candidate-*/result.json"))
assert len(cards) == 16
for path in cards:
    card = json.loads(path.read_text())
    for shader, cell in card["asahi"]["targets"].items():
        original = campaigns["asahi-four-target-audit-20261104"][(card["goal"], shader)]
        for key in ["status", "effects_pct"]:
            assert cell.get(key) == original.get(key), (path, shader, key)
    for cell in card["rtx5080"]:
        original = campaigns["rtx5080-four-target-audit-20261106"][(card["goal"], cell["shader"])]
        for key in ["status", "effects_pct"]:
            assert cell.get(key) == original.get(key), (path, cell["shader"], key)

print(f"PASS: {len(catalog)} historical records, all retained-file digests, split records, option references and extra-source identities")
print("PASS: GPU coverage 39/162, unknown health and incomplete tail; six-process unchanged-shader calibration retained")
print("PASS: all sixteen candidate cards match both complete 64-cell campaign tables")
