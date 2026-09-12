package com.insulinocytus.theyarebillions.horde;

import com.insulinocytus.theyarebillions.HordeSpawnAccess;
import com.insulinocytus.theyarebillions.TheyAreBillions;
import java.util.ArrayList;
import java.util.List;
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
    private HordeSpawner() {
    }

    public static void onServerTick(MinecraftServer server) {
        tick(server, server.overworld());
    }

    public static boolean spawnHordeMember(ServerLevel level, BlockPos pos) {
        return spawnHordeMember(level, pos, null);
    }

    public static boolean spawnHordeMember(
            ServerLevel level, BlockPos pos, HordePlanner.GroupIdentity group) {
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            return spawnFailed(pos, "peaceful");
        }
        if (!level.getWorldBorder().isWithinBounds(pos)) {
            return spawnFailed(pos, "world border");
        }
        level.getChunk(pos);
        if (!HordeSpawnAccess.checkSpawnPlacement(level, pos)) {
            return spawnFailed(pos, "placement");
        }
        Zombie zombie = EntityType.ZOMBIE.create(level);
        if (zombie == null) {
            return spawnFailed(pos, "create");
        }
        zombie.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.random.nextFloat() * 360.0F, 0.0F);
        if (!HordeSpawnAccess.checkSpawnPosition(zombie, level)) {
            zombie.discard();
            return spawnFailed(pos, "position");
        }
        if (group == null) {
            HordeIdentity.mark(zombie);
        } else {
            HordeIdentity.mark(zombie, group);
        }
        if (!HordeSpawnAccess.finalizeHordeSpawn(zombie, level)) {
            zombie.discard();
            return spawnFailed(pos, "finalize");
        }
        HordeIdentity.enforceHordeTraits(zombie);
        boolean newlyAcquiredTicket = HordeChunkTickets.beforeSpawn(level, zombie);
        if (!level.addFreshEntity(zombie)) {
            HordeChunkTickets.cancelSpawn(level, zombie, newlyAcquiredTicket);
            return spawnFailed(pos, "add");
        }
        HordeChunkTickets.onSpawn(level, zombie);
        return true;
    }

    static void tick(MinecraftServer server, ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD) {
            return;
        }
        List<HordePlanner.PlayerRef> players = validPlayers(level);
        HordeChunkTickets.TickResult tickets = HordeChunkTickets.tick(level, players);
        HordeNightData night = HordeNightData.get(level);
        long dayTime = level.getDayTime();
        HordePlanner.Plan plan = HordePlanner.plan(
                new HordePlanner.Snapshot(
                        true,
                        level.getDifficulty() == Difficulty.PEACEFUL,
                        dayTime,
                        HordeGameRules.target(level),
                        countOrdinaryZombies(server),
                        players,
                        night.state()),
                () -> level.random.nextDouble() * (Math.PI * 2.0),
                HordePerformance.spawnLimit(server));
        night.setState(plan.night());
        HordeDaytimeCleanup.tick(
                level, players, HordeDaytimeCleanup.REMOVALS_PER_TICK - tickets.removed());
        if (!tickets.ready()) {
            return;
        }
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
                i -> trySpawnInSector(level, groups.get(i)));
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

    private static boolean trySpawnInSector(ServerLevel level, HordePlanner.GroupPlan group) {
        HordePlanner.Sector sector = group.sector();
        double span = sector.maxDistance() - sector.minDistance();
        double distance = sector.minDistance() + level.random.nextDouble() * span;
        int blockX = Mth.floor(sector.originX() + Math.cos(sector.directionRadians()) * distance);
        int blockZ = Mth.floor(sector.originZ() + Math.sin(sector.directionRadians()) * distance);
        if (!sector.containsBlockCenter(blockX, blockZ)) {
            if (TheyAreBillions.LOGGER.isDebugEnabled()) {
                return spawnFailed(new BlockPos(blockX, 0, blockZ), "sector");
            }
            return false;
        }
        level.getChunk(blockX >> 4, blockZ >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ);
        return spawnHordeMember(level, new BlockPos(blockX, y, blockZ), group.identity());
    }

    static List<HordePlanner.PlayerRef> validPlayers(ServerLevel level) {
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

    static boolean isValidPlayer(ServerPlayer player) {
        GameType mode = player.gameMode.getGameModeForPlayer();
        return HordePlanner.isValidPlayer(
                HordeSpawnAccess.isFakePlayer(player),
                mode == GameType.SURVIVAL,
                mode == GameType.ADVENTURE);
    }

    static int countOrdinaryZombies(MinecraftServer server) {
        int ticking = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                boolean ordinaryZombie = HordeIdentity.isOrdinaryZombie(entity);
                boolean positionEntityTicking =
                        ordinaryZombie && level.isPositionEntityTicking(entity.blockPosition());
                if (countsTowardBudget(ordinaryZombie, positionEntityTicking)) {
                    ticking++;
                }
            }
        }
        return ticking;
    }

    static boolean countsTowardBudget(boolean ordinaryZombie, boolean positionEntityTicking) {
        return ordinaryZombie && positionEntityTicking;
    }

    private static boolean spawnFailed(BlockPos pos, String reason) {
        if (TheyAreBillions.LOGGER.isDebugEnabled()) {
            TheyAreBillions.LOGGER.debug("Horde spawn failed at {} ({})", pos, reason);
        }
        return false;
    }
}
