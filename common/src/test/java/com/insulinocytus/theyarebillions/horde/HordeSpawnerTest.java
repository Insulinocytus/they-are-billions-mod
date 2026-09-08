package com.insulinocytus.theyarebillions.horde;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HordeSpawnerTest {
    @Test
    void onlyTickingOrdinaryZombiesCountTowardBudget() {
        assertTrue(HordeSpawner.countsTowardBudget(true, true));
        assertFalse(HordeSpawner.countsTowardBudget(true, false));
        assertFalse(HordeSpawner.countsTowardBudget(false, true));
    }

    @Test
    void attemptStartRotationReachesLastGroupWithinFailedAttemptLimit() {
        int[] quotas = {0, 1, 1, 1, 1, 0, 0, 0, 0};
        boolean[] reached = new boolean[9];
        int spawned = HordeSpawner.executeAttempts(quotas, 1, 4, 8, i -> {
            reached[i] = true;
            return i == 8;
        });
        assertTrue(reached[8]);
        assertEquals(1, spawned);
    }
}
