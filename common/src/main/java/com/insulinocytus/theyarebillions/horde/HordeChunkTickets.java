package com.insulinocytus.theyarebillions.horde;

import com.insulinocytus.theyarebillions.HordeChunkTicketAccess;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.ChunkPos;

public final class HordeChunkTickets {
    private static final int OFFLINE_CLEANUP_LIMIT = 10;
    private static final Map<ServerLevel, State> STATES = new IdentityHashMap<>();

    private HordeChunkTickets() {
    }

    static boolean tick(ServerLevel level, List<HordePlanner.PlayerRef> players) {
        State state = STATES.computeIfAbsent(level, ignored -> new State());
        HordeChunkData data = HordeChunkData.get(level);
        List<Zombie> members = loadedMembers(level);

        if (players.isEmpty()) {
            HordePlanner.TicketPlan plan = HordePlanner.planTickets(
                    new HordePlanner.TicketSnapshot(players, memberRefs(members), state.activeCounts));
            removeMembers(members, plan.removeMemberIds(), OFFLINE_CLEANUP_LIMIT);
            plan.release().forEach(chunk -> release(level, chunk));
            state.reset();
            recountLoadedOccupancy(level, data, loadedMembers(level));
            return false;
        }

        if (!state.hadPlayers) {
            state.hadPlayers = true;
            state.recovering = true;
            data.occupancy().entrySet().stream()
                    .filter(entry -> chunkWithinRange(entry.getKey(), players))
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        acquireOrRenew(level, entry.getKey());
                        state.activeCounts.put(entry.getKey(), entry.getValue());
                        state.pendingRecovery.add(entry.getKey());
                    });
        }

        if (state.recovering) {
            state.activeCounts.keySet().forEach(chunk -> acquireOrRenew(level, chunk));
            state.pendingRecovery.removeIf(chunk -> isEntityTicking(level, chunk));
            if (!state.pendingRecovery.isEmpty()) {
                return false;
            }
            state.recovering = false;
            members = loadedMembers(level);
        }

        HordePlanner.TicketPlan plan = HordePlanner.planTickets(
                new HordePlanner.TicketSnapshot(players, memberRefs(members), state.activeCounts));
        plan.acquireOrRenew().forEach(chunk -> acquireOrRenew(level, chunk));
        assignGroups(members, plan.groupAssignments());
        removeMembers(members, plan.removeMemberIds(), Integer.MAX_VALUE);
        plan.release().forEach(chunk -> release(level, chunk));

        state.activeCounts.clear();
        state.activeCounts.putAll(plan.desiredCounts());
        recountLoadedOccupancy(level, data, loadedMembers(level));
        return true;
    }

    static void onSpawn(ServerLevel level, Zombie zombie) {
        if (!HordeIdentity.isHordeMember(zombie)) {
            return;
        }
        State state = STATES.computeIfAbsent(level, ignored -> new State());
        List<HordePlanner.PlayerRef> players = HordeSpawner.validPlayers(level);
        HordePlanner.TicketPlan plan = HordePlanner.planTickets(new HordePlanner.TicketSnapshot(
                players, memberRefs(List.of(zombie)), state.activeCounts));
        HordePlanner.ChunkRef chunk = chunk(zombie);
        if (!plan.desiredCounts().containsKey(chunk)) {
            return;
        }
        acquireOrRenew(level, chunk);
        state.activeCounts.merge(chunk, 1, Integer::sum);
        HordeChunkData data = HordeChunkData.get(level);
        Map<HordePlanner.ChunkRef, Integer> occupancy = new HashMap<>(data.occupancy());
        occupancy.merge(chunk, 1, Integer::sum);
        data.setOccupancy(occupancy);
    }
    public static void onLevelUnload(ServerLevel level) {
        State state = STATES.remove(level);
        if (state != null) {
            state.activeCounts.keySet().forEach(chunk -> release(level, chunk));
        }
    }

    private static List<Zombie> loadedMembers(ServerLevel level) {
        List<Zombie> members = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof Zombie zombie && HordeIdentity.isHordeMember(zombie)) {
                members.add(zombie);
            }
        }
        return members;
    }

    private static List<HordePlanner.MemberRef> memberRefs(List<Zombie> members) {
        return members.stream()
                .map(zombie -> new HordePlanner.MemberRef(
                        zombie.getUUID().toString(),
                        zombie.getX(),
                        zombie.getY(),
                        zombie.getZ(),
                        true,
                        zombie.isPersistenceRequired() || zombie.hasCustomName()))
                .toList();
    }

    private static void assignGroups(List<Zombie> members, Map<String, HordePlanner.GroupIdentity> assignments) {
        for (Zombie zombie : members) {
            HordePlanner.GroupIdentity group = assignments.get(zombie.getUUID().toString());
            if (group != null && !group.equals(HordeIdentity.group(zombie))) {
                HordeIdentity.assignGroup(zombie, group);
            }
        }
    }

    private static void removeMembers(List<Zombie> members, List<String> removeIds, int limit) {
        if (removeIds.isEmpty()) {
            return;
        }
        Set<String> remaining = new HashSet<>(removeIds);
        int removed = 0;
        for (Zombie zombie : members) {
            if (removed >= limit) {
                break;
            }
            if (remaining.remove(zombie.getUUID().toString())) {
                zombie.discard();
                removed++;
            }
        }
    }

    private static void recountLoadedOccupancy(ServerLevel level, HordeChunkData data, List<Zombie> members) {
        Map<HordePlanner.ChunkRef, Integer> loadedCounts = new HashMap<>();
        members.forEach(zombie -> loadedCounts.merge(chunk(zombie), 1, Integer::sum));
        Map<HordePlanner.ChunkRef, Integer> occupancy = new HashMap<>(data.occupancy());
        for (HordePlanner.ChunkRef chunk : List.copyOf(occupancy.keySet())) {
            if (level.getChunkSource().getChunkNow(chunk.x(), chunk.z()) != null) {
                Integer count = loadedCounts.remove(chunk);
                if (count == null) {
                    occupancy.remove(chunk);
                } else {
                    occupancy.put(chunk, count);
                }
            }
        }
        occupancy.putAll(loadedCounts);
        data.setOccupancy(occupancy);
    }

    private static boolean chunkWithinRange(
            HordePlanner.ChunkRef chunk, List<HordePlanner.PlayerRef> players) {
        int minX = chunk.x() << 4;
        int minZ = chunk.z() << 4;
        int maxX = minX + 15;
        int maxZ = minZ + 15;
        double maxDistanceSquared = (double) HordePlanner.TICKET_RANGE * HordePlanner.TICKET_RANGE;
        for (HordePlanner.PlayerRef player : players) {
            double dx = Math.max(minX - player.x(), Math.max(0.0, player.x() - maxX));
            double dz = Math.max(minZ - player.z(), Math.max(0.0, player.z() - maxZ));
            if (dx * dx + dz * dz <= maxDistanceSquared) {
                return true;
            }
        }
        return false;
    }

    private static boolean isEntityTicking(ServerLevel level, HordePlanner.ChunkRef chunk) {
        BlockPos center = new ChunkPos(chunk.x(), chunk.z()).getMiddleBlockPosition(level.getMinBuildHeight());
        return level.getChunkSource().getChunkNow(chunk.x(), chunk.z()) != null
                && level.isPositionEntityTicking(center);
    }

    private static HordePlanner.ChunkRef chunk(Zombie zombie) {
        ChunkPos chunk = zombie.chunkPosition();
        return new HordePlanner.ChunkRef(chunk.x, chunk.z);
    }

    private static void acquireOrRenew(ServerLevel level, HordePlanner.ChunkRef chunk) {
        HordeChunkTicketAccess.acquireOrRenew(level, new ChunkPos(chunk.x(), chunk.z()));
    }

    private static void release(ServerLevel level, HordePlanner.ChunkRef chunk) {
        HordeChunkTicketAccess.release(level, new ChunkPos(chunk.x(), chunk.z()));
    }

    private static final class State {
        private final Map<HordePlanner.ChunkRef, Integer> activeCounts = new HashMap<>();
        private final Set<HordePlanner.ChunkRef> pendingRecovery = new HashSet<>();
        private boolean recovering;
        private boolean hadPlayers;

        private void reset() {
            activeCounts.clear();
            pendingRecovery.clear();
            recovering = false;
            hadPlayers = false;
        }
    }
}
