package com.insulinocytus.theyarebillions;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ModVersionHandshakeTest {
    @Test
    void acceptsMatchingClientAndServerVersions() {
        assertTrue(ModVersionHandshake.accepts("0.1.0", "0.1.0"));
    }

    @Test
    void rejectsMissingOrDifferentClientVersions() {
        assertFalse(ModVersionHandshake.accepts("0.1.0", null));
        assertFalse(ModVersionHandshake.accepts("0.1.0", "0.1.1"));
    }
}
