package com.insulinocytus.theyarebillions.horde;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class HordeSpawnerTest {
    @Test
    void stopsAfterFourSuccessfulSpawns() {
        RecordingWorld world = RecordingWorld.accepting(8);
        HordeSpawnResult result = HordeSpawner.spawn(planWithAttempts(8), world);

        assertEquals(4, result.spawned());
        assertEquals(4, world.spawnedPositions().size());
        assertEquals(4, world.checkedPositions().size());
    }

    @Test
    void stopsAfterLimitedFailedPicks() {
        RecordingWorld world = RecordingWorld.rejecting();
        HordePlan plan = planWithAttempts(20);
        HordeSpawnResult result = HordeSpawner.spawn(plan, world);

        assertEquals(0, result.spawned());
        assertEquals(HordePlanner.MAX_FAILED_POSITION_PICKS, result.failedPicks());
        assertEquals(HordePlanner.MAX_FAILED_POSITION_PICKS, world.checkedPositions().size());
    }

    @Test
    void countsRejectedPositionsAsFailedPicksUntilQuotaIsFilled() {
        RecordingWorld world = RecordingWorld.acceptingEveryOther();
        HordeSpawnResult result = HordeSpawner.spawn(planWithAttempts(20), world);

        assertEquals(4, result.spawned());
        assertEquals(4, result.failedPicks());
        assertEquals(8, world.checkedPositions().size());
    }

    @Test
    void doesNothingWhenSpawnIsDisabled() {
        RecordingWorld world = RecordingWorld.accepting(8);
        HordePlan plan = new HordePlan(false, 0, 0, HordePlanner.MAX_FAILED_POSITION_PICKS, 0.0,
                new HordeNightState(0, 0.0), List.of(new HordeSpawnAttempt(1, 1, 64)));

        HordeSpawnResult result = HordeSpawner.spawn(plan, world);

        assertEquals(0, result.spawned());
        assertTrue(world.checkedPositions().isEmpty());
    }

    private static HordePlan planWithAttempts(int attemptCount) {
        List<HordeSpawnAttempt> attempts = new ArrayList<>();
        for (int i = 0; i < attemptCount; i++) {
            attempts.add(new HordeSpawnAttempt(100 + i, 200 + i, 64));
        }
        return new HordePlan(true, 1000, HordePlanner.MAX_SUCCESSFUL_SPAWNS_PER_TICK,
                HordePlanner.MAX_FAILED_POSITION_PICKS, 0.0, new HordeNightState(0, 0.0), attempts);
    }

    private static final class RecordingWorld implements HordeSpawnWorld {
        private final List<HordeSpawnAttempt> checked = new ArrayList<>();
        private final List<HordeSpawnAttempt> spawned = new ArrayList<>();
        private final Mode mode;
        private final int maxAccepts;
        private int accepted;

        private enum Mode {
            ACCEPT,
            REJECT,
            ALTERNATE
        }

        private RecordingWorld(Mode mode, int maxAccepts) {
            this.mode = mode;
            this.maxAccepts = maxAccepts;
        }

        static RecordingWorld accepting(int maxAccepts) {
            return new RecordingWorld(Mode.ACCEPT, maxAccepts);
        }

        static RecordingWorld rejecting() {
            return new RecordingWorld(Mode.REJECT, 0);
        }

        static RecordingWorld acceptingEveryOther() {
            return new RecordingWorld(Mode.ALTERNATE, Integer.MAX_VALUE);
        }

        @Override
        public boolean trySpawn(HordeSpawnAttempt attempt) {
            checked.add(attempt);
            boolean accept = switch (mode) {
                case ACCEPT -> accepted < maxAccepts;
                case REJECT -> false;
                case ALTERNATE -> checked.size() % 2 == 0;
            };
            if (accept) {
                accepted++;
                spawned.add(attempt);
            }
            return accept;
        }

        List<HordeSpawnAttempt> checkedPositions() {
            return checked;
        }

        List<HordeSpawnAttempt> spawnedPositions() {
            return spawned;
        }
    }
}
