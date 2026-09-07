package com.insulinocytus.theyarebillions.horde;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.world.entity.MobSpawnType;
import org.junit.jupiter.api.Test;

class HordeIdentityTest {
    @Test
    void takesOverNaturalPopulationSpawnsOnly() {
        for (MobSpawnType spawnType : MobSpawnType.values()) {
            boolean takenOver = spawnType == MobSpawnType.NATURAL
                    || spawnType == MobSpawnType.CHUNK_GENERATION
                    || spawnType == MobSpawnType.REINFORCEMENT;
            assertEquals(takenOver, HordeIdentity.takesOverNaturalPopulation(spawnType), spawnType.name());
        }
    }
}
