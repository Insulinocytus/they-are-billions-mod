package com.insulinocytus.theyarebillions.horde;

import net.minecraft.world.entity.Entity;

public final class HordeMembers {
    public static final String NBT_KEY = "TheyAreBillionsHorde";

    private HordeMembers() {
    }

    public static boolean isHordeMember(Entity entity) {
        return entity instanceof HordeMemberMarker marker && marker.theyarebillions$isHordeMember();
    }

    public static void setHordeMember(Entity entity, boolean hordeMember) {
        if (entity instanceof HordeMemberMarker marker) {
            marker.theyarebillions$setHordeMember(hordeMember);
        }
    }

    public static boolean suppressesVanillaDistanceDespawn(Entity entity) {
        return isHordeMember(entity);
    }
}
