package com.insulinocytus.theyarebillions.horde;

import com.insulinocytus.theyarebillions.HordeSpawnAccess;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

public final class HordeSpawner {
    // ponytail: in-memory night directions; persist in SavedData when restarts must keep them
    private static HordePlanner.NightState night = HordePlanner.NightState.none();
    // ponytail: pending until entity ticks or despawns; #8 tickets shrink this window
    private static final Set<UUID> pendingHordeIds = new HashSet<>();

    private HordeSpawner() {
    }

    public static void onServerTick(MinecraftServer server) {
        tick(server, server.overworld());
    }

    public static boolean spawnHordeMember(ServerLevel level, BlockPos pos) {
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            return false;
        }
        if (!level.getWorldBorder().isWithinBounds(pos)) {
            return false;
        }
        level.getChunk(pos);
        if (!HordeSpawnAccess.checkSpawnPlacement(level, pos)) {
            return false;
        }
        Zombie zombie = EntityType.ZOMBIE.create(level);
        if (zombie == null) {
            return false;
        }
        zombie.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.random.nextFloat() * 360.0F, 0.0F);
        if (!HordeSpawnAccess.checkSpawnPosition(zombie, level)) {
            zombie.discard();
            return false;
        }
        HordeIdentity.mark(zombie);
        if (!HordeSpawnAccess.finalizeHordeSpawn(zombie, level)) {
            zombie.discard();
            return false;
        }
        HordeIdentity.enforceHordeTraits(zombie);
        if (!level.addFreshEntity(zombie)) {
            return false;
        }
        pendingHordeIds.add(zombie.getUUID());
        return true;
    }

    static void tick(MinecraftServer server, ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD) {
            return;
        }
        long dayTime = level.getDayTime();
        HordePlanner.Plan plan = HordePlanner.plan(
                new HordePlanner.Snapshot(
                        true,
                        level.getDifficulty() == Difficulty.PEACEFUL,
                        dayTime,
                        HordeGameRules.target(level),
                        countOrdinaryZombies(server),
                        validPlayers(level),
                        night),
                () -> level.random.nextDouble() * (Math.PI * 2.0));
        night = plan.night();
        if (!plan.shouldSpawn()) {
            return;
        }
        execute(level, plan);
    }

    private static void execute(ServerLevel level, HordePlanner.Plan plan) {
        List<HordePlanner.GroupPlan> groups = plan.groups();
        int[] remainingQuota = new int[groups.size()];
        for (int i = 0; i < groups.size(); i++) {
            remainingQuota[i] = groups.get(i).spawnQuota();
        }
        executeAttempts(
                remainingQuota,
                Math.floorMod(plan.night().rotation(), groups.size()),
                plan.successfulSpawnLimit(),
                plan.failedAttemptLimit(),
                i -> trySpawnInSector(level, groups.get(i).sector()));
    }

    static int executeAttempts(
            int[] remainingQuota, int start, int successLimit, int failLimit, IntPredicate spawn) {
        int spawned = 0;
        int failed = 0;
        int index = start;
        while (spawned < successLimit && failed < failLimit) {
            int chosen = nextGroup(remainingQuota, index);
            if (spawn.test(chosen)) {
                spawned++;
            } else {
                failed++;
            }
            if (remainingQuota[chosen] > 0) {
                remainingQuota[chosen]--;
            }
            index = chosen + 1;
        }
        return spawned;
    }

    private static int nextGroup(int[] remainingQuota, int start) {
        int n = remainingQuota.length;
        for (int offset = 0; offset < n; offset++) {
            int index = Math.floorMod(start + offset, n);
            if (remainingQuota[index] > 0) {
                return index;
            }
        }
        return Math.floorMod(start, n);
    }

    private static boolean trySpawnInSector(ServerLevel level, HordePlanner.Sector sector) {
        double span = sector.maxDistance() - sector.minDistance();
        double distance = sector.minDistance() + level.random.nextDouble() * span;
        int blockX = Mth.floor(sector.originX() + Math.cos(sector.directionRadians()) * distance);
        int blockZ = Mth.floor(sector.originZ() + Math.sin(sector.directionRadians()) * distance);
        if (!sector.containsBlockCenter(blockX, blockZ)) {
            return false;
        }
        level.getChunk(blockX >> 4, blockZ >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ);
        return spawnHordeMember(level, new BlockPos(blockX, y, blockZ));
    }

    private static List<HordePlanner.PlayerRef> validPlayers(ServerLevel level) {
        List<HordePlanner.PlayerRef> players = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (!isValidPlayer(player)) {
                continue;
            }
            players.add(new HordePlanner.PlayerRef(
                    player.getUUID().toString(), player.getX(), player.getY(), player.getZ()));
        }
        return players;
    }

    private static boolean isValidPlayer(ServerPlayer player) {
        GameType mode = player.gameMode.getGameModeForPlayer();
        return HordePlanner.isValidPlayer(
                HordeSpawnAccess.isFakePlayer(player),
                mode == GameType.SURVIVAL,
                mode == GameType.ADVENTURE);
    }

    private static int countOrdinaryZombies(MinecraftServer server) {
        int ticking = 0;
        Set<UUID> loadedUnticked = new HashSet<>();
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!HordeIdentity.isOrdinaryZombie(entity)) {
                    continue;
                }
                if (level.isPositionEntityTicking(entity.blockPosition())) {
                    ticking++;
                } else {
                    loadedUnticked.add(entity.getUUID());
                }
            }
        }
        return ordinaryZombieBudget(ticking, pendingHordeIds, loadedUnticked);
    }

    static int ordinaryZombieBudget(int tickingCount, Set<UUID> pending, Set<UUID> loadedUnticked) {
        pending.retainAll(loadedUnticked);
        return tickingCount + pending.size();
    }
}
