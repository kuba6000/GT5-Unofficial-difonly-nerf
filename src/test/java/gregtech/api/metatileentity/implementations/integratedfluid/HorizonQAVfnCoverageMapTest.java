package gregtech.api.metatileentity.implementations.integratedfluid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class HorizonQAVfnCoverageMapTest {

    private static final String TEST_PACKAGE_PATH =
        "src/test/java/gregtech/api/metatileentity/implementations/integratedfluid";

    @Test
    void coverageMapListsCurrentHorizonQaVfnRiskClasses() {
        Set<String> riskClasses = new HashSet<String>();
        for (CoverageEntry entry : coverageMap()) {
            assertTrue(riskClasses.add(entry.riskClass), "duplicate risk class: " + entry.riskClass);
        }

        assertEquals(
            new HashSet<String>(Arrays.asList(
                "topology bootstrap",
                "topology matrix",
                "hatch matrix",
                "heat system",
                "long cycle",
                "failure matrix",
                "deterministic graph stress",
                "transfer stress",
                "blocked transfer stress",
                "persistence stress",
                "coverage governance")),
            riskClasses);
    }

    @Test
    void mappedTestsExistAndAreIncludedInHorizonsQa() throws IOException {
        Path repoRoot = Paths.get("").toAbsolutePath();
        String buildGradle = new String(
            Files.readAllBytes(repoRoot.resolve("build.gradle.kts")),
            StandardCharsets.UTF_8);

        for (CoverageEntry entry : coverageMap()) {
            Path testFile = repoRoot.resolve(TEST_PACKAGE_PATH).resolve(entry.testClass + ".java");
            assertTrue(Files.isRegularFile(testFile), entry.testClass + " must exist for " + entry.riskClass);
            assertTrue(
                buildGradle.contains("\"*" + entry.testClass + "*\""),
                entry.testClass + " must be included in horizonsQA for " + entry.riskClass);
        }
    }

    private static List<CoverageEntry> coverageMap() {
        return Arrays.asList(
            new CoverageEntry("topology bootstrap", "HorizonQAVfnTopologyScenarioTest"),
            new CoverageEntry("topology matrix", "HorizonQAVfnTopologyMatrixTest"),
            new CoverageEntry("hatch matrix", "HorizonQAVfnHatchMatrixTest"),
            new CoverageEntry("heat system", "HorizonQAVfnHeatSystemTest"),
            new CoverageEntry("long cycle", "HorizonQAVfnLongCycleTest"),
            new CoverageEntry("failure matrix", "HorizonQAVfnFailureMatrixTest"),
            new CoverageEntry("deterministic graph stress", "HorizonQAVfnDeterministicStressTest"),
            new CoverageEntry("transfer stress", "HorizonQAVfnTransferStressTest"),
            new CoverageEntry("blocked transfer stress", "HorizonQAVfnBlockedTransferStressTest"),
            new CoverageEntry("persistence stress", "HorizonQAVfnPersistenceStressTest"),
            new CoverageEntry("coverage governance", "HorizonQAVfnCoverageMapTest"));
    }

    private static final class CoverageEntry {

        private final String riskClass;
        private final String testClass;

        private CoverageEntry(String riskClass, String testClass) {
            this.riskClass = riskClass;
            this.testClass = testClass;
        }
    }
}
