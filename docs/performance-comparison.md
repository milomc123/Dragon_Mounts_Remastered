# Dragon performance comparison

Baseline: `main` at `a8f8c2774a86e5aa61a4f6287751e80d060a55bd`. Candidate: `performance` changes measured as a working tree based on the same commit.

2 runs per branch, executed sequentially in alternating order. Java 21, 2 GiB heap, fresh flat worlds with seed 8675309, view/simulation distance 6, 100 warmup ticks and 200 measured ticks per scenario. Every dragon ticked for every measured tick. Servers bound to localhost and shut down after the workload.

## Server tick work

Values are averages across runs. Percentiles are the average of each run’s percentile, not pooled percentiles. Tick pacing sleep and phase setup are excluded.

| Scenario | Dragons | Main mean ms | Performance mean ms | Mean reduction | Main p95 ms | Performance p95 ms |
|---|---:|---:|---:|---:|---:|---:|
| idle | 1 | 0.359 | 0.448 | -25.0% | 0.629 | 0.791 |
| idle | 20 | 1.093 | 0.817 | 25.2% | 1.580 | 1.135 |
| idle | 100 | 2.126 | 1.859 | 12.5% | 2.534 | 2.161 |
| reachable | 1 | 0.355 | 0.320 | 9.8% | 0.612 | 0.548 |
| reachable | 20 | 1.044 | 0.900 | 13.8% | 1.479 | 1.337 |
| reachable | 100 | 3.237 | 2.735 | 15.5% | 4.268 | 4.085 |
| blocked | 1 | 0.704 | 0.456 | 35.2% | 4.482 | 0.466 |
| blocked | 20 | 9.938 | 5.959 | 40.0% | 10.632 | 20.631 |
| blocked | 100 | 50.439 | 16.399 | 67.5% | 53.702 | 22.697 |

The first baseline was reused from a completed run with the same commit and a byte-identical Java harness; the runner verified both before reuse. The other baseline and both candidate runs used new worlds and frozen source snapshots. Reuse is recorded in metadata.


## Pathfinding work

Counts and cumulative search times are averages per 200-tick measured window. Expanded nodes count calls to the evaluator’s neighbour expansion, not every node discovered. Both branches use the identical counter wrapper.

| Scenario | Dragons | Main searches | Performance searches | Main expansions | Performance expansions | Main search ms | Performance search ms |
|---|---:|---:|---:|---:|---:|---:|---:|
| reachable | 1 | 4 | 4 | 18 | 18 | 2.6 | 3.6 |
| reachable | 20 | 80 | 80 | 360 | 360 | 12.3 | 20.6 |
| reachable | 100 | 400 | 400 | 1800 | 1800 | 32.0 | 78.1 |
| blocked | 1 | 16 | 6 | 2952 | 1209 | 77.0 | 45.2 |
| blocked | 20 | 394 | 146 | 68293 | 28881 | 1858.0 | 1025.7 |
| blocked | 100 | 1970 | 426 | 358871 | 80284 | 9562.8 | 2777.6 |

## Movement check

Reachable-target arrival ticks count time spent within three blocks of the alternating target, averaged per measured window. This is a progress check, not unique arrivals or a full navigation-quality score.

| Dragons | Main arrival ticks | Performance arrival ticks |
|---|---:|---:|
| 1 | 116 | 116 |
| 20 | 1940 | 1940 |
| 100 | 9700 | 9700 |

## Interpretation

The largest blocked-target workload improved by 67.5% in mean tick work and 57.7% in p95. Reachable-target progress was preserved in both runs.

This is not a universal latency improvement: with 20 blocked dragons, p95 changed from 10.63 ms to 20.63 ms. Smaller populations can still experience an expensive individual multi-mode search even though average work is lower. Idle and easy-route differences are small and noisy; do not infer a general speedup from them.

The shared budget addresses repeated planning bursts; it is a soft admission budget, not a preemptible search deadline. Further work on incremental searches and representative terrain tests is needed to improve the remaining small-population tail latency.

## Changes implemented

- One ground/flight/swim evaluator lifecycle per search; delegated caches and world references are cleaned up.
- Immediate requests no longer fail solely because of the old cooldown; repeated genuine failures use bounded retry backoff.
- Required detour nodes are preserved. Walking no longer triggers flight merely because the feet occupy air. Completed-path node reads are guarded.
- Brain-driven planning shares a configurable soft time budget (`performance.pathfinding_budget_ms`, default 4 ms per level per tick). Waiting preserves the movement target.
- Stable attributes refresh after genetic-stat or config changes instead of allocating modifiers every tick. Ordinary age updates are sent once per second with client prediction; dimensions refresh when scale changes.
- Mounting/sitting cancels conflicting movement and combat. Behaviour startup and continuation conditions are separate.
- Breath damage queries run only on the server with range/visibility/cone checks. Attack sensing rejects invalid/allied targets early and compares spawn-group UUID values.


## Validation and limits

75 dedicated-server GameTests passed, including immediate path requests, entity/block entry points, walking, takeoff, hatchling restrictions, sitting cancellation, obstacle detours, failed-route retry backoff, and target preservation when the shared search budget is exhausted.

These short synthetic runs show this workload on this machine. Two repeats do not establish statistical significance. Brain-driven planning uses a soft 4 ms budget per level per tick; individual searches finish even if they overrun it, and direct external navigation calls are not deferred. These runs do not measure multiplayer traffic, rendering, allocations, long-distance navigation, aquatic transitions, or typical modpacks. The lower search budget and retry backoff should be checked on representative worlds before release. Unreachable targets may be retried after up to roughly four seconds unless movement, request parameters, capabilities, or terrain invalidation require an earlier search.

Reproduce with `python3 tools/benchmark/run.py --accept-eula --rounds 2`. See [the harness documentation](../tools/benchmark/README.md). Raw measurements are in [performance-results.csv](performance-results.csv).
