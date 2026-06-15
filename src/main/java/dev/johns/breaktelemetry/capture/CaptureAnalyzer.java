package dev.johns.breaktelemetry.capture;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class CaptureAnalyzer {
    private CaptureAnalyzer() {
    }

    public static List<String> findings(List<CaptureRecord> records) {
        List<String> findings = new ArrayList<>();
        Set<String> activeTargets = new HashSet<>();
        long lastDigNanos = -1;
        int rapidActions = 0;

        for (CaptureRecord record : records.stream().sorted(java.util.Comparator.comparingLong(CaptureRecord::monotonicNanos)).toList()) {
            if (!record.event().startsWith("DIG_")) {
                continue;
            }
            String target = String.valueOf(record.data().getOrDefault("target", "unknown"));
            if (lastDigNanos >= 0 && record.monotonicNanos() - lastDigNanos < 5_000_000L) {
                rapidActions++;
            }
            lastDigNanos = record.monotonicNanos();

            if (record.event().equals("DIG_START")) {
                activeTargets.add(target);
            } else if (record.event().equals("DIG_FINISH") && !activeTargets.remove(target)) {
                findings.add("Finish without a matching start for " + target);
            } else if (record.event().equals("DIG_ABORT")) {
                activeTargets.remove(target);
            }
        }
        if (rapidActions > 0) {
            findings.add(rapidActions + " digging action gap(s) below 5 ms");
        }
        if (!activeTargets.isEmpty()) {
            findings.add(activeTargets.size() + " started target(s) had no finish or abort");
        }
        return findings;
    }
}
