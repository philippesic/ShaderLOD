from pathlib import Path

from shaderlod_host.contracts import mapping, identity, integer, digest


def verify_functional(
    value: object,
    profile: Path,
    expected_source: str,
    expected_sha256: str,
    dh_present: bool,
    expected_surface: tuple[int, int] = (2560, 1600),
) -> dict[str, object]:
    record = mapping(value, "functional result")
    required = {
        "schema",
        "scope",
        "source",
        "dh",
        "profiler",
        "native_width",
        "native_height",
        "outcome",
        "restored_state",
    }
    allowed = required | {
        "sampler_index_policy",
        "gpu_profiler",
        "native_activation",
        "selection",
        "identity_probe",
        "rendered_output_sha256",
        "image_has_variation",
        "limits",
        "reason",
        "restoration",
        "recovery_activation",
        "identity_output_sha256",
        "cancel_staged_unchanged",
        "cancel_preserved_active",
        "off_restored",
        "options_preserved",
        "failed_apply_restored",
        "failed_apply_reason",
        "native_rendered_sections",
        "identity_rendered_sections",
    }
    if (
        not required <= record.keys()
        or not record.keys() <= allowed
        or record["schema"] != "shaderlod-functional-v3"
    ):
        raise ValueError("unknown functional result schema or fields")
    if record["outcome"] not in ("PASS", "FAIL"):
        raise ValueError("unknown functional outcome")
    if record["outcome"] == "FAIL":
        if not isinstance(record.get("reason"), str) and not isinstance(
            record.get("restoration"), str
        ):
            raise ValueError("functional failure needs a reason")
        return record
    if (
        "reason" in record
        or "restoration" in record
        or record.get("image_has_variation") is not True
    ):
        raise ValueError("failure/missing output cannot become a pass")
    if record["source"] != expected_source:
        raise ValueError("functional source disagreement")
    if (
        integer(record["native_width"], "width") != expected_surface[0]
        or integer(record["native_height"], "height") != expected_surface[1]
    ):
        raise ValueError("declared native dimensions not preserved")
    integer(record.get("native_rendered_sections"), "native rendered sections", 1)
    integer(record.get("identity_rendered_sections"), "identity rendered sections", 1)
    dh = mapping(record["dh"], "DH observation")
    if type(dh.get("present")) is not bool or dh["present"] != dh_present:
        raise ValueError("DH mode disagreement")
    if dh_present and dh.get("enabled") is not True:
        raise ValueError("DH enabled state is disabled or unknown")
    activations = []
    for key in (
        "native_activation",
        "selection",
        "identity_probe",
        "recovery_activation",
    ):
        active = mapping(record.get(key), key)
        if (
            set(active)
            != {
                "packName",
                "archiveSha256",
                "generation",
                "linked",
                "effectiveOptions",
                "witnesses",
            }
            or active["linked"] is not True
        ):
            raise ValueError("missing actual linked activation")
        sha = identity(active["archiveSha256"])
        generation = integer(active["generation"], "generation", 0)
        name = active["packName"]
        if not isinstance(name, str) or Path(name).name != name or "\\" in name:
            raise ValueError("invalid active archive path")
        archive = profile / "shaderpacks" / name
        if archive.is_symlink() or digest(archive.read_bytes()) != sha:
            raise ValueError("loaded artifact identity disagreement")
        if (
            not isinstance(active["witnesses"], list)
            or len(active["witnesses"]) != 2
            or not all(isinstance(item, str) for item in active["witnesses"])
            or set(active["witnesses"])
            != {"linked-archive:" + sha, "submitted-frame:" + sha}
        ):
            raise ValueError("activation witness disagreement")
        mapping(active["effectiveOptions"], "effective options")
        activations.append((active, generation))
    for active, _ in activations[:2]:
        if (
            active["packName"] != expected_source
            or active["archiveSha256"] != expected_sha256
        ):
            raise ValueError("native/source selection disagreement")
    if (
        not activations[0][1]
        < activations[1][1]
        < activations[2][1]
        < activations[3][1]
    ):
        raise ValueError("stale graph generation")
    for active, _ in activations:
        if active["effectiveOptions"] != activations[0][0]["effectiveOptions"]:
            raise ValueError("effective native options changed")
    if (
        activations[3][0]["packName"] != expected_source
        or activations[3][0]["archiveSha256"] != expected_sha256
    ):
        raise ValueError("recovery graph disagreement")
    for key in (
        "cancel_staged_unchanged",
        "cancel_preserved_active",
        "off_restored",
        "options_preserved",
        "failed_apply_restored",
    ):
        if record.get(key) is not True:
            raise ValueError("missing actual UI restoration observation: " + key)
    fault_reason = record.get("failed_apply_reason")
    if not isinstance(fault_reason, str) or "Iris rejected" not in fault_reason:
        raise ValueError("missing reload-fault witness")
    restored = mapping(record["restored_state"], "restored native state")
    if (
        set(restored) != {"pack", "enabled", "queued", "effective"}
        or restored.get("enabled") is not True
        or restored
        != {
            "pack": expected_source,
            "enabled": True,
            "queued": {},
            "effective": activations[0][0]["effectiveOptions"],
        }
    ):
        raise ValueError("native state restoration disagreement")
    output = profile / "shaderlod-functional.png"
    if output.is_symlink() or digest(output.read_bytes()) != identity(
        record.get("rendered_output_sha256")
    ):
        raise ValueError("rendered output identity disagreement")
    identity_output = profile / "shaderlod-functional-identity.png"
    if identity_output.is_symlink() or digest(identity_output.read_bytes()) != identity(
        record.get("identity_output_sha256")
    ):
        raise ValueError("identity rendered output disagreement")
    return record
