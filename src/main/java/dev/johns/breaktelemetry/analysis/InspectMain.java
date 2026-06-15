package dev.johns.breaktelemetry.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.johns.breaktelemetry.capture.CaptureRecord;

import java.nio.file.Path;
import java.util.Set;

public final class InspectMain {
    private static final Set<String> RELEVANT = Set.of(
            "SESSION_START", "MARK", "DIG_START", "DIG_ABORT", "DIG_FINISH",
            "BUKKIT_DAMAGE", "BUKKIT_DAMAGE_ABORT", "BUKKIT_BREAK", "HELD_SLOT_PACKET", "BUKKIT_HELD_SLOT",
            "SERVER_ACK_BLOCK_CHANGES", "SERVER_ACK_DIGGING", "SERVER_BLOCK_CHANGE", "SERVER_BREAK_ANIMATION",
            "CLIENT_KEEP_ALIVE", "SERVER_KEEP_ALIVE", "CLIENT_PONG", "SERVER_PING", "USE_BLOCK", "USE_ITEM"
    );

    private InspectMain() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: inspectCapture --args='<capture.jsonl>'");
            System.exit(2);
        }
        var stats = CompareMain.load(Path.of(args[0]), new ObjectMapper());
        long[] previous = {-1};
        System.out.printf("%-12s %-10s %-22s %-18s %-18s %s%n",
                "time_ms", "delta_ms", "event", "target", "block", "tool/extra");
        stats.records().stream()
                .filter(record -> RELEVANT.contains(record.event()))
                .sorted(java.util.Comparator.comparingLong(CaptureRecord::monotonicNanos))
                .forEach(record -> {
                    double time = record.monotonicNanos() / 1_000_000.0;
                    double delta = previous[0] < 0 ? 0 : (record.monotonicNanos() - previous[0]) / 1_000_000.0;
                    previous[0] = record.monotonicNanos();
                    System.out.printf("%12.3f %10.3f %-22s %-18s %-18s %s%n", time, delta, record.event(),
                            record.data().getOrDefault("target", "-"), record.data().getOrDefault("blockType", "-"),
                            details(record));
                });
        if (!stats.breakSamples().isEmpty()) {
            System.out.println("\nCompleted breaks:");
            System.out.printf("%-18s %10s %10s %10s %10s %12s%n",
                    "target", "duration", "arms", "moves", "ticks", "dig_seq");
            stats.breakSamples().forEach(sample -> System.out.printf("%-18s %10.3f %10d %10d %10d %5d -> %-5d%n",
                    sample.target(), sample.durationNanos() / 1_000_000.0, sample.armPackets(),
                    sample.movementPackets(), sample.serverTickSpan(), sample.startSequence(), sample.finishSequence()));
        }
        if (!stats.findings().isEmpty()) {
            System.out.println("\nFindings:");
            stats.findings().forEach(value -> System.out.println("- " + value));
        }
    }

    private static Object details(CaptureRecord record) {
        StringBuilder value = new StringBuilder(String.valueOf(
                record.data().getOrDefault("tool", record.data().getOrDefault("text", "-"))));
        if (record.data().containsKey("sequenceId")) value.append(" seq=").append(record.data().get("sequenceId"));
        if (record.data().containsKey("destroyStage")) value.append(" stage=").append(record.data().get("destroyStage"));
        if (record.data().containsKey("id")) value.append(" id=").append(record.data().get("id"));
        value.append(" tick=").append(record.serverTick());
        return value;
    }
}
