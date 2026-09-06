package com.insulinocytus.theyarebillions.perf;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public record PerfSettings(
        boolean enabled,
        String mode,
        String scenario,
        String loader,
        String side,
        Path outputDirectory,
        int warmupTicks,
        int durationTicks,
        int generateTicks
) {
    public static final int DEFAULT_WARMUP_TICKS = 20 * 20;
    public static final int DEFAULT_DURATION_TICKS = 10 * 60 * 20;
    public static final int DEFAULT_GENERATE_TICKS = 200;

    public static PerfSettings load() {
        Properties properties = new Properties();
        String request = System.getProperty("theyarebillions.perf.request");
        if (request != null && !request.isBlank()) {
            Path path = Path.of(request);
            if (Files.isRegularFile(path)) {
                try (InputStream input = Files.newInputStream(path)) {
                    properties.load(input);
                } catch (IOException ignored) {
                }
            }
        }
        String mode = property(properties, "mode", "");
        String scenario = property(properties, "scenario", "open-field");
        String loader = property(properties, "loader", "unknown");
        String side = property(properties, "side", "server");
        Path output = Path.of(property(properties, "output", "perf/results"));
        int warmup = integer(properties, "warmupTicks", DEFAULT_WARMUP_TICKS);
        int duration = integer(properties, "durationTicks", DEFAULT_DURATION_TICKS);
        int generate = integer(properties, "generateTicks", DEFAULT_GENERATE_TICKS);
        boolean enabled = Boolean.parseBoolean(property(properties, "enabled", "false"))
                || "generate".equals(mode)
                || "idle".equals(mode);
        return new PerfSettings(enabled, mode, scenario, loader, side, output, warmup, duration, generate);
    }

    public boolean isGenerate() {
        return "generate".equals(mode);
    }

    public boolean isIdle() {
        return "idle".equals(mode);
    }

    private static String property(Properties properties, String key, String defaultValue) {
        String override = System.getProperty("theyarebillions.perf." + key);
        if (override != null && !override.isBlank()) {
            return override;
        }
        return properties.getProperty(key, defaultValue);
    }

    private static int integer(Properties properties, String key, int defaultValue) {
        try {
            return Integer.parseInt(property(properties, key, Integer.toString(defaultValue)));
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }
}
