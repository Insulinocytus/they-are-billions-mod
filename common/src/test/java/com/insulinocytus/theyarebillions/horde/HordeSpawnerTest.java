package com.insulinocytus.theyarebillions.horde;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HordeSpawnerTest {
    @Test
    void budgetCountsTickingZombiesPlusPendingUntickedHordeOnly() {
        UUID pending = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID gone = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID nowTicking = UUID.fromString("00000000-0000-0000-0000-000000000003");
        Set<UUID> pendingIds = new HashSet<>(Set.of(pending, gone, nowTicking));
        int budget = HordeSpawner.ordinaryZombieBudget(5, pendingIds, Set.of(pending));
        assertEquals(6, budget);
        assertEquals(Set.of(pending), pendingIds);
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
