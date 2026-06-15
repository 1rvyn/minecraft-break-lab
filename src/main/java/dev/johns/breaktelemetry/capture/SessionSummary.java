package dev.johns.breaktelemetry.capture;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record SessionSummary(
        UUID sessionId,
        String label,
        String player,
        long startedAt,
        long stoppedAt,
        long records,
        Map<String, Long> eventCounts,
        List<String> findings
) {
    public static SessionSummary from(List<CaptureRecord> records, String label, String player,
                                      UUID sessionId, long startedAt, long stoppedAt) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (CaptureRecord record : records) {
            counts.merge(record.event(), 1L, Long::sum);
        }
        return new SessionSummary(sessionId, label, player, startedAt, stoppedAt,
                records.size(), counts, CaptureAnalyzer.findings(records));
    }
}
