package com.insulinocytus.theyarebillions.horde;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HordeAdminTest {
    @TempDir
    Path tempDir;

    @Test
    void acceptsSupportedLogLevelsAndDefaultsInvalidValuesToInfo() {
        assertEquals(HordeAdmin.LogLevel.DEBUG, HordeAdmin.LogLevel.parse("DEBUG"));
        assertEquals(HordeAdmin.LogLevel.INFO, HordeAdmin.LogLevel.parse("info"));
        assertEquals(HordeAdmin.LogLevel.WARN, HordeAdmin.LogLevel.parse(" WARN "));
        assertEquals(HordeAdmin.LogLevel.ERROR, HordeAdmin.LogLevel.parse("ERROR"));
        assertEquals(HordeAdmin.LogLevel.INFO, HordeAdmin.LogLevel.parse("TRACE"));
        assertEquals(HordeAdmin.LogLevel.INFO, HordeAdmin.LogLevel.parse(null));
    }

    @Test
    void persistsTheInstanceLogLevelDeterministically() throws IOException {
        Path config = tempDir.resolve("serverconfig/theyarebillions.properties");

        HordeAdmin.writeLogLevel(config, HordeAdmin.LogLevel.DEBUG);

        assertEquals("logLevel=DEBUG\n", Files.readString(config, StandardCharsets.UTF_8));
        assertEquals(HordeAdmin.LogLevel.DEBUG, HordeAdmin.loadLogLevel(config));
    }

    @Test
    void createsAndRecoversTheDefaultInfoConfig() throws IOException {
        Path missing = tempDir.resolve("missing/theyarebillions.properties");
        assertEquals(HordeAdmin.LogLevel.INFO, HordeAdmin.loadLogLevel(missing));
        assertEquals("logLevel=INFO\n", Files.readString(missing, StandardCharsets.UTF_8));

        Files.writeString(missing, "logLevel=TRACE\n", StandardCharsets.UTF_8);
        assertEquals(HordeAdmin.LogLevel.INFO, HordeAdmin.loadLogLevel(missing));
        assertEquals("logLevel=INFO\n", Files.readString(missing, StandardCharsets.UTF_8));

        Path unreadable = tempDir.resolve("directory-not-file");
        Files.createDirectory(unreadable);
        assertEquals(HordeAdmin.LogLevel.INFO, HordeAdmin.loadLogLevel(unreadable));
    }

    @Test
    void formatsEveryRequiredStatusField() {
        HordeAdmin.Status status = new HordeAdmin.Status(
                12, 34, 2, 5, 7, 3, HordePerformance.Tier.MINIMUM, HordeAdmin.LogLevel.WARN);

        assertEquals(
                "hordeMembers=12 ordinaryZombies=34 playerGroups=2 ticketChunks=5 sharedRoutes=7 diggingSites=3 performanceTier=MINIMUM logLevel=WARN",
                HordeAdmin.formatStatus(status));
    }
}
