package io.github.insulinocytus.theyarebillions;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

/**
 * A summonable zombie that reuses the vanilla adult zombie model, attributes, sounds and animations
 * while giving up every zombie behaviour that would create content or convert entities: random
 * equipment, reinforcements, riding, item pickup, door breaking, drowned conversion, villager
 * conversion and death loot. {@code createWitherRose} is suppressed too, because the vanilla death
 * path drops that item outside {@code dropAllDeathLoot}.
 */
public final class HordeZombie extends Zombie {
    private static final String BRAIN_POS_TAG = "BrainPos";
    private static final Set<HordeZombie> PENDING_OWNERSHIP_VALIDATIONS = new HashSet<>();
    private static final Map<BlockPos, Set<UUID>> PENDING_OWNERSHIP_RELEASES = new HashMap<>();
    private static long lastPreBrainValidationGameTime = Long.MIN_VALUE;

    private @Nullable BlockPos brainPos;
    private boolean ownershipVerified;

    public HordeZombie(EntityType<? extends Zombie> entityType, Level level) {
        super(entityType, level);
    }

    void setBrainPos(BlockPos brainPos) {
        this.brainPos = brainPos.immutable();
        this.ownershipVerified = false;
    }

    @Nullable BlockPos getBrainPos() {
        return this.brainPos;
    }

    @Override
    public void tick() {
        if (this.level() instanceof ServerLevel level && !this.validateOwnership(level)) {
            this.discard();
            return;
        }
        super.tick();
    }

    public void onUnloaded(ServerLevel level) {
        PENDING_OWNERSHIP_VALIDATIONS.remove(this);
        Entity.RemovalReason removalReason = this.getRemovalReason();
        if (this.brainPos == null
            || level.getServer().isStopped()
            || removalReason == Entity.RemovalReason.CHANGED_DIMENSION) {
            return;
        }
        UUID zombieId = this.getUUID();
        BlockPos ownerPos = this.brainPos;

        ServerLevel ownerLevel = level.getServer().getLevel(Level.OVERWORLD);
        LevelChunk ownerChunk = ownerLevel == null ? null : this.getOwnerChunkNow(ownerLevel);
        if (ownerChunk != null
            && ownerChunk.getBlockEntity(ownerPos, LevelChunk.EntityCreationType.IMMEDIATE)
                instanceof BrainInAJarBlockEntity brain) {
            brain.releaseHordeZombie(zombieId);
        } else {
            PENDING_OWNERSHIP_RELEASES.computeIfAbsent(ownerPos, ignored -> new HashSet<>()).add(zombieId);
        }
        if (removalReason == null) {
            level.getServer().tell(new TickTask(level.getServer().getTickCount(), this::discard));
        }
    }

    public boolean validateOwnershipOnLoad(ServerLevel currentLevel) {
        boolean valid = this.validateOwnership(currentLevel);
        if (valid && !this.ownershipVerified) {
            PENDING_OWNERSHIP_VALIDATIONS.add(this);
        }
        return valid;
    }

    static void validatePendingOwnershipBeforeBrainTick(ServerLevel level) {
        long gameTime = level.getGameTime();
        if (lastPreBrainValidationGameTime != gameTime) {
            lastPreBrainValidationGameTime = gameTime;
            validatePendingOwnership(level);
        }
    }

    static void validatePendingOwnership(ServerLevel level) {
        if (PENDING_OWNERSHIP_RELEASES.isEmpty() && PENDING_OWNERSHIP_VALIDATIONS.isEmpty()) {
            return;
        }
        if (level.dimension().equals(Level.OVERWORLD)) {
            Iterator<Map.Entry<BlockPos, Set<UUID>>> releases = PENDING_OWNERSHIP_RELEASES.entrySet().iterator();
            while (releases.hasNext()) {
                Map.Entry<BlockPos, Set<UUID>> release = releases.next();
                ChunkPos ownerChunkPos = new ChunkPos(release.getKey());
                LevelChunk ownerChunk = level.getChunkSource().getChunkNow(ownerChunkPos.x, ownerChunkPos.z);
                if (ownerChunk == null) {
                    continue;
                }
                if (ownerChunk.getBlockEntity(release.getKey(), LevelChunk.EntityCreationType.IMMEDIATE)
                    instanceof BrainInAJarBlockEntity brain) {
                    release.getValue().forEach(brain::releaseHordeZombie);
                }
                releases.remove();
            }
        }

        Iterator<HordeZombie> zombies = PENDING_OWNERSHIP_VALIDATIONS.iterator();
        while (zombies.hasNext()) {
            HordeZombie zombie = zombies.next();
            if (zombie.isRemoved()) {
                zombies.remove();
                continue;
            }
            if (zombie.level() != level) {
                continue;
            }
            if (!zombie.validateOwnership(level)) {
                zombies.remove();
                zombie.discard();
            } else if (zombie.ownershipVerified) {
                zombies.remove();
            }
        }
    }

    static void clearPendingOwnership() {
        PENDING_OWNERSHIP_RELEASES.clear();
        lastPreBrainValidationGameTime = Long.MIN_VALUE;
        PENDING_OWNERSHIP_VALIDATIONS.clear();
    }

    public boolean validateOwnership(ServerLevel currentLevel) {
        if (this.ownershipVerified) {
            return true;
        }
        if (this.brainPos == null) {
            this.ownershipVerified = true;
            return true;
        }

        ServerLevel ownerLevel = currentLevel.getServer().getLevel(Level.OVERWORLD);
        if (ownerLevel == null) {
            return true;
        }
        LevelChunk ownerChunk = this.getOwnerChunkNow(ownerLevel);
        if (ownerChunk == null) {
            return true;
        }
        if (!(ownerChunk.getBlockEntity(this.brainPos, LevelChunk.EntityCreationType.IMMEDIATE)
            instanceof BrainInAJarBlockEntity brain)) {
            this.brainPos = null;
            this.ownershipVerified = true;
            return true;
        }
        if (!brain.ownsHordeZombie(this.getUUID())) {
            return false;
        }

        this.ownershipVerified = true;
        return true;
    }

    private @Nullable LevelChunk getOwnerChunkNow(ServerLevel ownerLevel) {
        ChunkPos ownerChunkPos = new ChunkPos(this.brainPos);
        return ownerLevel.getChunkSource().getChunkNow(ownerChunkPos.x, ownerChunkPos.z);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.brainPos != null) {
            tag.putLong(BRAIN_POS_TAG, this.brainPos.asLong());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.brainPos = tag.contains(BRAIN_POS_TAG) ? BlockPos.of(tag.getLong(BRAIN_POS_TAG)) : null;
        this.ownershipVerified = false;
    }

    @Override
    protected boolean supportsBreakDoorGoal() {
        return false;
    }

    @Override
    public boolean isBaby() {
        return false;
    }

    @Override
    public void setBaby(boolean baby) {
    }

    @Override
    protected boolean convertsInWater() {
        return false;
    }

    @Override
    public boolean isUnderWaterConverting() {
        return false;
    }

    @Override
    public boolean canPickUpLoot() {
        return false;
    }

    @Override
    public void setCanPickUpLoot(boolean canPickUpLoot) {
    }

    @Override
    public boolean startRiding(Entity vehicle, boolean force) {
        return false;
    }

    @Override
    public boolean killedEntity(ServerLevel serverLevel, LivingEntity livingEntity) {
        return true;
    }

    /**
     * Skips every spawn-time randomisation the vanilla zombie performs: baby roll, chicken jockey,
     * door breaking, random equipment and reinforcements. Nothing in this class calls
     * {@code super}, so {@code populateDefaultEquipmentSlots} is unreachable for a horde zombie.
     */
    @Override
    public SpawnGroupData finalizeSpawn(
        ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, @Nullable SpawnGroupData spawnGroupData
    ) {
        return spawnGroupData;
    }

    @Override
    protected void dropAllDeathLoot(ServerLevel serverLevel, DamageSource damageSource) {
    }

    @Override
    protected void createWitherRose(@Nullable LivingEntity killer) {
    }
}
