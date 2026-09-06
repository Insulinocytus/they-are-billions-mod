package com.insulinocytus.theyarebillions.horde;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class HordePlanner {
    public static final int NIGHT_START = 13_000;
    public static final int RAMP_END = 18_000;
    public static final int NIGHT_END = 22_999;
    public static final int RAMP_DURATION = RAMP_END - NIGHT_START;
    public static final int MAX_SUCCESSFUL_SPAWNS_PER_TICK = 4;
    public static final int MAX_FAILED_POSITION_PICKS = 16;
    public static final int MIN_SPAWN_DISTANCE = 128;
    public static final int MAX_SPAWN_DISTANCE = 144;
    public static final int DEFAULT_HORDE_TARGET = 1000;
    public static final int MAX_HORDE_TARGET = 1000;
    public static final String HORDE_TARGET_RULE = "theyAreBillionsHordeTarget";

    private static final double MAX_ANGLE_JITTER = Math.atan2(8.0, 136.0);

    private HordePlanner() {
    }

    public static int clampTarget(int hordeTarget) {
        if (hordeTarget < 0) {
            return 0;
        }
        return Math.min(hordeTarget, MAX_HORDE_TARGET);
    }

    public static int timeOfDay(long dayTime) {
        return (int) Math.floorMod(dayTime, 24_000);
    }

    public static long nightId(long dayTime) {
        long day = Math.floorDiv(dayTime, 24_000);
        int time = timeOfDay(dayTime);
        if (time < NIGHT_START) {
            return day - 1;
        }
        return day;
    }

    public static int desiredCount(long dayTime, int hordeTarget) {
        int clamped = clampTarget(hordeTarget);
        int time = timeOfDay(dayTime);
        if (time < NIGHT_START || time > NIGHT_END) {
            return 0;
        }
        if (time >= RAMP_END) {
            return clamped;
        }
        return (int) ((long) clamped * (time - NIGHT_START) / RAMP_DURATION);
    }

    public static HordePlan plan(HordeTickInput input) {
        int target = clampTarget(input.hordeTarget());
        long nightId = nightId(input.dayTime());
        HordeNightState nightState = resolveNightState(input, nightId);
        int desired = desiredCount(input.dayTime(), target);
        boolean inWindow = desired > 0 || (timeOfDay(input.dayTime()) >= NIGHT_START
                && timeOfDay(input.dayTime()) <= NIGHT_END);
        boolean spawnEnabled = inWindow
                && input.overworld()
                && !input.peaceful()
                && !input.validPlayers().isEmpty()
                && target > 0
                && desired > 0;
        int remaining = Math.max(0, desired - Math.max(0, input.ordinaryZombieCount()));
        int spawnQuota = spawnEnabled ? Math.min(MAX_SUCCESSFUL_SPAWNS_PER_TICK, remaining) : 0;
        spawnEnabled = spawnEnabled && spawnQuota > 0;
        List<HordeSpawnAttempt> attempts = spawnEnabled
                ? attempts(input, nightState.directionRadians(), spawnQuota)
                : List.of();
        return new HordePlan(
                spawnEnabled,
                desired,
                spawnQuota,
                MAX_FAILED_POSITION_PICKS,
                nightState.directionRadians(),
                nightState,
                attempts);
    }

    private static HordeNightState resolveNightState(HordeTickInput input, long nightId) {
        if (input.nightState() != null && input.nightState().nightId() == nightId) {
            return input.nightState();
        }
        Random random = new Random(input.worldSeed() ^ (nightId * 0x9E3779B97F4A7C15L));
        return new HordeNightState(nightId, random.nextDouble() * Math.PI * 2.0);
    }

    private static List<HordeSpawnAttempt> attempts(HordeTickInput input, double direction, int spawnQuota) {
        HordePlayer player = input.validPlayers().getFirst();
        Random random = new Random(input.worldSeed()
                ^ (nightId(input.dayTime()) * 0xBF58476D1CE4E5B9L)
                ^ input.dayTime()
                ^ ((long) input.ordinaryZombieCount() << 17));
        int count = spawnQuota + MAX_FAILED_POSITION_PICKS;
        List<HordeSpawnAttempt> attempts = new ArrayList<>(count);
        double range = MAX_SPAWN_DISTANCE - MIN_SPAWN_DISTANCE;
        for (int i = 0; i < count; i++) {
            double radius = MIN_SPAWN_DISTANCE + random.nextDouble() * range;
            double angle = direction + (random.nextDouble() - 0.5) * 2.0 * MAX_ANGLE_JITTER;
            attempts.add(new HordeSpawnAttempt(
                    player.x() + Math.cos(angle) * radius,
                    player.z() + Math.sin(angle) * radius,
                    player.y()));
        }
        return List.copyOf(attempts);
    }
}
