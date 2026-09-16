package io.github.insulinocytus.theyarebillions;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import org.jetbrains.annotations.Nullable;

public final class BrainInAJarBlockEntity extends BlockEntity {
    private static final TicketType<BlockPos> TICKET = TicketType.create("they_are_billions:brain_in_a_jar", Vec3i::compareTo);
    private static final int MAX_HORDE_SIZE = 300;
    private static final String OWNS_FORCED_CHUNK_TAG = "OwnsForcedChunk";
    private static final String HORDE_DIRECTION_TAG = "HordeDirection";
    private static final String WAS_NIGHT_TAG = "WasNight";
    private static final String SELECTED_NIGHT_TAG = "SelectedNight";
    private static final double MIN_SPAWN_DISTANCE = 64.0;
    private static final double MAX_SPAWN_DISTANCE = 128.0;
    private static final double HALF_SECTOR_ANGLE = Math.toRadians(22.5);
    private static final double SECTOR_TANGENT = Math.tan(HALF_SECTOR_ANGLE);

    private boolean ownsForcedChunk;
    private int appliedTicketRadius;
    private boolean ownershipReconciled;
    private int ownershipReconciliationTicks;
    private @Nullable Direction hordeDirection;
    private boolean wasNight;
    private long selectedNight;

    public BrainInAJarBlockEntity(BlockPos pos, BlockState state) {
        super(TheyAreBillions.BRAIN_IN_A_JAR_BLOCK_ENTITY_TYPE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BrainInAJarBlockEntity brain) {
        if (!(level instanceof ServerLevel serverLevel) || !level.dimension().equals(Level.OVERWORLD)) {
            return;
        }

        brain.updateTickets(serverLevel, pos);
        if (!brain.ownershipReconciled) {
            if (!brain.areOwnershipEntitiesLoaded(serverLevel, pos)) {
                brain.ownershipReconciliationTicks = 0;
                return;
            }
            if (++brain.ownershipReconciliationTicks < 2) {
                return;
            }
            brain.ownershipReconciled = true;
        }
        if (!serverLevel.isNight()) {
            if (brain.wasNight) {
                brain.wasNight = false;
                brain.setChanged();
            }
            return;
        }

        long night = Math.floorDiv(serverLevel.getDayTime(), 24000L);
        if (!brain.wasNight || brain.selectedNight != night) {
            brain.hordeDirection = Direction.Plane.HORIZONTAL.getRandomDirection(serverLevel.random);
            brain.wasNight = true;
            brain.selectedNight = night;
            brain.setChanged();
        }
        int ownedHordeZombies = brain.countOwnedHordeZombies(serverLevel, pos);
        if (brain.hordeDirection != null
            && level.getDifficulty() != Difficulty.PEACEFUL
            && level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)
            && ownedHordeZombies < MAX_HORDE_SIZE) {
            brain.trySpawnHordeZombie(serverLevel, pos, brain.hordeDirection);
        }
    }

    void onBlockRemoved(ServerLevel level) {
        if (!level.dimension().equals(Level.OVERWORLD)) {
            return;
        }

        var chunk = new ChunkPos(this.worldPosition);
        if (this.appliedTicketRadius != 0) {
            level.getChunkSource().removeRegionTicket(TICKET, chunk, this.appliedTicketRadius, this.worldPosition);
            this.appliedTicketRadius = 0;
        }
        if (this.ownsForcedChunk) {
            level.setChunkForced(chunk.x, chunk.z, false);
            this.ownsForcedChunk = false;
            this.setChanged();
        }
    }

    void updateTickets(ServerLevel level, BlockPos pos) {
        var chunk = new ChunkPos(pos);
        if (!level.getForcedChunks().contains(chunk.toLong())) {
            level.setChunkForced(chunk.x, chunk.z, true);
            this.ownsForcedChunk = true;
            this.setChanged();
        }

        int radius = level.getServer().getPlayerList().getSimulationDistance() + 2;
        if (radius == this.appliedTicketRadius) {
            return;
        }
        if (this.appliedTicketRadius != 0) {
            level.getChunkSource().removeRegionTicket(TICKET, chunk, this.appliedTicketRadius, pos);
        }
        level.getChunkSource().addRegionTicket(TICKET, chunk, radius, pos);
        this.appliedTicketRadius = radius;
        this.ownershipReconciled = false;
        this.ownershipReconciliationTicks = 0;
    }

    private boolean areOwnershipEntitiesLoaded(ServerLevel level, BlockPos brainPos) {
        ChunkPos brainChunk = new ChunkPos(brainPos);
        int radius = Mth.ceil(MAX_SPAWN_DISTANCE / 16.0);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            var boundaryChunk = new ChunkPos(
                brainChunk.x + direction.getStepX() * radius,
                brainChunk.z + direction.getStepZ() * radius
            );
            if (!level.isPositionEntityTicking(boundaryChunk.getMiddleBlockPosition(brainPos.getY()))) {
                return false;
            }
        }

        for (int x = brainChunk.x - radius; x <= brainChunk.x + radius; x++) {
            for (int z = brainChunk.z - radius; z <= brainChunk.z + radius; z++) {
                var chunk = new ChunkPos(x, z);
                BlockPos sample = chunk.getMiddleBlockPosition(brainPos.getY());
                if (level.isPositionEntityTicking(sample) && !level.areEntitiesLoaded(chunk.toLong())) {
                    return false;
                }
            }
        }
        return true;
    }
    private int countOwnedHordeZombies(ServerLevel level, BlockPos brainPos) {
        int count = 0;
        for (var entity : level.getAllEntities()) {
            if (entity instanceof HordeZombie zombie && zombie.isAlive() && brainPos.equals(zombie.getBrainPos())) {
                count++;
            }
        }
        return count;
    }

    private void trySpawnHordeZombie(ServerLevel level, BlockPos brainPos, Direction direction) {
        double angle = Math.atan2(direction.getStepZ(), direction.getStepX())
            + (level.random.nextDouble() * 2.0 - 1.0) * HALF_SECTOR_ANGLE;
        double sampledDistance = Mth.lerp(level.random.nextDouble(), MIN_SPAWN_DISTANCE, MAX_SPAWN_DISTANCE);
        int x = Mth.floor(brainPos.getX() + 0.5 + Math.cos(angle) * sampledDistance);
        int z = Mth.floor(brainPos.getZ() + 0.5 + Math.sin(angle) * sampledDistance);
        int dx = x - brainPos.getX();
        int dz = z - brainPos.getZ();
        double distance = Math.hypot(dx, dz);
        int forward = dx * direction.getStepX() + dz * direction.getStepZ();
        int side = dx * direction.getStepZ() - dz * direction.getStepX();
        if (distance < MIN_SPAWN_DISTANCE
            || distance > MAX_SPAWN_DISTANCE
            || forward <= 0
            || Math.abs(side) > forward * SECTOR_TANGENT) {
            return;
        }

        BlockPos horizontalCandidate = new BlockPos(x, brainPos.getY(), z);
        if (!level.isPositionEntityTicking(horizontalCandidate)) {
            return;
        }
        BlockPos candidate = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, horizontalCandidate);
        if (Math.abs(candidate.getY() - brainPos.getY()) > 32) {
            return;
        }

        EntityType<HordeZombie> entityType = TheyAreBillions.HORDE_ZOMBIE_ENTITY_TYPE.get();
        BlockPos below = candidate.below();
        BlockState belowState = level.getBlockState(below);
        BlockState candidateState = level.getBlockState(candidate);
        BlockPos above = candidate.above();
        BlockState aboveState = level.getBlockState(above);
        AABB spawnBox = entityType.getDimensions().makeBoundingBox(
            candidate.getX() + 0.5, candidate.getY(), candidate.getZ() + 0.5
        );
        if (!level.getWorldBorder().isWithinBounds(spawnBox)
            || !belowState.isFaceSturdy(level, below, Direction.UP)
            || entityType.isBlockDangerous(belowState)
            || !NaturalSpawner.isValidEmptySpawnBlock(
                level, candidate, candidateState, candidateState.getFluidState(), entityType
            )
            || !NaturalSpawner.isValidEmptySpawnBlock(level, above, aboveState, aboveState.getFluidState(), entityType)) {
            return;
        }

        HordeZombie zombie = entityType.create(level);
        if (zombie == null) {
            return;
        }
        zombie.setBrainPos(brainPos);
        zombie.moveTo(candidate.getX() + 0.5, candidate.getY(), candidate.getZ() + 0.5, level.random.nextFloat() * 360.0F, 0.0F);
        if (!level.noCollision(zombie, zombie.getBoundingBox())) {
            return;
        }
        zombie.finalizeSpawn(level, level.getCurrentDifficultyAt(candidate), MobSpawnType.EVENT, null);
        level.addFreshEntity(zombie);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.ownershipReconciled = false;
        this.ownsForcedChunk = tag.getBoolean(OWNS_FORCED_CHUNK_TAG);
        this.ownershipReconciliationTicks = 0;
        this.hordeDirection = tag.contains(HORDE_DIRECTION_TAG, Tag.TAG_INT)
            ? Direction.from2DDataValue(tag.getInt(HORDE_DIRECTION_TAG))
            : null;
        this.wasNight = tag.getBoolean(WAS_NIGHT_TAG);
        this.selectedNight = tag.getLong(SELECTED_NIGHT_TAG);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean(OWNS_FORCED_CHUNK_TAG, this.ownsForcedChunk);
        if (this.hordeDirection != null) {
            tag.putInt(HORDE_DIRECTION_TAG, this.hordeDirection.get2DDataValue());
        }
        tag.putBoolean(WAS_NIGHT_TAG, this.wasNight);
        tag.putLong(SELECTED_NIGHT_TAG, this.selectedNight);
    }
}
