package com.insulinocytus.theyarebillions.horde;

import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;

public final class HordeIdentity {
    public static final String HORDE_TAG = "theyarebillions.horde";
    private static final String GROUP_TAG_PREFIX = "theyarebillions.group.";

    private HordeIdentity() {
    }

    public static boolean takesOverNaturalPopulation(MobSpawnType spawnType) {
        return spawnType == MobSpawnType.NATURAL
                || spawnType == MobSpawnType.CHUNK_GENERATION
                || spawnType == MobSpawnType.REINFORCEMENT;
    }

    public static boolean isOrdinaryZombie(Entity entity) {
        return entity.getType() == EntityType.ZOMBIE;
    }

    public static boolean isHordeMember(Entity entity) {
        return isOrdinaryZombie(entity) && entity.getTags().contains(HORDE_TAG);
    }

    public static boolean isClientHordeMember(Entity entity) {
        return isOrdinaryZombie(entity)
                && entity instanceof HordeMemberState state
                && state.theyarebillions$isSyncedHordeMember();
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

    public static void mark(Entity entity, HordePlanner.GroupIdentity group) {
        mark(entity);
        for (String memberId : group.memberIds()) {
            entity.addTag(GROUP_TAG_PREFIX + memberId);
        }
    }

    public static HordePlanner.GroupIdentity group(Entity entity) {
        List<String> members = entity.getTags().stream()
                .filter(tag -> tag.startsWith(GROUP_TAG_PREFIX))
                .map(tag -> tag.substring(GROUP_TAG_PREFIX.length()))
                .toList();
        return members.isEmpty() ? null : new HordePlanner.GroupIdentity(members);
    }

    public static boolean hasPersistentGroup(Entity entity, HordePlanner.GroupIdentity group) {
        CompoundTag nbt = new CompoundTag();
        entity.saveWithoutId(nbt);
        ListTag tags = nbt.getList("Tags", Tag.TAG_STRING);
        for (String memberId : group.memberIds()) {
            if (!tags.contains(net.minecraft.nbt.StringTag.valueOf(GROUP_TAG_PREFIX + memberId))) {
                return false;
            }
        }
        return true;
    }

    public static void enforceHordeTraits(Zombie zombie) {
        if (!isHordeMember(zombie)) {
            return;
        }
        zombie.setBaby(false);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot != EquipmentSlot.BODY) {
                zombie.setItemSlot(slot, ItemStack.EMPTY);
            }
        }
    }

    public static void detachIfNamed(Entity entity) {
        if (isHordeMember(entity) && entity.hasCustomName()) {
            entity.removeTag(HORDE_TAG);
            entity.getTags().stream()
                    .filter(tag -> tag.startsWith(GROUP_TAG_PREFIX))
                    .toList()
                    .forEach(entity::removeTag);
        }
    }

    public static void syncClientState(Entity entity) {
        if (!entity.level().isClientSide() && isOrdinaryZombie(entity) && entity instanceof HordeMemberState state) {
            state.theyarebillions$setSyncedHordeMember(entity.getTags().contains(HORDE_TAG));
            HordeIdentityNetworking.sendToTracking(entity);
        }
    }
}
