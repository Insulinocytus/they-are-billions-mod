package com.insulinocytus.theyarebillions.horde;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.level.block.Blocks;

public final class HordeGameTests {
    public static final String EMPTY_TEMPLATE = "theyarebillions:empty";

    private HordeGameTests() {
    }

    public static void taggedVanillaZombieSpawns(GameTestHelper helper) {
        Zombie zombie = spawnHordeMember(helper, new BlockPos(2, 2, 2));
        helper.assertTrue(HordeIdentity.isOrdinaryZombie(zombie), "vanilla zombie should exist");
        helper.assertTrue(HordeIdentity.isHordeMember(zombie), "zombie should carry horde tag");
        helper.assertTrue(HordeIdentity.hasPersistentHordeTag(zombie), "horde tag should persist in NBT");
        helper.assertTrue(!zombie.isPersistenceRequired(), "horde tag must not force persistence");
        helper.assertTrue(!zombie.isBaby(), "horde members are adults");
        helper.assertTrue(!zombie.canPickUpLoot(), "horde members cannot pick up items");
        assertEmptyGear(helper, zombie);
        CompoundTag nbt = new CompoundTag();
        helper.assertTrue(zombie.save(nbt), "horde member should save as an entity");
        helper.assertTrue("minecraft:zombie".equals(nbt.getString("id")), "removing the mod must leave a vanilla zombie");
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

    public static void namingRemovesHordeMarkAndRestoresVanillaBehavior(GameTestHelper helper) {
        Zombie zombie = spawnHordeMember(helper, new BlockPos(2, 2, 2));
        zombie.setCanPickUpLoot(true);
        zombie.setBaby(true);
        helper.assertTrue(!zombie.canPickUpLoot(), "horde members cannot pick up items");
        helper.assertTrue(!zombie.isBaby(), "horde members stay adult");
        zombie.setCustomName(Component.literal("Pat"));
        helper.assertTrue(!HordeIdentity.isHordeMember(zombie), "naming must remove the horde mark");
        helper.assertTrue(!HordeIdentity.hasPersistentHordeTag(zombie), "naming must drop the persistent horde tag");
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
