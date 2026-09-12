package com.insulinocytus.theyarebillions.horde;

import com.insulinocytus.theyarebillions.HordeChunkTicketAccess;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

public final class HordeGameTests {
    public static final String EMPTY_TEMPLATE = "theyarebillions:empty";

    private HordeGameTests() {
    }

    public static void taggedVanillaZombieSpawns(GameTestHelper helper) {
        Zombie zombie = spawnHordeMember(helper, new BlockPos(2, 2, 2));
        helper.assertTrue(HordeIdentity.isOrdinaryZombie(zombie), "vanilla zombie should exist");
        helper.assertTrue(HordeIdentity.isHordeMember(zombie), "zombie should carry horde tag");
        helper.assertTrue(HordeIdentity.hasPersistentHordeTag(zombie), "horde tag should persist in NBT");
        helper.assertTrue(zombie instanceof HordeMemberState, "zombie should expose synchronized horde state");
        helper.assertTrue(
                ((HordeMemberState) zombie).theyarebillions$isSyncedHordeMember(),
                "horde state should be available to clients through the mod payload");
        helper.assertTrue(!zombie.isPersistenceRequired(), "horde tag must not force persistence");
        helper.assertTrue(!zombie.isBaby(), "horde members are adults");
        helper.assertTrue(!zombie.canPickUpLoot(), "horde members cannot pick up items");
        assertEmptyGear(helper, zombie);
        CompoundTag nbt = new CompoundTag();
        helper.assertTrue(zombie.save(nbt), "horde member should save as an entity");
        helper.assertTrue("minecraft:zombie".equals(nbt.getString("id")), "removing the mod must leave a vanilla zombie");
        helper.succeed();
    }

    public static void hordeMemberKeepsItsPlayerGroup(GameTestHelper helper) {
        helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
        prepareGrass(helper);
        HordePlanner.GroupIdentity group = HordePlanner.GroupIdentity.of(
                "00000000-0000-0000-0000-000000000001", "00000000-0000-0000-0000-000000000002");
        helper.assertTrue(
                HordeSpawner.spawnHordeMember(helper.getLevel(), helper.absolutePos(new BlockPos(2, 2, 2)), group),
                "horde member should spawn");
        Zombie zombie = helper.getEntities(EntityType.ZOMBIE).getFirst();

        helper.assertTrue(group.equals(HordeIdentity.group(zombie)), "horde member should keep its player group");
        helper.assertTrue(HordeIdentity.hasPersistentGroup(zombie, group), "player group should persist in NBT");
        helper.succeed();
    }

    public static void hordeMemberDeathDropsNothing(GameTestHelper helper) {
        Zombie zombie = spawnHordeMember(helper, new BlockPos(2, 2, 2));
        zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND));
        helper.assertTrue(zombie.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty(), "horde members cannot be equipped");
        zombie.kill();
        helper.assertEntityNotPresent(EntityType.ITEM);
        helper.assertEntityNotPresent(EntityType.EXPERIENCE_ORB);
        helper.succeed();
    }

    public static void removingHordeTagRestoresServerBehavior(GameTestHelper helper) {
        Zombie zombie = spawnHordeMember(helper, new BlockPos(2, 2, 2));
        zombie.removeTag(HordeIdentity.HORDE_TAG);
        helper.assertFalse(
                ((HordeMemberState) zombie).theyarebillions$isSyncedHordeMember(),
                "external tag removal must update the client identity mirror");
        helper.assertFalse(HordeIdentity.isHordeMember(zombie), "server identity must use only the horde tag");
        zombie.addTag(HordeIdentity.HORDE_TAG);
        helper.assertTrue(
                ((HordeMemberState) zombie).theyarebillions$isSyncedHordeMember(),
                "external tag addition must update the client identity mirror");
        zombie.removeTag(HordeIdentity.HORDE_TAG);
        zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND));
        helper.assertTrue(
                !zombie.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty(),
                "removing the tag must restore server behavior");
        helper.succeed();
    }

    public static void namingRemovesHordeMarkAndRestoresVanillaBehavior(GameTestHelper helper) {
        Zombie zombie = spawnHordeMember(helper, new BlockPos(2, 2, 2));
        zombie.setCanPickUpLoot(true);
        zombie.setBaby(true);
        helper.assertTrue(!zombie.canPickUpLoot(), "horde members cannot pick up items");
        helper.assertTrue(!zombie.isBaby(), "horde members stay adult");
        zombie.setCustomName(Component.literal("Pat"));
        helper.assertTrue(!HordeIdentity.isHordeMember(zombie), "naming must remove the horde mark");
        helper.assertTrue(!HordeIdentity.hasPersistentHordeTag(zombie), "naming must drop the persistent horde tag");
        helper.assertTrue(
                !((HordeMemberState) zombie).theyarebillions$isSyncedHordeMember(),
                "naming must clear synchronized horde state");
        helper.assertTrue(zombie.canPickUpLoot(), "named zombies restore pickup");
        zombie.setBaby(true);
        helper.assertTrue(zombie.isBaby(), "named zombies can be babies");
        zombie.setBaby(false);
        giveGuaranteedDiamond(zombie);
        zombie.kill();
        helper.assertItemEntityPresent(Items.DIAMOND);
        helper.succeed();
    }

    public static void explicitSpawnsStayVanilla(GameTestHelper helper) {
        prepareGrass(helper);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
        helper.assertTrue(!HordeIdentity.isHordeMember(zombie), "existing or explicit zombies are not horde members");
        ZombieVillager villager = helper.spawn(EntityType.ZOMBIE_VILLAGER, new BlockPos(1, 2, 2));
        helper.assertTrue(!HordeIdentity.isOrdinaryZombie(villager), "zombie villagers are not ordinary zombies");
        helper.assertTrue(!HordeIdentity.isHordeMember(villager), "zombie villagers are not horde members");
        helper.succeed();
    }

    public static void ordinaryZombieNaturalPopulationStaysTakenOver(GameTestHelper helper) {
        prepareGrass(helper);
        BlockPos feet = helper.absolutePos(new BlockPos(2, 2, 2));
        RandomSource random = helper.getLevel().random;
        helper.assertFalse(
                SpawnPlacements.checkSpawnRules(
                        EntityType.ZOMBIE, helper.getLevel(), MobSpawnType.NATURAL, feet, random),
                "ordinary zombie natural spawns are taken over");
        helper.assertFalse(
                SpawnPlacements.checkSpawnRules(
                        EntityType.ZOMBIE, helper.getLevel(), MobSpawnType.REINFORCEMENT, feet, random),
                "ordinary zombie reinforcements are taken over");
        helper.assertFalse(
                SpawnPlacements.checkSpawnRules(
                        EntityType.ZOMBIE, helper.getLevel(), MobSpawnType.CHUNK_GENERATION, feet, random),
                "ordinary zombie chunk generation is taken over");
        helper.succeed();
    }

    public static void nearbyMeleeAttackStillHits(GameTestHelper helper) {
        Zombie zombie = spawnHordeMember(helper, new BlockPos(2, 2, 2));
        zombie.setPersistenceRequired();
        helper.setBlock(new BlockPos(2, 4, 2), Blocks.STONE);
        nearbyPlayer(helper, zombie.getX() + 2.0, zombie.getY(), zombie.getZ());
        zombie.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.5);
        Villager villager = helper.spawn(EntityType.VILLAGER, helper.relativeVec(zombie.position()).add(1.0, 0.0, 0.0));
        villager.setNoAi(true);
        float health = villager.getHealth();

        helper.succeedWhen(() -> {
            zombie.setTarget(villager);
            helper.assertTrue(villager.getHealth() < health, "horde member near a player should retain vanilla melee hits");
        });
    }

    public static void hordeMemberPassesOneBlockDoorway(GameTestHelper helper) {
        for (int z = 0; z <= 5; z++) {
            for (int x = 0; x <= 4; x++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
                helper.setBlock(new BlockPos(x, 4, z), Blocks.STONE);
            }
            for (int y = 2; y <= 3; y++) {
                helper.setBlock(new BlockPos(1, y, z), Blocks.STONE);
                helper.setBlock(new BlockPos(3, y, z), Blocks.STONE);
            }
        }
        for (int y = 2; y <= 3; y++) {
            helper.setBlock(new BlockPos(0, y, 3), Blocks.STONE);
            helper.setBlock(new BlockPos(4, y, 3), Blocks.STONE);
        }
        Zombie zombie = spawnHordeMember(helper, new BlockPos(2, 2, 1));
        zombie.setPersistenceRequired();
        nearbyPlayer(helper, zombie.getX(), zombie.getY(), zombie.getZ() + 3.0);
        Villager villager = helper.spawn(EntityType.VILLAGER, new Vec3(2.5, 2.0, 4.5));
        villager.setNoAi(true);
        zombie.setTarget(villager);
        double destinationZ = villager.getZ();

        helper.succeedWhen(() -> helper.assertTrue(
                zombie.getZ() > destinationZ - 1.0,
                "horde member should pass through the one-block doorway"));
    }

    public static void nearbyPlayerStillGetsPushed(GameTestHelper helper) {
        Zombie zombie = spawnHordeMember(helper, new BlockPos(2, 2, 2));
        zombie.setPersistenceRequired();
        helper.setBlock(new BlockPos(2, 4, 2), Blocks.STONE);
        ServerPlayer player = nearbyPlayer(helper, zombie.getX() + 0.1, zombie.getY(), zombie.getZ());
        zombie.setNoAi(true);
        player.setDeltaMovement(Vec3.ZERO);

        helper.runAfterDelay(1, () -> {
            helper.assertTrue(player.getDeltaMovement().horizontalDistanceSqr() > 0.0, "nearby player should receive push impulse");
            helper.succeed();
        });
    }

    @SuppressWarnings("removal")
    private static ServerPlayer nearbyPlayer(GameTestHelper helper, double x, double y, double z) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        player.setPos(x, y, z);
        return player;
    }

    public static void hordeDiggingUsesEmptySurvivalFakePlayerDrops(GameTestHelper helper) {
        Zombie zombie = spawnHordeMember(helper, new BlockPos(2, 2, 2));
        zombie.setPersistenceRequired();
        helper.setBlock(new BlockPos(2, 4, 2), Blocks.STONE);
        zombie.setCanBreakDoors(true);
        HordeNavigation.tick(zombie);
        helper.assertFalse(zombie.canBreakDoors(), "horde members should not keep vanilla door breaking");
        CompoundTag saved = new CompoundTag();
        zombie.saveWithoutId(saved);
        Zombie restored = EntityType.ZOMBIE.create(helper.getLevel());
        helper.assertTrue(restored != null, "restored zombie should exist");
        restored.load(saved);
        restored.removeTag(HordeIdentity.HORDE_TAG);
        HordeNavigation.tick(restored);
        helper.assertTrue(restored.canBreakDoors(), "door breaking should restore after reload and horde removal");
        zombie.removeTag(HordeIdentity.HORDE_TAG);
        HordeNavigation.tick(zombie);
        helper.assertTrue(zombie.canBreakDoors(), "leaving the horde should restore vanilla door breaking");
        HordeIdentity.mark(zombie);

        BlockPos obstacle = new BlockPos(3, 2, 2);
        BlockPos destination = helper.absolutePos(new BlockPos(4, 2, 2));
        helper.setBlock(obstacle, Blocks.BEDROCK);
        helper.assertTrue(
                HordeBlockBreaking.start(helper.getLevel(), zombie, destination, null).digging() == null,
                "unbreakable blocks should stay intact");

        helper.setBlock(obstacle, Blocks.DIRT);
        HordeBlockBreaking.StartResult denied;
        helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false, helper.getLevel().getServer());
        try {
            denied = HordeBlockBreaking.start(helper.getLevel(), zombie, destination, null);
        } finally {
            helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true, helper.getLevel().getServer());
        }
        helper.assertTrue(denied.deniedPos() != null, "mobGriefing should deny digging");

        HordeBlockBreaking.StartResult start = HordeBlockBreaking.start(helper.getLevel(), zombie, destination, null);
        helper.assertTrue(start.digging() != null, "adjacent dirt should start a digging point");
        ServerPlayer player = HordeBlockBreakingAccess.player(helper.getLevel());
        helper.assertTrue(
                HordeBlockBreakingAccess.PROFILE.equals(player.getGameProfile()),
                "digging should use the mod GameProfile");
        helper.assertTrue(player.gameMode.isSurvival(), "digging should use survival mode");
        helper.assertTrue(player.getMainHandItem().isEmpty(), "digging should use an empty hand");

        helper.onEachTick(() -> HordeBlockBreaking.tick(helper.getLevel(), zombie, start.digging()));
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.AIR, obstacle);
            helper.assertTrue(
                    helper.getEntities(EntityType.ITEM).stream()
                            .anyMatch(item -> item.getItem().is(Items.DIRT)),
                    "dirt should use player drop semantics");
        });
    }

    public static void hordeTicketMakesChunkEntityTick(GameTestHelper helper) {
        ChunkPos origin = new ChunkPos(helper.absolutePos(BlockPos.ZERO));
        ChunkPos distant = new ChunkPos(origin.x + 12, origin.z);
        helper.onEachTick(() -> HordeChunkTicketAccess.acquireOrRenew(helper.getLevel(), distant));
        helper.succeedWhen(() -> {
            helper.assertTrue(
                    helper.getLevel().isPositionEntityTicking(distant.getMiddleBlockPosition(0)),
                    "horde ticket should make the chunk entity tick");
            HordeChunkTicketAccess.release(helper.getLevel(), distant);
        });
    }

    public static void hordeMemberIgnoresVanillaDistanceDespawn(GameTestHelper helper) {
        Zombie zombie = spawnHordeMember(helper, new BlockPos(2, 2, 2));
        helper.assertFalse(
                zombie.removeWhenFarAway((double) 200 * 200),
                "horde members should use the common 160-block cleanup instead of vanilla despawn");
        helper.succeed();
    }
    public static void daytimeCleanupRemovesSunlitWithoutDrops(GameTestHelper helper) {
        prepareGrass(helper);
        openSky(helper, new BlockPos(2, 2, 2));
        Zombie zombie = spawnOrdinaryZombie(helper, new BlockPos(2, 2, 2));
        giveGuaranteedDiamond(zombie);
        helper.succeedWhen(() -> {
            helper.assertTrue(zombie.isRemoved(), "vanilla sun-burn should queue silent daytime cleanup");
            helper.assertEntityNotPresent(EntityType.ITEM);
            helper.assertEntityNotPresent(EntityType.EXPERIENCE_ORB);
        });
    }

    public static void daytimeCleanupReleasesHordeTickets(GameTestHelper helper) {
        Zombie zombie = spawnHordeMember(helper, new BlockPos(2, 2, 2));
        HordePlanner.ChunkRef chunk = new HordePlanner.ChunkRef(zombie.chunkPosition().x, zombie.chunkPosition().z);
        helper.assertTrue(
                HordeChunkData.get(helper.getLevel()).occupancy().getOrDefault(chunk, 0) > 0,
                "horde member should occupy a chunk ticket");
        helper.assertTrue(HordeDaytimeCleanup.queueSunlit(zombie), "horde member can be sunlit");
        HordeDaytimeCleanup.tick(helper.getLevel(), List.of(), HordeDaytimeCleanup.REMOVALS_PER_TICK);
        helper.assertTrue(zombie.isRemoved(), "sunlit horde member should be discarded");
        helper.assertTrue(
                !HordeChunkData.get(helper.getLevel()).occupancy().containsKey(chunk),
                "cleanup should release occupancy and planning state");
        helper.succeed();
    }

    public static void emptyPlayersReleasePlatformHordeTickets(GameTestHelper helper) {
        ChunkPos origin = new ChunkPos(helper.absolutePos(BlockPos.ZERO));
        ChunkPos distant = new ChunkPos(origin.x + 12, origin.z);
        HordePlanner.ChunkRef chunk = new HordePlanner.ChunkRef(distant.x, distant.z);
        BlockPos center = distant.getMiddleBlockPosition(0);
        ServerLevel level = helper.getLevel();
        level.getServer().setDifficulty(Difficulty.NORMAL, true);
        HordeChunkTicketAccess.acquireOrRenew(level, distant);
        level.getChunk(distant.x, distant.z);
        int y = Math.max(
                level.getMinBuildHeight() + 1,
                level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.getX(), center.getZ()));
        BlockPos feet = new BlockPos(center.getX(), y, center.getZ());
        boolean[] started = {false};
        helper.onEachTick(() -> {
            if (started[0] || !level.isPositionEntityTicking(center)) {
                return;
            }
            for (int index = 0; index <= HordeDaytimeCleanup.REMOVALS_PER_TICK; index++) {
                BlockPos spawn = feet.offset(index % 4, 0, index / 4);
                level.setBlock(spawn.below(), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                level.setBlock(spawn, Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(spawn.above(), Blocks.AIR.defaultBlockState(), 3);
                helper.assertTrue(HordeSpawner.spawnHordeMember(level, spawn), "horde member should spawn");
            }
            HordeChunkTickets.TickResult first = HordeChunkTickets.tick(level, List.of());
            helper.assertTrue(
                    first.removed() == HordeDaytimeCleanup.REMOVALS_PER_TICK,
                    "first cleanup should remove ten members");
            helper.assertTrue(
                    HordeChunkData.get(level).occupancy().getOrDefault(chunk, 0) == 1,
                    "first cleanup should leave one horde member");
            helper.assertTrue(
                    HordeChunkTickets.hasActive(level, chunk),
                    "partial empty-player cleanup should retain planning state");
            HordeChunkTickets.TickResult second = HordeChunkTickets.tick(level, List.of());
            helper.assertTrue(second.removed() == 1, "second cleanup should remove the final member");
            helper.assertTrue(
                    !HordeChunkData.get(level).occupancy().containsKey(chunk),
                    "final cleanup should clear occupancy");
            helper.assertTrue(
                    !HordeChunkTickets.hasActive(level, chunk),
                    "final cleanup should clear planning state");
            started[0] = true;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(started[0], "horde members should spawn in the ticketed chunk");
            helper.assertTrue(
                    !HordeChunkData.get(level).occupancy().containsKey(chunk),
                    "empty-player cleanup should remove every ordinary horde member");
            helper.assertTrue(
                    !level.isPositionEntityTicking(center),
                    "final empty-player cleanup should release the platform chunk ticket");
        });
    }


    private static Zombie spawnHordeMember(GameTestHelper helper, BlockPos relativeFeet) {
        helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
        prepareGrass(helper);
        BlockPos feet = helper.absolutePos(relativeFeet);
        helper.assertTrue(HordeSpawner.spawnHordeMember(helper.getLevel(), feet), "horde member should spawn");
        Zombie zombie = null;
        for (Zombie candidate : helper.getEntities(EntityType.ZOMBIE)) {
            if (HordeIdentity.isHordeMember(candidate)) {
                zombie = candidate;
                break;
            }
        }
        helper.assertTrue(zombie != null, "vanilla zombie should exist");
        return zombie;
    }

    private static void prepareGrass(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.GRASS_BLOCK);
        helper.setBlock(new BlockPos(1, 1, 2), Blocks.GRASS_BLOCK);
    }



    private static void openSky(GameTestHelper helper, BlockPos relativeFeet) {
        BlockPos feet = helper.absolutePos(relativeFeet);
        int top = helper.getLevel().getMaxBuildHeight();
        for (int y = feet.getY() + 2; y < top; y++) {
            helper.getLevel().setBlock(new BlockPos(feet.getX(), y, feet.getZ()), Blocks.AIR.defaultBlockState(), 3);
        }
    }
    private static Zombie spawnOrdinaryZombie(GameTestHelper helper, BlockPos relativeFeet) {
        helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
        Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
        helper.assertTrue(zombie != null, "zombie should exist");
        BlockPos feet = helper.absolutePos(relativeFeet);
        zombie.moveTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, 0.0F, 0.0F);
        helper.assertTrue(helper.getLevel().addFreshEntity(zombie), "ordinary zombie should spawn");
        return zombie;
    }


    private static void giveGuaranteedDiamond(Zombie zombie) {
        zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND));
        zombie.setDropChance(EquipmentSlot.MAINHAND, 2.0F);
    }

    private static void assertEmptyGear(GameTestHelper helper, Zombie zombie) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot == EquipmentSlot.BODY) {
                continue;
            }
            helper.assertTrue(zombie.getItemBySlot(slot).isEmpty(), slot.getName() + " should be empty");
        }
    }
}
