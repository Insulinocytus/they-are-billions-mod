package com.insulinocytus.theyarebillions.horde;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        int rotation = Math.floorMod(nightState.rotation(), groups.size());
        int[] shares = evenSplit(remaining, groups.size(), rotation);
        int[] tickQuotas = tickQuotas(shares, quota, rotation);
        List<GroupPlan> groupPlans = new ArrayList<>(groups.size());
        for (int i = 0; i < groups.size(); i++) {
            PlayerGroup group = groups.get(i);
            double direction = nightState.directions().get(group.key());
            PlayerRef anchor = group.anchor(direction);
            groupPlans.add(new GroupPlan(
                    group.key(),
                    new Sector(
                            anchor.x(),
                            anchor.z(),
                            direction,
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
        List<Set<String>> previousIds = new ArrayList<>(previousDirections.size());
        List<Double> previousValues = new ArrayList<>(previousDirections.size());
        for (Map.Entry<String, Double> entry : previousDirections.entrySet()) {
            previousIds.add(idsOf(entry.getKey()));
            previousValues.add(entry.getValue());
        }
        List<Set<String>> currentIds = new ArrayList<>(groups.size());
        for (PlayerGroup group : groups) {
            currentIds.add(group.ids());
        }
        Map<String, Double> directions = new LinkedHashMap<>();
        for (int i = 0; i < groups.size(); i++) {
            int match = continuedGroup(currentIds, previousIds, i);
            directions.put(
                    groups.get(i).key(),
                    match >= 0 ? previousValues.get(match) : newDirectionRadians.getAsDouble());
        }
        int rotation = previous.worldDay() == worldDay ? previous.rotation() + 1 : 0;
        return new NightState(worldDay, directions, rotation);
    }

    private static NightState retainOrReset(NightState previous, long worldDay) {
        if (previous.worldDay() == worldDay) {
            return previous;
        }
        return new NightState(worldDay, Map.of());
    }

    private static int[] evenSplit(int remaining, int groups, int rotation) {
        int[] shares = new int[groups];
        int base = remaining / groups;
        int extra = remaining % groups;
        for (int i = 0; i < groups; i++) {
            shares[i] = base;
        }
        for (int i = 0; i < extra; i++) {
            shares[Math.floorMod(rotation + i, groups)]++;
        }
        return shares;
    }

    private static int[] tickQuotas(int[] shares, int tickLimit, int rotation) {
        int n = shares.length;
        int[] quotas = new int[n];
        int[] leftover = shares.clone();
        for (int granted = 0; granted < tickLimit; granted++) {
            int best = -1;
            for (int offset = 0; offset < n; offset++) {
                int i = Math.floorMod(rotation + offset, n);
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

    private static int continuedGroup(List<Set<String>> currentIds, List<Set<String>> previousIds, int current) {
        int match = -1;
        Set<String> ids = currentIds.get(current);
        for (int previous = 0; previous < previousIds.size(); previous++) {
            if (Collections.disjoint(ids, previousIds.get(previous))) {
                continue;
            }
            if (match >= 0) {
                return -1;
            }
            match = previous;
        }
        if (match < 0) {
            return -1;
        }
        for (int other = 0; other < currentIds.size(); other++) {
            if (other != current && !Collections.disjoint(currentIds.get(other), previousIds.get(match))) {
                return -1;
            }
        }
        return match;
    }

    private static Set<String> idsOf(String key) {
        return Set.of(key.split(","));
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

    private record PlayerGroup(List<PlayerRef> members) {
        PlayerGroup {
            members = List.copyOf(members);
        }

        static PlayerGroup of(List<PlayerRef> members) {
            return new PlayerGroup(members);
        }

        String key() {
            return members.stream().map(PlayerRef::id).collect(Collectors.joining(","));
        }

        Set<String> ids() {
            return members.stream().map(PlayerRef::id).collect(Collectors.toUnmodifiableSet());
        }

        PlayerRef anchor(double directionRadians) {
            double cos = Math.cos(directionRadians);
            double sin = Math.sin(directionRadians);
            PlayerRef best = members.getFirst();
            double bestProj = best.x() * cos + best.z() * sin;
            for (int i = 1; i < members.size(); i++) {
                PlayerRef member = members.get(i);
                double proj = member.x() * cos + member.z() * sin;
                if (proj > bestProj) {
                    best = member;
                    bestProj = proj;
                }
            }
            return best;
        }
    }

    public record PlayerRef(String id, double x, double y, double z) {
    }

    public record NightState(long worldDay, Map<String, Double> directions, int rotation) {
        public NightState {
            directions = Map.copyOf(directions);
        }

        public NightState(long worldDay, Map<String, Double> directions) {
            this(worldDay, directions, 0);
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
