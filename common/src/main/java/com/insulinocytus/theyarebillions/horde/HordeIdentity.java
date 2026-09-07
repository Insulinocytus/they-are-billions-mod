package com.insulinocytus.theyarebillions.horde;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;

public final class HordeIdentity {
    public static final String HORDE_TAG = "theyarebillions.horde";

    private HordeIdentity() {
    }

    public static boolean retainsHordeMark(boolean marked, boolean named) {
        return marked && !named;
    }

    public static boolean takesOverNaturalPopulation(String spawnTypeName) {
        return "NATURAL".equals(spawnTypeName)
                || "CHUNK_GENERATION".equals(spawnTypeName)
                || "REINFORCEMENT".equals(spawnTypeName);
    }

    public static boolean isOrdinaryZombie(Entity entity) {
        return entity.getType() == EntityType.ZOMBIE;
    }

    public static boolean isHordeMember(Entity entity) {
        return isOrdinaryZombie(entity) && entity.getTags().contains(HORDE_TAG);
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

    public static void mark(Entity entity) {
        entity.addTag(HORDE_TAG);
    }

    public static void applySpawnIdentity(Zombie zombie) {
        zombie.setBaby(false);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot != EquipmentSlot.BODY) {
                zombie.setItemSlot(slot, ItemStack.EMPTY);
            }
        }
    }

    public static void detachIfNamed(Entity entity) {
        if (isHordeMember(entity) && !retainsHordeMark(true, entity.hasCustomName())) {
            entity.removeTag(HORDE_TAG);
        }
    }
}
