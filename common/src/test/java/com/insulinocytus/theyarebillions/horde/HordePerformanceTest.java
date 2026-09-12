package com.insulinocytus.theyarebillions.horde;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HordePerformanceTest {
    private static final long LOW = 39_000_000L;
    private static final long HOLD_MAX = 45_000_000L;
    private static final long HIGH = 46_000_000L;

    @Test
    void usesStrictP95ThresholdOverTheLatestTwoHundredTicks() {
        HordePerformance.Tracker tracker = new HordePerformance.Tracker();

        sample(tracker, HOLD_MAX, 190);
        sample(tracker, HIGH, 10);
        assertEquals(HordePerformance.Tier.NORMAL, tracker.tier());

        tracker.sample(HIGH);
        assertEquals(HordePerformance.Tier.REDUCED, tracker.tier());
    }

    @Test
    void recoveryRequiresEveryTickInTheWindowBelowFortyMs() {
        HordePerformance.Tracker tracker = new HordePerformance.Tracker(HordePerformance.Tier.PAUSED);

        sample(tracker, LOW, 190);
        sample(tracker, 42_000_000L, 10);
        assertEquals(HordePerformance.Tier.PAUSED, tracker.tier());
        assertPlan(tracker, 18000, 0, 1000, 0);
    }

    @Test
    void degradesOnePlanningTierPerTwoHundredHighTicks() {
        HordePerformance.Tracker tracker = new HordePerformance.Tracker();
        HordeDiggingCoordinator<String> digging = new HordeDiggingCoordinator<>();
        for (int i = 0; i < 10; i++) {
            assertTrue(digging.request("wall" + i, 1, 9.0, 0.1F, 0));
        }
        digging.tick(0);

        sample(tracker, HIGH, 200);
        assertEquals(HordePerformance.Tier.REDUCED, tracker.tier());
        assertPlan(tracker, 18000, 0, 1000, 2);
        sample(tracker, HIGH, 199);
        assertEquals(HordePerformance.Tier.REDUCED, tracker.tier());
        tracker.sample(HIGH);
        assertEquals(HordePerformance.Tier.MINIMUM, tracker.tier());
        assertPlan(tracker, 18000, 0, 1000, 1);
        sample(tracker, HIGH, 200);
        assertEquals(HordePerformance.Tier.PAUSED, tracker.tier());
        assertPlan(tracker, 18000, 0, 1000, 0);

        digging.setLimit(tracker.tier().diggingLimit());
        assertEquals(10, digging.activeCount());
    }

    @Test
    void recoversPlanningTiersWithoutBurstingCatchup() {
        HordePerformance.Tracker tracker = new HordePerformance.Tracker(HordePerformance.Tier.PAUSED);

        sample(tracker, LOW, 199);
        tracker.sample(40_000_000L);
        sample(tracker, LOW, 199);
        assertEquals(HordePerformance.Tier.PAUSED, tracker.tier());
        assertPlan(tracker, 15500, 0, 500, 0);

        tracker.sample(LOW);
        assertEquals(HordePerformance.Tier.MINIMUM, tracker.tier());
        assertPlan(tracker, 15500, 0, 500, 1);

        sample(tracker, 42_000_000L, 200);
        assertEquals(HordePerformance.Tier.MINIMUM, tracker.tier());

        sample(tracker, LOW, 200);
        assertEquals(HordePerformance.Tier.REDUCED, tracker.tier());
        assertPlan(tracker, 18000, 0, 1000, 2);

        sample(tracker, LOW, 200);
        assertEquals(HordePerformance.Tier.NORMAL, tracker.tier());
        assertPlan(tracker, 20000, 0, 1000, 4);
    }

    @Test
    void tiersProgressivelyReduceOnlyLoadSensitiveWork() {
        assertEquals(1, HordePerformance.Tier.NORMAL.simulationIntervalMultiplier());
        assertEquals(64, HordePerformance.Tier.NORMAL.diggingLimit());
        assertEquals(4, HordePerformance.Tier.NORMAL.spawnLimit());

        assertEquals(2, HordePerformance.Tier.REDUCED.simulationIntervalMultiplier());
        assertEquals(32, HordePerformance.Tier.REDUCED.diggingLimit());
        assertEquals(2, HordePerformance.Tier.REDUCED.spawnLimit());

        assertEquals(4, HordePerformance.Tier.MINIMUM.simulationIntervalMultiplier());
        assertEquals(8, HordePerformance.Tier.MINIMUM.diggingLimit());
        assertEquals(1, HordePerformance.Tier.MINIMUM.spawnLimit());

        assertEquals(8, HordePerformance.Tier.PAUSED.simulationIntervalMultiplier());
        assertEquals(8, HordePerformance.Tier.PAUSED.diggingLimit());
        assertEquals(0, HordePerformance.Tier.PAUSED.spawnLimit());

        assertEquals(0, HordeSimulation.delayUntilScheduled(HordeSimulation.Tier.NEAR, 10, 3, 8));
        assertEquals(7, HordeSimulation.delayUntilScheduled(HordeSimulation.Tier.FAR, 10, 1, 2));
    }

    private static void assertPlan(
            HordePerformance.Tracker tracker, long dayTime, int ordinaryZombies, int desired, int spawnLimit) {
        HordePlanner.Plan plan = HordePlanner.plan(
                new HordePlanner.Snapshot(
                        true,
                        false,
                        dayTime,
                        1000,
                        ordinaryZombies,
                        List.of(new HordePlanner.PlayerRef("p", 0.0, 0.0, 0.0)),
                        new HordePlanner.NightState(
                                dayTime, Map.of(HordePlanner.GroupIdentity.of("p"), 0.0))),
                () -> 0.0,
                tracker.tier().spawnLimit());
        assertEquals(desired, plan.desiredCount());
        assertEquals(spawnLimit, plan.successfulSpawnLimit());
    }

    private static void sample(HordePerformance.Tracker tracker, long durationNanos, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            tracker.sample(durationNanos);
        }
    }
}
