package dev.johns.breaktelemetry.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.johns.breaktelemetry.capture.CaptureAnalyzer;
import dev.johns.breaktelemetry.capture.CaptureRecord;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CompareMain {
    private CompareMain() { }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println("Usage: compareCaptures --args='<baseline.jsonl> <modded.jsonl>'");
            System.exit(2);
        }
        ObjectMapper mapper = new ObjectMapper();
        CaptureStats baseline = load(Path.of(args[0]), mapper);
        CaptureStats modded = load(Path.of(args[1]), mapper);
        printComparison(baseline, modded);
    }

    static CaptureStats load(Path path, ObjectMapper mapper) throws IOException {
        List<CaptureRecord> records;
        try (var lines = Files.lines(path)) {
            records = lines.filter(line -> !line.isBlank()).map(line -> {
                try { return mapper.readValue(line, CaptureRecord.class); }
                catch (IOException exception) { throw new InvalidCaptureException(exception); }
            }).toList();
        } catch (InvalidCaptureException exception) {
            throw exception.ioException;
        }
        Map<String, Long> counts = new LinkedHashMap<>();
        records.forEach(record -> counts.merge(record.event(), 1L, Long::sum));
        return new CaptureStats(path, records, counts, CaptureAnalyzer.findings(records),
                breakSamples(records), postBreakSamples(records), tickIntervals(records), roundTripSamples(records));
    }

    static void printComparison(CaptureStats baseline, CaptureStats modded) {
        System.out.printf("%-28s %10s %10s %10s%n", "metric", "baseline", "modded", "delta");
        System.out.println("-".repeat(62));
        var events = new java.util.TreeSet<String>();
        events.addAll(baseline.counts().keySet()); events.addAll(modded.counts().keySet());
        for (String event : events) {
            long left = baseline.counts().getOrDefault(event, 0L);
            long right = modded.counts().getOrDefault(event, 0L);
            System.out.printf("%-28s %10d %10d %+10d%n", event, left, right, right - left);
        }
        printCount("completed_breaks", baseline.breakSamples().size(), modded.breakSamples().size());
        printMetric("start_to_finish_ms", values(baseline, BreakSample::durationNanos, 1_000_000.0),
                values(modded, BreakSample::durationNanos, 1_000_000.0));
        printMetric("arm_packets_per_break", values(baseline, BreakSample::armPackets, 1.0),
                values(modded, BreakSample::armPackets, 1.0));
        printMetric("movement_packets_per_break", values(baseline, BreakSample::movementPackets, 1.0),
                values(modded, BreakSample::movementPackets, 1.0));
        printMetric("client_tick_end_per_break", values(baseline, BreakSample::clientTickEnds, 1.0),
                values(modded, BreakSample::clientTickEnds, 1.0));
        printMetric("server_tick_span", values(baseline, BreakSample::serverTickSpan, 1.0),
                values(modded, BreakSample::serverTickSpan, 1.0));
        printMetric("dig_sequence_delta", values(baseline, BreakSample::sequenceDelta, 1.0),
                values(modded, BreakSample::sequenceDelta, 1.0));
        printMetric("finish_to_next_start_ms", scaled(baseline.postBreakNanos(), 1_000_000.0),
                scaled(modded.postBreakNanos(), 1_000_000.0));
        printMetric("client_tick_interval_ms", scaled(baseline.tickIntervalNanos(), 1_000_000.0),
                scaled(modded.tickIntervalNanos(), 1_000_000.0));
        printMetric("keepalive_rtt_ms", scaled(baseline.roundTripNanos(), 1_000_000.0),
                scaled(modded.roundTripNanos(), 1_000_000.0));
        printRange("baseline duration ms", values(baseline, BreakSample::durationNanos, 1_000_000.0));
        printRange("modded duration ms", values(modded, BreakSample::durationNanos, 1_000_000.0));

        System.out.println("\nBaseline findings:");
        baseline.findings().forEach(value -> System.out.println("- " + value));
        if (baseline.findings().isEmpty()) System.out.println("- none");
        System.out.println("Modded findings:");
        modded.findings().forEach(value -> System.out.println("- " + value));
        if (modded.findings().isEmpty()) System.out.println("- none");
    }

    static List<BreakSample> breakSamples(List<CaptureRecord> records) {
        Map<String, ActiveBreak> active = new LinkedHashMap<>();
        List<BreakSample> samples = new ArrayList<>();
        records.stream().sorted(java.util.Comparator.comparingLong(CaptureRecord::monotonicNanos)).forEach(record -> {
            String target = String.valueOf(record.data().getOrDefault("target", "unknown"));
            if (record.event().equals("DIG_START")) {
                active.put(target, new ActiveBreak(record));
                return;
            }
            if (record.event().equals("DIG_ABORT")) {
                active.remove(target);
                return;
            }
            if (record.event().equals("ARM_ANIMATION")) active.values().forEach(value -> value.armPackets++);
            if (record.event().equals("MOVEMENT_PACKET")) active.values().forEach(value -> value.movementPackets++);
            if (record.event().equals("CLIENT_TICK_END")) active.values().forEach(value -> value.clientTickEnds++);
            if (record.event().equals("DIG_FINISH")) {
                ActiveBreak started = active.remove(target);
                if (started == null) return;
                long startSequence = number(started.start.data().get("sequenceId"), -1);
                long finishSequence = number(record.data().get("sequenceId"), -1);
                long sequenceDelta = startSequence >= 0 && finishSequence >= 0 ? finishSequence - startSequence : -1;
                samples.add(new BreakSample(target, started.start.monotonicNanos(), record.monotonicNanos(),
                        record.monotonicNanos() - started.start.monotonicNanos(),
                        record.serverTick() - started.start.serverTick(), started.armPackets,
                        started.movementPackets, started.clientTickEnds, startSequence, finishSequence, sequenceDelta));
            }
        });
        return samples;
    }

    static List<Long> postBreakSamples(List<CaptureRecord> records) {
        List<Long> samples = new ArrayList<>();
        long[] lastFinish = {-1};
        records.stream().sorted(java.util.Comparator.comparingLong(CaptureRecord::monotonicNanos)).forEach(record -> {
            if (record.event().equals("DIG_FINISH")) {
                lastFinish[0] = record.monotonicNanos();
            } else if (record.event().equals("DIG_START") && lastFinish[0] >= 0) {
                samples.add(record.monotonicNanos() - lastFinish[0]);
                lastFinish[0] = -1;
            }
        });
        return samples;
    }

    static List<Long> tickIntervals(List<CaptureRecord> records) {
        List<Long> samples = new ArrayList<>();
        long[] previous = {-1};
        records.stream().filter(record -> record.event().equals("CLIENT_TICK_END"))
                .sorted(java.util.Comparator.comparingLong(CaptureRecord::monotonicNanos)).forEach(record -> {
                    if (previous[0] >= 0) samples.add(record.monotonicNanos() - previous[0]);
                    previous[0] = record.monotonicNanos();
                });
        return samples;
    }

    private static List<Long> roundTripSamples(List<CaptureRecord> records) {
        Map<Long, Long> keepAlives = new LinkedHashMap<>();
        Map<Long, Long> pings = new LinkedHashMap<>();
        List<Long> samples = new ArrayList<>();
        records.stream().sorted(java.util.Comparator.comparingLong(CaptureRecord::monotonicNanos)).forEach(record -> {
            long id = number(record.data().get("id"), Long.MIN_VALUE);
            if (id == Long.MIN_VALUE) return;
            if (record.event().equals("SERVER_KEEP_ALIVE")) keepAlives.put(id, record.monotonicNanos());
            if (record.event().equals("SERVER_PING")) pings.put(id, record.monotonicNanos());
            if (record.event().equals("CLIENT_KEEP_ALIVE")) addRoundTrip(samples, keepAlives.remove(id), record.monotonicNanos());
            if (record.event().equals("CLIENT_PONG")) addRoundTrip(samples, pings.remove(id), record.monotonicNanos());
        });
        return samples;
    }

    private static void addRoundTrip(List<Long> samples, Long startedAt, long finishedAt) {
        if (startedAt != null && finishedAt >= startedAt) samples.add(finishedAt - startedAt);
    }

    private static long number(Object value, long fallback) {
        return value instanceof Number number ? number.longValue() : fallback;
    }

    private static List<Double> values(CaptureStats stats, LongMetric metric, double divisor) {
        return stats.breakSamples().stream().map(sample -> metric.value(sample) / divisor).toList();
    }

    private static List<Double> scaled(List<Long> values, double divisor) {
        return values.stream().map(value -> value / divisor).toList();
    }

    private static void printCount(String metric, int baseline, int modded) {
        System.out.printf("%-28s %10d %10d %+10d%n", metric, baseline, modded, modded - baseline);
    }

    private static void printMetric(String metric, List<Double> baseline, List<Double> modded) {
        double left = median(baseline);
        double right = median(modded);
        System.out.printf("%-28s %10.2f %10.2f %+10.2f%n", metric, left, right, right - left);
    }

    private static void printRange(String label, List<Double> values) {
        if (values.isEmpty()) return;
        var sorted = values.stream().sorted().toList();
        System.out.printf("%s: min=%.2f median=%.2f max=%.2f samples=%d%n",
                label, sorted.getFirst(), median(sorted), sorted.getLast(), sorted.size());
    }

    private static double median(List<Double> values) {
        if (values.isEmpty()) return Double.NaN;
        List<Double> sorted = values.stream().sorted().toList();
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 0
                ? (sorted.get(middle - 1) + sorted.get(middle)) / 2.0 : sorted.get(middle);
    }

    record CaptureStats(Path path, List<CaptureRecord> records, Map<String, Long> counts,
                        List<String> findings, List<BreakSample> breakSamples, List<Long> postBreakNanos,
                        List<Long> tickIntervalNanos, List<Long> roundTripNanos) { }

    record BreakSample(String target, long startNanos, long finishNanos, long durationNanos,
                       long serverTickSpan, long armPackets, long movementPackets, long clientTickEnds,
                       long startSequence, long finishSequence, long sequenceDelta) { }

    @FunctionalInterface
    private interface LongMetric {
        long value(BreakSample sample);
    }

    private static final class ActiveBreak {
        private final CaptureRecord start;
        private long armPackets;
        private long movementPackets;
        private long clientTickEnds;

        private ActiveBreak(CaptureRecord start) {
            this.start = start;
        }
    }

    private static final class InvalidCaptureException extends RuntimeException {
        private final IOException ioException;
        private InvalidCaptureException(IOException ioException) { this.ioException = ioException; }
    }
}
