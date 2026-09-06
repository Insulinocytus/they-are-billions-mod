package com.insulinocytus.theyarebillions.horde;

public final class HordePlanner {
    public static final int DEFAULT_TARGET = 1000;
    public static final int MIN_TARGET = 0;
    public static final int MAX_TARGET = 1000;
    public static final int NIGHT_START = 13000;
    public static final int RAMP_END = 18000;
    public static final int NIGHT_END = 23000;
    public static final int MAX_SUCCESSFUL_SPAWNS_PER_TICK = 4;
    public static final int MAX_FAILED_SPAWN_ATTEMPTS_PER_TICK = 8;
    public static final int SPAWN_RANGE_MIN = 128;
    public static final int SPAWN_RANGE_MAX = 144;

    private HordePlanner() {
    }

    public static boolean isHordeNight(long dayTime) {
        int time = dayTimeOfDay(dayTime);
        return time >= NIGHT_START && time < NIGHT_END;
    }

    public static Plan plan(Snapshot snapshot) {
        int target = Math.clamp(snapshot.hordeTarget(), MIN_TARGET, MAX_TARGET);
        if (!snapshot.overworld()
                || snapshot.peaceful()
                || !snapshot.hasValidPlayer()
                || target == 0
                || !isHordeNight(snapshot.dayTime())) {
            return Plan.none();
        }
        int desired = desiredCount(snapshot.dayTime(), target);
        int remaining = Math.max(0, desired - snapshot.ordinaryZombieCount());
        int quota = Math.min(MAX_SUCCESSFUL_SPAWNS_PER_TICK, remaining);
        if (quota == 0) {
            return new Plan(desired, 0, 0, null);
        }
        return new Plan(
                desired,
                quota,
                MAX_FAILED_SPAWN_ATTEMPTS_PER_TICK,
                new Sector(
                        snapshot.playerX(),
                        snapshot.playerZ(),
                        snapshot.spawnDirectionRadians(),
                        SPAWN_RANGE_MIN,
                        SPAWN_RANGE_MAX));
    }

    public static int desiredCount(long dayTime, int target) {
        int time = dayTimeOfDay(dayTime);
        if (time < NIGHT_START || time >= NIGHT_END) {
            return 0;
        }
        if (time >= RAMP_END) {
            return target;
        }
        return (int) ((long) target * (time - NIGHT_START) / (RAMP_END - NIGHT_START));
    }

    private static int dayTimeOfDay(long dayTime) {
        return (int) Math.floorMod(dayTime, 24000L);
    }

    public record Snapshot(
            boolean overworld,
            boolean peaceful,
            boolean hasValidPlayer,
            long dayTime,
            int hordeTarget,
            int ordinaryZombieCount,
            double playerX,
            double playerZ,
            double spawnDirectionRadians) {
    }

    public record Plan(int desiredCount, int successfulSpawnLimit, int failedAttemptLimit, Sector sector) {
        public static Plan none() {
            return new Plan(0, 0, 0, null);
        }

        public boolean shouldSpawn() {
            return successfulSpawnLimit > 0 && sector != null;
        }
    }

    public record Sector(
            double originX, double originZ, double directionRadians, int minDistance, int maxDistance) {
    }
}
