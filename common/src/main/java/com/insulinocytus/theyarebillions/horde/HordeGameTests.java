package com.insulinocytus.theyarebillions.horde;

import com.insulinocytus.theyarebillions.HordeChunkTicketAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.ChunkPos;

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
    public static void hordeDiggingUsesEmptySurvivalFakePlayerDrops(GameTestHelper helper) {
        Zombie zombie = spawnHordeMember(helper, new BlockPos(2, 2, 2));
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
