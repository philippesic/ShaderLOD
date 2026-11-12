import json
from pathlib import Path

import matplotlib.pyplot as plt

root = Path(__file__).resolve().parent
record = json.loads((root / "result.json").read_text())
sessions = record["sensitivity"]["sessions"]
figure, axes = plt.subplots(1, 2, figsize=(10, 3.4), layout="constrained")
for arm, color in [("original", "#1764ab"), ("delay-100ms", "#b84d16")]:
    rows = [
        next(row for row in session["arms"] if row["arm"] == arm)
        for session in sessions
    ]
    axes[0].plot(
        [s["session"] for s in sessions],
        [r["fps"] for r in rows],
        "o-",
        label=arm,
        color=color,
    )
    bins = rows[0]["bins"]
    axes[1].plot(
        [b["second"] for b in bins],
        [b["frames"] * 1e9 / b["observed_interval_ns"] for b in bins],
        "o-",
        label=arm,
        color=color,
    )
axes[0].set(
    xlabel="Independent JVM session",
    ylabel="Application frame throughput (FPS)",
    ylim=(0, 70),
    xticks=range(1, 7),
)
axes[1].set(
    xlabel="Within-route time (s), first session",
    ylabel="Observed interval throughput (FPS)",
    ylim=(0, 70),
)
for axis in axes:
    axis.grid(alpha=0.2)
    axis.legend()
figure.suptitle("RTX 2080 Ti control: original versus deliberate 100 ms delay")
figure.savefig(root / "chart.png", dpi=130, metadata={"Software": "Matplotlib 3.10.7"})
