package io.github.insulinocytus.theyarebillions;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
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

    private @Nullable BlockPos brainPos;
    private boolean ownershipVerified;
    private boolean runtimeUnloaded;

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
        if (!this.verifyOwnership()) {
            return;
        }
        super.tick();
    }

    public void onUnloaded(ServerLevel level) {
        Entity.RemovalReason removalReason = this.getRemovalReason();
        if (this.brainPos == null
            || level.getServer().isStopped()
            || removalReason == Entity.RemovalReason.CHANGED_DIMENSION) {
            return;
        }

        this.runtimeUnloaded = removalReason == null || removalReason == Entity.RemovalReason.UNLOADED_TO_CHUNK;
        this.ownershipVerified = false;
        ServerLevel ownerLevel = level.getServer().getLevel(Level.OVERWORLD);
        if (ownerLevel != null
            && ownerLevel.isLoaded(this.brainPos)
            && ownerLevel.getBlockEntity(this.brainPos) instanceof BrainInAJarBlockEntity brain) {
            brain.releaseHordeZombie(this.getUUID());
        }
    }

    private boolean verifyOwnership() {
        if (this.ownershipVerified) {
            return true;
        }
        if (this.brainPos == null) {
            this.ownershipVerified = true;
            return true;
        }
        if (!(this.level() instanceof ServerLevel currentLevel)) {
            return true;
        }

        ServerLevel ownerLevel = currentLevel.getServer().getLevel(Level.OVERWORLD);
        if (ownerLevel == null || !ownerLevel.isLoaded(this.brainPos)) {
            return true;
        }
        if (!(ownerLevel.getBlockEntity(this.brainPos) instanceof BrainInAJarBlockEntity brain)) {
            this.brainPos = null;
            this.ownershipVerified = true;
            return true;
        }
        if (!brain.ownsHordeZombie(this.getUUID())) {
            this.discard();
            return false;
        }

        this.ownershipVerified = true;
        return true;
    }

    @Override
    public boolean shouldBeSaved() {
        return !this.runtimeUnloaded && super.shouldBeSaved();
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
        this.runtimeUnloaded = false;
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
