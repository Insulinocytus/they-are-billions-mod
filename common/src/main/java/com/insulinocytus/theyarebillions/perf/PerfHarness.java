package com.insulinocytus.theyarebillions.perf;

import com.insulinocytus.theyarebillions.TheyAreBillions;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.jfr.Environment;
import net.minecraft.util.profiling.jfr.JvmProfiler;
import net.minecraft.world.level.block.Blocks;

public final class PerfHarness {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private static PerfSettings settings;
    private static DurationSampler tickSampler;
    private static DurationSampler frameSampler;
    private static volatile boolean recording;
    private static volatile boolean started;
    private static volatile boolean stopping;
    private static int ticks;
    private static Path runOutput;
    private static Path jfrPath;

    private PerfHarness() {
    }

    public static void initialize() {
        settings = PerfSettings.load();
        if (!settings.enabled()) {
            return;
        }
        tickSampler = new DurationSampler();
        frameSampler = new DurationSampler();
        TheyAreBillions.LOGGER.info(
                "Performance harness enabled mode={} scenario={} loader={}",
                settings.mode(),
                settings.scenario(),
                settings.loader()
        );
    }

    public static void onTickStart(MinecraftServer server) {
        if (settings == null || !settings.enabled() || stopping) {
            return;
        }
        if (!started) {
            started = true;
            onServerStarted(server);
        }
        if (tickSampler != null) {
            tickSampler.begin(Util.getNanos());
        }
    }

    public static void onTickEnd(MinecraftServer server) {
        onServerTickPost(server);
    }

    public static void beginFrame() {
        if (!recording || frameSampler == null) {
            return;
        }
        frameSampler.begin(Util.getNanos());
    }

    public static void endFrame() {
        if (frameSampler == null) {
            return;
        }
        frameSampler.end(Util.getNanos());
    }

    private static void onServerStarted(MinecraftServer server) {
        runOutput = settings.outputDirectory()
                .resolve(settings.loader() + "-" + settings.scenario() + "-" + settings.mode() + "-"
                        + LocalDateTime.now().format(TIMESTAMP));
        try {
            Files.createDirectories(runOutput);
        } catch (IOException exception) {
            TheyAreBillions.LOGGER.warn("Could not create performance output directory {}", runOutput, exception);
        }
        prepareWorld(server);
        if (settings.isIdle()) {
            boolean jfrStarted = JvmProfiler.INSTANCE.start(
                    server.isDedicatedServer() ? Environment.SERVER : Environment.CLIENT);
            TheyAreBillions.LOGGER.info("Minecraft JFR recording {}", jfrStarted ? "started" : "unavailable");
        }
    }

    private static void onServerTickPost(MinecraftServer server) {
        if (tickSampler == null || stopping) {
            return;
        }
        long now = Util.getNanos();
        ticks++;
        if (settings.isGenerate()) {
            if (ticks >= settings.generateTicks()) {
                finish(server);
            }
            return;
        }
        if (!settings.isIdle()) {
            return;
        }
        if (ticks <= settings.warmupTicks()) {
            return;
        }
        recording = true;
        tickSampler.end(now);
        int measured = ticks - settings.warmupTicks();
        if (measured >= settings.durationTicks()) {
            finish(server);
        }
    }

    private static void prepareWorld(MinecraftServer server) {
        ServerLevel level = server.overworld();
        BlockPos spawn = new BlockPos(0, -60, 0);
        level.setDefaultSpawnPos(spawn, 0.0F);
        int radius = 8;
        int spawnChunkX = spawn.getX() >> 4;
        int spawnChunkZ = spawn.getZ() >> 4;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                level.getChunk(spawnChunkX + x, spawnChunkZ + z);
            }
        }
        server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack(),
                "function theyarebillions_perf:setup"
        );
        if (!level.getBlockState(new BlockPos(0, -61, 0)).is(Blocks.GOLD_BLOCK)) {
            TheyAreBillions.LOGGER.warn("Scenario marker gold block was not placed at 0,-61,0");
        }
    }

    private static void finish(MinecraftServer server) {
        stopping = true;
        recording = false;
        if (settings.isGenerate()) {
            server.saveEverything(false, true, true);
        }
        writeReports();
        Thread exit = new Thread(() -> {
            try {
                Thread.sleep(3000L);
            } catch (InterruptedException ignored) {
            }
            Runtime.getRuntime().halt(0);
        }, "theyarebillions-perf-exit");
        exit.setDaemon(true);
        exit.start();
        if (server.isDedicatedServer()) {
            server.halt(false);
        } else {
            stopClient();
        }
    }

    private static void writeReports() {
        if (settings == null || runOutput == null) {
            return;
        }
        if (settings.isIdle() && JvmProfiler.INSTANCE.isRunning()) {
            try {
                Path dumped = JvmProfiler.INSTANCE.stop();
                if (dumped != null) {
                    jfrPath = runOutput.resolve("recording.jfr");
                    Files.copy(dumped, jfrPath, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (RuntimeException | IOException exception) {
                TheyAreBillions.LOGGER.warn("Could not stop Minecraft JFR recording", exception);
            }
        }
        if (!settings.isIdle()) {
            return;
        }
        PerfSummary mspt = PerfSummary.of(tickSampler.snapshot());
        PerfSummary frames = PerfSummary.of(frameSampler.snapshot());
        try {
            PerfReports.write(runOutput, PerfReports.toJson(settings, mspt, frames, jfrPath));
        } catch (IOException exception) {
            TheyAreBillions.LOGGER.warn("Could not write performance metrics", exception);
        }
        TheyAreBillions.LOGGER.info(
                "Idle baseline {} / {}: average MSPT {} ms, P95 MSPT {} ms, samples {}",
                settings.loader(),
                settings.scenario(),
                String.format(java.util.Locale.ROOT, "%.3f", mspt.averageMs()),
                String.format(java.util.Locale.ROOT, "%.3f", mspt.p95Ms()),
                mspt.samples()
        );
        if (frames.samples() > 0) {
            TheyAreBillions.LOGGER.info(
                    "Client baseline: average FPS {}, P95 frame time {} ms, samples {}",
                    String.format(java.util.Locale.ROOT, "%.2f", frames.averageMs() > 0.0 ? 1000.0 / frames.averageMs() : 0.0),
                    String.format(java.util.Locale.ROOT, "%.3f", frames.p95Ms()),
                    frames.samples()
            );
        }
    }

    private static void stopClient() {
        try {
            Class<?> minecraft = Class.forName("net.minecraft.client.Minecraft");
            Object instance = minecraft.getMethod("getInstance").invoke(null);
            minecraft.getMethod("stop").invoke(instance);
        } catch (ReflectiveOperationException ignored) {
        }
    }
}
