# Local dragon comparison

Run from the repository with Java 21:

```sh
python3 tools/benchmark/run.py --accept-eula --rounds 2
```

The runner archives `main` and freezes the current working tree into separate temporary checkouts, adds the identical opt-in harness to both versions, then starts dedicated servers sequentially in alternating branch order. Each server binds only `127.0.0.1:25575`, uses a fresh flat world with seed `8675309`, a 2 GiB heap, and view/simulation distance 6. It shuts itself down after the workload. Normal builds exclude the harness.

Each run tests 1, 20, and 100 adult dragons sitting idle, navigating to alternating reachable targets, and trying to reach targets enclosed in solid blocks. Every phase has 100 warmup ticks followed by 200 measured ticks at the normal 20 TPS. Attributes and entity RNG seeds are fixed. Terrain preparation and population replacement happen outside the measured interval. Natural spawning and random block ticks are disabled; arena chunks are force-loaded.

The output directory contains logs, individual `benchmark.csv` files, combined `results.csv`, commit metadata, and the checkout's tracked patch. The script rejects incomplete workloads or populations that do not tick on every measured tick. `--output /absolute/fresh/path`, `--base REF`, and `--gradle-user-home PATH` override defaults. Requires Python 3.9+ and the system `tar` command.

`mean_ms` and percentile columns measure work between server pre/post tick events, excluding tick pacing sleep. `path_request_ms` measures time in public position-based path requests, including cache hits and walking/flight fallback; it is **not** a count of A* searches or visited nodes. `searches` counts actual pathfinder invocations, `expanded_nodes` counts evaluator neighbour-expansion calls, and `search_ms` is their cumulative wall time. `arrival_ticks` counts dragon-ticks within three blocks of the phase's target (not unique arrivals). `entity_ticks` must equal population × samples. Idle arrival counts are not meaningful.

This is a short synthetic server CPU comparison, not a multiplayer, rendering, packet-volume, allocation, or long-distance flight benchmark. Compare progress as well as timings: a faster server with stationary dragons is a regression. Repeat longer runs on representative worlds before choosing production search budgets.

Generate a Markdown report after a completed comparison:

```sh
python3 tools/benchmark/summarize.py /path/to/output --report docs/performance-comparison.md
```

Brain-driven requests use a soft shared time budget on the candidate branch (`performance.pathfinding_budget_ms`, default 4 ms per level per tick). A search already running can overrun that budget. External callers of navigation APIs retain their ordinary synchronous behaviour.

For repeated tuning, `--reuse-first-baseline /path/to/previous/main-1` reuses one completed baseline only after verifying its commit and byte-identical Java harness. Metadata records reuse; candidate sources are always snapshotted.
