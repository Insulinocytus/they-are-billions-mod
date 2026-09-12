package com.insulinocytus.theyarebillions.horde;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleSupplier;

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
    public static final int TICKET_RANGE = 160;

    private static final long DAY_LENGTH = 24000L;
    private static final long UNOBSERVED_DAY_TIME = Long.MIN_VALUE;

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
        return plan(snapshot, newDirectionRadians, MAX_SUCCESSFUL_SPAWNS_PER_TICK);
    }

    static Plan plan(Snapshot snapshot, DoubleSupplier newDirectionRadians, int spawnLimit) {
        long dayTime = snapshot.dayTime();
        if (!snapshot.overworld()) {
            return Plan.none(observeTime(snapshot.night(), dayTime));
        }
        boolean night = isHordeNight(dayTime);
        List<PlayerGroup> groups = night ? connectedGroups(snapshot.validPlayers()) : List.of();
        NightState nightState = nextNightState(snapshot.night(), dayTime, groups, night, newDirectionRadians);
        int target = Math.clamp(snapshot.hordeTarget(), MIN_TARGET, MAX_TARGET);
        if (!night || snapshot.peaceful() || snapshot.validPlayers().isEmpty() || target == 0) {
            return Plan.none(nightState);
        }
        int desired = desiredCount(snapshot.dayTime(), target);
        int remaining = Math.max(0, desired - snapshot.ordinaryZombieCount());
        int quota = Math.min(Math.clamp(spawnLimit, 0, MAX_SUCCESSFUL_SPAWNS_PER_TICK), remaining);
        if (quota == 0) {
            return new Plan(desired, 0, 0, List.of(), nightState);
        }
        int rotation = Math.floorMod(nightState.rotation(), groups.size());
        int[] shares = evenSplit(remaining, groups.size(), rotation);
        int[] tickQuotas = tickQuotas(shares, quota, rotation);
        List<GroupPlan> groupPlans = new ArrayList<>(groups.size());
        for (int i = 0; i < groups.size(); i++) {
            PlayerGroup group = groups.get(i);
            double direction = nightState.directions().get(group.identity());
            PlayerRef anchor = group.anchor(direction);
            groupPlans.add(new GroupPlan(
                    group.identity(),
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
    public static TicketPlan planTickets(TicketSnapshot snapshot) {
        List<String> removeMemberIds = snapshot.members().stream()
                .filter(MemberRef::tagged)
                .filter(member -> !member.persistent())
                .filter(member -> snapshot.validPlayers().isEmpty() || !withinTicketRange(member, snapshot.validPlayers()))
                .map(MemberRef::id)
                .sorted()
                .toList();
        if (snapshot.validPlayers().isEmpty()) {
            return new TicketPlan(
                    Map.of(), List.of(), snapshot.activeCounts().keySet().stream().sorted().toList(), removeMemberIds, Map.of());
        }

        List<PlayerGroup> groups = connectedGroups(snapshot.validPlayers());
        Map<ChunkRef, Integer> desiredCounts = new LinkedHashMap<>();
        Map<String, GroupIdentity> groupAssignments = new LinkedHashMap<>();
        snapshot.members().stream()
                .filter(MemberRef::tagged)
                .filter(member -> withinTicketRange(member, snapshot.validPlayers()))
                .sorted(Comparator.comparing(MemberRef::id))
                .forEach(member -> {
                    desiredCounts.merge(member.chunk(), 1, Integer::sum);
                    groupAssignments.put(member.id(), nearestGroup(member, groups).identity());
                });
        Map<ChunkRef, Integer> sortedCounts = desiredCounts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(LinkedHashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()), Map::putAll);
        List<ChunkRef> acquireOrRenew = List.copyOf(sortedCounts.keySet());
        List<ChunkRef> release = snapshot.activeCounts().keySet().stream()
                .filter(chunk -> !sortedCounts.containsKey(chunk))
                .sorted()
                .toList();
        return new TicketPlan(sortedCounts, acquireOrRenew, release, removeMemberIds, groupAssignments);
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
        groups.sort(Comparator.comparing(PlayerGroup::identity));
        return groups;
    }

    static int connectedGroupCount(List<PlayerRef> players) {
        return connectedGroups(players).size();
    }

    private static NightState nextNightState(
            NightState previous,
            long dayTime,
            List<PlayerGroup> groups,
            boolean night,
            DoubleSupplier newDirectionRadians) {
        long nightIdentity = nightIdentity(dayTime);
        boolean newNight = night && nightIdentity > previous.initializedNight();
        Map<GroupIdentity, Double> previousDirections = newNight ? Map.of() : previous.directions();
        int previousRotation = newNight ? 0 : previous.rotation();
        long initializedNight = night ? nightIdentity : previous.initializedNight();
        if (!night || groups.isEmpty()) {
            return new NightState(dayTime, previousDirections, previousRotation, initializedNight);
        }
        List<GroupIdentity> currentIdentities = groups.stream().map(PlayerGroup::identity).toList();
        Map<GroupIdentity, Double> directions = new LinkedHashMap<>();
        for (int i = 0; i < groups.size(); i++) {
            GroupIdentity identity = groups.get(i).identity();
            Double continuedDirection = continuedDirection(currentIdentities, previousDirections, i);
            directions.put(
                    identity,
                    continuedDirection != null ? continuedDirection : newDirectionRadians.getAsDouble());
        }
        int rotation = newNight || previous.observedDayTime() == UNOBSERVED_DAY_TIME ? 0 : previous.rotation() + 1;
        return new NightState(dayTime, directions, rotation, initializedNight);
    }

    private static NightState observeTime(NightState previous, long dayTime) {
        return new NightState(dayTime, previous.directions(), previous.rotation(), previous.initializedNight());
    }

    private static long nightIdentity(long dayTime) {
        long day = Math.floorDiv(dayTime, DAY_LENGTH);
        return dayTimeOfDay(dayTime) < NIGHT_START ? day - 1 : day;
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

    private static Double continuedDirection(
            List<GroupIdentity> currentIdentities,
            Map<GroupIdentity, Double> previousDirections,
            int current) {
        GroupIdentity identity = currentIdentities.get(current);
        GroupIdentity match = null;
        for (GroupIdentity previous : previousDirections.keySet()) {
            if (!identity.overlaps(previous)) {
                continue;
            }
            if (match != null) {
                return null;
            }
            match = previous;
        }
        if (match == null) {
            return null;
        }
        for (int other = 0; other < currentIdentities.size(); other++) {
            if (other != current && currentIdentities.get(other).overlaps(match)) {
                return null;
            }
        }
        return previousDirections.get(match);
    }

    private static boolean withinGroupRange(PlayerRef left, PlayerRef right) {
        double dx = left.x() - right.x();
        double dy = left.y() - right.y();
        double dz = left.z() - right.z();
        return dx * dx + dy * dy + dz * dz <= (double) GROUP_RANGE * GROUP_RANGE;
    }
    private static boolean withinTicketRange(MemberRef member, List<PlayerRef> players) {
        double maxDistanceSquared = (double) TICKET_RANGE * TICKET_RANGE;
        for (PlayerRef player : players) {
            double dx = member.x() - player.x();
            double dy = member.y() - player.y();
            double dz = member.z() - player.z();
            if (dx * dx + dy * dy + dz * dz <= maxDistanceSquared) {
                return true;
            }
        }
        return false;
    }

    private static PlayerGroup nearestGroup(MemberRef member, List<PlayerGroup> groups) {
        PlayerGroup nearest = groups.getFirst();
        double nearestDistance = distanceSquared(member, nearest);
        for (int i = 1; i < groups.size(); i++) {
            PlayerGroup candidate = groups.get(i);
            double distance = distanceSquared(member, candidate);
            if (distance < nearestDistance) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private static double distanceSquared(MemberRef member, PlayerGroup group) {
        double nearest = Double.POSITIVE_INFINITY;
        for (PlayerRef player : group.members()) {
            double dx = member.x() - player.x();
            double dy = member.y() - player.y();
            double dz = member.z() - player.z();
            nearest = Math.min(nearest, dx * dx + dy * dy + dz * dz);
        }
        return nearest;
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

    private record PlayerGroup(List<PlayerRef> members, GroupIdentity identity) {
        PlayerGroup {
            members = List.copyOf(members);
        }

        static PlayerGroup of(List<PlayerRef> members) {
            List<PlayerRef> copy = List.copyOf(members);
            return new PlayerGroup(copy, new GroupIdentity(copy.stream().map(PlayerRef::id).toList()));
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

    public record GroupIdentity(List<String> memberIds) implements Comparable<GroupIdentity> {
        public GroupIdentity {
            memberIds = memberIds.stream().distinct().sorted().toList();
            if (memberIds.isEmpty()) {
                throw new IllegalArgumentException("group identity must contain at least one member");
            }
        }

        public static GroupIdentity of(String... memberIds) {
            return new GroupIdentity(List.of(memberIds));
        }

        boolean overlaps(GroupIdentity other) {
            int left = 0;
            int right = 0;
            while (left < memberIds.size() && right < other.memberIds.size()) {
                int compared = memberIds.get(left).compareTo(other.memberIds.get(right));
                if (compared == 0) {
                    return true;
                }
                if (compared < 0) {
                    left++;
                } else {
                    right++;
                }
            }
            return false;
        }

        @Override
        public int compareTo(GroupIdentity other) {
            int common = Math.min(memberIds.size(), other.memberIds.size());
            for (int i = 0; i < common; i++) {
                int compared = memberIds.get(i).compareTo(other.memberIds.get(i));
                if (compared != 0) {
                    return compared;
                }
            }
            return Integer.compare(memberIds.size(), other.memberIds.size());
        }
    }

    public record PlayerRef(String id, double x, double y, double z) {
    }
    public record MemberRef(String id, double x, double y, double z, boolean tagged, boolean persistent) {
        public ChunkRef chunk() {
            return new ChunkRef(
                    Math.floorDiv((int) Math.floor(x), 16), Math.floorDiv((int) Math.floor(z), 16));
        }
    }

    public record ChunkRef(int x, int z) implements Comparable<ChunkRef> {
        @Override
        public int compareTo(ChunkRef other) {
            int xComparison = Integer.compare(x, other.x);
            return xComparison != 0 ? xComparison : Integer.compare(z, other.z);
        }
    }

    public record TicketSnapshot(
            List<PlayerRef> validPlayers, List<MemberRef> members, Map<ChunkRef, Integer> activeCounts) {
        public TicketSnapshot {
            validPlayers = List.copyOf(validPlayers);
            members = List.copyOf(members);
            activeCounts = Map.copyOf(activeCounts);
        }
    }

    public record TicketPlan(
            Map<ChunkRef, Integer> desiredCounts,
            List<ChunkRef> acquireOrRenew,
            List<ChunkRef> release,
            List<String> removeMemberIds,
            Map<String, GroupIdentity> groupAssignments) {
        public TicketPlan {
            desiredCounts = Map.copyOf(desiredCounts);
            acquireOrRenew = List.copyOf(acquireOrRenew);
            release = List.copyOf(release);
            removeMemberIds = List.copyOf(removeMemberIds);
            groupAssignments = Map.copyOf(groupAssignments);
        }
    }


    public record NightState(
            long observedDayTime, Map<GroupIdentity, Double> directions, int rotation, long initializedNight) {
        public NightState {
            directions = Map.copyOf(directions);
        }

        public NightState(long observedDayTime, Map<GroupIdentity, Double> directions, int rotation) {
            this(observedDayTime, directions, rotation, nightIdentity(observedDayTime));
        }

        public NightState(long observedDayTime, Map<GroupIdentity, Double> directions) {
            this(observedDayTime, directions, 0);
        }

        public static NightState none() {
            return new NightState(UNOBSERVED_DAY_TIME, Map.of(), 0, UNOBSERVED_DAY_TIME);
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

    public record GroupPlan(GroupIdentity identity, Sector sector, int remainingBudget, int spawnQuota) {
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
