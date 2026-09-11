package com.insulinocytus.theyarebillions.horde;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class HordeChunkDataTest {
    @Test
    void persistsOnlyExpectedChunkOccupancyAcrossReload() {
        Map<HordePlanner.ChunkRef, Integer> occupancy = Map.of(
                new HordePlanner.ChunkRef(-2, 3), 4,
                new HordePlanner.ChunkRef(7, -11), 1);
        HordeChunkData saved = new HordeChunkData(occupancy);

        CompoundTag tag = saved.save(new CompoundTag(), null);

        assertEquals(occupancy, HordeChunkData.load(tag, null).occupancy());
    }
}
