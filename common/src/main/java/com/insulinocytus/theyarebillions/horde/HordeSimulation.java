package com.insulinocytus.theyarebillions.horde;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Zombie;

public final class HordeSimulation {
    private static final double NEAR_DISTANCE_SQUARED = 24.0 * 24.0;
    private static final double NEAR_RETURN_DISTANCE_SQUARED = 20.0 * 20.0;
    private static final double FAR_DISTANCE_SQUARED = 64.0 * 64.0;
    private static final double FAR_RETURN_DISTANCE_SQUARED = 60.0 * 60.0;
    private static final Map<Zombie, State> STATES = new WeakHashMap<>();

    private HordeSimulation() {
    }

    public static int collisionNeighborLimit(Zombie zombie) {
        State state = state(zombie);
        if (state == null) {
            return Integer.MAX_VALUE;
        }
        return scheduled(state.tier, state.tick, zombie.getId(), intervalMultiplier(zombie))
                ? state.tier.collisionNeighborLimit()
                : 0;
    }

    public static int alignPathRecalculation(Zombie zombie, int vanillaDelay) {
        if (vanillaDelay > 0) {
            return vanillaDelay;
        }
        State state = state(zombie);
        if (state == null) {
            return vanillaDelay;
        }
        return delayUntilScheduled(state.tier, state.tick, zombie.getId(), intervalMultiplier(zombie));
    }

    private static int intervalMultiplier(Zombie zombie) {
        return HordePerformance.simulationIntervalMultiplier(((ServerLevel) zombie.level()).getServer());
    }

    static Tier tier(Tier current, double distanceSquared) {
        if (current == null) {
            if (distanceSquared <= NEAR_DISTANCE_SQUARED) {
                return Tier.NEAR;
            }
            return distanceSquared <= FAR_DISTANCE_SQUARED ? Tier.MEDIUM : Tier.FAR;
        }
        return switch (current) {
            case NEAR -> distanceSquared > FAR_DISTANCE_SQUARED
                    ? Tier.FAR
                    : distanceSquared > NEAR_DISTANCE_SQUARED ? Tier.MEDIUM : Tier.NEAR;
            case MEDIUM -> distanceSquared <= NEAR_RETURN_DISTANCE_SQUARED
                    ? Tier.NEAR
                    : distanceSquared > FAR_DISTANCE_SQUARED ? Tier.FAR : Tier.MEDIUM;
            case FAR -> distanceSquared <= NEAR_RETURN_DISTANCE_SQUARED
                    ? Tier.NEAR
                    : distanceSquared <= FAR_RETURN_DISTANCE_SQUARED ? Tier.MEDIUM : Tier.FAR;
        };
    }

    static boolean scheduled(Tier tier, long tick, int entityId) {
        return scheduled(tier, tick, entityId, 1);
    }

    static boolean scheduled(Tier tier, long tick, int entityId, int intervalMultiplier) {
        return delayUntilScheduled(tier, tick, entityId, intervalMultiplier) == 0;
    }

    static int delayUntilScheduled(Tier tier, long tick, int entityId) {
        return delayUntilScheduled(tier, tick, entityId, 1);
    }

    static int delayUntilScheduled(Tier tier, long tick, int entityId, int intervalMultiplier) {
        int interval = tier == Tier.NEAR ? 1 : tier.interval * Math.max(1, intervalMultiplier);
        return (int) Math.floorMod((long) entityId - tick, interval);
    }

    private static State state(Zombie zombie) {
        if (!(zombie.level() instanceof ServerLevel level) || !HordeIdentity.isHordeMember(zombie)) {
            STATES.remove(zombie);
            return null;
        }
        long tick = level.getGameTime();
        State state = STATES.computeIfAbsent(zombie, ignored -> new State());
        if (state.tick == tick) {
            return state;
        }
        double nearestDistanceSquared = Double.POSITIVE_INFINITY;
        for (ServerPlayer player : level.players()) {
            if (HordeSpawner.isValidPlayer(player)) {
                nearestDistanceSquared = Math.min(nearestDistanceSquared, zombie.distanceToSqr(player));
            }
        }
        state.tier = tier(state.tier, nearestDistanceSquared);
        state.tick = tick;
        return state;
    }

    enum Tier {
        NEAR(Integer.MAX_VALUE, 1),
        MEDIUM(8, 2),
        FAR(4, 4);

        private final int neighborLimit;
        private final int interval;

        Tier(int neighborLimit, int interval) {
            this.neighborLimit = neighborLimit;
            this.interval = interval;
        }

        int collisionNeighborLimit() {
            return neighborLimit;
        }
    }

    private static final class State {
        private Tier tier;
        private long tick = Long.MIN_VALUE;
    }
}
