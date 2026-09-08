package com.insulinocytus.theyarebillions.horde;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class HordeNightDataTest {
    @Test
    void persistsNightDirectionAcrossReload() {
        HordePlanner.NightState state = new HordePlanner.NightState(
                5 * 24000L + 18000,
                Map.of(HordePlanner.GroupIdentity.of(
                                "00000000-0000-0000-0000-000000000001",
                                "00000000-0000-0000-0000-000000000002"),
                        1.5),
                3,
                5);
        HordeNightData saved = new HordeNightData(state);

        CompoundTag tag = saved.save(new CompoundTag(), null);

        assertEquals(state, HordeNightData.load(tag, null).state());
    }
}
