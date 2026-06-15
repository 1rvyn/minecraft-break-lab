package dev.johns.breaktelemetry.capture;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptureAnalyzerTest {
    private final UUID session = UUID.randomUUID();
    private final UUID player = UUID.randomUUID();

    @Test
    void normalStartAndFinishHasNoFinding() {
        assertTrue(CaptureAnalyzer.findings(List.of(
                record("DIG_START", 0, "1,2,3"), record("DIG_FINISH", 10_000_000, "1,2,3")
        )).isEmpty());
    }

    @Test
    void finishWithoutStartIsReported() {
        List<String> findings = CaptureAnalyzer.findings(List.of(record("DIG_FINISH", 0, "1,2,3")));
        assertEquals(List.of("Finish without a matching start for 1,2,3"), findings);
    }

    @Test
    void rapidActionsAndUnclosedStartsAreReported() {
        List<String> findings = CaptureAnalyzer.findings(List.of(
                record("DIG_START", 0, "1,2,3"), record("DIG_START", 1_000_000, "2,2,3")
        ));
        assertTrue(findings.contains("1 digging action gap(s) below 5 ms"));
        assertTrue(findings.contains("2 started target(s) had no finish or abort"));
    }

    private CaptureRecord record(String event, long nanos, String target) {
        return new CaptureRecord("event", session, 1, 0, nanos, 0, player, "tester", event, Map.of("target", target));
    }
}
