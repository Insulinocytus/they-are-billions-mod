package com.insulinocytus.theyarebillions.horde;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.entity.MobSpawnType;
import org.junit.jupiter.api.Test;

class HordePopulationTest {
    @Test
    void ownsNaturalChunkGenerationAndReinforcement() {
        assertTrue(HordePopulation.owns(MobSpawnType.NATURAL));
        assertTrue(HordePopulation.owns(MobSpawnType.CHUNK_GENERATION));
        assertTrue(HordePopulation.owns(MobSpawnType.REINFORCEMENT));
    }

    @Test
    void leavesCommandsSpawnEggsSpawnersAndConversionsAlone() {
        assertFalse(HordePopulation.owns(MobSpawnType.COMMAND));
        assertFalse(HordePopulation.owns(MobSpawnType.SPAWN_EGG));
        assertFalse(HordePopulation.owns(MobSpawnType.SPAWNER));
        assertFalse(HordePopulation.owns(MobSpawnType.CONVERSION));
        assertFalse(HordePopulation.owns(MobSpawnType.EVENT));
    }
}
