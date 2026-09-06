package com.insulinocytus.theyarebillions.perf;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ScenarioFilesTest {
    @Test
    void openFieldAndWalledMountainHaveSeedsStructuresAndInitData() throws IOException {
        Path root = repoRoot();
        assertScenario(root.resolve("perf/scenarios/open-field"), "origin.nbt");
        assertScenario(root.resolve("perf/scenarios/walled-mountain"), "walled_compound.nbt", "mountain.nbt");
    }

    private static void assertScenario(Path scenarioDir, String... structureFiles) throws IOException {
        String json = Files.readString(scenarioDir.resolve("scenario.json"), StandardCharsets.UTF_8);
        assertTrue(json.contains("\"seed\""), scenarioDir + " missing seed");
        assertTrue(json.contains("\"spawn\""), scenarioDir + " missing spawn");
        assertTrue(json.contains("\"gameRules\""), scenarioDir + " missing init game rules");
        assertTrue(json.contains("\"datapack\""), scenarioDir + " missing datapack");
        assertTrue(json.contains("\"structures\""), scenarioDir + " missing structures");
        String pack = Files.readString(scenarioDir.resolve("datapack/pack.mcmeta"), StandardCharsets.UTF_8);
        assertTrue(pack.contains("\"pack_format\": 48"), scenarioDir + " datapack format");
        String setup = Files.readString(
                scenarioDir.resolve("datapack/data/theyarebillions_perf/function/setup.mcfunction"),
                StandardCharsets.UTF_8);
        assertTrue(setup.contains("setworldspawn"), scenarioDir + " missing spawn init");
        assertTrue(setup.contains("time set 18000"), scenarioDir + " missing time init");
        Path structureDir = scenarioDir.resolve("datapack/data/theyarebillions_perf/structure");
        for (String structureFile : structureFiles) {
            Path nbt = structureDir.resolve(structureFile);
            assertTrue(Files.isRegularFile(nbt), "missing structure " + nbt);
            assertTrue(Files.size(nbt) > 16, "structure too small: " + nbt);
        }
    }

    private static Path repoRoot() {
        String configured = System.getProperty("theyarebillions.repoRoot");
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured);
        }
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        if (Files.isRegularFile(cwd.resolve("perf/scenarios/open-field/scenario.json"))) {
            return cwd;
        }
        return cwd.getParent();
    }
}
