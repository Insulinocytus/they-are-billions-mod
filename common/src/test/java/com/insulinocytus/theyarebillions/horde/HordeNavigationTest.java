package com.insulinocytus.theyarebillions.horde;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class HordeNavigationTest {
    @Test
    void switchesBetweenSharedAndVanillaWithEightBlockHysteresis() {
        assertEquals(HordeNavigation.Mode.SHARED, HordeNavigation.mode(HordeNavigation.Mode.VANILLA, 41.0));
        assertEquals(HordeNavigation.Mode.VANILLA, HordeNavigation.mode(HordeNavigation.Mode.SHARED, 32.0));
        assertEquals(HordeNavigation.Mode.SHARED, HordeNavigation.mode(HordeNavigation.Mode.SHARED, 36.0));
        assertEquals(HordeNavigation.Mode.VANILLA, HordeNavigation.mode(HordeNavigation.Mode.VANILLA, 36.0));
    }

    @Test
    void followersShareAnImmutableTemplateButAdvanceIndependentCursors() {
        List<HordeNavigation.Waypoint> source = new ArrayList<>(List.of(
                new HordeNavigation.Waypoint(0, 64, 0), new HordeNavigation.Waypoint(4, 64, 0)));
        HordeNavigation.RouteTemplate route = new HordeNavigation.RouteTemplate(source);
        source.clear();

        int firstCursor = HordeNavigation.advanceCursor(route, 0);
        int secondCursor = 0;

        assertEquals(2, route.waypoints().size());
        assertEquals(1, firstCursor);
        assertEquals(0, secondCursor);
    }

    @Test
    void selectsTheFurthestDirectlyReachableForwardWaypointOnTheSameLevel() {
        HordeNavigation.RouteTemplate route = new HordeNavigation.RouteTemplate(List.of(
                new HordeNavigation.Waypoint(3, 64, 0),
                new HordeNavigation.Waypoint(7, 64, 0),
                new HordeNavigation.Waypoint(8, 65, 0),
                new HordeNavigation.Waypoint(9, 64, 0)));

        assertEquals(1, HordeNavigation.forwardWaypoint(route, 0, new HordeNavigation.Waypoint(0, 64, 0), i -> true));
        assertEquals(-1, HordeNavigation.forwardWaypoint(route, 0, new HordeNavigation.Waypoint(0, 64, 0), i -> false));
        assertEquals(1, HordeNavigation.connectionWaypoint(route, 0, new HordeNavigation.Waypoint(0, 64, 0)));
    }

    @Test
    void invalidatesRoutesOnlyForSharedRouteFailuresAndFreshnessChanges() {
        HordeNavigation.Waypoint target = new HordeNavigation.Waypoint(100, 64, 0);
        HordeNavigation.RouteTemplate route = new HordeNavigation.RouteTemplate(
                List.of(new HordeNavigation.Waypoint(4, 64, 0)), target, 10, 7);

        assertEquals(true, HordeNavigation.isRouteValid(route, target, 109, 7, 0, 0));
        assertEquals(false, HordeNavigation.isRouteValid(route, target, 110, 7, 0, 0));
        assertEquals(true, HordeNavigation.isRouteValid(
                route, new HordeNavigation.Waypoint(116, 64, 0), 20, 7, 0, 0));
        assertEquals(false, HordeNavigation.isRouteValid(
                route, new HordeNavigation.Waypoint(117, 64, 0), 20, 7, 0, 0));
        assertEquals(false, HordeNavigation.isRouteValid(route, target, 20, 8, 0, 0));
        assertEquals(false, HordeNavigation.isRouteValid(route, target, 20, 7, 1, 0));
        assertEquals(false, HordeNavigation.isRouteValid(route, target, 20, 7, 0, 3));
    }

    @Test
    void reportsBlockedOnlyAfterDistanceToTheWaypointStopsImprovingAndAnIndependentPathFails() {
        assertEquals(HordeNavigation.Recovery.MOVING, HordeNavigation.sampleProgress(0.25));
        assertEquals(HordeNavigation.Recovery.RETRY_INDEPENDENT, HordeNavigation.sampleProgress(0.24));
        assertEquals(HordeNavigation.Recovery.RETRY_INDEPENDENT, HordeNavigation.sampleProgress(-1.0));
        assertEquals(HordeNavigation.Recovery.CONNECTING, HordeNavigation.afterIndependentRetry(true));
        assertEquals(HordeNavigation.Recovery.BLOCKED, HordeNavigation.afterIndependentRetry(false));
    }

    @Test
    void eachFollowerReportsOnlyItsFirstFailureForARouteSegment() {
        HordeNavigation.RouteEntry route = new HordeNavigation.RouteEntry(new HordeNavigation.RouteTemplate(List.of(
                new HordeNavigation.Waypoint(4, 64, 0), new HordeNavigation.Waypoint(8, 64, 0))));
        HordeNavigation.Follower first = new HordeNavigation.Follower(0, 0);
        HordeNavigation.Follower second = new HordeNavigation.Follower(0, 0);

        assertEquals(true, first.reportFailure(route, 0));
        assertEquals(false, first.reportFailure(route, 0));
        assertEquals(true, first.reportFailure(route, 1));
        assertEquals(true, second.reportFailure(route, 0));

        first.clearFailure();
        assertEquals(true, first.reportFailure(route, 0));
    }

    @Test
    void staggersInitialPathfindingByEntityId() {
        assertEquals(101, HordeNavigation.staggeredPathTick(100, 1));
        assertEquals(102, HordeNavigation.staggeredPathTick(100, 2));
        assertEquals(101, HordeNavigation.staggeredPathTick(100, 21));
        assertEquals(119, HordeNavigation.staggeredPathTick(100, -1));
    }
    @Test
    void acceptsPartialPathsOnlyWhenTheyMoveTowardTheTarget() {
        HordeNavigation.Waypoint start = new HordeNavigation.Waypoint(0, 64, 0);
        HordeNavigation.Waypoint target = new HordeNavigation.Waypoint(100, 64, 0);

        assertEquals(true, HordeNavigation.makesForwardProgress(
                start, target, new HordeNavigation.Waypoint(16, 64, 0)));
        assertEquals(false, HordeNavigation.makesForwardProgress(start, target, start));
        assertEquals(false, HordeNavigation.makesForwardProgress(
                start, target, new HordeNavigation.Waypoint(-1, 64, 0)));
    }

}
