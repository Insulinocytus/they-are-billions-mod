package com.insulinocytus.theyarebillions.horde;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HordePlannerTest {
    private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final HordePlayer PLAYER = new HordePlayer(PLAYER_ID, 0.5, 64.0, 0.5);

    @Test
    void doesNotSpawnDuringDay() {
        assertFalse(planAt(6000).spawnEnabled());
        assertEquals(0, planAt(6000).desiredCount());
        assertEquals(0, planAt(6000).spawnQuota());
    }

    @Test
    void doesNotSpawnOutsideOverworldOrInPeacefulOrWithoutPlayers() {
        assertFalse(plan(input(18000).overworld(false).build()).spawnEnabled());
        assertFalse(plan(input(18000).peaceful(true).build()).spawnEnabled());
        assertFalse(plan(input(18000).players(List.of()).build()).spawnEnabled());
    }

    @Test
    void zeroTargetDisablesSpawn() {
        HordePlan planned = plan(input(18000).hordeTarget(0).build());
        assertFalse(planned.spawnEnabled());
        assertEquals(0, planned.desiredCount());
        assertEquals(0, planned.spawnQuota());
    }

    @Test
    void targetIsZeroAtNightStartAndFullFromMidnight() {
        assertEquals(0, planAt(13_000).desiredCount());
        assertEquals(0, planAt(13_000).spawnQuota());
        assertEquals(1000, planAt(18_000).desiredCount());
        assertEquals(1000, planAt(22_999).desiredCount());
        assertEquals(0, planAt(23_000).desiredCount());
    }

    @Test
    void targetRampsLinearlyUntilMidnight() {
        assertEquals(500, planAt(15_500).desiredCount());
        assertEquals(200, plan(input(14_000).hordeTarget(1000).build()).desiredCount());
        assertEquals(4, plan(input(15_500).hordeTarget(8).build()).desiredCount());
    }

    @Test
    void spawnQuotaCapsAtFourAndUsesRemainingBudget() {
        assertEquals(4, planAt(18_000).spawnQuota());
        assertEquals(2, plan(input(18_000).ordinaryZombieCount(998).build()).spawnQuota());
        assertEquals(0, plan(input(18_000).ordinaryZombieCount(1000).build()).spawnQuota());
        assertEquals(4, plan(input(14_000).hordeTarget(1000).ordinaryZombieCount(196).build()).spawnQuota());
    }

    @Test
    void clampsTargetToConfiguredRange() {
        assertEquals(1000, plan(input(18_000).hordeTarget(5000).build()).desiredCount());
        assertEquals(0, plan(input(18_000).hordeTarget(-3).build()).desiredCount());
    }

    @Test
    void keepsDirectionStableDuringTheSameNightAndResamplesTheNextNight() {
        HordePlan first = planAt(13_500);
        HordePlan laterSameNight = plan(input(18_000).nightState(first.nightState()).build());
        HordePlan nextNight = plan(input(13_500 + 24_000).nightState(first.nightState()).build());

        assertEquals(first.directionRadians(), laterSameNight.directionRadians());
        assertEquals(first.nightState().nightId(), laterSameNight.nightState().nightId());
        assertTrue(nextNight.nightState().nightId() != first.nightState().nightId());
        assertTrue(nextNight.directionRadians() != first.directionRadians());
    }

    @Test
    void spawnAttemptsLieOnTheDirectedRing() {
        HordePlan planned = planAt(18_000);
        assertFalse(planned.attempts().isEmpty());
        assertEquals(HordePlanner.MAX_SUCCESSFUL_SPAWNS_PER_TICK + HordePlanner.MAX_FAILED_POSITION_PICKS,
                planned.attempts().size());

        for (HordeSpawnAttempt attempt : planned.attempts()) {
            double dx = attempt.x() - PLAYER.x();
            double dz = attempt.z() - PLAYER.z();
            double distance = Math.hypot(dx, dz);
            assertTrue(distance >= HordePlanner.MIN_SPAWN_DISTANCE - 1.0e-6, "distance " + distance);
            assertTrue(distance <= HordePlanner.MAX_SPAWN_DISTANCE + 1.0e-6, "distance " + distance);

            double angle = Math.atan2(dz, dx);
            double delta = Math.abs(Math.atan2(Math.sin(angle - planned.directionRadians()),
                    Math.cos(angle - planned.directionRadians())));
            assertTrue(delta < 0.1, "angle delta " + delta);
        }
    }

    private static HordePlan planAt(long dayTime) {
        return plan(input(dayTime).build());
    }

    private static HordePlan plan(HordeTickInput input) {
        return HordePlanner.plan(input);
    }

    private static InputBuilder input(long dayTime) {
        return new InputBuilder(dayTime);
    }

    private static final class InputBuilder {
        private final long dayTime;
        private boolean overworld = true;
        private boolean peaceful = false;
        private List<HordePlayer> players = List.of(PLAYER);
        private int ordinaryZombieCount;
        private int hordeTarget = HordePlanner.DEFAULT_HORDE_TARGET;
        private HordeNightState nightState;

        private InputBuilder(long dayTime) {
            this.dayTime = dayTime;
        }

        private InputBuilder overworld(boolean overworld) {
            this.overworld = overworld;
            return this;
        }

        private InputBuilder peaceful(boolean peaceful) {
            this.peaceful = peaceful;
            return this;
        }

        private InputBuilder players(List<HordePlayer> players) {
            this.players = players;
            return this;
        }

        private InputBuilder ordinaryZombieCount(int ordinaryZombieCount) {
            this.ordinaryZombieCount = ordinaryZombieCount;
            return this;
        }

        private InputBuilder hordeTarget(int hordeTarget) {
            this.hordeTarget = hordeTarget;
            return this;
        }

        private InputBuilder nightState(HordeNightState nightState) {
            this.nightState = nightState;
            return this;
        }

        private HordeTickInput build() {
            return new HordeTickInput(dayTime, overworld, peaceful, players, ordinaryZombieCount, hordeTarget, 42L,
                    nightState);
        }
    }
}
