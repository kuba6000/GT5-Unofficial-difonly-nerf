package gregtech.api.metatileentity.implementations.integratedfluid;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.DoubleStream;
import java.util.stream.Stream;

import net.minecraftforge.fluids.Fluid;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class HeatPumpNormalModeMatrixTest {

    private static final double[] START_PRESSURES_BAR = { 0.01d, 0.05d, 0.10d, 0.50d, 1.0d, 2.0d, 5.0d, 10.0d, 20.0d };
    private static final int INPUT_BASE_CAPACITY = 10_000;
    private static final int INPUT_ACCUMULATOR_CAPACITY = 10_000;
    private static final int OUTPUT_BASE_CAPACITY = 100;
    private static final int OUTPUT_ACCUMULATOR_CAPACITY = 10_000;
    private static final float MAX_PRESSURE_BAR = 50.0f;
    private static final int FLUID_PER_OPERATION_MB = 1_000;
    private static final int MAX_STEPS = 80;
    private static final double TEMPERATURE_TOLERANCE = 0.5d;
    private static final double PRESSURE_TOLERANCE = 1.0e-4d;
    private static final double SEED_PRESSURE_TOLERANCE_RATIO = 0.10d;
    private static final double OUTPUT_START_PRESSURE_RATIO = 0.50d;

    static Stream<Arguments> heatingPressureCases() {
        return matrixCases(380.0d, 500.0d);
    }

    static Stream<Arguments> coolingPressureCases() {
        return matrixCases(500.0d, 380.0d);
    }

    @ParameterizedTest(name = "Heating startP={0} bar startT={1} K targetT={2} K")
    @MethodSource("heatingPressureCases")
    void normalModeHeatingMatrixRespectsInvariants(double startPressureBar, double startTemperatureK,
        double targetTemperatureK) {
        runNormalModeMatrixScenario(startPressureBar, startTemperatureK, targetTemperatureK, true);
    }

    @ParameterizedTest(name = "Cooling startP={0} bar startT={1} K targetT={2} K")
    @MethodSource("coolingPressureCases")
    void normalModeCoolingMatrixRespectsInvariants(double startPressureBar, double startTemperatureK,
        double targetTemperatureK) {
        runNormalModeMatrixScenario(startPressureBar, startTemperatureK, targetTemperatureK, false);
    }

    private static void runNormalModeMatrixScenario(double startPressureBar, double startTemperatureK,
        double targetTemperatureK, boolean heating) {
        Fluid fluid = IFNTestSupport.gameLikeCoolantFluid();
        double outputStartPressureBar = Math.max(0.01d, startPressureBar * OUTPUT_START_PRESSURE_RATIO);
        HeatPumpScenarioHarness.NetworkPair networks = HeatPumpScenarioHarness.createSeededNormalModeNetworks(
            fluid,
            startPressureBar,
            outputStartPressureBar,
            startTemperatureK,
            INPUT_BASE_CAPACITY,
            INPUT_ACCUMULATOR_CAPACITY,
            OUTPUT_BASE_CAPACITY,
            OUTPUT_ACCUMULATOR_CAPACITY,
            MAX_PRESSURE_BAR
        );

        assertTrue(
            Math.abs(networks.inputSeed.pressureBar - startPressureBar)
                <= Math.max(0.02d, startPressureBar * SEED_PRESSURE_TOLERANCE_RATIO),
            "input seed startP=" + startPressureBar + " actual=" + networks.inputSeed.pressureBar
        );
        assertTrue(
            Math.abs(networks.outputSeed.pressureBar - outputStartPressureBar)
                <= Math.max(0.02d, outputStartPressureBar * SEED_PRESSURE_TOLERANCE_RATIO),
            "output seed startP=" + outputStartPressureBar + " actual=" + networks.outputSeed.pressureBar
        );

        int successfulSteps = 0;
        for (int step = 0; step < MAX_STEPS; step++) {
            HeatPumpScenarioHarness.StepResult result = HeatPumpScenarioHarness.runNormalTargetTemperatureStep(
                networks.inputNetwork,
                networks.outputNetwork,
                fluid,
                targetTemperatureK,
                FLUID_PER_OPERATION_MB
            );
            if (!result.progressed) {
                break;
            }
            successfulSteps++;

            if (heating) {
                assertTrue(
                    result.actualOutputTemperature <= targetTemperatureK + TEMPERATURE_TOLERANCE,
                    result.describe(step, startPressureBar, startTemperatureK, targetTemperatureK)
                );
            } else {
                assertTrue(
                    result.actualOutputTemperature >= targetTemperatureK - TEMPERATURE_TOLERANCE,
                    result.describe(step, startPressureBar, startTemperatureK, targetTemperatureK)
                );
            }

            assertTrue(
                result.predictedOutputPressure
                    <= result.predictedInputPressure * HeatPumpScenarioHarness.PRESSURE_RATIO_LIMIT + PRESSURE_TOLERANCE,
                result.describe(step, startPressureBar, startTemperatureK, targetTemperatureK)
            );
            assertTrue(
                result.actualOutputPressure
                    <= result.actualInputPressure * HeatPumpScenarioHarness.PRESSURE_RATIO_LIMIT + PRESSURE_TOLERANCE,
                result.describe(step, startPressureBar, startTemperatureK, targetTemperatureK)
            );
            assertTrue(
                Math.abs(result.actualOutputPressure - result.predictedOutputPressure) <= 1.0e-3d,
                result.describe(step, startPressureBar, startTemperatureK, targetTemperatureK)
            );
        }

        assertTrue(
            successfulSteps > 0,
            "scenario made no progress startP=" + startPressureBar + " startT=" + startTemperatureK + " targetT="
                + targetTemperatureK
        );
    }

    private static Stream<Arguments> matrixCases(double startTemperatureK, double targetTemperatureK) {
        return DoubleStream.of(START_PRESSURES_BAR)
            .filter(pressure -> isRunnableMatrixCase(pressure, startTemperatureK, targetTemperatureK))
            .mapToObj(pressure -> Arguments.of(pressure, startTemperatureK, targetTemperatureK));
    }

    private static boolean isRunnableMatrixCase(double startPressureBar, double startTemperatureK,
        double targetTemperatureK) {
        Fluid fluid = IFNTestSupport.gameLikeCoolantFluid();
        double outputStartPressureBar = Math.max(0.01d, startPressureBar * OUTPUT_START_PRESSURE_RATIO);
        HeatPumpScenarioHarness.NetworkPair networks = HeatPumpScenarioHarness.createSeededNormalModeNetworks(
            fluid,
            startPressureBar,
            outputStartPressureBar,
            startTemperatureK,
            INPUT_BASE_CAPACITY,
            INPUT_ACCUMULATOR_CAPACITY,
            OUTPUT_BASE_CAPACITY,
            OUTPUT_ACCUMULATOR_CAPACITY,
            MAX_PRESSURE_BAR
        );

        boolean seedsOk = Math.abs(networks.inputSeed.pressureBar - startPressureBar)
            <= Math.max(0.02d, startPressureBar * SEED_PRESSURE_TOLERANCE_RATIO)
            && Math.abs(networks.outputSeed.pressureBar - outputStartPressureBar)
                <= Math.max(0.02d, outputStartPressureBar * SEED_PRESSURE_TOLERANCE_RATIO);
        if (!seedsOk) {
            return false;
        }

        HeatPumpScenarioHarness.StepResult firstStep = HeatPumpScenarioHarness.runNormalTargetTemperatureStep(
            networks.inputNetwork,
            networks.outputNetwork,
            fluid,
            targetTemperatureK,
            FLUID_PER_OPERATION_MB
        );
        return firstStep.progressed;
    }
}
