package dev.johns.breaktelemetry.capture;

import java.util.Map;
import java.util.UUID;

public record CaptureRecord(
        String recordType,
        UUID sessionId,
        long sequence,
        long epochMillis,
        long monotonicNanos,
        long serverTick,
        UUID playerId,
        String playerName,
        String event,
        Map<String, Object> data
) {
}
