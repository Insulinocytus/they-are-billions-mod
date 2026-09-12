package com.insulinocytus.theyarebillions.horde;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Zombie;

public final class HordeDaytimeCleanup {
    static final int SECTION_LIMIT = 4;
    static final int REMOVALS_PER_TICK = 10;

    private static final Comparator<ZombieRef> FARTHEST = Comparator
            .comparingDouble(ZombieRef::nearestPlayerDistanceSquared)
            .reversed()
            .thenComparing(ZombieRef::id);
    private static final Comparator<ZombieRef> NEAREST = Comparator
            .comparingDouble(ZombieRef::nearestPlayerDistanceSquared)
            .thenComparing(ZombieRef::id);
    private static final Map<ServerLevel, Set<Zombie>> SUNLIT = new IdentityHashMap<>();

    private HordeDaytimeCleanup() {
    }

    public static boolean queueSunlit(Zombie zombie) {
        if (!HordeIdentity.isOrdinaryZombie(zombie) || namedOrPersistent(zombie)) {
            return false;
        }
        if (!(zombie.level() instanceof ServerLevel level)) {
            return false;
        }
        SUNLIT.computeIfAbsent(level, ignored -> Collections.newSetFromMap(new WeakHashMap<>())).add(zombie);
        return true;
    }

    static void tick(ServerLevel level, List<HordePlanner.PlayerRef> players, int limit) {
        if (HordePlanner.isHordeNight(level.getDayTime())) {
            SUNLIT.remove(level);
            return;
        }
        if (limit <= 0) {
            return;
        }
        Set<Zombie> sunlit = SUNLIT.getOrDefault(level, Set.of());
        Map<String, Zombie> byId = new HashMap<>();
        List<ZombieRef> refs = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof Zombie zombie)
                    || !HordeIdentity.isOrdinaryZombie(zombie)
                    || zombie.isRemoved()) {
                continue;
            }
            String id = zombie.getUUID().toString();
            byId.put(id, zombie);
            refs.add(new ZombieRef(
                    id,
                    SectionPos.asLong(BlockPos.containing(zombie.getX(), zombie.getY(), zombie.getZ())),
                    nearestPlayerDistanceSquared(zombie, players),
                    namedOrPersistent(zombie),
                    sunlit.contains(zombie),
                    unseenSun(zombie)));
        }
        for (String id : removals(refs, limit)) {
            Zombie zombie = byId.get(id);
            if (zombie != null) {
                remove(zombie);
            }
        }
        if (!sunlit.isEmpty()) {
            sunlit.removeIf(Entity::isRemoved);
            if (sunlit.isEmpty()) {
                SUNLIT.remove(level);
            }
        }
    }

    public static void onLevelUnload(ServerLevel level) {
        SUNLIT.remove(level);
    }

    static List<String> removals(List<ZombieRef> zombies, int limit) {
        List<ZombieRef> sunlit = new ArrayList<>();
        List<ZombieRef> unseen = new ArrayList<>();
        for (ZombieRef zombie : zombies) {
            if (zombie.sunlit && !zombie.namedOrPersistent) {
                sunlit.add(zombie);
            } else if (zombie.unseenSun) {
                unseen.add(zombie);
            }
        }
        sunlit.sort(FARTHEST);
        List<String> ids = new ArrayList<>();
        for (ZombieRef zombie : sunlit) {
            if (ids.size() < limit) {
                ids.add(zombie.id);
            } else if (zombie.unseenSun) {
                unseen.add(zombie);
            }
        }
        if (ids.size() >= limit) {
            return ids;
        }

        Map<Long, List<ZombieRef>> sections = new HashMap<>();
        for (ZombieRef zombie : unseen) {
            sections.computeIfAbsent(zombie.section, ignored -> new ArrayList<>()).add(zombie);
        }
        List<ZombieRef> excess = new ArrayList<>();
        for (List<ZombieRef> section : sections.values()) {
            List<ZombieRef> namedOrPersistent = new ArrayList<>();
            List<ZombieRef> others = new ArrayList<>();
            for (ZombieRef zombie : section) {
                if (zombie.namedOrPersistent) {
                    namedOrPersistent.add(zombie);
                } else {
                    others.add(zombie);
                }
            }
            others.sort(NEAREST);
            int keep = Math.max(0, SECTION_LIMIT - namedOrPersistent.size());
            if (others.size() > keep) {
                excess.addAll(others.subList(keep, others.size()));
            }
        }
        excess.sort(FARTHEST);
        for (ZombieRef zombie : excess) {
            if (ids.size() >= limit) {
                break;
            }
            ids.add(zombie.id);
        }
        return ids;
    }

    static void remove(Zombie zombie) {
        HordeNavigation.release(zombie);
        if (zombie.level() instanceof ServerLevel level) {
            HordeChunkTickets.releaseMember(level, zombie);
        }
        zombie.discard();
    }

    static boolean namedOrPersistent(Zombie zombie) {
        return zombie.isPersistenceRequired() || zombie.hasCustomName();
    }

    private static boolean unseenSun(Zombie zombie) {
        return !zombie.level().canSeeSky(BlockPos.containing(zombie.getX(), zombie.getEyeY(), zombie.getZ()));
    }

    private static double nearestPlayerDistanceSquared(Zombie zombie, List<HordePlanner.PlayerRef> players) {
        double nearest = Double.POSITIVE_INFINITY;
        for (HordePlanner.PlayerRef player : players) {
            double dx = zombie.getX() - player.x();
            double dy = zombie.getY() - player.y();
            double dz = zombie.getZ() - player.z();
            nearest = Math.min(nearest, dx * dx + dy * dy + dz * dz);
        }
        return nearest;
    }

    record ZombieRef(
            String id,
            long section,
            double nearestPlayerDistanceSquared,
            boolean namedOrPersistent,
            boolean sunlit,
            boolean unseenSun) {
    }
}
