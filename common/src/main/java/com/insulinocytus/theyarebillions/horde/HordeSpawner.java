package com.insulinocytus.theyarebillions.horde;

public final class HordeSpawner {
    private HordeSpawner() {
    }

    public static HordeSpawnResult spawn(HordePlan plan, HordeSpawnWorld world) {
        if (!plan.spawnEnabled() || plan.spawnQuota() <= 0) {
            return new HordeSpawnResult(0, 0);
        }
        int spawned = 0;
        int failedPicks = 0;
        for (HordeSpawnAttempt attempt : plan.attempts()) {
            if (spawned >= plan.spawnQuota() || failedPicks >= plan.maxFailedPicks()) {
                break;
            }
            if (world.trySpawn(attempt)) {
                spawned++;
            } else {
                failedPicks++;
            }
        }
        return new HordeSpawnResult(spawned, failedPicks);
    }
}
