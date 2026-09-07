package com.insulinocytus.theyarebillions.horde;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.DoubleSupplier;
import org.junit.jupiter.api.Test;

class HordePlannerTest {
    private static final DoubleSupplier NO_NEW_DIRECTION = () -> {
        throw new AssertionError("should not assign a new direction");
    };

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
        HordePlanner.Plan plan = HordePlanner.plan(snapshot(
                true, false, 18000, 1000, 0, List.of(), HordePlanner.NightState.none()));
        assertFalse(plan.shouldSpawn());
    }

    @Test
    void doesNotSpawnInPeaceful() {
        HordePlanner.Plan plan = HordePlanner.plan(snapshot(
                true, true, 18000, 1000, 0, List.of(player("p", 0, 0)), seeded("p", 0.0)));
        assertFalse(plan.shouldSpawn());
    }

    @Test
    void doesNotSpawnOutsideOverworld() {
        HordePlanner.Plan plan = HordePlanner.plan(snapshot(
                false, false, 18000, 1000, 0, List.of(player("p", 0, 0)), seeded("p", 0.0)));
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
        HordePlanner.Plan plan = HordePlanner.plan(
                snapshot(true, false, 18000, 1000, 0, List.of(player("p", 12.5, -8.25)), seeded("p", 1.25)),
                NO_NEW_DIRECTION);
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

    @Test
    void acceptsBlockCentersOnInclusiveRingEdges() {
        HordePlanner.Sector sector = new HordePlanner.Sector(0.5, 0.5, 0.0, 128, 144);
        assertTrue(sector.containsBlockCenter(128, 0));
        assertTrue(sector.containsBlockCenter(144, 0));
        assertFalse(sector.containsBlockCenter(127, 0));
        assertFalse(sector.containsBlockCenter(145, 0));
    }

    @Test
    void rejectsFlooredBlockCentersThatLeaveTheRing() {
        HordePlanner.Sector sector = new HordePlanner.Sector(0.9, 0.0, 0.0, 128, 144);
        assertFalse(sector.containsBlockCenter(128, 0));
        assertTrue(sector.containsBlockCenter(129, 0));
        HordePlanner.Sector outer = new HordePlanner.Sector(0.1, 0.0, 0.0, 128, 144);
        assertFalse(outer.containsBlockCenter(144, 0));
        assertTrue(outer.containsBlockCenter(143, 0));
    }

    @Test
    void validPlayersAreSurvivalOrAdventureAndNotFake() {
        assertTrue(HordePlanner.isValidPlayer(false, true, false));
        assertTrue(HordePlanner.isValidPlayer(false, false, true));
        assertFalse(HordePlanner.isValidPlayer(true, true, false));
        assertFalse(HordePlanner.isValidPlayer(true, false, true));
        assertFalse(HordePlanner.isValidPlayer(false, false, false));
    }

    @Test
    void groupsPlayersWithinInclusive128Blocks() {
        HordePlanner.Plan plan = HordePlanner.plan(
                snapshot(
                        18000,
                        1000,
                        0,
                        List.of(player("a", 0, 0), player("b", 128, 0)),
                        HordePlanner.NightState.none()),
                directions(0.5));
        assertEquals(1, plan.groups().size());
        assertEquals("a,b", plan.groups().getFirst().key());
    }

    @Test
    void doesNotGroupPlayersFartherThan128Blocks() {
        HordePlanner.Plan plan = HordePlanner.plan(
                snapshot(
                        18000,
                        1000,
                        0,
                        List.of(player("a", 0, 0), player("b", 129, 0)),
                        HordePlanner.NightState.none()),
                directions(0.1, 0.2));
        assertEquals(2, plan.groups().size());
        assertEquals("a", plan.groups().get(0).key());
        assertEquals("b", plan.groups().get(1).key());
    }

    @Test
    void groupingIsTransitiveAlong128BlockAdjacency() {
        HordePlanner.Plan plan = HordePlanner.plan(
                snapshot(
                        18000,
                        1000,
                        0,
                        List.of(player("a", 0, 0), player("b", 100, 0), player("c", 200, 0)),
                        HordePlanner.NightState.none()),
                directions(1.0));
        assertEquals(1, plan.groups().size());
        assertEquals("a,b,c", plan.groups().getFirst().key());
    }

    @Test
    void groupingUses3dDistance() {
        HordePlanner.Plan plan = HordePlanner.plan(
                snapshot(
                        18000,
                        1000,
                        0,
                        List.of(player("a", 0, 0, 0), player("b", 128, 1, 0)),
                        HordePlanner.NightState.none()),
                directions(0.1, 0.2));
        assertEquals(2, plan.groups().size());
    }

    @Test
    void spawnOriginIsGroupCentroid() {
        HordePlanner.Plan plan = HordePlanner.plan(
                snapshot(
                        18000,
                        1000,
                        0,
                        List.of(player("a", 0, 0), player("b", 10, 0)),
                        HordePlanner.NightState.none()),
                directions(0.3));
        HordePlanner.Sector sector = plan.sector();
        assertNotNull(sector);
        assertEquals(5.0, sector.originX());
        assertEquals(0.0, sector.originZ());
    }

    @Test
    void remainingBudgetSplitsEvenlyAcrossGroups() {
        HordePlanner.Plan plan = HordePlanner.plan(
                snapshot(
                        18000,
                        1000,
                        0,
                        List.of(player("a", 0, 0), player("b", 1000, 0)),
                        HordePlanner.NightState.none()),
                directions(0.1, 0.2));
        assertEquals(500, plan.groups().get(0).remainingBudget());
        assertEquals(500, plan.groups().get(1).remainingBudget());
        assertEquals(2, plan.groups().get(0).spawnQuota());
        assertEquals(2, plan.groups().get(1).spawnQuota());
        assertEquals(4, plan.successfulSpawnLimit());
    }

    @Test
    void leftoverRemainingBudgetGoesToEarlierGroup() {
        HordePlanner.Plan plan = HordePlanner.plan(
                snapshot(
                        18000,
                        1000,
                        0,
                        List.of(player("a", 0, 0), player("b", 1000, 0), player("c", 2000, 0)),
                        HordePlanner.NightState.none()),
                directions(0.1, 0.2, 0.3));
        assertEquals(334, plan.groups().get(0).remainingBudget());
        assertEquals(333, plan.groups().get(1).remainingBudget());
        assertEquals(333, plan.groups().get(2).remainingBudget());
        assertEquals(2, plan.groups().get(0).spawnQuota());
        assertEquals(1, plan.groups().get(1).spawnQuota());
        assertEquals(1, plan.groups().get(2).spawnQuota());
    }

    @Test
    void unusedGroupShareDoesNotReduceGlobalTickLimit() {
        HordePlanner.Plan plan = HordePlanner.plan(
                snapshot(
                        18000,
                        2,
                        0,
                        List.of(player("a", 0, 0), player("b", 1000, 0), player("c", 2000, 0)),
                        HordePlanner.NightState.none()),
                directions(0.1, 0.2, 0.3));
        assertEquals(1, plan.groups().get(0).remainingBudget());
        assertEquals(1, plan.groups().get(1).remainingBudget());
        assertEquals(0, plan.groups().get(2).remainingBudget());
        assertEquals(1, plan.groups().get(0).spawnQuota());
        assertEquals(1, plan.groups().get(1).spawnQuota());
        assertEquals(0, plan.groups().get(2).spawnQuota());
        assertEquals(2, plan.successfulSpawnLimit());
    }

    @Test
    void allOrdinaryZombiesCountTowardBudgetRegardlessOfSource() {
        HordePlanner.Plan plan = plan(18000, 1000, 400);
        assertEquals(1000, plan.desiredCount());
        assertEquals(4, plan.successfulSpawnLimit());
        assertEquals(600, plan.groups().getFirst().remainingBudget());
    }

    @Test
    void keepsDirectionForUnchangedGroup() {
        HordePlanner.Plan first = HordePlanner.plan(
                snapshot(18000, 1000, 0, List.of(player("a", 0, 0), player("b", 10, 0)), HordePlanner.NightState.none()),
                directions(1.25));
        HordePlanner.Plan second = HordePlanner.plan(
                snapshot(20000, 1000, 0, List.of(player("a", 0, 0), player("b", 10, 0)), first.night()),
                NO_NEW_DIRECTION);
        assertEquals(1.25, second.groups().getFirst().sector().directionRadians());
    }

    @Test
    void reselectsDirectionWhenGroupsMerge() {
        HordePlanner.Plan split = HordePlanner.plan(
                snapshot(
                        18000,
                        1000,
                        0,
                        List.of(player("a", 0, 0), player("b", 1000, 0)),
                        HordePlanner.NightState.none()),
                directions(0.1, 0.2));
        HordePlanner.Plan merged = HordePlanner.plan(
                snapshot(18100, 1000, 0, List.of(player("a", 0, 0), player("b", 10, 0)), split.night()),
                directions(9.0));
        assertEquals(1, merged.groups().size());
        assertEquals(9.0, merged.groups().getFirst().sector().directionRadians());
    }

    @Test
    void reselectsDirectionWhenGroupsSplit() {
        HordePlanner.Plan together = HordePlanner.plan(
                snapshot(18000, 1000, 0, List.of(player("a", 0, 0), player("b", 10, 0)), HordePlanner.NightState.none()),
                directions(1.0));
        HordePlanner.Plan split = HordePlanner.plan(
                snapshot(
                        18100,
                        1000,
                        0,
                        List.of(player("a", 0, 0), player("b", 1000, 0)),
                        together.night()),
                directions(2.0, 3.0));
        assertEquals(2, split.groups().size());
        assertEquals(2.0, split.groups().get(0).sector().directionRadians());
        assertEquals(3.0, split.groups().get(1).sector().directionRadians());
    }

    @Test
    void sameWorldDayTimeJumpsKeepDirectionAndDoNotStackBudget() {
        HordePlanner.Plan dusk = HordePlanner.plan(
                snapshot(14000, 1000, 0, List.of(player("p", 0, 0)), HordePlanner.NightState.none()),
                directions(1.5));
        HordePlanner.Plan day = HordePlanner.plan(
                snapshot(1000, 1000, 0, List.of(player("p", 0, 0)), dusk.night()),
                NO_NEW_DIRECTION);
        HordePlanner.Plan nightAgain = HordePlanner.plan(
                snapshot(18000, 1000, 250, List.of(player("p", 0, 0)), day.night()),
                NO_NEW_DIRECTION);
        assertFalse(day.shouldSpawn());
        assertEquals(0, day.desiredCount());
        assertEquals(1.5, nightAgain.groups().getFirst().sector().directionRadians());
        assertEquals(1000, nightAgain.desiredCount());
        assertEquals(750, nightAgain.groups().getFirst().remainingBudget());
    }

    @Test
    void splitAfterMergeDoesNotRestoreOldDirections() {
        HordePlanner.Plan split = HordePlanner.plan(
                snapshot(
                        18000,
                        1000,
                        0,
                        List.of(player("a", 0, 0), player("b", 1000, 0)),
                        HordePlanner.NightState.none()),
                directions(0.1, 0.2));
        HordePlanner.Plan merged = HordePlanner.plan(
                snapshot(18100, 1000, 0, List.of(player("a", 0, 0), player("b", 10, 0)), split.night()),
                directions(9.0));
        HordePlanner.Plan splitAgain = HordePlanner.plan(
                snapshot(
                        18200,
                        1000,
                        0,
                        List.of(player("a", 0, 0), player("b", 1000, 0)),
                        merged.night()),
                directions(4.0, 5.0));
        assertEquals(4.0, splitAgain.groups().get(0).sector().directionRadians());
        assertEquals(5.0, splitAgain.groups().get(1).sector().directionRadians());
    }

    @Test
    void newWorldDayReselectsDirection() {
        HordePlanner.Plan night = HordePlanner.plan(
                snapshot(18000, 1000, 0, List.of(player("p", 0, 0)), HordePlanner.NightState.none()),
                directions(1.5));
        HordePlanner.Plan morning = HordePlanner.plan(
                snapshot(24000, 1000, 0, List.of(player("p", 0, 0)), night.night()),
                NO_NEW_DIRECTION);
        HordePlanner.Plan nextNight = HordePlanner.plan(
                snapshot(24000 + 18000, 1000, 0, List.of(player("p", 0, 0)), morning.night()),
                directions(2.25));
        assertEquals(2.25, nextNight.groups().getFirst().sector().directionRadians());
        assertNotEquals(1.5, nextNight.groups().getFirst().sector().directionRadians());
    }

    private static HordePlanner.Plan plan(long dayTime, int target, int ordinaryZombies) {
        String id = "p";
        long worldDay = Math.floorDiv(dayTime, 24000L);
        return HordePlanner.plan(
                snapshot(
                        true,
                        false,
                        dayTime,
                        target,
                        ordinaryZombies,
                        List.of(player(id, 0, 0)),
                        new HordePlanner.NightState(worldDay, Map.of(id, 0.0))),
                NO_NEW_DIRECTION);
    }

    private static HordePlanner.Snapshot snapshot(
            long dayTime,
            int target,
            int ordinaryZombies,
            List<HordePlanner.PlayerRef> players,
            HordePlanner.NightState night) {
        return snapshot(true, false, dayTime, target, ordinaryZombies, players, night);
    }

    private static HordePlanner.Snapshot snapshot(
            boolean overworld,
            boolean peaceful,
            long dayTime,
            int target,
            int ordinaryZombies,
            List<HordePlanner.PlayerRef> players,
            HordePlanner.NightState night) {
        return new HordePlanner.Snapshot(overworld, peaceful, dayTime, target, ordinaryZombies, players, night);
    }

    private static HordePlanner.PlayerRef player(String id, double x, double z) {
        return player(id, x, 0.0, z);
    }

    private static HordePlanner.PlayerRef player(String id, double x, double y, double z) {
        return new HordePlanner.PlayerRef(id, x, y, z);
    }

    private static HordePlanner.NightState seeded(String key, double direction) {
        return new HordePlanner.NightState(0, Map.of(key, direction));
    }

    private static DoubleSupplier directions(double... values) {
        AtomicInteger index = new AtomicInteger();
        return () -> values[index.getAndIncrement()];
    }
}
