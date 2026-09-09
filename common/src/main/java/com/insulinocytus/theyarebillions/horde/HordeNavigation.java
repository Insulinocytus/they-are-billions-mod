package com.insulinocytus.theyarebillions.horde;

import com.insulinocytus.theyarebillions.mixin.PathNavigationAccessor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

public final class HordeNavigation {
    static final int ROUTE_TTL_TICKS = 100;
    private static final int PATHFINDING_PER_TICK = 4;
    private static final int PATH_RETRY_TICKS = 20;
    private static final int PROGRESS_SAMPLE_TICKS = 20;
    private static final double SPEED = 1.0;
    private static final Map<Zombie, Follower> FOLLOWERS = new WeakHashMap<>();
    private static final Map<ServerLevel, RouteCache> ROUTES = new WeakHashMap<>();

    private HordeNavigation() {
    }

    public static void tick(Zombie zombie) {
        if (!(zombie.level() instanceof ServerLevel level) || !HordeIdentity.isHordeMember(zombie)) {
            FOLLOWERS.remove(zombie);
            return;
        }
        HordePlanner.GroupIdentity group = HordeIdentity.group(zombie);
        if (group == null) {
            Follower follower = FOLLOWERS.remove(zombie);
            if (follower != null) {
                follower.useVanilla(zombie);
            }
            return;
        }
        Follower follower = FOLLOWERS.computeIfAbsent(
                zombie, ignored -> new Follower(level.getGameTime()));
        ServerPlayer target = nearestOwnedServerPlayer(
                group, level.players(), zombie.position(), HordeSpawner::isValidPlayer);
        if (target == null) {
            follower.useVanilla(zombie);
            return;
        }

        double distance = zombie.distanceTo(target);
        double followRange = zombie.getAttributeValue(Attributes.FOLLOW_RANGE);
        if (distance <= followRange) {
            TargetingConditions conditions = TargetingConditions.forCombat().range(followRange);
            ServerPlayer attackTarget = nearestOwnedServerPlayer(
                    group,
                    level.players(),
                    zombie.position(),
                    player -> HordeSpawner.isValidPlayer(player) && conditions.test(zombie, player));
            follower.useVanilla(zombie);
            if (attackTarget != null) {
                zombie.setTarget(attackTarget);
            }
            return;
        }
        if (zombie.getTarget() != null) {
            follower.useVanilla(zombie);
            return;
        }

        Mode nextMode = mode(follower.mode, distance);
        if (nextMode == Mode.VANILLA) {
            follower.useVanilla(zombie);
            if (zombie.tickCount >= follower.nextPathTick) {
                zombie.getNavigation().moveTo(target, SPEED);
                follower.nextPathTick = zombie.tickCount + PATH_RETRY_TICKS;
            }
            return;
        }
        if (follower.mode != Mode.SHARED) {
            zombie.getNavigation().stop();
            follower.mode = Mode.SHARED;
        }
        followShared(level, zombie, target, follower);
    }

    private static void followShared(ServerLevel level, Zombie zombie, ServerPlayer target, Follower follower) {
        long tick = level.getGameTime();
        RouteCache cache = ROUTES.computeIfAbsent(level, ignored -> new RouteCache());
        Waypoint targetPoint = Waypoint.of(target.blockPosition());
        if (follower.route == null
                || !cache.valid(level, follower.route, targetPoint, tick, follower.cursor)) {
            RouteKey key = RouteKey.of(zombie, target.blockPosition());
            follower.route = cache.route(level, zombie, target.blockPosition(), key, tick);
            follower.cursor = 0;
            follower.clearFailure();
        }
        RouteEntry route = follower.route;
        if (route == null) {
            return;
        }

        while (follower.cursor < route.template.waypoints().size()
                && distanceSquared(zombie, route.template.waypoints().get(follower.cursor)) <= 2.25) {
            route.succeeded(follower.cursor);
            follower.clearFailure();
            follower.cursor = advanceCursor(route.template, follower.cursor);
        }
        if (follower.cursor >= route.template.waypoints().size()) {
            follower.clearRoute();
            return;
        }

        Waypoint position = Waypoint.of(zombie.blockPosition());
        int direct = forwardWaypoint(
                route.template,
                follower.cursor,
                position,
                index -> directlyReachable(zombie, route.template.waypoints().get(index)));
        if (direct >= 0) {
            follower.cursor = direct;
            Waypoint waypoint = route.template.waypoints().get(direct);
            zombie.getMoveControl().setWantedPosition(waypoint.x() + 0.5, waypoint.y(), waypoint.z() + 0.5, SPEED);
        } else {
            int connection = connectionWaypoint(route.template, follower.cursor, position);
            if (connection < 0) {
                follower.useVanilla(zombie);
                zombie.getNavigation().moveTo(target, SPEED);
                follower.nextPathTick = zombie.tickCount + PATH_RETRY_TICKS;
                return;
            }
            follower.cursor = connection;
            connect(level, zombie, follower, route, tick);
        }

        double distanceToWaypoint = Math.sqrt(distanceSquared(zombie, route.template.waypoints().get(follower.cursor)));
        if (!follower.matchesSample(route, follower.cursor)) {
            follower.sample(route, follower.cursor, distanceToWaypoint, tick);
        } else if (tick - follower.lastSampleTick >= PROGRESS_SAMPLE_TICKS) {
            Recovery progress = sampleProgress(follower.sampleDistance - distanceToWaypoint);
            if (progress == Recovery.MOVING) {
                route.succeeded(follower.cursor);
                follower.clearFailure();
            } else {
                connect(level, zombie, follower, route, tick);
            }
            follower.sample(route, follower.cursor, distanceToWaypoint, tick);
        }
    }

    private static void connect(
            ServerLevel level, Zombie zombie, Follower follower, RouteEntry route, long tick) {
        if (zombie.tickCount < follower.nextPathTick || !ROUTES.get(level).acquire(tick)) {
            return;
        }
        follower.nextPathTick = zombie.tickCount + PATH_RETRY_TICKS;
        Waypoint waypoint = route.template.waypoints().get(follower.cursor);
        Path path = zombie.getNavigation().createPath(new BlockPos(waypoint.x(), waypoint.y(), waypoint.z()), 0);
        if (afterIndependentRetry(path != null && path.canReach()) == Recovery.BLOCKED) {
            if (follower.reportFailure(route, follower.cursor)) {
                route.failed(follower.cursor);
            }
        } else {
            route.succeeded(follower.cursor);
            follower.clearFailure();
            zombie.getNavigation().moveTo(path, SPEED);
        }
    }

    private static boolean directlyReachable(Zombie zombie, Waypoint waypoint) {
        Vec3 from = zombie.position();
        Vec3 to = new Vec3(waypoint.x() + 0.5, waypoint.y(), waypoint.z() + 0.5);
        return ((PathNavigationAccessor) zombie.getNavigation()).theyarebillions$canMoveDirectly(from, to);
    }

    private static ServerPlayer nearestOwnedServerPlayer(
            HordePlanner.GroupIdentity group,
            List<ServerPlayer> players,
            Vec3 position,
            Predicate<ServerPlayer> eligible) {
        ServerPlayer nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (ServerPlayer player : players) {
            if (!group.memberIds().contains(player.getUUID().toString()) || !eligible.test(player)) {
                continue;
            }
            double distance = player.distanceToSqr(position);
            if (distance < nearestDistance) {
                nearest = player;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    static Mode mode(Mode current, double distance) {
        if (distance > 40.0) {
            return Mode.SHARED;
        }
        if (distance <= 32.0) {
            return Mode.VANILLA;
        }
        return current;
    }

    static int advanceCursor(RouteTemplate route, int cursor) {
        return Math.min(cursor + 1, route.waypoints().size());
    }

    static int forwardWaypoint(RouteTemplate route, int cursor, Waypoint position, IntPredicate directlyReachable) {
        int selected = -1;
        for (int i = cursor; i < route.waypoints().size(); i++) {
            Waypoint waypoint = route.waypoints().get(i);
            long dx = waypoint.x() - position.x();
            long dz = waypoint.z() - position.z();
            if (waypoint.y() == position.y() && dx * dx + dz * dz <= 64 && directlyReachable.test(i)) {
                selected = i;
            }
        }
        return selected;
    }

    static int connectionWaypoint(RouteTemplate route, int cursor, Waypoint position) {
        int selected = -1;
        for (int i = cursor; i < route.waypoints().size(); i++) {
            Waypoint waypoint = route.waypoints().get(i);
            long dx = waypoint.x() - position.x();
            long dy = waypoint.y() - position.y();
            long dz = waypoint.z() - position.z();
            if (dx * dx + dy * dy + dz * dz <= 64) {
                selected = i;
            }
        }
        return selected;
    }

    static boolean isRouteValid(
            RouteTemplate route, Waypoint target, long tick, long terrainFingerprint, int cursor, int failures) {
        return distanceSquared(route.target(), target) <= 16L * 16L
                && tick - route.createdTick() < ROUTE_TTL_TICKS
                && route.terrainFingerprint() == terrainFingerprint
                && cursor < route.waypoints().size()
                && failures < 3;
    }

    static Recovery sampleProgress(double distanceImprovement) {
        return distanceImprovement >= 0.25 ? Recovery.MOVING : Recovery.RETRY_INDEPENDENT;
    }

    static Recovery afterIndependentRetry(boolean pathFound) {
        return pathFound ? Recovery.CONNECTING : Recovery.BLOCKED;
    }

    static HordePlanner.PlayerRef nearestOwnedPlayer(
            HordePlanner.GroupIdentity group,
            List<HordePlanner.PlayerRef> players,
            Vec3 position) {
        HordePlanner.PlayerRef nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (HordePlanner.PlayerRef player : players) {
            if (!group.memberIds().contains(player.id())) {
                continue;
            }
            double dx = player.x() - position.x;
            double dy = player.y() - position.y;
            double dz = player.z() - position.z;
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance < nearestDistance) {
                nearest = player;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private static double distanceSquared(Zombie zombie, Waypoint waypoint) {
        return zombie.distanceToSqr(waypoint.x() + 0.5, waypoint.y(), waypoint.z() + 0.5);
    }

    private static long distanceSquared(Waypoint first, Waypoint second) {
        long dx = first.x() - second.x();
        long dy = first.y() - second.y();
        long dz = first.z() - second.z();
        return dx * dx + dy * dy + dz * dz;
    }

    private static long terrainFingerprint(ServerLevel level, List<Waypoint> waypoints) {
        long fingerprint = 1;
        for (Waypoint waypoint : waypoints) {
            BlockPos pos = new BlockPos(waypoint.x(), waypoint.y(), waypoint.z());
            fingerprint = 31 * fingerprint + level.getBlockState(pos.below()).hashCode();
            fingerprint = 31 * fingerprint + level.getBlockState(pos).hashCode();
            fingerprint = 31 * fingerprint + level.getBlockState(pos.above()).hashCode();
        }
        return fingerprint;
    }

    enum Mode {
        VANILLA,
        SHARED
    }

    enum Recovery {
        MOVING,
        RETRY_INDEPENDENT,
        CONNECTING,
        BLOCKED
    }

    record Waypoint(int x, int y, int z) {
        static Waypoint of(BlockPos pos) {
            return new Waypoint(pos.getX(), pos.getY(), pos.getZ());
        }
    }

    record RouteTemplate(List<Waypoint> waypoints, Waypoint target, long createdTick, long terrainFingerprint) {
        RouteTemplate {
            waypoints = List.copyOf(waypoints);
        }

        RouteTemplate(List<Waypoint> waypoints) {
            this(waypoints, new Waypoint(0, 0, 0), 0, 0);
        }
    }

    private record RouteKey(
            int startX,
            int startY,
            int startZ,
            int targetX,
            int targetY,
            int targetZ,
            String navigationClassName,
            int width,
            int height) {
        static RouteKey of(Zombie zombie, BlockPos target) {
            BlockPos start = zombie.blockPosition();
            return new RouteKey(
                    start.getX() >> 2,
                    start.getY() >> 2,
                    start.getZ() >> 2,
                    target.getX() >> 4,
                    target.getY() >> 3,
                    target.getZ() >> 4,
                    zombie.getNavigation().getClass().getName(),
                    Math.round(zombie.getBbWidth() * 100),
                    Math.round(zombie.getBbHeight() * 100));
        }
    }

    static final class Follower {
        private Mode mode = Mode.VANILLA;
        private RouteEntry route;
        private int cursor;
        private int nextPathTick;
        private long lastSampleTick;
        private RouteEntry sampleRoute;
        private int sampleCursor = -1;
        private double sampleDistance;
        private RouteEntry failedRoute;
        private int failedCursor = -1;

        Follower(long tick) {
            lastSampleTick = tick;
        }

        private void useVanilla(Zombie zombie) {
            if (mode == Mode.SHARED) {
                zombie.getNavigation().stop();
            }
            mode = Mode.VANILLA;
            clearRoute();
        }

        private void clearRoute() {
            route = null;
            cursor = 0;
            sampleRoute = null;
            sampleCursor = -1;
            clearFailure();
        }

        private boolean matchesSample(RouteEntry route, int cursor) {
            return sampleRoute == route && sampleCursor == cursor;
        }

        private void sample(RouteEntry route, int cursor, double distance, long tick) {
            lastSampleTick = tick;
            sampleRoute = route;
            sampleCursor = cursor;
            sampleDistance = distance;
        }

        boolean reportFailure(RouteEntry route, int cursor) {
            if (failedRoute == route && failedCursor == cursor) {
                return false;
            }
            failedRoute = route;
            failedCursor = cursor;
            return true;
        }

        void clearFailure() {
            failedRoute = null;
            failedCursor = -1;
        }
    }

    static final class RouteEntry {
        private final RouteTemplate template;
        private final int[] failures;
        private long validatedTick = Long.MIN_VALUE;
        private boolean terrainValid = true;
        private boolean invalid;

        RouteEntry(RouteTemplate template) {
            this.template = template;
            failures = new int[template.waypoints().size()];
        }

        private void failed(int segment) {
            failures[segment]++;
            if (failures[segment] >= 3) {
                invalid = true;
            }
        }

        private void succeeded(int segment) {
            failures[segment] = 0;
        }
    }

    private static final class RouteCache {
        private final Map<RouteKey, RouteEntry> entries = new HashMap<>();
        private long permitTick = Long.MIN_VALUE;
        private int permits;

        private RouteEntry route(ServerLevel level, Zombie zombie, BlockPos target, RouteKey key, long tick) {
            entries.values().removeIf(entry -> entry.invalid
                    || tick - entry.template.createdTick() >= ROUTE_TTL_TICKS);
            RouteEntry existing = entries.get(key);
            if (existing != null) {
                if (valid(level, existing, Waypoint.of(target), tick, 0)
                        && connectionWaypoint(existing.template, 0, Waypoint.of(zombie.blockPosition())) >= 0) {
                    return existing;
                }
                entries.remove(key);
            }
            if (!acquire(tick)) {
                return null;
            }
            Path path = zombie.getNavigation().createPath(target, 0);
            if (path == null || path.getNodeCount() == 0) {
                return null;
            }
            List<Waypoint> waypoints = java.util.stream.IntStream.range(0, path.getNodeCount())
                    .mapToObj(path::getNodePos)
                    .map(Waypoint::of)
                    .toList();
            RouteTemplate template = new RouteTemplate(
                    waypoints, Waypoint.of(target), tick, terrainFingerprint(level, waypoints));
            RouteEntry created = new RouteEntry(template);
            entries.put(key, created);
            return created;
        }

        private boolean valid(ServerLevel level, RouteEntry route, Waypoint target, long tick, int cursor) {
            if (route.invalid) {
                return false;
            }
            if (route.validatedTick != tick) {
                route.validatedTick = tick;
                route.terrainValid = route.template.terrainFingerprint()
                        == terrainFingerprint(level, route.template.waypoints());
            }
            int failures = cursor < route.failures.length ? route.failures[cursor] : 3;
            return isRouteValid(
                    route.template,
                    target,
                    tick,
                    route.terrainValid ? route.template.terrainFingerprint() : Long.MIN_VALUE,
                    cursor,
                    failures);
        }

        private boolean acquire(long tick) {
            if (permitTick != tick) {
                permitTick = tick;
                permits = PATHFINDING_PER_TICK;
            }
            if (permits == 0) {
                return false;
            }
            permits--;
            return true;
        }
    }
}
