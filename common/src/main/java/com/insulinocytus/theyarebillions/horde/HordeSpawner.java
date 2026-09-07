package com.insulinocytus.theyarebillions.horde;

import com.insulinocytus.theyarebillions.HordeSpawnAccess;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
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
    public static final String HORDE_TAG = "theyarebillions.horde";

    // ponytail: in-memory night directions; persist in SavedData when restarts must keep them
    private static HordePlanner.NightState night = HordePlanner.NightState.none();

    private HordeSpawner() {
    }

    public static void onServerTick(MinecraftServer server) {
        tick(server, server.overworld());
    }

    public static boolean isHordeMember(Entity entity) {
        return entity.getType() == EntityType.ZOMBIE && entity.getTags().contains(HORDE_TAG);
    }

    public static boolean hasPersistentHordeTag(Entity entity) {
        CompoundTag nbt = new CompoundTag();
        entity.saveWithoutId(nbt);
        ListTag tags = nbt.getList("Tags", Tag.TAG_STRING);
        for (int i = 0; i < tags.size(); i++) {
            if (HORDE_TAG.equals(tags.getString(i))) {
                return true;
            }
        }
        return false;
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
        if (!HordeSpawnAccess.finalizeHordeSpawn(zombie, level)) {
            zombie.discard();
            return false;
        }
        zombie.addTag(HORDE_TAG);
        return level.addFreshEntity(zombie);
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
        int spawned = 0;
        int failed = 0;
        int index = 0;
        while (spawned < plan.successfulSpawnLimit() && failed < plan.failedAttemptLimit()) {
            int chosen = nextGroup(remainingQuota, index);
            if (trySpawnInSector(level, groups.get(chosen).sector())) {
                spawned++;
            } else {
                failed++;
            }
            if (remainingQuota[chosen] > 0) {
                remainingQuota[chosen]--;
            }
            index = chosen + 1;
        }
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
        int count = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity.getType() == EntityType.ZOMBIE
                        && (level.isPositionEntityTicking(entity.blockPosition()) || isHordeMember(entity))) {
                    count++;
                }
            }
        }
        return count;
    }
}
