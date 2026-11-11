from pathlib import Path

from shaderlod_host.adapters import semantic_mapping


def test_mapping_requires_exact_archive_dimension_and_options() -> None:
    root = Path(__file__).resolve().parents[1] / "benchmarks/mappings"
    observed: dict[str, object] = {
        "activation": {
            "archiveSha256": "fed6c879e732cf7fc7bc6570ebb1def43235aa7e96f62859fbdbdbfa0b484b38"
        },
        "dimension": "minecraft:overworld",
        "options": {"SHADOW": "true", "GBUFFERS_TERRAIN": "true"},
    }
    assert semantic_mapping(observed, root)["status"] == "mapped"
    for changes in (
        {"activation": {"archiveSha256": "a" * 64}},
        {"dimension": "minecraft:the_nether"},
        {"options": {"SHADOW": "false"}},
    ):
        assert semantic_mapping(observed | changes, root)["status"] == "unmapped"
