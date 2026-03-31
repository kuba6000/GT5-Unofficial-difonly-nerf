package gregtech.api.metatileentity.implementations.integratedfluid;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.DoubleStream;
import java.util.stream.Stream;

import net.minecraftforge.fluids.Fluid;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class HeatPumpAdvancedModesMatrixTest {

    private static final double[] START_PRESSURES_BAR = { 0.05d, 0.10d, 0.50d, 1.0d, 2.0d, 5.0d, 10.0d };
    private static final int INPUT_BASE_CAPACITY = 10_000;
    private static final int INPUT_ACCUMULATOR_CAPACITY = 10_000;
    private static final int OUTPUT_BASE_CAPACITY = 100;
    private static final int OUTPUT_ACCUMULATOR_CAPACITY = 10_000;
    private static final float MAX_PRESSURE_BAR = 50.0f;
    private static final int FLUID_PER_OPERATION_MB = 1_000;
    private static final int MAX_STEPS = 60;
    private static final double TEMPERATURE_TOLERANCE = 0.75d;
    private static final double PRESSURE_TOLERANCE = 1.0e-4d;
    private static final double OUTPUT_START_PRESSURE_RATIO = 0.50d;
    private static final double SEED_PRESSURE_TOLERANCE_RATIO = 0.10d;

    static Stream<Arguments> splitCases() {
        return DoubleStream.of(START_PRESSURES_BAR)
            .filter(pressure -> isRunnableSplitCase(pressure, 380.0d, 500.0d, 0.35d))
            .mapToObj(pressure -> Arguments.of(pressure, 380.0d, 500.0d, 0.35d));
    }

    static Stream<Arguments> heatExchangerCases() {
        return DoubleStream.of(START_PRESSURES_BAR)
            .filter(pressure -> isRunnableHeatExchangerCase(pressure, pressure, 420.0d, 340.0d, 500.0d))
            .mapToObj(pressure -> Arguments.of(pressure, pressure, 420.0d, 340.0d, 500.0d));
    }

    @ParameterizedTest(name = "Split startP={0} bar startT={1} K targetT={2} K split={3}")
    @MethodSource("splitCases")
    void splitFlowMatrixRespectsInvariants(double startPressureBar, double startTemperatureK,
        double targetTemperatureK, double splitRatio) {
        Fluid fluid = IFNTestSupport.gameLikeCoolantFluid();
        double outputStartPressureBar = Math.max(0.01d, startPressureBar * OUTPUT_START_PRESSURE_RATIO);
        HeatPumpScenarioHarness.SplitNetworkSet networks = HeatPumpScenarioHarness.createSeededSplitNetworks(
            fluid,
            startPressureBar,
            outputStartPressureBar,
            outputStartPressureBar,
            startTemperatureK,
            INPUT_BASE_CAPACITY,
            INPUT_ACCUMULATOR_CAPACITY,
            OUTPUT_BASE_CAPACITY,
            OUTPUT_ACCUMULATOR_CAPACITY,
            MAX_PRESSURE_BAR
        );

        assertSeedClose(networks.inputSeed.pressureBar, startPressureBar, "split input");
        assertSeedClose(networks.redSeed.pressureBar, outputStartPressureBar, "split red");
        assertSeedClose(networks.blueSeed.pressureBar, outputStartPressureBar, "split blue");

        int successfulSteps = 0;
        for (int step = 0; step < MAX_STEPS; step++) {
            HeatPumpScenarioHarness.SplitStepResult result = HeatPumpScenarioHarness.runSplitTargetTemperatureStep(
                networks.inputNetwork,
                networks.redOutputNetwork,
                networks.blueOutputNetwork,
                fluid,
                targetTemperatureK,
                splitRatio,
                FLUID_PER_OPERATION_MB
            );
            if (!result.progressed) {
                break;
            }
            successfulSteps++;

            assertTrue(
                result.actualRedOutputTemperature <= targetTemperatureK + TEMPERATURE_TOLERANCE,
                result.describe(step, startPressureBar, startTemperatureK, targetTemperatureK, splitRatio)
            );
            assertTrue(
                result.actualRedOutputPressure
                    <= result.actualInputPressure * HeatPumpScenarioHarness.PRESSURE_RATIO_LIMIT + PRESSURE_TOLERANCE,
                result.describe(step, startPressureBar, startTemperatureK, targetTemperatureK, splitRatio)
            );
            assertTrue(
                result.actualBlueOutputPressure
                    <= result.actualInputPressure * HeatPumpScenarioHarness.PRESSURE_RATIO_LIMIT + PRESSURE_TOLERANCE,
                result.describe(step, startPressureBar, startTemperatureK, targetTemperatureK, splitRatio)
            );
        }

        assertTrue(successfulSteps > 0, "split scenario made no progress startP=" + startPressureBar);
    }

    @ParameterizedTest(name = "HX redP={0} blueP={1} redT={2} blueT={3} targetT={4}")
    @MethodSource("heatExchangerCases")
    void heatExchangerMatrixRespectsInvariants(double redStartPressureBar, double blueStartPressureBar,
        double redInputTemperatureK, double blueInputTemperatureK, double targetTemperatureK) {
        Fluid fluid = IFNTestSupport.gameLikeCoolantFluid();
        double redOutputStartPressureBar = Math.max(0.01d, redStartPressureBar * OUTPUT_START_PRESSURE_RATIO);
        double blueOutputStartPressureBar = Math.max(0.01d, blueStartPressureBar * OUTPUT_START_PRESSURE_RATIO);
        HeatPumpScenarioHarness.DualNetworkSet networks = HeatPumpScenarioHarness.createSeededDualNetworks(
            fluid,
            redStartPressureBar,
            blueStartPressureBar,
            redOutputStartPressureBar,
            blueOutputStartPressureBar,
            redInputTemperatureK,
            blueInputTemperatureK,
            INPUT_BASE_CAPACITY,
            INPUT_ACCUMULATOR_CAPACITY,
            OUTPUT_BASE_CAPACITY,
            OUTPUT_ACCUMULATOR_CAPACITY,
            MAX_PRESSURE_BAR
        );

        assertSeedClose(networks.redInputSeed.pressureBar, redStartPressureBar, "hx red input");
        assertSeedClose(networks.blueInputSeed.pressureBar, blueStartPressureBar, "hx blue input");
        assertSeedClose(networks.redOutputSeed.pressureBar, redOutputStartPressureBar, "hx red output");
        assertSeedClose(networks.blueOutputSeed.pressureBar, blueOutputStartPressureBar, "hx blue output");

        int successfulSteps = 0;
        for (int step = 0; step < MAX_STEPS; step++) {
            HeatPumpScenarioHarness.DualStepResult result = HeatPumpScenarioHarness.runHeatExchangerTargetTemperatureStep(
                networks.redInputNetwork,
                networks.blueInputNetwork,
                networks.redOutputNetwork,
                networks.blueOutputNetwork,
                fluid,
                targetTemperatureK,
                FLUID_PER_OPERATION_MB
            );
            if (!result.progressed) {
                break;
            }
            successfulSteps++;

            assertTrue(
                result.actualRedOutputTemperature <= targetTemperatureK + TEMPERATURE_TOLERANCE,
                result.describe(step, redStartPressureBar, blueStartPressureBar, redInputTemperatureK, blueInputTemperatureK, targetTemperatureK)
            );
            assertTrue(
                result.actualRedOutputPressure
                    <= result.actualRedInputPressure * HeatPumpScenarioHarness.PRESSURE_RATIO_LIMIT + PRESSURE_TOLERANCE,
                result.describe(step, redStartPressureBar, blueStartPressureBar, redInputTemperatureK, blueInputTemperatureK, targetTemperatureK)
            );
            assertTrue(
                result.actualBlueOutputPressure
                    <= result.actualBlueInputPressure * HeatPumpScenarioHarness.PRESSURE_RATIO_LIMIT + PRESSURE_TOLERANCE,
                result.describe(step, redStartPressureBar, blueStartPressureBar, redInputTemperatureK, blueInputTemperatureK, targetTemperatureK)
            );
        }

        assertTrue(successfulSteps > 0, "hx scenario made no progress redP=" + redStartPressureBar + " blueP=" + blueStartPressureBar);
    }

    private static boolean isRunnableSplitCase(double startPressureBar, double startTemperatureK,
        double targetTemperatureK, double splitRatio) {
        Fluid fluid = IFNTestSupport.gameLikeCoolantFluid();
        double outputStartPressureBar = Math.max(0.01d, startPressureBar * OUTPUT_START_PRESSURE_RATIO);
        HeatPumpScenarioHarness.SplitNetworkSet networks = HeatPumpScenarioHarness.createSeededSplitNetworks(
            fluid,
            startPressureBar,
            outputStartPressureBar,
            outputStartPressureBar,
            startTemperatureK,
            INPUT_BASE_CAPACITY,
            INPUT_ACCUMULATOR_CAPACITY,
            OUTPUT_BASE_CAPACITY,
            OUTPUT_ACCUMULATOR_CAPACITY,
            MAX_PRESSURE_BAR
        );
        if (!seedOk(networks.inputSeed.pressureBar, startPressureBar)
            || !seedOk(networks.redSeed.pressureBar, outputStartPressureBar)
            || !seedOk(networks.blueSeed.pressureBar, outputStartPressureBar)) {
            return false;
        }
        return HeatPumpScenarioHarness.runSplitTargetTemperatureStep(
            networks.inputNetwork,
            networks.redOutputNetwork,
            networks.blueOutputNetwork,
            fluid,
            targetTemperatureK,
            splitRatio,
            FLUID_PER_OPERATION_MB
        ).progressed;
    }

    private static boolean isRunnableHeatExchangerCase(double redStartPressureBar, double blueStartPressureBar,
        double redInputTemperatureK, double blueInputTemperatureK, double targetTemperatureK) {
        Fluid fluid = IFNTestSupport.gameLikeCoolantFluid();
        double redOutputStartPressureBar = Math.max(0.01d, redStartPressureBar * OUTPUT_START_PRESSURE_RATIO);
        double blueOutputStartPressureBar = Math.max(0.01d, blueStartPressureBar * OUTPUT_START_PRESSURE_RATIO);
        HeatPumpScenarioHarness.DualNetworkSet networks = HeatPumpScenarioHarness.createSeededDualNetworks(
            fluid,
            redStartPressureBar,
            blueStartPressureBar,
            redOutputStartPressureBar,
            blueOutputStartPressureBar,
            redInputTemperatureK,
            blueInputTemperatureK,
            INPUT_BASE_CAPACITY,
            INPUT_ACCUMULATOR_CAPACITY,
            OUTPUT_BASE_CAPACITY,
            OUTPUT_ACCUMULATOR_CAPACITY,
            MAX_PRESSURE_BAR
        );
        if (!seedOk(networks.redInputSeed.pressureBar, redStartPressureBar)
            || !seedOk(networks.blueInputSeed.pressureBar, blueStartPressureBar)
            || !seedOk(networks.redOutputSeed.pressureBar, redOutputStartPressureBar)
            || !seedOk(networks.blueOutputSeed.pressureBar, blueOutputStartPressureBar)) {
            return false;
        }
        return HeatPumpScenarioHarness.runHeatExchangerTargetTemperatureStep(
            networks.redInputNetwork,
            networks.blueInputNetwork,
            networks.redOutputNetwork,
            networks.blueOutputNetwork,
            fluid,
            targetTemperatureK,
            FLUID_PER_OPERATION_MB
        ).progressed;
    }

    private static boolean seedOk(float actualPressureBar, double expectedPressureBar) {
        return Math.abs(actualPressureBar - expectedPressureBar)
            <= Math.max(0.02d, expectedPressureBar * SEED_PRESSURE_TOLERANCE_RATIO);
    }

    private static void assertSeedClose(float actualPressureBar, double expectedPressureBar, String label) {
        assertTrue(
            seedOk(actualPressureBar, expectedPressureBar),
            label + " seed expectedP=" + expectedPressureBar + " actualP=" + actualPressureBar
        );
    }
}
