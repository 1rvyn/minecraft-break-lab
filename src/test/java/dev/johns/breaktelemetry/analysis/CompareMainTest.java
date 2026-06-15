package dev.johns.breaktelemetry.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.johns.breaktelemetry.capture.CaptureRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompareMainTest {
    @TempDir Path tempDir;

    @Test
    void loadsJsonLinesAndCountsEvents() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        UUID session = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        CaptureRecord start = new CaptureRecord("event", session, 1, 1, 0, 1, player, "tester", "DIG_START", Map.of("target", "1,2,3"));
        CaptureRecord finish = new CaptureRecord("event", session, 2, 2, 10_000_000, 2, player, "tester", "DIG_FINISH", Map.of("target", "1,2,3"));
        Path path = tempDir.resolve("capture.jsonl");
        Files.writeString(path, mapper.writeValueAsString(start) + "\n" + mapper.writeValueAsString(finish) + "\n");

        CompareMain.CaptureStats stats = CompareMain.load(path, mapper);
        assertEquals(1L, stats.counts().get("DIG_START"));
        assertEquals(1L, stats.counts().get("DIG_FINISH"));
    }

    @Test
    void measuresPacketsAndSequenceAcrossACompletedBreak() {
        UUID session = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        var records = java.util.List.of(
                record(session, player, 1, 0, 10, "DIG_START", Map.of("target", "1,2,3", "sequenceId", 40)),
                record(session, player, 2, 10_000_000, 10, "ARM_ANIMATION", Map.of()),
                record(session, player, 3, 20_000_000, 11, "MOVEMENT_PACKET", Map.of()),
                record(session, player, 4, 30_000_000, 11, "CLIENT_TICK_END", Map.of()),
                record(session, player, 5, 55_000_000, 12, "DIG_FINISH", Map.of("target", "1,2,3", "sequenceId", 41))
        );

        CompareMain.BreakSample sample = CompareMain.breakSamples(records).getFirst();
        assertEquals(55_000_000, sample.durationNanos());
        assertEquals(2, sample.serverTickSpan());
        assertEquals(1, sample.armPackets());
        assertEquals(1, sample.movementPackets());
        assertEquals(1, sample.clientTickEnds());
        assertEquals(1, sample.sequenceDelta());
    }

    @Test
    void measuresPostBreakDelayAndClientTickCadence() {
        UUID session = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        var records = java.util.List.of(
                record(session, player, 1, 10_000_000, 1, "CLIENT_TICK_END", Map.of()),
                record(session, player, 2, 60_000_000, 2, "DIG_FINISH", Map.of("target", "1,2,3")),
                record(session, player, 3, 60_000_000, 2, "CLIENT_TICK_END", Map.of()),
                record(session, player, 4, 110_000_000, 3, "CLIENT_TICK_END", Map.of()),
                record(session, player, 5, 310_000_000, 7, "DIG_START", Map.of("target", "2,2,3"))
        );

        assertEquals(java.util.List.of(250_000_000L), CompareMain.postBreakSamples(records));
        assertEquals(java.util.List.of(50_000_000L, 50_000_000L), CompareMain.tickIntervals(records));
    }

    private CaptureRecord record(UUID session, UUID player, long sequence, long nanos, long tick,
                                 String event, Map<String, Object> data) {
        return new CaptureRecord("event", session, sequence, 0, nanos, tick, player, "tester", event, data);
    }
}
