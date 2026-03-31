package gregtech.api.metatileentity.implementations.integratedfluid;

import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraftforge.fluids.Fluid;

import org.junit.jupiter.api.Test;

public class HeatPumpMultiStepSimulationTest {

    private static final float PRESSURE_RATIO_LIMIT = 1.00f;
    private static final double TEMPERATURE_TOLERANCE = 0.1d;

    @Test
    void repeatedNormalModeHeatingStopsBeforePressureOvershoot() {
        Fluid fluid = IFNTestSupport.pressureSensitiveLiquidFluid();
        IntegratedFluidNetwork inputNetwork = IFNTestSupport.newNetwork(fluid, 4_000, 100, 10.0f);
        IntegratedFluidNetwork outputNetwork = IFNTestSupport.newNetwork(fluid, 100, 100, 10.0f);

        long initialAmountQ = 3_000L * IntegratedFluidNetwork.AMOUNT_SCALE;
        inputNetwork.addState(fluid, initialAmountQ, IntegratedFluidNetwork.toEnthalpyQFromSpecific(301.0d, initialAmountQ));

        boolean stoppedByPressure = false;
        int processedSteps = 0;
        double targetTemperature = 320.0d;

        for (int i = 0; i < 50; i++) {
            StepResult result = runNormalTargetTemperatureStep(inputNetwork, outputNetwork, fluid, targetTemperature, 1_000);
            if (!result.progressed) {
                stoppedByPressure = true;
                break;
            }
            processedSteps++;

            assertTrue(outputNetwork.getTemperature() <= targetTemperature + TEMPERATURE_TOLERANCE);
            assertTrue(outputNetwork.getPressure() <= inputNetwork.getPressure() * PRESSURE_RATIO_LIMIT + 1.0e-4f);
        }

        assertTrue(processedSteps > 0);
        assertTrue(stoppedByPressure);
    }

    @Test
    void repeatedSplitFlowHeatingKeepsHotSideAtTargetAndRespectsPressure() {
        Fluid fluid = IFNTestSupport.pressureSensitiveLiquidFluid();
        IntegratedFluidNetwork inputNetwork = IFNTestSupport.newNetwork(fluid, 4_000, 100, 10.0f);
        IntegratedFluidNetwork redOutputNetwork = IFNTestSupport.newNetwork(fluid, 100, 100, 10.0f);
        IntegratedFluidNetwork blueOutputNetwork = IFNTestSupport.newNetwork(fluid, 100, 100, 10.0f);

        long initialAmountQ = 3_000L * IntegratedFluidNetwork.AMOUNT_SCALE;
        inputNetwork.addState(fluid, initialAmountQ, IntegratedFluidNetwork.toEnthalpyQFromSpecific(301.0d, initialAmountQ));

        boolean stoppedByPressure = false;
        int processedSteps = 0;
        double targetTemperature = 320.0d;
        double splitRatio = 0.35d;

        for (int i = 0; i < 50; i++) {
            if (!runSplitTargetTemperatureStep(
                inputNetwork,
                redOutputNetwork,
                blueOutputNetwork,
                fluid,
                targetTemperature,
                splitRatio,
                1_000
            )) {
                stoppedByPressure = true;
                break;
            }
            processedSteps++;

            assertTrue(redOutputNetwork.getTemperature() <= targetTemperature + TEMPERATURE_TOLERANCE);
            assertTrue(redOutputNetwork.getPressure() <= inputNetwork.getPressure() * PRESSURE_RATIO_LIMIT + 1.0e-4f);
            assertTrue(blueOutputNetwork.getPressure() <= inputNetwork.getPressure() * PRESSURE_RATIO_LIMIT + 1.0e-4f);
        }

        assertTrue(processedSteps > 0);
        assertTrue(stoppedByPressure);
    }

    @Test
    void repeatedHeatExchangerHeatingRedKeepsTargetAndRespectsPressure() {
        Fluid fluid = IFNTestSupport.pressureSensitiveLiquidFluid();
        IntegratedFluidNetwork redInputNetwork = IFNTestSupport.newNetwork(fluid, 4_000, 100, 10.0f);
        IntegratedFluidNetwork blueInputNetwork = IFNTestSupport.newNetwork(fluid, 4_000, 100, 10.0f);
        IntegratedFluidNetwork redOutputNetwork = IFNTestSupport.newNetwork(fluid, 100, 100, 10.0f);
        IntegratedFluidNetwork blueOutputNetwork = IFNTestSupport.newNetwork(fluid, 100, 100, 10.0f);

        long initialAmountQ = 3_000L * IntegratedFluidNetwork.AMOUNT_SCALE;
        redInputNetwork.addState(fluid, initialAmountQ, IntegratedFluidNetwork.toEnthalpyQFromSpecific(301.0d, initialAmountQ));
        blueInputNetwork.addState(fluid, initialAmountQ, IntegratedFluidNetwork.toEnthalpyQFromSpecific(291.0d, initialAmountQ));

        boolean stoppedByPressure = false;
        int processedSteps = 0;
        double targetTemperature = 320.0d;

        for (int i = 0; i < 50; i++) {
            if (!runHeatExchangerTargetTemperatureStep(
                redInputNetwork,
                blueInputNetwork,
                redOutputNetwork,
                blueOutputNetwork,
                fluid,
                targetTemperature,
                1_000
            )) {
                stoppedByPressure = true;
                break;
            }
            processedSteps++;

            assertTrue(redOutputNetwork.getTemperature() <= targetTemperature + TEMPERATURE_TOLERANCE);
            assertTrue(redOutputNetwork.getPressure() <= redInputNetwork.getPressure() * PRESSURE_RATIO_LIMIT + 1.0e-4f);
            assertTrue(blueOutputNetwork.getPressure() <= blueInputNetwork.getPressure() * PRESSURE_RATIO_LIMIT + 1.0e-4f);
        }

        assertTrue(processedSteps > 0);
        assertTrue(stoppedByPressure);
    }

    @Test
    void repeatedNormalModeHeatingWithGameLikeCoolantNeverOvershootsPressure() {
        Fluid fluid = IFNTestSupport.gameLikeCoolantFluid();
        IntegratedFluidNetwork inputNetwork = IFNTestSupport.newNetwork(fluid, 10_000, 10_000, 10.0f);
        IntegratedFluidNetwork outputNetwork = IFNTestSupport.newNetwork(fluid, 100, 10_000, 10.0f);

        long initialAmountQ = 8_000L * IntegratedFluidNetwork.AMOUNT_SCALE;
        double startTemperature = 300.0d;
        inputNetwork.addState(
            fluid,
            initialAmountQ,
            IntegratedFluidNetwork.toEnthalpyQFromSpecific(
                FluidThermalProperties.getSpecificEnthalpyFromPT(fluid, inputNetwork.getPressure(), startTemperature),
                initialAmountQ
            )
        );

        double targetTemperature = 500.0d;
        int successfulSteps = 0;
        for (int i = 0; i < 80; i++) {
            StepResult result = runNormalTargetTemperatureStep(inputNetwork, outputNetwork, fluid, targetTemperature, 1_000);
            if (!result.progressed) {
                break;
            }
            successfulSteps++;

            assertTrue(
                outputNetwork.getTemperature() <= targetTemperature + 0.5d,
                "step=" + i
                    + " targetT=" + targetTemperature
                    + " actualT=" + outputNetwork.getTemperature()
                    + " acceptedQ=" + result.acceptedAmountQ
                    + " predictedPin=" + result.predictedInputPressure
                    + " predictedPout=" + result.predictedOutputPressure
                    + " actualPin=" + inputNetwork.getPressure()
                    + " actualPout=" + outputNetwork.getPressure());
            assertTrue(
                outputNetwork.getPressure() <= inputNetwork.getPressure() * PRESSURE_RATIO_LIMIT + 1.0e-4f,
                "step=" + i
                    + " targetT=" + targetTemperature
                    + " acceptedQ=" + result.acceptedAmountQ
                    + " predictedPin=" + result.predictedInputPressure
                    + " predictedPout=" + result.predictedOutputPressure
                    + " actualPin=" + inputNetwork.getPressure()
                    + " actualPout=" + outputNetwork.getPressure());
        }

        assertTrue(successfulSteps > 0);
    }

    private static StepResult runNormalTargetTemperatureStep(IntegratedFluidNetwork inputNetwork,
        IntegratedFluidNetwork outputNetwork, Fluid fluid, double targetTemperature, int fluidAmountPerOperationMb) {
        long availableAmountQ = inputNetwork.getAmountQ();
        if (availableAmountQ <= 0L) {
            return StepResult.notProgressed();
        }

        double inputSpecificEnthalpy = inputNetwork.getSpecificEnthalpy();
        double vFactor = IntegratedFluidThermoModel
            .specificVolumeFromPressureAndSpecificEnthalpy(fluid, inputNetwork.getPressure(), inputSpecificEnthalpy);
        if (vFactor <= 0.0d) {
            return StepResult.notProgressed();
        }

        long amountToProcessQ = computeProcessAmountQ(availableAmountQ, fluidAmountPerOperationMb, vFactor);
        if (amountToProcessQ < IntegratedFluidNetwork.AMOUNT_SCALE) {
            return StepResult.notProgressed();
        }

        long plannedAmountQ = amountToProcessQ;
        double outputSpecificEnthalpy = inputSpecificEnthalpy;
        long acceptedAmountQ = 0L;
        float predictedInputPressure = inputNetwork.getPressure();
        float predictedOutputPressure = outputNetwork.getPressure();
        for (int pass = 0; pass < 3; pass++) {
            outputSpecificEnthalpy = IFNMachineThermo.computeTargetSpecificEnthalpyForStateAdd(
                outputNetwork,
                fluid,
                targetTemperature,
                plannedAmountQ
            );

            var plan = IFNStateTransferPlanner.planSingleOutputStateAdd(
                inputNetwork,
                outputNetwork,
                fluid,
                outputSpecificEnthalpy,
                plannedAmountQ,
                PRESSURE_RATIO_LIMIT
            );
            if (plan.acceptedAmountQ < IntegratedFluidNetwork.AMOUNT_SCALE) {
                return StepResult.notProgressed();
            }
            acceptedAmountQ = plan.acceptedAmountQ;
            predictedInputPressure = plan.predictedInputPressure;
            predictedOutputPressure = plan.predictedOutputPressure;
            if (plan.acceptedAmountQ >= plannedAmountQ) {
                break;
            }
            plannedAmountQ = plan.acceptedAmountQ;
        }

        var extracted = inputNetwork.extractProportional(acceptedAmountQ, false);
        if (extracted.amountQ < IntegratedFluidNetwork.AMOUNT_SCALE) {
            return StepResult.notProgressed();
        }

        outputSpecificEnthalpy = IFNMachineThermo.computeTargetSpecificEnthalpyForStateAdd(
            outputNetwork,
            fluid,
            targetTemperature,
            extracted.amountQ
        );
        outputNetwork.addState(
            fluid,
            extracted.amountQ,
            IntegratedFluidNetwork.toEnthalpyQFromSpecific(outputSpecificEnthalpy, extracted.amountQ)
        );
        return StepResult.progressed(extracted.amountQ, predictedInputPressure, predictedOutputPressure);
    }

    private static final class StepResult {
        private final boolean progressed;
        private final long acceptedAmountQ;
        private final float predictedInputPressure;
        private final float predictedOutputPressure;

        private StepResult(boolean progressed, long acceptedAmountQ, float predictedInputPressure,
            float predictedOutputPressure) {
            this.progressed = progressed;
            this.acceptedAmountQ = acceptedAmountQ;
            this.predictedInputPressure = predictedInputPressure;
            this.predictedOutputPressure = predictedOutputPressure;
        }

        private static StepResult notProgressed() {
            return new StepResult(false, 0L, 0.0f, 0.0f);
        }

        private static StepResult progressed(long acceptedAmountQ, float predictedInputPressure,
            float predictedOutputPressure) {
            return new StepResult(true, acceptedAmountQ, predictedInputPressure, predictedOutputPressure);
        }
    }

    private static boolean runSplitTargetTemperatureStep(IntegratedFluidNetwork inputNetwork,
        IntegratedFluidNetwork redOutputNetwork, IntegratedFluidNetwork blueOutputNetwork, Fluid fluid,
        double targetTemperature, double splitRatio, int fluidAmountPerOperationMb) {
        long availableAmountQ = inputNetwork.getAmountQ();
        if (availableAmountQ <= 0L) {
            return false;
        }

        double inputSpecificEnthalpy = inputNetwork.getSpecificEnthalpy();
        double inputTemperature = FluidThermalProperties.getTemperatureFromPH(
            fluid,
            inputNetwork.getPressure(),
            inputSpecificEnthalpy
        );
        double vFactor = IntegratedFluidThermoModel
            .specificVolumeFromPressureAndSpecificEnthalpy(fluid, inputNetwork.getPressure(), inputSpecificEnthalpy);
        if (vFactor <= 0.0d) {
            return false;
        }

        long amountToProcessQ = computeProcessAmountQ(availableAmountQ, fluidAmountPerOperationMb, vFactor);
        if (amountToProcessQ < IntegratedFluidNetwork.AMOUNT_SCALE) {
            return false;
        }

        long hotAmountQ = (long) (amountToProcessQ * splitRatio);
        long coldAmountQ = amountToProcessQ - hotAmountQ;
        if (hotAmountQ < IntegratedFluidNetwork.AMOUNT_SCALE || coldAmountQ < IntegratedFluidNetwork.AMOUNT_SCALE) {
            return false;
        }

        double hotSpecificEnthalpy = FluidThermalProperties.getSpecificEnthalpyFromPT(
            fluid,
            redOutputNetwork.getPressure(),
            targetTemperature
        );
        float cop = FluidThermalProperties.calculateHeatPumpCOP((float) Math.min(inputTemperature, targetTemperature),
            (float) Math.max(inputTemperature, targetTemperature));
        float penalty = FluidThermalProperties.calculateTemperaturePenalty((float) Math.abs(targetTemperature - inputTemperature));
        long energyCost = IFNMachineThermo.computeHeatPumpEnergyCost(
            inputSpecificEnthalpy,
            hotSpecificEnthalpy,
            hotAmountQ,
            cop,
            penalty
        );

        double hotAmount = toAmount(hotAmountQ);
        double coldAmount = toAmount(coldAmountQ);
        double qHotTotal = Math.abs(hotSpecificEnthalpy - inputSpecificEnthalpy) * hotAmount;
        double qColdTotal = Math.max(0.0d, qHotTotal - energyCost);
        double coldSpecificEnthalpy = inputSpecificEnthalpy - (qColdTotal / coldAmount);

        var plan = IFNStateTransferPlanner.planSplitStateAdd(
            inputNetwork,
            redOutputNetwork,
            fluid,
            hotSpecificEnthalpy,
            blueOutputNetwork,
            fluid,
            coldSpecificEnthalpy,
            amountToProcessQ,
            splitRatio,
            PRESSURE_RATIO_LIMIT
        );
        if (plan.acceptedTotalAmountQ < IntegratedFluidNetwork.AMOUNT_SCALE) {
            return false;
        }

        var extracted = inputNetwork.extractProportional(plan.acceptedTotalAmountQ, false);
        if (extracted.amountQ < IntegratedFluidNetwork.AMOUNT_SCALE) {
            return false;
        }

        long acceptedHotQ = (long) (extracted.amountQ * splitRatio);
        long acceptedColdQ = extracted.amountQ - acceptedHotQ;
        if (acceptedHotQ < IntegratedFluidNetwork.AMOUNT_SCALE || acceptedColdQ < IntegratedFluidNetwork.AMOUNT_SCALE) {
            return false;
        }

        redOutputNetwork.addState(
            fluid,
            acceptedHotQ,
            IntegratedFluidNetwork.toEnthalpyQFromSpecific(hotSpecificEnthalpy, acceptedHotQ)
        );
        blueOutputNetwork.addState(
            fluid,
            acceptedColdQ,
            IntegratedFluidNetwork.toEnthalpyQFromSpecific(coldSpecificEnthalpy, acceptedColdQ)
        );
        return true;
    }

    private static boolean runHeatExchangerTargetTemperatureStep(IntegratedFluidNetwork redInputNetwork,
        IntegratedFluidNetwork blueInputNetwork, IntegratedFluidNetwork redOutputNetwork,
        IntegratedFluidNetwork blueOutputNetwork, Fluid fluid, double targetTemperature, int fluidAmountPerOperationMb) {
        long redAvailQ = redInputNetwork.getAmountQ();
        long blueAvailQ = blueInputNetwork.getAmountQ();
        if (redAvailQ <= 0L || blueAvailQ <= 0L) {
            return false;
        }

        double redInH = redInputNetwork.getSpecificEnthalpy();
        double blueInH = blueInputNetwork.getSpecificEnthalpy();
        double redInTemp = FluidThermalProperties.getTemperatureFromPH(fluid, redInputNetwork.getPressure(), redInH);
        double blueInTemp = FluidThermalProperties.getTemperatureFromPH(fluid, blueInputNetwork.getPressure(), blueInH);

        double redVFactor = IntegratedFluidThermoModel
            .specificVolumeFromPressureAndSpecificEnthalpy(fluid, redInputNetwork.getPressure(), redInH);
        double blueVFactor = IntegratedFluidThermoModel
            .specificVolumeFromPressureAndSpecificEnthalpy(fluid, blueInputNetwork.getPressure(), blueInH);
        if (redVFactor <= 0.0d || blueVFactor <= 0.0d) {
            return false;
        }

        long redProcessQ = computeProcessAmountQ(redAvailQ, fluidAmountPerOperationMb, redVFactor);
        long blueProcessQ = computeProcessAmountQ(blueAvailQ, fluidAmountPerOperationMb, blueVFactor);
        if (redProcessQ < IntegratedFluidNetwork.AMOUNT_SCALE || blueProcessQ < IntegratedFluidNetwork.AMOUNT_SCALE) {
            return false;
        }

        double redOutH = FluidThermalProperties.getSpecificEnthalpyFromPT(fluid, redOutputNetwork.getPressure(), targetTemperature);
        float cop = FluidThermalProperties.calculateHeatPumpCOP((float) Math.min(blueInTemp, targetTemperature),
            (float) Math.max(redInTemp, targetTemperature));
        float penalty = FluidThermalProperties.calculateTemperaturePenalty((float) Math.abs(targetTemperature - redInTemp));
        long energyCost = IFNMachineThermo.computeHeatPumpEnergyCost(redInH, redOutH, redProcessQ, cop, penalty);

        double redAmount = toAmount(redProcessQ);
        double blueAmount = toAmount(blueProcessQ);
        double qTargetTotal = Math.abs(redOutH - redInH) * redAmount;
        double qSourceTotal = Math.max(0.0d, qTargetTotal - energyCost);
        double blueOutH = blueInH - (qSourceTotal / blueAmount);

        var plan = IFNStateTransferPlanner.planDualStateAddWithSharedRatio(
            redInputNetwork,
            redOutputNetwork,
            fluid,
            redOutH,
            redProcessQ,
            blueInputNetwork,
            blueOutputNetwork,
            fluid,
            blueOutH,
            blueProcessQ,
            PRESSURE_RATIO_LIMIT
        );
        if (plan.acceptedRatio <= 0.0d
            || plan.acceptedRedAmountQ < IntegratedFluidNetwork.AMOUNT_SCALE
            || plan.acceptedBlueAmountQ < IntegratedFluidNetwork.AMOUNT_SCALE) {
            return false;
        }

        var extractedRed = redInputNetwork.extractProportional(plan.acceptedRedAmountQ, false);
        var extractedBlue = blueInputNetwork.extractProportional(plan.acceptedBlueAmountQ, false);
        if (extractedRed.amountQ < IntegratedFluidNetwork.AMOUNT_SCALE
            || extractedBlue.amountQ < IntegratedFluidNetwork.AMOUNT_SCALE) {
            return false;
        }

        redOutputNetwork.addState(
            fluid,
            extractedRed.amountQ,
            IntegratedFluidNetwork.toEnthalpyQFromSpecific(redOutH, extractedRed.amountQ)
        );
        blueOutputNetwork.addState(
            fluid,
            extractedBlue.amountQ,
            IntegratedFluidNetwork.toEnthalpyQFromSpecific(blueOutH, extractedBlue.amountQ)
        );
        return true;
    }

    private static long computeProcessAmountQ(long availableAmountQ, int fluidAmountPerOperationMb, double vFactor) {
        long desiredAmountMb = (long) Math.floor(fluidAmountPerOperationMb / vFactor);
        return Math.min(availableAmountQ, desiredAmountMb * IntegratedFluidNetwork.AMOUNT_SCALE);
    }

    private static double toAmount(long amountQ) {
        return amountQ / (double) IntegratedFluidNetwork.AMOUNT_SCALE;
    }
}
