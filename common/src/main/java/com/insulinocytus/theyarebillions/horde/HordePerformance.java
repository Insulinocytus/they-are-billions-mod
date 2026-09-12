package com.insulinocytus.theyarebillions.horde;

import java.util.Arrays;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;

public final class HordePerformance {
    static final int WINDOW_TICKS = 200;
    static final long LOW_THRESHOLD_NANOS = 40_000_000L;
    static final long HIGH_THRESHOLD_NANOS = 45_000_000L;

    private static final Map<MinecraftServer, ServerState> SERVERS = new WeakHashMap<>();

    private HordePerformance() {
    }

    public static void onTickStart(MinecraftServer server) {
        state(server).tickStartNanos = Util.getNanos();
    }

    public static void onTickEnd(MinecraftServer server) {
        ServerState state = state(server);
        if (state.tickStartNanos == Long.MIN_VALUE) {
            return;
        }
        state.tracker.sample(Util.getNanos() - state.tickStartNanos);
        state.tickStartNanos = Long.MIN_VALUE;
        HordeNavigation.setActiveSiteLimit(state.tracker.tier().diggingLimit());
    }

    static int spawnLimit(MinecraftServer server) {
        return state(server).tracker.tier().spawnLimit();
    }

    static int simulationIntervalMultiplier(MinecraftServer server) {
        return state(server).tracker.tier().simulationIntervalMultiplier();
    }

    private static ServerState state(MinecraftServer server) {
        return SERVERS.computeIfAbsent(server, ignored -> new ServerState());
    }

    enum Tier {
        NORMAL(1, 64, 4),
        REDUCED(2, 32, 2),
        MINIMUM(4, 8, 1),
        PAUSED(8, 8, 0);

        private static final Tier[] VALUES = values();

        private final int simulationIntervalMultiplier;
        private final int diggingLimit;
        private final int spawnLimit;

        Tier(int simulationIntervalMultiplier, int diggingLimit, int spawnLimit) {
            this.simulationIntervalMultiplier = simulationIntervalMultiplier;
            this.diggingLimit = diggingLimit;
            this.spawnLimit = spawnLimit;
        }

        int simulationIntervalMultiplier() {
            return simulationIntervalMultiplier;
        }

        int diggingLimit() {
            return diggingLimit;
        }

        int spawnLimit() {
            return spawnLimit;
        }

        Tier degrade() {
            return VALUES[Math.min(ordinal() + 1, VALUES.length - 1)];
        }

        Tier recover() {
            return VALUES[Math.max(ordinal() - 1, 0)];
        }
    }

    static final class Tracker {
        private final long[] recent = new long[WINDOW_TICKS];
        private final long[] sorted = new long[WINDOW_TICKS];
        private Tier tier;
        private int samples;
        private int next;
        private int lowTicks;
        private int transitionCooldown;

        Tracker() {
            this(Tier.NORMAL);
        }

        Tracker(Tier tier) {
            this.tier = tier;
        }

        void sample(long durationNanos) {
            durationNanos = Math.max(0L, durationNanos);
            recent[next] = durationNanos;
            next = (next + 1) % WINDOW_TICKS;
            samples = Math.min(samples + 1, WINDOW_TICKS);
            lowTicks = durationNanos < LOW_THRESHOLD_NANOS ? Math.min(lowTicks + 1, WINDOW_TICKS) : 0;
            if (transitionCooldown > 0) {
                transitionCooldown--;
            }
            if (samples < WINDOW_TICKS || transitionCooldown > 0) {
                return;
            }
            if (lowTicks == WINDOW_TICKS && tier != Tier.NORMAL) {
                transition(tier.recover());
                lowTicks = 0;
                return;
            }
            if (tier == Tier.PAUSED || lowTicks == WINDOW_TICKS) {
                return;
            }
            System.arraycopy(recent, 0, sorted, 0, WINDOW_TICKS);
            Arrays.sort(sorted);
            if (sorted[(int) Math.ceil(0.95 * WINDOW_TICKS) - 1] > HIGH_THRESHOLD_NANOS) {
                transition(tier.degrade());
            }
        }

        Tier tier() {
            return tier;
        }

        private void transition(Tier nextTier) {
            if (nextTier != tier) {
                tier = nextTier;
                transitionCooldown = WINDOW_TICKS;
            }
        }
    }

    private static final class ServerState {
        private final Tracker tracker = new Tracker();
        private long tickStartNanos = Long.MIN_VALUE;
    }
}
