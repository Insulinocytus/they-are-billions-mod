package com.insulinocytus.theyarebillions.horde;

import com.insulinocytus.theyarebillions.HordeSpawnAccess;
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
    // ponytail: one overworld in-memory night direction; persist in SavedData when restarts must keep it
    private static boolean wasNight;
    private static double nightSpawnDirectionRadians;

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
        HordeIdentity.applySpawnIdentity(zombie);
        return level.addFreshEntity(zombie);
    }

    static void tick(MinecraftServer server, ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD) {
            return;
        }
        long dayTime = level.getDayTime();
        boolean night = HordePlanner.isHordeNight(dayTime);
        if (night && !wasNight) {
            nightSpawnDirectionRadians = level.random.nextDouble() * (Math.PI * 2.0);
        }
        wasNight = night;
        ServerPlayer player = findValidPlayer(level);
        HordePlanner.Plan plan = HordePlanner.plan(new HordePlanner.Snapshot(
                true,
                level.getDifficulty() == Difficulty.PEACEFUL,
                player != null,
                dayTime,
                HordeGameRules.target(level),
                countOrdinaryZombies(server),
                player == null ? 0.0 : player.getX(),
                player == null ? 0.0 : player.getZ(),
                nightSpawnDirectionRadians));
        if (!plan.shouldSpawn()) {
            return;
        }
        execute(level, plan);
    }

    private static void execute(ServerLevel level, HordePlanner.Plan plan) {
        int spawned = 0;
        int failed = 0;
        while (spawned < plan.successfulSpawnLimit() && failed < plan.failedAttemptLimit()) {
            if (trySpawnInSector(level, plan.sector())) {
                spawned++;
            } else {
                failed++;
            }
        }
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

    private static ServerPlayer findValidPlayer(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            if (HordeSpawnAccess.isFakePlayer(player)) {
                continue;
            }
            GameType mode = player.gameMode.getGameModeForPlayer();
            if (mode == GameType.SURVIVAL || mode == GameType.ADVENTURE) {
                return player;
            }
        }
        return null;
    }

    private static int countOrdinaryZombies(MinecraftServer server) {
        int count = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (HordeIdentity.isOrdinaryZombie(entity)) {
                    count++;
                }
            }
        }
        return count;
    }
}
