package com.insulinocytus.theyarebillions.neoforge;

import java.util.Comparator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

public final class HordeChunkTicketAccessImpl {
    private static final int ENTITY_TICKING_DISTANCE = 2;
    private static final TicketType<ChunkPos> HORDE =
            TicketType.create("theyarebillions:horde", Comparator.comparingLong(ChunkPos::toLong), 40);

    private HordeChunkTicketAccessImpl() {
    }

    public static void acquireOrRenew(ServerLevel level, ChunkPos chunk) {
        level.getChunkSource().addRegionTicket(HORDE, chunk, ENTITY_TICKING_DISTANCE, chunk);
    }

    public static void release(ServerLevel level, ChunkPos chunk) {
        level.getChunkSource().removeRegionTicket(HORDE, chunk, ENTITY_TICKING_DISTANCE, chunk);
    }
}
