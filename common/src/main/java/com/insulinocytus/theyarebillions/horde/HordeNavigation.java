package com.insulinocytus.theyarebillions.horde;

import com.insulinocytus.theyarebillions.TheyAreBillions;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
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
    private static final int TERRAIN_CHECKS_PER_TICK = 16;
    private static final int PATH_RETRY_TICKS = 20;
    private static final int PROGRESS_SAMPLE_TICKS = 20;
    private static final double HUNT_RANGE_SQUARED =
            (double) HordePlanner.TICKET_RANGE * HordePlanner.TICKET_RANGE;
    private static final double SPEED = 1.0;
    private static final Map<Zombie, Follower> FOLLOWERS = new WeakHashMap<>();
    private static final Map<ServerLevel, RouteCache> ROUTES = new WeakHashMap<>();

    private HordeNavigation() {
    }

    public static void setActiveSiteLimit(int limit) {
        HordeBlockBreaking.setActiveSiteLimit(limit);
    }

    public static void onServerTick(MinecraftServer server) {
        HordeBlockBreaking.onServerTick(server);
    }

    public static void onLevelUnload(ServerLevel level) {
        ROUTES.remove(level);
        FOLLOWERS.entrySet().removeIf(entry -> entry.getKey().level() == level);
        HordeBlockBreaking.onLevelUnload(level);
    }

    static int sharedRouteCount() {
        int total = 0;
        for (RouteCache cache : ROUTES.values()) {
            total += cache.entries.size();
        }
        return total;
    }

    public static boolean tick(Zombie zombie) {
        if (!(zombie.level() instanceof ServerLevel level) || !HordeIdentity.isHordeMember(zombie)) {
            ((HordeMemberState) zombie).theyarebillions$restoreVanillaDoorBreaking();
            release(zombie);
            return false;
        }
        ((HordeMemberState) zombie).theyarebillions$disableVanillaDoorBreaking();
        if (!HordePlanner.isHordeNight(level.getDayTime())) {
            release(zombie);
            return false;
        }
        HordePlanner.GroupIdentity group = HordeIdentity.group(zombie);
        if (group == null) {
            release(zombie);
            return false;
        }
        Follower follower = FOLLOWERS.computeIfAbsent(zombie, ignored -> new Follower(
                level.getGameTime(), staggeredPathTick(zombie.tickCount, zombie.getId())));
        double followRange = zombie.getAttributeValue(Attributes.FOLLOW_RANGE);
        TargetingConditions conditions = TargetingConditions.forCombat().range(followRange);
        ServerPlayer target = nearestOwnedServerPlayer(
                group,
                level.players(),
                zombie.position(),
                player -> HordeSpawner.isValidPlayer(player) && isHuntTargetValid(player, zombie));
        if (zombie.getTarget() instanceof ServerPlayer player) {
            String playerId = player.getUUID().toString();
            boolean assigned = playerId.equals(follower.attackTargetId);
            if (!shouldKeepPlayerTarget(
                    group,
                    playerId,
                    assigned,
                    level.players().contains(player)
                            && HordeSpawner.isValidPlayer(player)
                            && conditions.test(zombie, player),
                    target != null)) {
                zombie.setTarget(null);
            }
            if (!assigned || zombie.getTarget() == null) {
                follower.attackTargetId = null;
            }
        } else {
            follower.attackTargetId = null;
        }
        if (target == null) {
            follower.useVanilla(zombie);
            return false;
        }
        long tick = level.getGameTime();
        RouteCache cache = ROUTES.computeIfAbsent(level, ignored -> new RouteCache());
        cache.maintain(level, tick);
        if (follower.digging != null) {
            if (target == null || !target.getUUID().toString().equals(follower.diggingTargetId)) {
                follower.useVanilla(zombie);
            } else {
                HordeBlockBreaking.TickResult result = HordeBlockBreaking.tick(level, zombie, follower.digging);
                if (result == HordeBlockBreaking.TickResult.ACTIVE) {
                    return true;
                }
                follower.digging = null;
                follower.diggingTargetId = null;
                follower.useVanilla(zombie);
                return true;
            }
        }

        double targetDistance = zombie.distanceTo(target);
        if (targetDistance <= followRange) {
            ServerPlayer attackTarget = nearestOwnedServerPlayer(
                    group,
                    level.players(),
                    zombie.position(),
                    player -> HordeSpawner.isValidPlayer(player) && conditions.test(zombie, player));
            if (attackTarget != null) {
                follower.useVanilla(zombie);
                follower.attackTargetId = attackTarget.getUUID().toString();
                zombie.setTarget(attackTarget);
            }
        }
        if (zombie.getTarget() != null) {
            follower.useVanilla(zombie);
            if (zombie.getTarget() instanceof ServerPlayer
                    && zombie.getNavigation().isDone()
                    && acquirePathfinding(cache, zombie, follower, tick)) {
                Path path = zombie.getNavigation().createPath(target.blockPosition(), 0);
                if ((path == null || !path.canReach())
                        && tryStartDigging(zombie, target.blockPosition(), follower, target)) {
                    return true;
                }
            }
            return false;
        }
        if (follower.vanillaFallback && followVanillaFallback(cache, zombie, target, follower, tick)) {
            return true;
        }
        double lodDistance = Double.MAX_VALUE;
        for (ServerPlayer player : level.players()) {
            if (HordeSpawner.isValidPlayer(player)) {
                lodDistance = Math.min(lodDistance, zombie.distanceTo(player));
            }
        }

        Mode nextMode = mode(follower.mode, lodDistance);
        if (nextMode == Mode.VANILLA) {
            follower.useVanilla(zombie);
            if (acquirePathfinding(cache, zombie, follower, tick)) {
                Path path = zombie.getNavigation().createPath(target, 0);
                if ((path == null || !path.canReach())
                        && tryStartDigging(zombie, target.blockPosition(), follower, target)) {
                    return true;
                }
                if (path != null) {
                    zombie.getNavigation().moveTo(path, SPEED);
                }
            }
            return false;
        }
        if (follower.mode != Mode.SHARED) {
            zombie.getNavigation().stop();
            follower.mode = Mode.SHARED;
            return true;
        }
        followShared(level, zombie, target, follower, cache, tick);
        return follower.mode == Mode.SHARED || follower.vanillaFallback;
    }

    static void release(Zombie zombie) {
        Follower follower = FOLLOWERS.remove(zombie);
        if (follower != null) {
            follower.releaseAttackTarget(zombie);
            follower.useVanilla(zombie);
            zombie.getNavigation().stop();
        }
    }

    private static void followShared(
            ServerLevel level,
            Zombie zombie,
            ServerPlayer target,
            Follower follower,
            RouteCache cache,
            long tick) {
        Waypoint targetPoint = Waypoint.of(target.blockPosition());
        boolean routeBuildFailed = false;
        if (follower.route == null
                || !follower.route.valid(targetPoint, tick, follower.cursor)) {
            RouteKey key = RouteKey.of(zombie, target.blockPosition());
            RouteResult result = cache.route(level, zombie, target.blockPosition(), key, tick);
            follower.route = result.route();
            routeBuildFailed = result.pathfindingFailed();
            follower.cursor = 0;
            follower.clearFailure();
        }
        RouteEntry route = follower.route;
        if (route == null) {
            if (routeBuildFailed) {
                follower.useVanilla(zombie);
                follower.vanillaFallback = true;
                followVanillaFallback(cache, zombie, target, follower, tick);
            }
            return;
        }

        while (follower.cursor < route.template.waypoints().size()
                && distanceSquared(zombie, route.template.waypoints().get(follower.cursor)) <= 2.25) {
            route.succeeded(follower.cursor);
            follower.clearFailure();
            follower.cursor = advanceCursor(route.template, follower.cursor);
        }
        if (follower.cursor >= route.template.waypoints().size()) {
            cache.evict(route);
            follower.clearRoute();
            return;
        }

        Waypoint position = Waypoint.of(zombie.blockPosition());
        double distanceToWaypoint = Math.sqrt(distanceSquared(zombie, route.template.waypoints().get(follower.cursor)));
        if (!follower.matchesSample(route, follower.cursor)) {
            follower.sample(route, follower.cursor, distanceToWaypoint, tick);
        } else if (tick - follower.lastSampleTick >= PROGRESS_SAMPLE_TICKS) {
            Recovery progress = sampleProgress(follower.sampleDistance - distanceToWaypoint);
            if (progress == Recovery.MOVING) {
                route.succeeded(follower.cursor);
                follower.clearFailure();
                follower.sample(route, follower.cursor, distanceToWaypoint, tick);
            } else {
                if (connect(cache, zombie, follower, route, tick, true, target)) {
                    follower.sample(route, follower.cursor, distanceToWaypoint, tick);
                }
                return;
            }
        }

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
                follower.vanillaFallback = true;
                followVanillaFallback(cache, zombie, target, follower, tick);
                return;
            }
            follower.cursor = connection;
            connect(cache, zombie, follower, route, tick, false, target);
        }
    }

    private static boolean followVanillaFallback(
            RouteCache cache, Zombie zombie, ServerPlayer target, Follower follower, long tick) {
        if (!follower.fallbackPathStarted) {
            if (!acquirePathfinding(cache, zombie, follower, tick)) {
                return true;
            }
            Path path = zombie.getNavigation().createPath(target, 0);
            if ((path == null || !path.canReach())
                    && tryStartDigging(zombie, target.blockPosition(), follower, target)) {
                follower.vanillaFallback = false;
                follower.fallbackPathStarted = false;
                return true;
            }
            if (path == null || !zombie.getNavigation().moveTo(path, SPEED)) {
                follower.vanillaFallback = false;
                follower.fallbackPathStarted = false;
                return false;
            }
            follower.fallbackPathStarted = true;
        }
        if (!zombie.getNavigation().isDone()) {
            return true;
        }
        follower.vanillaFallback = false;
        follower.fallbackPathStarted = false;
        return false;
    }

    private static boolean connect(
            RouteCache cache,
            Zombie zombie,
            Follower follower,
            RouteEntry route,
            long tick,
            boolean reportRouteFailure,
            ServerPlayer target) {
        if (!acquirePathfinding(cache, zombie, follower, tick)) {
            return false;
        }
        Waypoint waypoint = route.template.waypoints().get(follower.cursor);
        if (reportRouteFailure) {
            zombie.getNavigation().stop();
        }
        BlockPos waypointPos = new BlockPos(waypoint.x(), waypoint.y(), waypoint.z());
        Path path = zombie.getNavigation().createPath(waypointPos, 0);
        if (afterIndependentRetry(path != null && path.canReach()) == Recovery.BLOCKED) {
            if (tryStartDigging(zombie, waypointPos, follower, target)) {
                return true;
            }
            if (reportRouteFailure && follower.reportFailure(route, follower.cursor)) {
                route.failed(follower.cursor);
                logPathFailure(zombie, waypointPos);
            }
        } else {
            route.succeeded(follower.cursor);
            follower.clearFailure();
            zombie.getNavigation().moveTo(path, SPEED);
        }
        return true;
    }

    private static boolean tryStartDigging(
            Zombie zombie, BlockPos nextStep, Follower follower, ServerPlayer target) {
        HordeBlockBreaking.StartResult result =
                HordeBlockBreaking.start((ServerLevel) zombie.level(), zombie, nextStep);
        if (result.digging() != null) {
            follower.digging = result.digging();
            follower.diggingTargetId = target.getUUID().toString();
            return true;
        }
        return false;
    }

    private static boolean acquirePathfinding(RouteCache cache, Zombie zombie, Follower follower, long tick) {
        if (zombie.tickCount < follower.nextPathTick || !cache.acquire(tick)) {
            return false;
        }
        follower.nextPathTick = zombie.tickCount + PATH_RETRY_TICKS;
        return true;
    }

    private static boolean directlyReachable(Zombie zombie, Waypoint waypoint) {
        double dx = waypoint.x() + 0.5 - zombie.getX();
        double dz = waypoint.z() + 0.5 - zombie.getZ();
        int steps = Math.max(1, (int) Math.ceil(Math.sqrt(dx * dx + dz * dz) * 2.0));
        for (int i = 1; i <= steps; i++) {
            double progress = (double) i / steps;
            double x = zombie.getX() + dx * progress;
            double z = zombie.getZ() + dz * progress;
            if (!zombie.level().noCollision(
                            zombie,
                            zombie.getBoundingBox().move(
                                    x - zombie.getX(), waypoint.y() - zombie.getY(), z - zombie.getZ()))
                    || !zombie.level().loadedAndEntityCanStandOn(
                            BlockPos.containing(x, waypoint.y() - 1, z), zombie)) {
                return false;
            }
        }
        return true;
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

    static boolean shouldKeepPlayerTarget(
            HordePlanner.GroupIdentity group,
            String playerId,
            boolean assigned,
            boolean attackable,
            boolean hasValidOwnedPlayer) {
        if (!hasValidOwnedPlayer) {
            return !assigned;
        }
        return group.memberIds().contains(playerId) && (!assigned || attackable);
    }

    static boolean isHuntTargetValid(ServerPlayer target, Zombie zombie) {
        return target != null
                && isHuntTargetValid(
                        target.isAlive(),
                        !target.isRemoved(),
                        target.level() == zombie.level(),
                        HordeSpawner.isValidPlayer(target),
                        zombie.distanceToSqr(target));
    }

    static boolean isHuntTargetValid(
            boolean alive,
            boolean present,
            boolean sameLevel,
            boolean validPlayer,
            double distanceSquared) {
        return alive && present && sameLevel && validPlayer && distanceSquared <= HUNT_RANGE_SQUARED;
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

    static int staggeredPathTick(int tickCount, int entityId) {
        return tickCount + (int) Math.floorMod((long) entityId - tickCount, PATH_RETRY_TICKS);
    }

    static Recovery sampleProgress(double distanceImprovement) {
        return distanceImprovement >= 0.25 ? Recovery.MOVING : Recovery.RETRY_INDEPENDENT;
    }

    static Recovery afterIndependentRetry(boolean pathFound) {
        return pathFound ? Recovery.CONNECTING : Recovery.BLOCKED;
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
    static boolean makesForwardProgress(Waypoint start, Waypoint target, Waypoint end) {
        return distanceSquared(end, target) < distanceSquared(start, target);
    }


    private static long terrainFingerprint(ServerLevel level, List<Waypoint> waypoints) {
        long fingerprint = 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (Waypoint waypoint : waypoints) {
            pos.set(waypoint.x(), waypoint.y() - 1, waypoint.z());
            fingerprint = 31 * fingerprint + level.getBlockState(pos).hashCode();
            pos.set(waypoint.x(), waypoint.y(), waypoint.z());
            fingerprint = 31 * fingerprint + level.getBlockState(pos).hashCode();
            pos.set(waypoint.x(), waypoint.y() + 1, waypoint.z());
            fingerprint = 31 * fingerprint + level.getBlockState(pos).hashCode();
        }
        return fingerprint;
    }

    private static void logPathFailure(Zombie zombie, BlockPos to) {
        if (TheyAreBillions.LOGGER.isDebugEnabled()) {
            TheyAreBillions.LOGGER.debug("Horde path failed from {} toward {}", zombie.blockPosition(), to);
        }
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

    private record RouteResult(RouteEntry route, boolean pathfindingFailed) {
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
        private boolean vanillaFallback;
        private boolean fallbackPathStarted;
        private String attackTargetId;
        private HordeBlockBreaking.Digging digging;
        private String diggingTargetId;

        Follower(long tick, int nextPathTick) {
            lastSampleTick = tick;
            this.nextPathTick = nextPathTick;
        }

        private void useVanilla(Zombie zombie) {
            if (digging != null) {
                HordeBlockBreaking.stop((ServerLevel) zombie.level(), zombie, digging);
                digging = null;
                diggingTargetId = null;
            }
            if (mode == Mode.SHARED || vanillaFallback) {
                zombie.getNavigation().stop();
            }
            mode = Mode.VANILLA;
            vanillaFallback = false;
            fallbackPathStarted = false;
            clearRoute();
        }

        private void releaseAttackTarget(Zombie zombie) {
            if (attackTargetId != null
                    && zombie.getTarget() instanceof ServerPlayer player
                    && attackTargetId.equals(player.getUUID().toString())) {
                zombie.setTarget(null);
            }
            attackTargetId = null;
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

        private void validateTerrain(ServerLevel level) {
            terrainValid = template.terrainFingerprint() == terrainFingerprint(level, template.waypoints());
            if (!terrainValid) {
                invalid = true;
            }
        }

        private boolean valid(Waypoint target, long tick, int cursor) {
            if (invalid) {
                return false;
            }
            int failureCount = cursor < failures.length ? failures[cursor] : 3;
            return isRouteValid(
                    template,
                    target,
                    tick,
                    terrainValid ? template.terrainFingerprint() : Long.MIN_VALUE,
                    cursor,
                    failureCount);
        }
    }

    private static final class RouteCache {
        private final Map<RouteKey, RouteEntry> entries = new HashMap<>();
        private final ArrayDeque<RouteEntry> terrainChecks = new ArrayDeque<>();
        private long maintenanceTick = Long.MIN_VALUE;
        private long permitTick = Long.MIN_VALUE;
        private int permits;

        private void maintain(ServerLevel level, long tick) {
            if (maintenanceTick == tick) {
                return;
            }
            maintenanceTick = tick;
            entries.values().removeIf(entry -> entry.invalid
                    || tick - entry.template.createdTick() >= ROUTE_TTL_TICKS);
            int checks = Math.min(TERRAIN_CHECKS_PER_TICK, terrainChecks.size());
            for (int i = 0; i < checks; i++) {
                RouteEntry entry = terrainChecks.removeFirst();
                if (entry.invalid || tick - entry.template.createdTick() >= ROUTE_TTL_TICKS) {
                    continue;
                }
                entry.validateTerrain(level);
                if (!entry.invalid) {
                    terrainChecks.addLast(entry);
                }
            }
        }

        private RouteResult route(ServerLevel level, Zombie zombie, BlockPos target, RouteKey key, long tick) {
            Waypoint position = Waypoint.of(zombie.blockPosition());
            Waypoint targetPoint = Waypoint.of(target);
            RouteEntry existing = entries.get(key);
            if (existing != null) {
                if (!existing.valid(targetPoint, tick, 0)) {
                    entries.remove(key);
                    existing = null;
                } else if (connectionWaypoint(existing.template, 0, position) >= 0
                        && makesForwardProgress(position, targetPoint, existing.template.waypoints().getLast())) {
                    return new RouteResult(existing, false);
                }
            }
            if (!acquire(tick)) {
                return new RouteResult(null, false);
            }
            Path path = zombie.getNavigation().createPath(target, 0);
            if (path == null || path.getNodeCount() == 0) {
                return new RouteResult(null, true);
            }
            List<Waypoint> waypoints = java.util.stream.IntStream.range(0, path.getNodeCount())
                    .mapToObj(path::getNodePos)
                    .map(Waypoint::of)
                    .toList();
            if (!makesForwardProgress(position, targetPoint, waypoints.getLast())) {
                return new RouteResult(null, true);
            }
            RouteTemplate template = new RouteTemplate(
                    waypoints, targetPoint, tick, terrainFingerprint(level, waypoints));
            RouteEntry created = new RouteEntry(template);
            terrainChecks.addLast(created);
            if (existing == null) {
                entries.put(key, created);
            }
            return new RouteResult(created, false);
        }

        private void evict(RouteEntry route) {
            entries.values().removeIf(entry -> entry == route);
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
