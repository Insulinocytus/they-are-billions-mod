package com.insulinocytus.theyarebillions.horde;

import java.util.List;

public record HordePlan(
        boolean spawnEnabled,
        int desiredCount,
        int spawnQuota,
        int maxFailedPicks,
        double directionRadians,
        HordeNightState nightState,
        List<HordeSpawnAttempt> attempts) {
}
