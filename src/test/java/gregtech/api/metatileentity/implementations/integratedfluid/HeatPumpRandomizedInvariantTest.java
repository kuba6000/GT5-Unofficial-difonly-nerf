package gregtech.api.metatileentity.implementations.integratedfluid;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import net.minecraftforge.fluids.Fluid;

import org.junit.jupiter.api.Test;

class HeatPumpRandomizedInvariantTest {

    private static final long SEED = 0x5A17C0FFEEBABEL;
    private static final int CASES = 40;
    private static final int STEPS_PER_CASE = 20;
    private static final int INPUT_BASE_CAPACITY = 10_000;
    private static final int INPUT_ACCUMULATOR_CAPACITY = 10_000;
    private static final int OUTPUT_BASE_CAPACITY = 100;
    private static final int OUTPUT_ACCUMULATOR_CAPACITY = 10_000;
    private static final float MAX_PRESSURE_BAR = 50.0f;
    private static final double OUTPUT_START_PRESSURE_RATIO = 0.50d;
    private static final int FLUID_PER_OPERATION_MB = 1_000;
    private static final double PRESSURE_TOLERANCE = 1.0e-4d;
    private static final double TEMPERATURE_TOLERANCE = 0.75d;

    @Test
    void randomizedNormalModeScenariosRespectCoreInvariants() {
        Random random = new Random(SEED);
        Fluid fluid = IFNTestSupport.gameLikeCoolantFluid();

        int runnableCases = 0;
        for (int caseIndex = 0; caseIndex < CASES; caseIndex++) {
            double startPressureBar = expInterpolate(random, 0.05d, 10.0d);
            double startTemperatureK = interpolate(random, 340.0d, 460.0d);
            boolean heating = random.nextBoolean();
            double targetTemperatureK = heating
                ? interpolate(random, startTemperatureK + 20.0d, 520.0d)
                : interpolate(random, 320.0d, startTemperatureK - 20.0d);
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

            HeatPumpScenarioHarness.StepResult firstStep = HeatPumpScenarioHarness.runNormalTargetTemperatureStep(
                networks.inputNetwork,
                networks.outputNetwork,
                fluid,
                targetTemperatureK,
                FLUID_PER_OPERATION_MB
            );
            if (!firstStep.progressed) {
                continue;
            }
            runnableCases++;
            assertStep(
                firstStep,
                0,
                startPressureBar,
                startTemperatureK,
                targetTemperatureK,
                heating
            );

            for (int step = 1; step < STEPS_PER_CASE; step++) {
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
                assertStep(
                    result,
                    step,
                    startPressureBar,
                    startTemperatureK,
                    targetTemperatureK,
                    heating
                );
            }
        }

        assertTrue(runnableCases >= 10, "Too few runnable randomized cases. seed=" + SEED + " runnable=" + runnableCases);
    }

    private static void assertStep(HeatPumpScenarioHarness.StepResult result, int step, double startPressureBar,
        double startTemperatureK, double targetTemperatureK, boolean heating) {
        assertTrue(
            Float.isFinite(result.actualOutputTemperature)
                && Float.isFinite(result.actualInputTemperature)
                && result.actualOutputTemperature > 0.0f
                && result.actualInputTemperature > 0.0f,
            describe(step, startPressureBar, startTemperatureK, targetTemperatureK, heating, result)
        );

        assertTrue(
            result.predictedOutputPressure
                <= result.predictedInputPressure * HeatPumpScenarioHarness.PRESSURE_RATIO_LIMIT + PRESSURE_TOLERANCE,
            describe(step, startPressureBar, startTemperatureK, targetTemperatureK, heating, result)
        );
        assertTrue(
            result.actualOutputPressure
                <= result.actualInputPressure * HeatPumpScenarioHarness.PRESSURE_RATIO_LIMIT + PRESSURE_TOLERANCE,
            describe(step, startPressureBar, startTemperatureK, targetTemperatureK, heating, result)
        );
        assertTrue(
            Math.abs(result.actualOutputPressure - result.predictedOutputPressure) <= 2.0e-3d,
            describe(step, startPressureBar, startTemperatureK, targetTemperatureK, heating, result)
        );
    }

    private static String describe(int step, double startPressureBar, double startTemperatureK,
        double targetTemperatureK, boolean heating, HeatPumpScenarioHarness.StepResult result) {
        return "seed=" + SEED
            + " step=" + step
            + " mode=" + (heating ? "heating" : "cooling")
            + " startP=" + startPressureBar
            + " startT=" + startTemperatureK
            + " targetT=" + targetTemperatureK
            + " acceptedQ=" + result.acceptedAmountQ
            + " predictedPin=" + result.predictedInputPressure
            + " predictedPout=" + result.predictedOutputPressure
            + " actualPin=" + result.actualInputPressure
            + " actualPout=" + result.actualOutputPressure
            + " actualTin=" + result.actualInputTemperature
            + " actualTout=" + result.actualOutputTemperature;
    }

    private static double interpolate(Random random, double min, double max) {
        if (max <= min) {
            return min;
        }
        return min + random.nextDouble() * (max - min);
    }

    private static double expInterpolate(Random random, double min, double max) {
        double logMin = Math.log(min);
        double logMax = Math.log(max);
        return Math.exp(interpolate(random, logMin, logMax));
    }
}
