# Minecraft Break Telemetry Lab

A localhost Paper 1.21.10 server and passive telemetry plugin for collecting packet and server-event data while investigating client-side block-breaking behavior. It is the measurement half of the experiment: run controlled baseline and modified-client captures here, compare the timelines, then reproduce the observed behavior in [Fabric Break Theory](https://github.com/1rvyn/fabric-break-theory).

The plugin records selected inbound gameplay packet metadata and corresponding server events only while you explicitly run a capture. It is intended to make claims about a client modification testable against packet order, timing, server ticks, player state, tools, blocks, and acknowledgements.

## Published evidence

The repository includes a [paired baseline and modified capture](evidence/2026-06-14-break-test/README.md), including both complete JSONL files and a compact writeup of the packet shape, timing differences, per-material results, integrity hashes, and reproduction commands.

## Requirements

- Java 21
- A legitimate Minecraft Java Edition 1.21.10 client
- Internet access for the first Gradle build and Paper download

## Start the lab

```bash
./gradlew runServer
```

The first run downloads the latest stable Paper build published for Minecraft 1.21.10, verifies its SHA-256, writes a local development configuration under `run/`, builds the plugin, and starts the server. The EULA is accepted by the task, so only run it if you agree to the Minecraft EULA.

In the server console, whitelist and op your account:

```text
whitelist add YOUR_NAME
op YOUR_NAME
```

Connect the 1.21.10 client to `172.28.109.78:25565` from Windows. The generated server binds to
`0.0.0.0` because a Windows Minecraft client and a server running inside WSL
do not share the same loopback interface. The whitelist remains enabled.

## Capture paired runs

Use identical game mode, tool, effects, position, and block order for both runs. Labels may include the tested value,
such as `modded-1.15`.

```text
/breaklab arena reset
/breaklab start baseline
/breaklab mark beginning-stone-row
# Break the test blocks with the unmodified client.
/breaklab stop

/breaklab arena reset
/breaklab start modded-1.15
/breaklab mark beginning-stone-row
# Repeat with the modified client.
/breaklab stop
```

Files are written to `run/plugins/BreakTelemetry/captures/`. Each session has raw JSONL, a JSON summary, and a
two-column CSV summary. JSONL includes packet sequence, monotonic receive/send time, server tick, player
position/rotation, ping, game mode, held tool and enchantments, effects, and target block data. It records client
digging, animation, movement, tick-end, use, pong, and keepalive packets plus server block acknowledgements, block
changes, break animations, ping, and keepalive packets.

Compare two captures:

```bash
./gradlew compareCaptures --args="run/plugins/BreakTelemetry/captures/BASELINE.jsonl run/plugins/BreakTelemetry/captures/MODDED.jsonl"
```

Inspect one capture as a compact receive-time timeline:

```bash
./gradlew inspectCapture --args="run/plugins/BreakTelemetry/captures/SESSION.jsonl"
```

The report compares event counts and completed-break duration, arm-packet count, movement count, client tick-end
count, server-tick span, digging sequence delta, and measured round-trip samples. It also flags finishes without
starts, sub-5 ms digging action gaps, and starts without a finish or abort. These are investigation leads, not proof
by themselves. Network scheduling, lag, creative mode, effects, enchantments, and server plugins can all affect timing.

## Commands

- `/breaklab start baseline`
- `/breaklab start modded-1.15` (or another short descriptive label)
- `/breaklab mark <text>`
- `/breaklab status`
- `/breaklab stop`
- `/breaklab arena reset`
- `/breaklab arena stone` creates nine identical stone blocks.
- `/breaklab arena matrix` creates three stone, three cobblestone, and three deepslate blocks.
- `/breaklab setup` selects Survival, clears effects, and equips an Efficiency I iron pickaxe.

## Build and test

```bash
./gradlew clean test shadowJar
```

The plugin JAR is `build/libs/minecraft-break-lab-0.1.0.jar`.

## Privacy and scope

The plugin does not capture chat, authentication data, raw packet payloads, or traffic from players without an active session. This project is intended for controlled testing on a server you own and administer. It observes behavior; it does not implement an anti-cheat bypass or modify client packets.

## Related project

[Fabric Break Theory](https://github.com/1rvyn/fabric-break-theory) implements the mining-progress multiplier and post-break delay inferred from paired captures produced by this lab. Keep the server and client projects separate so packet collection remains independent from the behavior being tested.
