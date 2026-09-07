package com.insulinocytus.theyarebillions.horde;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HordeIdentityTest {
    @Test
    void unnamedHordeMemberKeepsMark() {
        assertTrue(HordeIdentity.retainsHordeMark(true, false));
    }

    @Test
    void namingRemovesHordeMark() {
        assertFalse(HordeIdentity.retainsHordeMark(true, true));
    }

    @Test
    void existingZombiesAreNotConvertedToHordeMembers() {
        assertFalse(HordeIdentity.retainsHordeMark(false, false));
        assertFalse(HordeIdentity.retainsHordeMark(false, true));
    }

    @Test
    void takesOverNaturalPopulationSpawnsOnly() {
        assertTrue(HordeIdentity.takesOverNaturalPopulation("NATURAL"));
        assertTrue(HordeIdentity.takesOverNaturalPopulation("CHUNK_GENERATION"));
        assertTrue(HordeIdentity.takesOverNaturalPopulation("REINFORCEMENT"));
        assertFalse(HordeIdentity.takesOverNaturalPopulation("COMMAND"));
        assertFalse(HordeIdentity.takesOverNaturalPopulation("SPAWN_EGG"));
        assertFalse(HordeIdentity.takesOverNaturalPopulation("SPAWNER"));
        assertFalse(HordeIdentity.takesOverNaturalPopulation("CONVERSION"));
        assertFalse(HordeIdentity.takesOverNaturalPopulation("EVENT"));
        assertFalse(HordeIdentity.takesOverNaturalPopulation("MOB_SUMMONED"));
        assertFalse(HordeIdentity.takesOverNaturalPopulation("DISPENSER"));
        assertFalse(HordeIdentity.takesOverNaturalPopulation("TRIAL_SPAWNER"));
    }
}
