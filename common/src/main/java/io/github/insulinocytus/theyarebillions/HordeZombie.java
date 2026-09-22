package io.github.insulinocytus.theyarebillions;

import java.util.HashMap;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
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
    private static final String PLAYER_TARGET_TAG = "PlayerTarget";
    private static final String PLAYER_RETARGET_COOLDOWN_TAG = "PlayerRetargetCooldown";
    private static final String REBIND_COOLDOWN_TAG = "RebindCooldown";
    private static final String DECAY_TICKS_TAG = "DecayTicks";
    private static final int DECAY_INTERVAL = 20;
    private static final int PLAYER_TARGET_RANGE = 24;
    private static final int PLAYER_RETARGET_COOLDOWN = 100;
    private static final int REBIND_INTERVAL = 100;
    private static final int REBIND_RANGE = 128;
    private static final double MOVE_TO_BRAIN_SPEED = 1.0;
    private static final Set<HordeZombie> PENDING_OWNERSHIP_VALIDATIONS = new HashSet<>();
    private static final Map<BlockPos, Set<UUID>> PENDING_OWNERSHIP_RELEASES = new HashMap<>();
    private static long lastPreBrainValidationGameTime = Long.MIN_VALUE;
    private static final Set<MinecraftServer> PERSISTING_SERVERS = Collections.newSetFromMap(new IdentityHashMap<>());

    private @Nullable BlockPos brainPos;
    private boolean ownershipVerified;
    private @Nullable UUID playerTargetId;
    private int playerRetargetCooldown;
    private int rebindCooldown;
    private int decayTicks;

    public HordeZombie(EntityType<? extends Zombie> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.addBehaviourGoals();
    }

    void setBrainPos(BlockPos brainPos) {
        this.brainPos = brainPos.immutable();
        this.ownershipVerified = false;
        this.playerTargetId = null;
        this.playerRetargetCooldown = 0;
        this.rebindCooldown = REBIND_INTERVAL;
        this.setTarget(null);
    }

    void abandonBrain(BlockPos expectedBrainPos) {
        if (!expectedBrainPos.equals(this.brainPos)) {
            return;
        }
        this.brainPos = null;
        this.ownershipVerified = true;
        this.playerTargetId = null;
        this.playerRetargetCooldown = 0;
        this.rebindCooldown = REBIND_INTERVAL;
    }

    @Nullable BlockPos getBrainPos() {
        return this.brainPos;
    }

    @Override
    public void tick() {
        if (!(this.level() instanceof ServerLevel level)) {
            super.tick();
            return;
        }
        if (!this.validateOwnership(level)) {
            this.discard();
            return;
        }
        if (this.tickSunriseCleanup(level)) {
            return;
        }

        super.tick();
        if (this.isRemoved()) {
            return;
        }
        if (this.brainPos == null) {
            this.tickRebinding(level);
        } else {
            this.tickOwnedTarget(level);
        }
    }

    private boolean tickSunriseCleanup(ServerLevel level) {
        if (level.isDay()) {
            BlockPos eyePos = BlockPos.containing(this.getX(), this.getEyeY(), this.getZ());
            if (level.canSeeSky(eyePos)) {
                this.kill();
                return true;
            }
            if (!this.hasEffect(TheyAreBillions.decayEffect())) {
                this.addEffect(new MobEffectInstance(
                    TheyAreBillions.decayEffect(),
                    MobEffectInstance.INFINITE_DURATION,
                    0,
                    false,
                    false,
                    false
                ));
                this.decayTicks = 0;
            }
        }
        if (!this.hasEffect(TheyAreBillions.decayEffect())) {
            this.decayTicks = 0;
            return false;
        }
        if (++this.decayTicks < DECAY_INTERVAL) {
            return false;
        }

        this.decayTicks = 0;
        var maxHealth = this.getAttribute(Attributes.MAX_HEALTH);
        double nextMaxHealth = maxHealth.getBaseValue() - 1.0;
        float nextHealth = this.getHealth() - 1.0F;
        if (nextMaxHealth <= 0.0 || nextHealth <= 0.0F) {
            this.kill();
            return true;
        }
        maxHealth.setBaseValue(nextMaxHealth);
        this.setHealth(nextHealth);
        return false;
    }

    private void tickOwnedTarget(ServerLevel level) {
        if (this.playerTargetId != null) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(this.playerTargetId);
            if (player == null) {
                if (this.getTarget() != null) {
                    this.setTarget(null);
                }
                if (this.getNavigation().isDone() || this.tickCount % 20 == 0) {
                    this.moveTowardBrain();
                }
                return;
            }
            if (this.isValidOwnedPlayerTarget(player)) {
                if (this.getTarget() != player) {
                    this.setTarget(player);
                }
                return;
            }
            this.playerTargetId = null;
            this.setTarget(null);
            this.playerRetargetCooldown = PLAYER_RETARGET_COOLDOWN;
            return;
        }

        if (this.getTarget() != null) {
            this.setTarget(null);
        }
        if (this.getNavigation().isDone() || this.tickCount % 20 == 0) {
            this.moveTowardBrain();
        }
        if (this.playerRetargetCooldown > 0 && --this.playerRetargetCooldown > 0) {
            return;
        }

        ServerPlayer nearest = null;
        int nearestDistance = Integer.MAX_VALUE;
        BlockPos zombiePos = this.blockPosition();
        for (ServerPlayer player : level.players()) {
            int distance = manhattanDistance(zombiePos, player.blockPosition());
            if (distance <= PLAYER_TARGET_RANGE && distance < nearestDistance && this.isValidOwnedPlayerTarget(player)) {
                nearest = player;
                nearestDistance = distance;
            }
        }
        if (nearest != null) {
            this.playerTargetId = nearest.getUUID();
            this.setTarget(nearest);
        }
    }

    private boolean isValidOwnedPlayerTarget(Player player) {
        return player.level() == this.level()
            && player.isAlive()
            && player.isAttackable()
            && !player.isCreative()
            && !player.isSpectator()
            && manhattanDistance(this.blockPosition(), player.blockPosition()) <= PLAYER_TARGET_RANGE
            && super.canAttack(player);
    }

    private static int manhattanDistance(BlockPos first, BlockPos second) {
        return Math.abs(first.getX() - second.getX())
            + Math.abs(first.getY() - second.getY())
            + Math.abs(first.getZ() - second.getZ());
    }

    private void tickRebinding(ServerLevel level) {
        if (this.rebindCooldown > 0 && --this.rebindCooldown > 0) {
            return;
        }
        this.rebindCooldown = REBIND_INTERVAL;
        if (!level.dimension().equals(Level.OVERWORLD) || !level.isNight()) {
            return;
        }

        BrainInAJarBlockEntity nearest = this.findNearestAvailableBrain(level);
        if (nearest != null && nearest.tryClaimHordeZombie(this.getUUID())) {
            this.setBrainPos(nearest.getBlockPos());
        }
    }

    private @Nullable BrainInAJarBlockEntity findNearestAvailableBrain(ServerLevel level) {
        return BrainInAJarBlockEntity.findNearestAvailable(level, this.blockPosition(), REBIND_RANGE);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        if (this.brainPos == null) {
            return super.canAttack(target);
        }
        return target instanceof Player player
            && this.playerTargetId != null
            && this.playerTargetId.equals(player.getUUID())
            && this.isValidOwnedPlayerTarget(player);
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }


    public void onUnloaded(ServerLevel level) {
        PENDING_OWNERSHIP_VALIDATIONS.remove(this);
        Entity.RemovalReason removalReason = this.getRemovalReason();
        if (isPersisting(level) || removalReason == Entity.RemovalReason.CHANGED_DIMENSION) {
            return;
        }
        if (this.brainPos == null) {
            level.getServer().tell(new TickTask(level.getServer().getTickCount() + 1, this::discard));
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
        level.getServer().tell(new TickTask(level.getServer().getTickCount() + 1, this::discard));
    }

    @Override
    public boolean shouldBeSaved() {
        if (!(this.level() instanceof ServerLevel level)) {
            return super.shouldBeSaved();
        }
        return isPersisting(level) && super.shouldBeSaved();
    }

    private static boolean isPersisting(ServerLevel level) {
        MinecraftServer server = level.getServer();
        return PERSISTING_SERVERS.contains(server) || server.isCurrentlySaving() || server.isStopped();
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

    static void beginServerShutdown(MinecraftServer server) {
        PERSISTING_SERVERS.add(server);
    }


    static void clearPendingOwnership(MinecraftServer server) {
        PERSISTING_SERVERS.remove(server);
        clearPendingOwnership();
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
            this.abandonBrain(this.brainPos);
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
        if (this.playerTargetId != null) {
            tag.putUUID(PLAYER_TARGET_TAG, this.playerTargetId);
        }
        if (this.playerRetargetCooldown > 0) {
            tag.putInt(PLAYER_RETARGET_COOLDOWN_TAG, this.playerRetargetCooldown);
        }
        if (this.rebindCooldown > 0) {
            tag.putInt(REBIND_COOLDOWN_TAG, this.rebindCooldown);
        }
        if (this.decayTicks > 0) {
            tag.putInt(DECAY_TICKS_TAG, this.decayTicks);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.brainPos = tag.contains(BRAIN_POS_TAG) ? BlockPos.of(tag.getLong(BRAIN_POS_TAG)) : null;
        this.playerTargetId = tag.hasUUID(PLAYER_TARGET_TAG) ? tag.getUUID(PLAYER_TARGET_TAG) : null;
        this.playerRetargetCooldown = tag.getInt(PLAYER_RETARGET_COOLDOWN_TAG);
        this.rebindCooldown = tag.getInt(REBIND_COOLDOWN_TAG);
        this.decayTicks = tag.getInt(DECAY_TICKS_TAG);
        this.ownershipVerified = false;
    }

    private void moveTowardBrain() {
        BlockPos brain = this.brainPos;
        if (brain == null) {
            return;
        }
        BlockPos zombie = this.blockPosition();
        int dx = zombie.getX() - brain.getX();
        int dz = zombie.getZ() - brain.getZ();
        if (dx == 0 && dz == 0) {
            if (zombie.getY() == brain.getY()) {
                return;
            }
            dx = 1;
        }
        if (Math.abs(dx) > Math.abs(dz)) {
            dz = 0;
        } else {
            dx = 0;
        }
        this.getNavigation().moveTo(
            brain.getX() + Integer.signum(dx) + 0.5,
            brain.getY(),
            brain.getZ() + Integer.signum(dz) + 0.5,
            MOVE_TO_BRAIN_SPEED
        );
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
