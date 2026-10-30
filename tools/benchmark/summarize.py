#!/usr/bin/env python3
"""Summarize completed benchmark runs without treating short runs as statistical proof."""
import argparse
import csv
import json
from collections import defaultdict
from pathlib import Path
from statistics import mean

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('directory', type=Path)
parser.add_argument('--report', type=Path, required=True)
args = parser.parse_args()
with (args.directory / 'results.csv').open() as file:
    rows = list(csv.DictReader(file))
metadata = json.loads((args.directory / 'metadata.json').read_text())
groups = defaultdict(list)
for row in rows:
    groups[(row['branch'], row['scenario'], int(row['dragons']))].append(row)


def value(branch, scenario, count, field):
    return mean(float(row[field]) for row in groups[(branch, scenario, count)])


lines = ['# Dragon performance comparison', '',
         f"Baseline: `main` at `{metadata['base_commit']}`. Candidate: `performance` changes measured as a working tree based on the same commit.", '',
         f"{metadata['rounds']} runs per branch, executed sequentially in alternating order. Java 21, 2 GiB heap, fresh flat worlds with seed 8675309, view/simulation distance 6, 100 warmup ticks and 200 measured ticks per scenario. Every dragon ticked for every measured tick. Servers bound to localhost and shut down after the workload.", '',
         '## Server tick work', '',
         'Values are averages across runs. Percentiles are the average of each run’s percentile, not pooled percentiles. Tick pacing sleep and phase setup are excluded.', '',
         '| Scenario | Dragons | Main mean ms | Performance mean ms | Mean reduction | Main p95 ms | Performance p95 ms |',
         '|---|---:|---:|---:|---:|---:|---:|']
for scenario in ['idle', 'reachable', 'blocked']:
    for count in [1, 20, 100]:
        baseline = value('main', scenario, count, 'mean_ms')
        candidate = value('performance', scenario, count, 'mean_ms')
        lines.append(f"| {scenario} | {count} | {baseline:.3f} | {candidate:.3f} | {(1 - candidate / baseline) * 100:.1f}% | {value('main', scenario, count, 'p95_ms'):.3f} | {value('performance', scenario, count, 'p95_ms'):.3f} |")

if metadata.get('reused_first_baseline'):
    lines += ['', 'The first baseline was reused from a completed run with the same commit and a byte-identical Java harness; the runner verified both before reuse. The other baseline and both candidate runs used new worlds and frozen source snapshots. Reuse is recorded in metadata.', '']
lines += ['', '## Pathfinding work', '',
          'Counts and cumulative search times are averages per 200-tick measured window. Expanded nodes count calls to the evaluator’s neighbour expansion, not every node discovered. Both branches use the identical counter wrapper.', '',
          '| Scenario | Dragons | Main searches | Performance searches | Main expansions | Performance expansions | Main search ms | Performance search ms |',
          '|---|---:|---:|---:|---:|---:|---:|---:|']
for scenario in ['reachable', 'blocked']:
    for count in [1, 20, 100]:
        fields = ['searches', 'expanded_nodes', 'search_ms']
        data = [(value('main', scenario, count, field), value('performance', scenario, count, field)) for field in fields]
        lines.append(f'| {scenario} | {count} | {data[0][0]:.0f} | {data[0][1]:.0f} | {data[1][0]:.0f} | {data[1][1]:.0f} | {data[2][0]:.1f} | {data[2][1]:.1f} |')
lines += ['', '## Movement check', '',
          'Reachable-target arrival ticks count time spent within three blocks of the alternating target, averaged per measured window. This is a progress check, not unique arrivals or a full navigation-quality score.', '',
          '| Dragons | Main arrival ticks | Performance arrival ticks |', '|---|---:|---:|']
for count in [1, 20, 100]:
    lines.append(f"| {count} | {value('main', 'reachable', count, 'arrival_ticks'):.0f} | {value('performance', 'reachable', count, 'arrival_ticks'):.0f} |")
b100 = value('main', 'blocked', 100, 'mean_ms')
c100 = value('performance', 'blocked', 100, 'mean_ms')
b95 = value('main', 'blocked', 100, 'p95_ms')
c95 = value('performance', 'blocked', 100, 'p95_ms')
b20 = value('main', 'blocked', 20, 'p95_ms')
c20 = value('performance', 'blocked', 20, 'p95_ms')
lines += ['', '## Interpretation', '',
          f'The largest blocked-target workload improved by {(1 - c100 / b100) * 100:.1f}% in mean tick work and {(1 - c95 / b95) * 100:.1f}% in p95. Reachable-target progress was preserved in both runs.', '',
          f'This is not a universal latency improvement: with 20 blocked dragons, p95 changed from {b20:.2f} ms to {c20:.2f} ms. Smaller populations can still experience an expensive individual multi-mode search even though average work is lower. Idle and easy-route differences are small and noisy; do not infer a general speedup from them.', '',
          'The shared budget addresses repeated planning bursts; it is a soft admission budget, not a preemptible search deadline. Further work on incremental searches and representative terrain tests is needed to improve the remaining small-population tail latency.', '',
          '## Changes implemented', '',
          '- One ground/flight/swim evaluator lifecycle per search; delegated caches and world references are cleaned up.',
          '- Immediate requests no longer fail solely because of the old cooldown; repeated genuine failures use bounded retry backoff.',
          '- Required detour nodes are preserved. Walking no longer triggers flight merely because the feet occupy air. Completed-path node reads are guarded.',
          '- Brain-driven planning shares a configurable soft time budget (`performance.pathfinding_budget_ms`, default 4 ms per level per tick). Waiting preserves the movement target.',
          '- Stable attributes refresh after genetic-stat or config changes instead of allocating modifiers every tick. Ordinary age updates are sent once per second with client prediction; dimensions refresh when scale changes.',
          '- Mounting/sitting cancels conflicting movement and combat. Behaviour startup and continuation conditions are separate.',
          '- Breath damage queries run only on the server with range/visibility/cone checks. Attack sensing rejects invalid/allied targets early and compares spawn-group UUID values.', '']
lines += ['', '## Validation and limits', '',
          '75 dedicated-server GameTests passed, including immediate path requests, entity/block entry points, walking, takeoff, hatchling restrictions, sitting cancellation, obstacle detours, failed-route retry backoff, and target preservation when the shared search budget is exhausted.', '',
          'These short synthetic runs show this workload on this machine. Two repeats do not establish statistical significance. Brain-driven planning uses a soft 4 ms budget per level per tick; individual searches finish even if they overrun it, and direct external navigation calls are not deferred. These runs do not measure multiplayer traffic, rendering, allocations, long-distance navigation, aquatic transitions, or typical modpacks. The lower search budget and retry backoff should be checked on representative worlds before release. Unreachable targets may be retried after up to roughly four seconds unless movement, request parameters, capabilities, or terrain invalidation require an earlier search.', '',
          'Reproduce with `python3 tools/benchmark/run.py --accept-eula --rounds 2`. See [the harness documentation](../tools/benchmark/README.md). Raw measurements are in [performance-results.csv](performance-results.csv).', '']
args.report.parent.mkdir(parents=True, exist_ok=True)
args.report.write_text('\n'.join(lines))
print(args.report)
