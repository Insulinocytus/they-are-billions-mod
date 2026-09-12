package com.insulinocytus.theyarebillions.horde;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class HordeDaytimeCleanupTest {
    @Test
    void removesSunlitThenFarthestUnseenExcessWhileNamedZombiesOccupySlots() {
        List<HordeDaytimeCleanup.ZombieRef> zombies = List.of(
                zombie("sunlit", 0, 100.0, false, true, false),
                zombie("named", 1, 0.0, true, false, true),
                zombie("near-1", 1, 1.0, false, false, true),
                zombie("near-2", 1, 4.0, false, false, true),
                zombie("near-3", 1, 9.0, false, false, true),
                zombie("far-1", 1, 16.0, false, false, true),
                zombie("far-2", 1, 25.0, false, false, true),
                zombie("other-1", 2, 100.0, false, false, true),
                zombie("other-2", 2, 81.0, false, false, true),
                zombie("other-3", 2, 64.0, false, false, true),
                zombie("other-4", 2, 49.0, false, false, true));

        assertEquals(
                List.of("sunlit", "far-2", "far-1"),
                HordeDaytimeCleanup.removals(zombies, 3));
    }

    @Test
    void namedSunlitZombieIsExemptButOccupiesASlot() {
        List<HordeDaytimeCleanup.ZombieRef> zombies = List.of(
                zombie("named-sunlit", 1, 100.0, true, true, true),
                zombie("a", 1, 1.0, false, false, true),
                zombie("b", 1, 2.0, false, false, true),
                zombie("c", 1, 3.0, false, false, true),
                zombie("d", 1, 4.0, false, false, true));

        assertEquals(List.of("d"), HordeDaytimeCleanup.removals(zombies, 10));
    }

    @Test
    void doesNotDensityCullSkyExposedZombiesWaitingForSun() {
        List<HordeDaytimeCleanup.ZombieRef> zombies = List.of(
                zombie("a", 1, 1.0, false, false, false),
                zombie("b", 1, 2.0, false, false, false),
                zombie("c", 1, 3.0, false, false, false),
                zombie("d", 1, 4.0, false, false, false),
                zombie("e", 1, 25.0, false, false, false));

        assertEquals(List.of(), HordeDaytimeCleanup.removals(zombies, 10));
    }

    @Test
    void sharesTheMainThreadRemovalBudget() {
        List<HordeDaytimeCleanup.ZombieRef> zombies = List.of(
                zombie("sunlit", 0, 100.0, false, true, false),
                zombie("a", 1, 1.0, false, false, true),
                zombie("b", 1, 2.0, false, false, true),
                zombie("c", 1, 3.0, false, false, true),
                zombie("d", 1, 4.0, false, false, true),
                zombie("e", 1, 25.0, false, false, true));

        assertEquals(List.of(), HordeDaytimeCleanup.removals(zombies, 0));
        assertEquals(List.of("sunlit"), HordeDaytimeCleanup.removals(zombies, 1));
    }

    @Test
    void removesAtMostTenZombiesPerTick() {
        List<HordeDaytimeCleanup.ZombieRef> zombies = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            zombies.add(zombie("s%02d".formatted(i), 0, i, false, true, false));
        }

        assertEquals(
                List.of("s11", "s10", "s09", "s08", "s07", "s06", "s05", "s04", "s03", "s02"),
                HordeDaytimeCleanup.removals(zombies, 10));
    }

    @Test
    void keepsAllNamedOrPersistentZombiesEvenWhenTheyExceedTheSectionCap() {
        List<HordeDaytimeCleanup.ZombieRef> zombies = List.of(
                zombie("p1", 1, 1.0, true, false, true),
                zombie("p2", 1, 2.0, true, false, true),
                zombie("p3", 1, 3.0, true, false, true),
                zombie("p4", 1, 4.0, true, false, true),
                zombie("p5", 1, 5.0, true, false, true),
                zombie("n", 1, 100.0, false, false, true));

        assertEquals(List.of("n"), HordeDaytimeCleanup.removals(zombies, 10));
    }

    @Test
    void combatDoesNotOccupyANamedOrPersistentSlot() {
        List<HordeDaytimeCleanup.ZombieRef> zombies = List.of(
                zombie("near", 1, 1.0, false, false, true),
                zombie("mid-1", 1, 2.0, false, false, true),
                zombie("mid-2", 1, 3.0, false, false, true),
                zombie("mid-3", 1, 4.0, false, false, true),
                zombie("fighting", 1, 25.0, false, false, true));

        assertEquals(List.of("fighting"), HordeDaytimeCleanup.removals(zombies, 10));
    }

    @Test
    void densityCleanupRunsWithoutPlayersUsingDeterministicTies() {
        List<HordeDaytimeCleanup.ZombieRef> zombies = List.of(
                zombie("a", 1, Double.POSITIVE_INFINITY, false, false, true),
                zombie("b", 1, Double.POSITIVE_INFINITY, false, false, true),
                zombie("c", 1, Double.POSITIVE_INFINITY, false, false, true),
                zombie("d", 1, Double.POSITIVE_INFINITY, false, false, true),
                zombie("e", 1, Double.POSITIVE_INFINITY, false, false, true));

        assertEquals(List.of("e"), HordeDaytimeCleanup.removals(zombies, 10));
    }

    private static HordeDaytimeCleanup.ZombieRef zombie(
            String id,
            long section,
            double nearestPlayerDistanceSquared,
            boolean namedOrPersistent,
            boolean sunlit,
            boolean unseenSun) {
        return new HordeDaytimeCleanup.ZombieRef(
                id, section, nearestPlayerDistanceSquared, namedOrPersistent, sunlit, unseenSun);
    }
}
