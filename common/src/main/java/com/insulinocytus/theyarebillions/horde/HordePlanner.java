package com.insulinocytus.theyarebillions.horde;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleSupplier;
import java.util.stream.Collectors;

public final class HordePlanner {
    public static final int DEFAULT_TARGET = 1000;
    public static final int MIN_TARGET = 0;
    public static final int MAX_TARGET = 1000;
    public static final int NIGHT_START = 13000;
    public static final int RAMP_END = 18000;
    public static final int NIGHT_END = 23000;
    public static final int MAX_SUCCESSFUL_SPAWNS_PER_TICK = 4;
    public static final int MAX_FAILED_SPAWN_ATTEMPTS_PER_TICK = 8;
    public static final int SPAWN_RANGE_MIN = 128;
    public static final int SPAWN_RANGE_MAX = 144;
    public static final int GROUP_RANGE = 128;
    private static final long DAY_LENGTH = 24000L;

    private HordePlanner() {
    }

    public static boolean isHordeNight(long dayTime) {
        int time = dayTimeOfDay(dayTime);
        return time >= NIGHT_START && time < NIGHT_END;
    }

    public static boolean isValidPlayer(boolean fakePlayer, boolean survival, boolean adventure) {
        return !fakePlayer && (survival || adventure);
    }

    public static Plan plan(Snapshot snapshot) {
        return plan(snapshot, () -> 0.0);
    }

    public static Plan plan(Snapshot snapshot, DoubleSupplier newDirectionRadians) {
        long worldDay = worldDay(snapshot.dayTime());
        if (!snapshot.overworld()) {
            return Plan.none(retainOrReset(snapshot.night(), worldDay));
        }
        boolean night = isHordeNight(snapshot.dayTime());
        List<PlayerGroup> groups = night ? connectedGroups(snapshot.validPlayers()) : List.of();
        NightState nightState = nextNightState(snapshot.night(), worldDay, groups, night, newDirectionRadians);
        int target = Math.clamp(snapshot.hordeTarget(), MIN_TARGET, MAX_TARGET);
        if (!night || snapshot.peaceful() || snapshot.validPlayers().isEmpty() || target == 0) {
            return Plan.none(nightState);
        }
        int desired = desiredCount(snapshot.dayTime(), target);
        int remaining = Math.max(0, desired - snapshot.ordinaryZombieCount());
        int quota = Math.min(MAX_SUCCESSFUL_SPAWNS_PER_TICK, remaining);
        if (quota == 0) {
            return new Plan(desired, 0, 0, List.of(), nightState);
        }
        int[] shares = evenSplit(remaining, groups.size());
        int[] tickQuotas = tickQuotas(shares, quota);
        List<GroupPlan> groupPlans = new ArrayList<>(groups.size());
        for (int i = 0; i < groups.size(); i++) {
            PlayerGroup group = groups.get(i);
            groupPlans.add(new GroupPlan(
                    group.key(),
                    new Sector(
                            group.originX(),
                            group.originZ(),
                            nightState.directions().get(group.key()),
                            SPAWN_RANGE_MIN,
                            SPAWN_RANGE_MAX),
                    shares[i],
                    tickQuotas[i]));
        }
        return new Plan(desired, quota, MAX_FAILED_SPAWN_ATTEMPTS_PER_TICK, groupPlans, nightState);
    }

    public static int desiredCount(long dayTime, int target) {
        int time = dayTimeOfDay(dayTime);
        if (time < NIGHT_START || time >= NIGHT_END) {
            return 0;
        }
        if (time >= RAMP_END) {
            return target;
        }
        return (int) ((long) target * (time - NIGHT_START) / (RAMP_END - NIGHT_START));
    }

    private static List<PlayerGroup> connectedGroups(List<PlayerRef> players) {
        int n = players.size();
        if (n == 0) {
            return List.of();
        }
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }
        for (int i = 0; i < n; i++) {
            PlayerRef left = players.get(i);
            for (int j = i + 1; j < n; j++) {
                if (withinGroupRange(left, players.get(j))) {
                    union(parent, i, j);
                }
            }
        }
        Map<Integer, List<PlayerRef>> clustered = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            clustered.computeIfAbsent(find(parent, i), key -> new ArrayList<>()).add(players.get(i));
        }
        List<PlayerGroup> groups = new ArrayList<>(clustered.size());
        for (List<PlayerRef> members : clustered.values()) {
            members.sort(Comparator.comparing(PlayerRef::id));
            groups.add(PlayerGroup.of(members));
        }
        groups.sort(Comparator.comparing(PlayerGroup::key));
        return groups;
    }

    private static NightState nextNightState(
            NightState previous,
            long worldDay,
            List<PlayerGroup> groups,
            boolean night,
            DoubleSupplier newDirectionRadians) {
        if (!night || groups.isEmpty()) {
            return retainOrReset(previous, worldDay);
        }
        Map<String, Double> previousDirections =
                previous.worldDay() == worldDay ? previous.directions() : Map.of();
        Map<String, Double> directions = new LinkedHashMap<>();
        for (PlayerGroup group : groups) {
            Double existing = previousDirections.get(group.key());
            directions.put(group.key(), existing != null ? existing : newDirectionRadians.getAsDouble());
        }
        return new NightState(worldDay, directions);
    }

    private static NightState retainOrReset(NightState previous, long worldDay) {
        if (previous.worldDay() == worldDay) {
            return previous;
        }
        return new NightState(worldDay, Map.of());
    }

    private static int[] evenSplit(int remaining, int groups) {
        int[] shares = new int[groups];
        int base = remaining / groups;
        int extra = remaining % groups;
        for (int i = 0; i < groups; i++) {
            shares[i] = base + (i < extra ? 1 : 0);
        }
        return shares;
    }

    private static int[] tickQuotas(int[] shares, int tickLimit) {
        int n = shares.length;
        int[] quotas = new int[n];
        int[] leftover = shares.clone();
        for (int granted = 0; granted < tickLimit; granted++) {
            int best = -1;
            for (int i = 0; i < n; i++) {
                if (leftover[i] <= 0) {
                    continue;
                }
                if (best < 0
                        || leftover[i] > leftover[best]
                        || (leftover[i] == leftover[best] && quotas[i] < quotas[best])) {
                    best = i;
                }
            }
            if (best < 0) {
                break;
            }
            quotas[best]++;
            leftover[best]--;
        }
        return quotas;
    }

    private static boolean withinGroupRange(PlayerRef left, PlayerRef right) {
        double dx = left.x() - right.x();
        double dy = left.y() - right.y();
        double dz = left.z() - right.z();
        return dx * dx + dy * dy + dz * dz <= (double) GROUP_RANGE * GROUP_RANGE;
    }

    private static int find(int[] parent, int index) {
        int root = index;
        while (parent[root] != root) {
            root = parent[root];
        }
        while (parent[index] != root) {
            int next = parent[index];
            parent[index] = root;
            index = next;
        }
        return root;
    }

    private static void union(int[] parent, int left, int right) {
        int leftRoot = find(parent, left);
        int rightRoot = find(parent, right);
        if (leftRoot != rightRoot) {
            parent[rightRoot] = leftRoot;
        }
    }

    private static int dayTimeOfDay(long dayTime) {
        return (int) Math.floorMod(dayTime, DAY_LENGTH);
    }

    private static long worldDay(long dayTime) {
        return Math.floorDiv(dayTime, DAY_LENGTH);
    }

    private record PlayerGroup(String key, double originX, double originZ) {
        static PlayerGroup of(List<PlayerRef> members) {
            String key = members.stream().map(PlayerRef::id).collect(Collectors.joining(","));
            double x = 0.0;
            double z = 0.0;
            for (PlayerRef member : members) {
                x += member.x();
                z += member.z();
            }
            int n = members.size();
            return new PlayerGroup(key, x / n, z / n);
        }
    }

    public record PlayerRef(String id, double x, double y, double z) {
    }

    public record NightState(long worldDay, Map<String, Double> directions) {
        public NightState {
            directions = Map.copyOf(directions);
        }

        public static NightState none() {
            return new NightState(Long.MIN_VALUE, Map.of());
        }
    }

    public record Snapshot(
            boolean overworld,
            boolean peaceful,
            long dayTime,
            int hordeTarget,
            int ordinaryZombieCount,
            List<PlayerRef> validPlayers,
            NightState night) {
        public Snapshot {
            validPlayers = List.copyOf(validPlayers);
        }
    }

    public record GroupPlan(String key, Sector sector, int remainingBudget, int spawnQuota) {
    }

    public record Plan(
            int desiredCount,
            int successfulSpawnLimit,
            int failedAttemptLimit,
            List<GroupPlan> groups,
            NightState night) {
        public Plan {
            groups = List.copyOf(groups);
        }

        public static Plan none() {
            return none(NightState.none());
        }

        public static Plan none(NightState night) {
            return new Plan(0, 0, 0, List.of(), night);
        }

        public boolean shouldSpawn() {
            return successfulSpawnLimit > 0 && !groups.isEmpty();
        }

        public Sector sector() {
            return groups.isEmpty() ? null : groups.getFirst().sector();
        }
    }

    public record Sector(
            double originX, double originZ, double directionRadians, int minDistance, int maxDistance) {
        public boolean containsBlockCenter(int blockX, int blockZ) {
            double dx = blockX + 0.5 - originX;
            double dz = blockZ + 0.5 - originZ;
            double horizontal = Math.hypot(dx, dz);
            return horizontal >= minDistance && horizontal <= maxDistance;
        }
    }
}
