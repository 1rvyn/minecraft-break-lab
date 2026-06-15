package dev.johns.breaktelemetry.capture;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptureSessionTest {
    @TempDir Path tempDir;

    @Test
    void writesMultipleJsonLinesAndSummaries() throws Exception {
        CaptureSession session = new CaptureSession(tempDir, "baseline", UUID.randomUUID(), "tester", new ObjectMapper());
        session.append("DIG_START", 10, Map.of("target", "1,2,3"));
        session.append("DIG_FINISH", 16, Map.of("target", "1,2,3"));
        Path jsonl = session.jsonlPath();
        session.close();

        assertEquals(3, Files.readAllLines(jsonl).size());
        assertTrue(Files.exists(Path.of(jsonl.toString().replace(".jsonl", "-summary.json"))));
        assertTrue(Files.exists(Path.of(jsonl.toString().replace(".jsonl", "-summary.csv"))));
    }
}
