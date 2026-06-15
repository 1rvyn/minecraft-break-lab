# Paired Block-Breaking Capture

This evidence set contains the complete JSONL telemetry from two controlled nine-block runs on June 14, 2026. Both runs used Survival mode, no status effects, an Efficiency I iron pickaxe, and three blocks each of stone, cobblestone, and deepslate.

- [`baseline.jsonl`](baseline.jsonl): unmodified client, 534 records.
- [`modded-1.15-delay-0.jsonl`](modded-1.15-delay-0.jsonl): tested client with multiplier `1.15` and delay `0`, 328 records.

The files contain packet metadata and corresponding server events, not raw packet payloads, chat, or authentication data. They include the test player UUID/name and in-world coordinates.

## Vital comparison

| Metric | Baseline | Modified |
|---|---:|---:|
| Completed breaks | 9 | 9 |
| `DIG_START` / `DIG_FINISH` | 9 / 9 | 9 / 9 |
| Median start-to-finish | 440.33 ms | 385.13 ms |
| Median arm packets per break | 8 | 7 |
| Median client tick interval | 55.01 ms | 54.96 ms |
| Median finish-to-next-start | 329.78 ms | 54.75 ms |
| Dig sequence delta | 1 | 1 |
| Analyzer findings | none | none |

Per-material medians show the progress reduction directly:

| Block | Baseline duration / arm packets | Modified duration / arm packets |
|---|---:|---:|
| Stone | 329.99 ms / 6 | 275.01 ms / 5 |
| Cobblestone | 440.33 ms / 8 | 385.13 ms / 7 |
| Deepslate | 660.15 ms / 12 | 550.52 ms / 10 |

## Packet shape

The modified capture still uses one normal start followed by one normal finish. For its first stone block, the relevant records reduce to:

```text
DIG_START  target=-111,217,995 block=STONE seq=19 serverTick=3417
DIG_FINISH target=-111,217,995 block=AIR   seq=20 serverTick=3421
```

There is no extra digging-action burst, sequence jump, or accelerated client tick clock in this pair. The observable differences are fewer mining-progress ticks and a shorter delay before starting the next block.

## Reproduce the analysis

From the repository root:

```bash
./gradlew compareCaptures --args="evidence/2026-06-14-break-test/baseline.jsonl evidence/2026-06-14-break-test/modded-1.15-delay-0.jsonl"
./gradlew inspectCapture --args="evidence/2026-06-14-break-test/modded-1.15-delay-0.jsonl"
```

This supports, but does not by itself prove, the client-side implementation used by [Fabric Break Theory](https://github.com/1rvyn/fabric-break-theory): multiply vanilla destroy progress by `1.15`, then replace vanilla's five-tick post-break delay with the configured value. Network timing and server scheduling remain possible sources of individual-sample variation.

## Integrity

```text
eefa7057661cdb63db64ddd2ece76993b9fe61955c200ca9194239fd0591c23e  baseline.jsonl
1b3366e242009fbbb0224a26dab0bb28f801f9d739ce530ecaacb38f7c6ab3c1  modded-1.15-delay-0.jsonl
```
