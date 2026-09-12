package com.insulinocytus.theyarebillions.horde;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HordeDiggingCoordinatorTest {
    @Test
    void combinesAtMostThreeParticipantContributions() {
        HordeDiggingCoordinator<String> coordinator = new HordeDiggingCoordinator<>();

        assertTrue(coordinator.request("wall", 1, 9.0, 0.1F, 0));
        assertTrue(coordinator.request("wall", 2, 9.0, 0.1F, 0));
        assertTrue(coordinator.request("wall", 3, 9.0, 0.1F, 0));
        assertFalse(coordinator.request("wall", 4, 9.0, 0.1F, 0));

        coordinator.tick(0);

        assertEquals(0.3F, coordinator.progress("wall"), 0.0001F);
    }

    @Test
    void doesNotApplyProgressTwiceOnTheSameTick() {
        HordeDiggingCoordinator<String> coordinator = new HordeDiggingCoordinator<>();

        assertTrue(coordinator.request("wall", 1, 9.0, 0.1F, 0));
        coordinator.tick(0);
        coordinator.tick(0);

        assertEquals(0.1F, coordinator.progress("wall"), 0.0001F);
    }

    @Test
    void activeHeartbeatsApplyImmediately() {
        HordeDiggingCoordinator<String> coordinator = new HordeDiggingCoordinator<>();

        assertTrue(coordinator.request("wall", 1, 9.0, 0.1F, 0));
        coordinator.tick(0);
        assertTrue(coordinator.request("wall", 1, 9.0, 0.1F, 1));

        assertEquals(0.2F, coordinator.progress("wall"), 0.0001F);

        coordinator.tick(1);

        assertEquals(0.2F, coordinator.progress("wall"), 0.0001F);
    }

    @Test
    void admitsPendingSitesOnTheNextTickWithoutANewHeartbeat() {
        HordeDiggingCoordinator<String> coordinator = new HordeDiggingCoordinator<>();

        assertTrue(coordinator.request("wall", 1, 9.0, 0.1F, 0));
        coordinator.tick(1);

        assertEquals(0.1F, coordinator.progress("wall"), 0.0001F);
        assertEquals(1, coordinator.activeCount());
    }

    @Test
    void admitsCurrentlyNearerPendingPointsWhenASlotFrees() {
        HordeDiggingCoordinator<String> coordinator = new HordeDiggingCoordinator<>();
        coordinator.setLimit(8);

        for (int i = 0; i < 8; i++) {
            assertTrue(coordinator.request("held" + i, 1, 10.0, 0.1F, 0));
        }
        coordinator.tick(0);
        assertTrue(coordinator.request("wasNear", 1, 1.0, 0.1F, 0));
        coordinator.complete("held0");
        for (int i = 1; i < 8; i++) {
            assertTrue(coordinator.request("held" + i, 1, 10.0, 0.1F, 1));
        }
        assertTrue(coordinator.request("wasNear", 1, 50.0, 0.1F, 1));
        assertTrue(coordinator.request("stillNear", 1, 2.0, 0.1F, 1));

        coordinator.tick(1);

        assertEquals(8, coordinator.activeCount());
        assertEquals(0.1F, coordinator.progress("stillNear"), 0.0001F);
        assertEquals(0.0F, coordinator.progress("wasNear"), 0.0001F);
    }

    @Test
    void admitsNearerPendingSitesFirst() {
        HordeDiggingCoordinator<String> coordinator = new HordeDiggingCoordinator<>();
        coordinator.setLimit(8);

        for (int i = 0; i < 8; i++) {
            assertTrue(coordinator.request("far" + i, 1, 20.0 + i, 0.1F, 0));
        }
        assertTrue(coordinator.request("near", 1, 1.0, 0.1F, 0));

        coordinator.tick(0);

        assertEquals(8, coordinator.activeCount());
        assertEquals(0.1F, coordinator.progress("near"), 0.0001F);
        assertEquals(0.1F, coordinator.progress("far0"), 0.0001F);
        assertEquals(0.0F, coordinator.progress("far7"), 0.0001F);
    }

    @Test
    void keepsProgressForTwoSecondsAfterLastParticipantLeaves() {
        HordeDiggingCoordinator<String> coordinator = new HordeDiggingCoordinator<>();

        assertTrue(coordinator.request("wall", 1, 9.0, 0.1F, 0));
        coordinator.tick(0);
        coordinator.tick(39);

        assertEquals(0.1F, coordinator.progress("wall"), 0.0001F);

        coordinator.tick(40);

        assertEquals(0.0F, coordinator.progress("wall"), 0.0001F);
    }

    @Test
    void treatsDeniedSitesAsUndiggableForFiveSeconds() {
        HordeDiggingCoordinator<String> coordinator = new HordeDiggingCoordinator<>();

        coordinator.deny("wall", 0);

        assertFalse(coordinator.request("wall", 1, 9.0, 0.1F, 0));
        assertFalse(coordinator.request("wall", 1, 9.0, 0.1F, 99));
        assertTrue(coordinator.request("wall", 1, 9.0, 0.1F, 100));
    }

    @Test
    void loweringTheCapKeepsExistingProgress() {
        HordeDiggingCoordinator<String> coordinator = new HordeDiggingCoordinator<>();

        for (int i = 0; i < 10; i++) {
            assertTrue(coordinator.request("wall" + i, 1, 9.0, 0.1F, 0));
        }
        coordinator.tick(0);
        coordinator.setLimit(8);

        assertEquals(10, coordinator.activeCount());
        for (int i = 0; i < 10; i++) {
            assertEquals(0.1F, coordinator.progress("wall" + i), 0.0001F);
            assertTrue(coordinator.request("wall" + i, 1, 9.0, 0.1F, 1));
        }
        assertTrue(coordinator.request("extra", 1, 1.0, 0.1F, 1));

        coordinator.tick(1);

        assertEquals(10, coordinator.activeCount());
        assertEquals(0.0F, coordinator.progress("extra"), 0.0001F);
    }

    @Test
    void existingSitesAcceptMoreParticipantsAfterCapDrop() {
        HordeDiggingCoordinator<String> coordinator = new HordeDiggingCoordinator<>();

        for (int i = 0; i < 10; i++) {
            assertTrue(coordinator.request("wall" + i, 1, 9.0, 0.1F, 0));
        }
        coordinator.tick(0);
        coordinator.setLimit(8);

        assertTrue(coordinator.request("wall0", 1, 9.0, 0.1F, 1));
        assertTrue(coordinator.request("wall0", 2, 9.0, 0.1F, 1));
        assertTrue(coordinator.request("wall0", 3, 9.0, 0.1F, 1));
        assertFalse(coordinator.request("wall0", 4, 9.0, 0.1F, 1));

        coordinator.tick(1);

        assertEquals(10, coordinator.activeCount());
        assertEquals(0.4F, coordinator.progress("wall0"), 0.0001F);
    }

    @Test
    void admitsNearerPendingWhenASlotFrees() {
        HordeDiggingCoordinator<String> coordinator = new HordeDiggingCoordinator<>();
        coordinator.setLimit(8);

        for (int i = 0; i < 8; i++) {
            assertTrue(coordinator.request("held" + i, 1, 10.0, 0.1F, 0));
        }
        coordinator.tick(0);
        coordinator.complete("held0");
        for (int i = 1; i < 8; i++) {
            assertTrue(coordinator.request("held" + i, 1, 10.0, 0.1F, 1));
        }
        assertTrue(coordinator.request("near", 1, 1.0, 0.1F, 1));
        assertTrue(coordinator.request("far", 1, 50.0, 0.1F, 1));

        coordinator.tick(1);

        assertEquals(8, coordinator.activeCount());
        assertEquals(0.1F, coordinator.progress("near"), 0.0001F);
        assertEquals(0.0F, coordinator.progress("far"), 0.0001F);
    }
}
