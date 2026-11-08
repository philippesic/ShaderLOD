"""Publish bounded conclusions from preserved measurements and reviewed samples."""
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent


def main():
    result = json.loads((HERE/'results.json').read_text())
    terminal = json.loads((HERE/'terminal-audit-v7.json').read_text())
    assert terminal['all_terminal_restored_released'] and terminal['clients_remaining'] == 0
    assert len(result['cells']) == 64
    measured = [c for c in result['cells'] if c.get('arms')]
    assert len(measured) == 40 and all(c['plotted_runs'] == 2 for c in measured)
    quality_cases = [
        ('makeup', 'G01', 'makeup-copy-motion-quality-v5', HERE/'makeup-copy-quality-v5-analysis.json'),
        ('bsl', 'G02', 'bsl-mips-motion-quality-v6', HERE/'bsl-mips-quality-v6-analysis/quality-analysis.json'),
        *[(shader, 'C02', shader+'-bloom-motion-quality-v6', HERE/(shader+'-bloom-motion-quality-v6-analysis-v7/quality-analysis.json'))
          for shader in ('bsl', 'reimagined', 'unbound')],
        ('makeup', 'N04', 'makeup-sky-motion-quality-v6', HERE/'makeup-sky-motion-quality-v6-analysis-v7/quality-analysis.json'),
    ]
    quality = []
    for shader, goal, report, path in quality_cases:
        data = json.loads(path.read_text())
        assert data['captures_complete'] and data['common_written_slots'] == 121 and len(data['frames']) == 121
        quality.append(dict(shader=shader, goal=goal, report=report, captures=363,
                            aggregate=data['aggregate'], analysis=str(path.relative_to(HERE)),
                            visual_review='No gross scene loss or obvious routing/block artifact in reviewed slots0/60/120; overview and unscaled crop review, not a full temporal acceptance test.'))
    quality_scope = ('All2178 successful PNGs decoded/hashed and all121 paired slots per case compared in final RGB8. '
                     'Selected frames0/60/120 reviewed visually; supplemental unscaled crops retained. '
                     'Shader clocks/history and small rendered pose differences remain uncontrolled. '
                     'RGB differences are descriptive against native-return variation, not deterministic equivalence. '
                     'The logical20s path holds for rendering/writer capacity and may take longer in real time; '
                     'these captures never supply performance bars. Brief shimmer/ghosting, HDR, all scenes/transitions, '
                     'DH and broad PBR compatibility remain unqualified.')
    (HERE/'quality-summary.json').write_text(json.dumps(dict(scope=quality_scope, cases=quality, successful_images=2178), indent=2)+'\n')
    retained = [dict(shader=c['shader'], goal=c['goal'], frame_time_reduction_pct=c['effects_pct'],
                     case=c['plotted_case'], actual_gl=c['plotted_gl_context'])
                for c in measured if (c['shader'],c['goal']) in (('makeup','G01'),('bsl','G02')) or c['goal']=='C02']
    conclusions = dict(
        scope='Completed current64-cell RTX5080 screening audit, not a claim that all64 combinations run or that implementations are hardware-optimal.40 cells have two-process primary timing;22 have explicit source-held/no-certified-route reasons;two share G01. Existing general-route scope does not become four separate new pack-specific implementations.',
        decision='No broadly useful shared v1/default candidate qualified. Retain MakeUp copy omission and BSL mip omission as scoped experimental gains; C02 is a small NVIDIA-only bloom quality tradeoff. Other routes are regressive, inconclusive, or offer small scene-limited reductions without an established broadly useful gain.',
        retained_observations=retained,
        minor_observation=next(dict(shader=c['shader'],goal=c['goal'],effect=c['effects_pct'],limit='Sky-focused MakeUp N04 only; small n2 result and no measured fragment skip frequency; not a general benefit or default recommendation.') for c in measured if c['shader']=='makeup' and c['goal']=='N04'),
        variance='N-1 sample frame variance within each window; window-mean variation and equal-weight independent-process variance stay separate. SD bars are not confidence intervals.40 primary cells have n2; separate modern-context diagnosis has n1 and unavailable between-process variance.',
        regression='Real4.6 G03 diagnostics remain slower by6.989%/10.806% on Reimagined/Unbound.572 valid windows have no exploratory2% quarter/pair trend flag. G06 full-size allocation/half viewport is intentional. Observed-work/JFR checks do not isolate every CPU/GPU bottleneck or prove algorithms cannot improve.',
        quality_scope=quality_scope,
        operations=terminal['operation_count'],all_terminal_restored_released=True,
        untested='Production/default enablement; hardware-optimal variants; universal HDR/temporal/transition/DH/PBR compatibility; AMD and renewed2080Ti testing; held geometry/lifetime/new general-route implementations.',
    )
    (HERE/'audit-conclusions.json').write_text(json.dumps(conclusions, indent=2)+'\n')
    titles={'makeup':'MakeUp','bsl':'BSL','reimagined':'Reimagined','unbound':'Unbound'}
    lines=['# RTX5080 four-target audit', '',
           'The current screening audit is complete. Most routes are neutral, slower or held for missing proof. '
           'The clearest local reductions are MakeUp G01 copy omission (3.24%) and BSL G02 mip omission (3.95%). '
           'NVIDIA bloom VRS reduces means by1.77–2.24% over three packs, with a quality tradeoff and vendor-specific scope. '
           'No broadly useful shared v1/default optimization qualified.', '',
           'All64 cells have dispositions:40 have two independent primary timing processes,22 have source-held/no-certified-route reasons, '
           'and two share G01 without additive credit. There are202 analyzed timing/admission reports (177 valid); '
           'the native terminal audit covers all217 operations, including additional quality/diagnostic/failed attempts. '
           'All217 are terminal/restored/released, both managed profiles match canonical inputs, and no Minecraft clients remain.', '',
           '[Interactive charts](interactive-charts.html) · [PNG](candidate-frametimes.png) · [SVG](candidate-frametimes.svg) · '
           '[Within-window data](frame-variance.csv) · [Between-process data](between-run-variance.csv) · '
           '[Compact tracked measurements](timing-summary-v4.json)', '',
           '## Baselines and variability', '',
           'Native Windows11 / RTX5080 / driver617.42 /3840×2160 / original shader options and AA. '
           'Each original-only baseline omits ShaderLOD, conditions for120s and records three20s windows per process. '
           'There are two processes in each of nine separately retained baseline cohorts. '
           'Candidate rows use matched original-pack controls with ShaderLOD installed and Off; C02 uses On with empty public requests/VRS Off. '
           'The separate baseline/card difference is not a causal mod-overhead estimate.', '',
           '| Pack | Actual GL / stack | Process mean, ms | Process SD, ms | Process variance, ms² |',
           '|---|---|---:|---:|---:|']
    by_name={r['name']:r for r in result['runs']}
    for cohort in result['baseline_cohorts'].values():
        run=by_name[cohort['runs'][0]];spread=cohort['process_means_ms']
        version='.'.join(map(str,run['gl_context']['actual_version']))
        stack='optimized' if 'optimized' in run['case'] else 'minimal'
        lines.append(f"| {titles[run['shader']]} | {version} / {stack} | {spread['mean']:.6f} | {spread['sd']:.6f} | {spread['variance']:.8g} |")
    lines += ['', 'Frame jitter, variation between windows and between independent processes remain separate. '
              'Sample SD/variance are descriptive, not confidence intervals or IID-frame inference. '
              'Native3.2 and real4.3/4.5/4.6 startup/admission were exercised where relevant. '
              'Rows retain their actual required context; contexts, source versions, scenes, PBR cases and stacks are not pooled.', '',
              '## All sixteen candidates', '',
              'Values below are mean frame-time reduction ± process sample SD in percentage points (n2). '
              'Positive means faster. The charts/data provide absolute native/candidate frame times and both variance levels. '
              'Held means unmeasured with a specific reason, not zero or unsupported hardware. '
              'N01 uses natural terrain; G09 includes native-PBR cases; C01 uses its declared authored GPU case. '
              'Other cases remain separate in the every-attempt view.', '',
              '| Goal | MakeUp | BSL | Reimagined | Unbound |','|---|---:|---:|---:|---:|']
    cells={(c['shader'],c['goal']):c for c in result['cells']}
    for goal in [f'G{i:02}' for i in range(1,11)]+['C01','C02']+[f'N{i:02}' for i in range(1,5)]:
        values=[]
        for shader in titles:
            cell=cells[shader,goal]
            if cell.get('arms'):
                effect=cell['effects_pct'];values.append(f"{effect['mean']:+.2f}% ±{effect['sd']:.2f}")
            else:
                values.append('shared G01' if cell['status']=='CONSOLIDATED_WITH_G01' else 'held')
        lines.append('| '+goal+' | '+' | '.join(values)+' |')
    lines += ['', '[Each source-held reason and input pins](source-review-findings.json) · [Source review](SOURCE_REVIEW.md)', '',
              'MakeUp N04 has a small sky-view-only1.12% reduction (SD0.27pp); fragment skip frequency and general benefit are not established. '
              'The two G01/G02 reductions are experimental observations, not pixel/HDR/default qualification. '
              'C02 remains outside shared cross-vendor v1 scope. Effects are independent observations and must not be added together.', '',
              '## Repairs and regression checks', '',
              'Repaired Windows ownership-file sharing/recovery, the core-profile C01 polygon-state guard, finite Windows source admission, '
              'authored/packaged helper drift and catalog pins, stale MakeUp recipe admission, and incorrect contexts for min-version paths. '
              'All eight focused hardware repair checks and all24 affected repeat/control operations pass/restored. '
              'Earlier failed and old-source attempts remain separate.', '',
              'The additional OpenGL4.6 G03 comparisons remain6.99% slower on Reimagined and10.81% slower on Unbound. '
              'These n1 diagnostic contexts do not replace primary n2 estimates. '
              'All572 valid measured windows pass the exploratory2% chronological-quarter/candidate-window-pair review threshold; '
              'the largest first/last quarter change is1.094%. No samples are trimmed or warm-up-corrected. '
              'Observed graph/JFR checks find no simple extra-pass explanation outside the intentional G06 producer/target. '
              'They do not isolate every regression bottleneck or establish hardware-optimal implementations. '
              '[Detailed diagnosis](REGRESSION_DIAGNOSIS.md) · [Trend audit](time-trend-summary.json)', '',
              '## Bounded image review', '',quality_scope,'',
              '| Pack / route | Optimized vs native mean absolute RGB8 difference | Return vs native |',
              '|---|---:|---:|']
    for row in quality:
        lines.append(f"| {titles[row['shader']]} / {row['goal']} | {row['aggregate']['candidate_vs_native']['mean_absolute_rgb_u8']:.3f} | {row['aggregate']['return_vs_native']['mean_absolute_rgb_u8']:.3f} |")
    lines += ['', 'No gross scene loss or obvious routing/block artifact was seen in reviewed samples. '
              'MakeUp G01 full-frame differences are larger than return variation, with visibly different cloud phases; '
              'the supplemental lower-half samples are comparable to native-return variation. '
              'RGB difference alone cannot qualify temporal correctness or acceptable quality. '
              'Three earlier incomplete captures remain invalid with raw bytes and metadata retained. '
              'The recorder repair holds the logical path after render/writer stalls instead of dropping requested slots; '
              '121 complete PNGs per arm remain required. [Quality summary](quality-summary.json)', '',
              '## Validation and retention', '',
              'The Windows/readiness repairs passed103 maintained benchmark tests on both hosts; C01 passed22 Java tests. '
              'Adapter repair passed17 Python tests locally plus actual-original assembly/native catalog validation. '
              'The recorder clock passes three Java regressions and two Python request tests on both hosts with dev compilation. '
              'The per-arm archiver passes three local byte-preservation/live-client/concurrent-change tests and actual native archive verification. '
              'The native4K encoder fixture independently round-trips RGBA pixels at PNG levels0/1. '
              '[Terminal audit](terminal-audit-v7.json) verifies all217 operations and both canonical profiles.', '',
              'Cleanup stayed within ShaderLOD. Original packs, live/canonical worlds, useful dependencies/caches and failed evidence remain. '
              'Retired raw/recovery copies have independently verified backups; redundant transport/setup/source-freeze copies were removed only after off-host byte verification. '
              'The32GiB reserve was preserved. [Artifact locations and recovery](ARTIFACTS.md)', '']
    (HERE/'REPORT.md').write_text('\n'.join(lines))
    print(json.dumps(dict(measured_cells=len(measured),quality_images=2178,terminal_operations=terminal['operation_count'])))


if __name__ == '__main__':
    main()
