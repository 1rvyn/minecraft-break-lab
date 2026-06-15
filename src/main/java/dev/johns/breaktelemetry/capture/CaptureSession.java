package dev.johns.breaktelemetry.capture;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.bukkit.entity.Player;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public final class CaptureSession implements AutoCloseable {
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter
            .ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private final UUID id = UUID.randomUUID();
    private final String label;
    private final UUID playerId;
    private final String playerName;
    private final long startedAt = System.currentTimeMillis();
    private final long startedNanos = System.nanoTime();
    private final AtomicLong sequence = new AtomicLong();
    private final List<CaptureRecord> records = new ArrayList<>();
    private final ObjectMapper mapper;
    private final BufferedWriter writer;
    private final Path jsonlPath;
    private final Path summaryPath;
    private boolean closed;

    public CaptureSession(Path directory, String label, Player player, ObjectMapper mapper) throws IOException {
        this(directory, label, player.getUniqueId(), player.getName(), mapper);
    }

    CaptureSession(Path directory, String label, UUID playerId, String playerName, ObjectMapper mapper) throws IOException {
        this.label = label;
        this.playerId = playerId;
        this.playerName = playerName;
        this.mapper = mapper;
        Files.createDirectories(directory);
        String stem = FILE_TIME.format(Instant.now()) + "-" + safe(label) + "-" + safe(playerName)
                + "-" + id.toString().substring(0, 8);
        this.jsonlPath = directory.resolve(stem + ".jsonl");
        this.summaryPath = directory.resolve(stem + "-summary.json");
        this.writer = Files.newBufferedWriter(jsonlPath);
        append("SESSION_START", -1, Map.of("label", label));
    }

    public synchronized void append(String event, long tick, Map<String, Object> data) throws IOException {
        appendObserved(event, tick, data, System.currentTimeMillis(), System.nanoTime());
    }

    public synchronized void appendObserved(String event, long tick, Map<String, Object> data,
                                            long observedEpochMillis, long observedNanos) throws IOException {
        if (closed) return;
        CaptureRecord record = new CaptureRecord("event", id, sequence.incrementAndGet(),
                observedEpochMillis, observedNanos - startedNanos, tick,
                playerId, playerName, event, data);
        writer.write(mapper.writeValueAsString(record));
        writer.newLine();
        writer.flush();
        records.add(record);
    }

    public UUID id() { return id; }
    public String label() { return label; }
    public String playerName() { return playerName; }
    public UUID playerId() { return playerId; }
    public long recordCount() { return records.size(); }
    public Path jsonlPath() { return jsonlPath; }

    @Override
    public synchronized void close() throws IOException {
        if (closed) return;
        long stoppedAt = System.currentTimeMillis();
        closed = true;
        writer.close();
        SessionSummary summary = SessionSummary.from(List.copyOf(records), label, playerName,
                id, startedAt, stoppedAt);
        mapper.writerWithDefaultPrettyPrinter().writeValue(summaryPath.toFile(), summary);
        writeCsv(summaryPath.resolveSibling(summaryPath.getFileName().toString().replace("-summary.json", "-summary.csv")), summary);
    }

    private static void writeCsv(Path path, SessionSummary summary) throws IOException {
        try (BufferedWriter csv = Files.newBufferedWriter(path)) {
            csv.write("metric,value\n");
            csv.write("session_id," + summary.sessionId() + "\n");
            csv.write("label," + safe(summary.label()) + "\n");
            csv.write("player," + safe(summary.player()) + "\n");
            csv.write("duration_ms," + (summary.stoppedAt() - summary.startedAt()) + "\n");
            csv.write("records," + summary.records() + "\n");
            summary.eventCounts().forEach((event, count) -> {
                try { csv.write("event_" + safe(event) + "," + count + "\n"); }
                catch (IOException exception) { throw new CsvWriteException(exception); }
            });
        } catch (CsvWriteException exception) {
            throw exception.ioException;
        }
    }

    private static String safe(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static final class CsvWriteException extends RuntimeException {
        private final IOException ioException;
        private CsvWriteException(IOException ioException) { this.ioException = ioException; }
    }
}
