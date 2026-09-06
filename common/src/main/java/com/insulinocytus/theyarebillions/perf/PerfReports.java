package com.insulinocytus.theyarebillions.perf;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

public final class PerfReports {
    private PerfReports() {
    }

    public static JsonObject toJson(
            PerfSettings settings,
            PerfSummary mspt,
            PerfSummary frames,
            Path jfrPath
    ) {
        JsonObject root = new JsonObject();
        root.addProperty("scenario", settings.scenario());
        root.addProperty("loader", settings.loader());
        root.addProperty("mode", settings.mode());
        root.addProperty("side", settings.side());
        root.addProperty("recordedAt", Instant.now().toString());
        root.addProperty("warmupTicks", settings.warmupTicks());
        root.addProperty("durationTicks", settings.durationTicks());
        root.add("hardware", hardware());
        root.add("server", summaryJson(mspt, "averageMsptMs", "p95MsptMs"));
        if (frames.samples() > 0) {
            JsonObject client = summaryJson(frames, "averageFrameTimeMs", "p95FrameTimeMs");
            if (frames.averageMs() > 0.0) {
                client.addProperty("averageFps", 1000.0 / frames.averageMs());
            }
            root.add("client", client);
        }
        if (jfrPath != null) {
            root.addProperty("jfr", jfrPath.toString());
        }
        return root;
    }

    public static void write(Path directory, JsonObject report) throws IOException {
        Files.createDirectories(directory);
        String json = new GsonBuilder().setPrettyPrinting().create().toJson(report);
        Files.writeString(directory.resolve("metrics.json"), json);
    }

    private static JsonObject summaryJson(PerfSummary summary, String averageKey, String p95Key) {
        JsonObject json = new JsonObject();
        json.addProperty("samples", summary.samples());
        json.addProperty(averageKey, summary.averageMs());
        json.addProperty("p50Ms", summary.p50Ms());
        json.addProperty(p95Key, summary.p95Ms());
        json.addProperty("p99Ms", summary.p99Ms());
        json.addProperty("maxMs", summary.maxMs());
        return json;
    }

    private static JsonObject hardware() {
        JsonObject json = new JsonObject();
        json.addProperty("osName", System.getProperty("os.name"));
        json.addProperty("osArch", System.getProperty("os.arch"));
        json.addProperty("javaVersion", System.getProperty("java.version"));
        json.addProperty("availableProcessors", Runtime.getRuntime().availableProcessors());
        json.addProperty("maxMemoryBytes", Runtime.getRuntime().maxMemory());
        return json;
    }
}
