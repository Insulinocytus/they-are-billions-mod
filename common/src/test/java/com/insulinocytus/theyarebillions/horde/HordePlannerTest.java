package com.insulinocytus.theyarebillions.horde;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HordePlannerTest {
    @Test
    void rampsDesiredCountFromDuskToMidnight() {
        assertEquals(0, plan(13000, 1000, 0).desiredCount());
        assertEquals(500, plan(15500, 1000, 0).desiredCount());
        assertEquals(1000, plan(18000, 1000, 0).desiredCount());
    }

    @Test
    void holdsTargetAfterMidnightUntilDawn() {
        assertEquals(1000, plan(18000, 1000, 0).desiredCount());
        assertEquals(1000, plan(20000, 1000, 0).desiredCount());
        assertEquals(1000, plan(22999, 1000, 0).desiredCount());
    }

    @Test
    void doesNotSpawnDuringDay() {
        assertFalse(plan(0, 1000, 0).shouldSpawn());
        assertFalse(plan(12999, 1000, 0).shouldSpawn());
        assertFalse(plan(23000, 1000, 0).shouldSpawn());
        assertEquals(0, plan(23000, 1000, 0).desiredCount());
    }

    @Test
    void doesNotSpawnWhenTargetIsZero() {
        HordePlanner.Plan plan = plan(18000, 0, 0);
        assertEquals(0, plan.desiredCount());
        assertFalse(plan.shouldSpawn());
    }

    @Test
    void doesNotSpawnWithoutValidPlayer() {
        HordePlanner.Plan plan = HordePlanner.plan(snapshot(true, false, false, 18000, 1000, 0));
        assertFalse(plan.shouldSpawn());
    }

    @Test
    void doesNotSpawnInPeaceful() {
        HordePlanner.Plan plan = HordePlanner.plan(snapshot(true, true, true, 18000, 1000, 0));
        assertFalse(plan.shouldSpawn());
    }

    @Test
    void doesNotSpawnOutsideOverworld() {
        HordePlanner.Plan plan = HordePlanner.plan(snapshot(false, false, true, 18000, 1000, 0));
        assertFalse(plan.shouldSpawn());
    }

    @Test
    void limitsSuccessfulSpawnsPerTickToFour() {
        assertEquals(4, plan(18000, 1000, 0).successfulSpawnLimit());
        assertEquals(1, plan(18000, 1000, 999).successfulSpawnLimit());
        assertEquals(0, plan(18000, 1000, 1000).successfulSpawnLimit());
        assertFalse(plan(18000, 1000, 1000).shouldSpawn());
    }

    @Test
    void limitsFailedSpawnAttemptsWhenSpawning() {
        HordePlanner.Plan plan = plan(18000, 1000, 0);
        assertTrue(plan.failedAttemptLimit() > 0);
        assertTrue(plan.failedAttemptLimit() <= 32);
        assertEquals(0, plan(18000, 1000, 1000).failedAttemptLimit());
    }

    @Test
    void clampsTargetToZeroThroughOneThousand() {
        assertEquals(1000, plan(18000, 2000, 0).desiredCount());
        assertEquals(0, plan(18000, -5, 0).desiredCount());
        assertFalse(plan(18000, -5, 0).shouldSpawn());
    }

    @Test
    void placesSpawnSectorInConfiguredRing() {
        HordePlanner.Plan plan = HordePlanner.plan(new HordePlanner.Snapshot(
                true, false, true, 18000, 1000, 0, 12.5, -8.25, 1.25));
        assertTrue(plan.shouldSpawn());
        HordePlanner.Sector sector = plan.sector();
        assertNotNull(sector);
        assertEquals(12.5, sector.originX());
        assertEquals(-8.25, sector.originZ());
        assertEquals(1.25, sector.directionRadians());
        assertEquals(128, sector.minDistance());
        assertEquals(144, sector.maxDistance());
    }

    @Test
    void usesTimeOfDayWhenWorldTimeExceedsOneDay() {
        assertEquals(1000, plan(24000 + 18000, 1000, 0).desiredCount());
        assertEquals(0, plan(24000 + 13000, 1000, 0).desiredCount());
    }

    @Test
    void omitsSpawnSectorWhenNotSpawning() {
        assertNull(plan(0, 1000, 0).sector());
    }

    private static HordePlanner.Plan plan(long dayTime, int target, int ordinaryZombies) {
        return HordePlanner.plan(snapshot(true, false, true, dayTime, target, ordinaryZombies));
    }

    private static HordePlanner.Snapshot snapshot(
            boolean overworld,
            boolean peaceful,
            boolean hasValidPlayer,
            long dayTime,
            int target,
            int ordinaryZombies) {
        return new HordePlanner.Snapshot(
                overworld, peaceful, hasValidPlayer, dayTime, target, ordinaryZombies, 0.0, 0.0, 0.0);
    }
}
