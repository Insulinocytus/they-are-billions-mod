package com.insulinocytus.theyarebillions;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

public final class HordeChunkTicketAccess {
    private HordeChunkTicketAccess() {
    }

    @ExpectPlatform
    public static void acquireOrRenew(ServerLevel level, ChunkPos chunk) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static void release(ServerLevel level, ChunkPos chunk) {
        throw new AssertionError();
    }
}
