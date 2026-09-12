package com.insulinocytus.theyarebillions.horde;

import com.insulinocytus.theyarebillions.TheyAreBillions;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.event.events.common.LifecycleEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.LevelResource;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;

public final class HordeAdmin {
    private static final LevelResource SERVER_CONFIG = new LevelResource("serverconfig");
    private static final String CONFIG_FILE = TheyAreBillions.MOD_ID + ".properties";
    private static volatile LogLevel logLevel = LogLevel.INFO;
    private static Path configPath;

    private HordeAdmin() {
    }

    public static void initialize() {
        CommandRegistrationEvent.EVENT.register((dispatcher, registry, selection) -> register(dispatcher));
        LifecycleEvent.SERVER_BEFORE_START.register(HordeAdmin::onServerStarting);
    }

    static void onServerStarting(MinecraftServer server) {
        configPath = configFile(server);
        apply(loadLogLevel(configPath));
    }

    static Path configFile(MinecraftServer server) {
        return server.getWorldPath(SERVER_CONFIG).resolve(CONFIG_FILE);
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root =
                Commands.literal(TheyAreBillions.MOD_ID).requires(source -> source.hasPermission(2));
        root.then(Commands.literal("status").executes(context -> status(context.getSource())));
        LiteralArgumentBuilder<CommandSourceStack> log = Commands.literal("log");
        for (LogLevel level : LogLevel.values()) {
            log.then(Commands.literal(level.name()).executes(context -> setLogLevel(context.getSource(), level)));
        }
        dispatcher.register(root.then(log));
    }

    static void apply(LogLevel level) {
        logLevel = level;
        Configurator.setLevel(TheyAreBillions.LOGGER.getName(), level.log4j());
    }


    static boolean allows(LogLevel required) {
        return logLevel.allows(required);
    }

    static Status collect(MinecraftServer server) {
        int members = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (HordeIdentity.isHordeMember(entity)
                        && level.isPositionEntityTicking(entity.blockPosition())) {
                    members++;
                }
            }
        }
        int groups = 0;
        ServerLevel overworld = server.overworld();
        if (overworld != null) {
            groups = HordePlanner.connectedGroupCount(HordeSpawner.validPlayers(overworld));
        }
        return new Status(
                members,
                HordeSpawner.countOrdinaryZombies(server),
                groups,
                HordeChunkTickets.activeChunkCount(),
                HordeNavigation.sharedRouteCount(),
                HordeBlockBreaking.activeSiteCount(),
                HordePerformance.currentTier(server),
                logLevel);
    }

    static String formatStatus(Status status) {
        return "hordeMembers="
                + status.hordeMembers()
                + " ordinaryZombies="
                + status.ordinaryZombies()
                + " playerGroups="
                + status.playerGroups()
                + " ticketChunks="
                + status.ticketChunks()
                + " sharedRoutes="
                + status.sharedRoutes()
                + " diggingSites="
                + status.diggingSites()
                + " performanceTier="
                + status.performanceTier().name()
                + " logLevel="
                + status.logLevel().name();
    }

    static LogLevel loadLogLevel(Path config) {
        try {
            if (!Files.isRegularFile(config)) {
                if (!Files.exists(config)) {
                    writeLogLevel(config, LogLevel.INFO);
                }
                return LogLevel.INFO;
            }
            String raw = null;
            for (String line : Files.readAllLines(config, StandardCharsets.UTF_8)) {
                if (line.startsWith("logLevel=")) {
                    raw = line.substring("logLevel=".length()).trim();
                    break;
                }
            }
            LogLevel level = LogLevel.parse(raw);
            if (!level.name().equals(raw)) {
                writeLogLevel(config, level);
            }
            return level;
        } catch (IOException ignored) {
            return LogLevel.INFO;
        }
    }

    static void writeLogLevel(Path config, LogLevel level) throws IOException {
        Path parent = config.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(config, "logLevel=" + level.name() + "\n", StandardCharsets.UTF_8);
    }

    private static int status(CommandSourceStack source) {
        String message = formatStatus(collect(source.getServer()));
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int setLogLevel(CommandSourceStack source, LogLevel level) {
        if (configPath == null) {
            source.sendFailure(Component.literal("logLevel config is not available"));
            return 0;
        }
        try {
            writeLogLevel(configPath, level);
        } catch (IOException exception) {
            source.sendFailure(Component.literal("Could not persist logLevel"));
            return 0;
        }
        apply(level);
        source.sendSuccess(() -> Component.literal("logLevel=" + level.name()), false);
        return 1;
    }

    enum LogLevel {
        DEBUG(Level.DEBUG),
        INFO(Level.INFO),
        WARN(Level.WARN),
        ERROR(Level.ERROR);

        private final Level log4j;

        LogLevel(Level log4j) {
            this.log4j = log4j;
        }

        Level log4j() {
            return log4j;
        }

        boolean allows(LogLevel required) {
            return ordinal() <= required.ordinal();
        }

        static LogLevel parse(String raw) {
            if (raw == null) {
                return INFO;
            }
            try {
                return valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return INFO;
            }
        }
    }

    record Status(
            int hordeMembers,
            int ordinaryZombies,
            int playerGroups,
            int ticketChunks,
            int sharedRoutes,
            int diggingSites,
            HordePerformance.Tier performanceTier,
            LogLevel logLevel) {}
}
