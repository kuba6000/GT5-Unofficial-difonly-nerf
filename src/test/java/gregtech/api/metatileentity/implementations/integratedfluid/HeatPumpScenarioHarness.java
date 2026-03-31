package gregtech.api.metatileentity.implementations.integratedfluid;

import net.minecraftforge.fluids.Fluid;

final class HeatPumpScenarioHarness {

    static final float PRESSURE_RATIO_LIMIT = 1.00f;

    private HeatPumpScenarioHarness() {}

    static NetworkPair createSeededNormalModeNetworks(Fluid fluid, double inputStartPressureBar,
        double outputStartPressureBar, double startTemperatureK, int inputBaseCapacity, int inputAccumulatorCapacity,
        int outputBaseCapacity, int outputAccumulatorCapacity, float maxPressureBar) {
        IntegratedFluidNetwork inputNetwork = IFNTestSupport.newNetwork(
            fluid,
            inputBaseCapacity,
            inputAccumulatorCapacity,
            maxPressureBar
        );
        IntegratedFluidNetwork outputNetwork = IFNTestSupport.newNetwork(
            fluid,
            outputBaseCapacity,
            outputAccumulatorCapacity,
            maxPressureBar
        );

        IFNTestSupport.SeededState inputSeed = IFNTestSupport.seedNetworkAtPressureAndTemperature(
            inputNetwork,
            fluid,
            inputStartPressureBar,
            startTemperatureK
        );
        IFNTestSupport.SeededState outputSeed = IFNTestSupport.seedNetworkAtPressureAndTemperature(
            outputNetwork,
            fluid,
            outputStartPressureBar,
            startTemperatureK
        );

        return new NetworkPair(inputNetwork, outputNetwork, inputSeed, outputSeed);
    }

    static SplitNetworkSet createSeededSplitNetworks(Fluid fluid, double inputStartPressureBar,
        double redOutputStartPressureBar, double blueOutputStartPressureBar, double startTemperatureK,
        int inputBaseCapacity, int inputAccumulatorCapacity, int outputBaseCapacity, int outputAccumulatorCapacity,
        float maxPressureBar) {
        IntegratedFluidNetwork inputNetwork = IFNTestSupport.newNetwork(
            fluid,
            inputBaseCapacity,
            inputAccumulatorCapacity,
            maxPressureBar
        );
        IntegratedFluidNetwork redOutputNetwork = IFNTestSupport.newNetwork(
            fluid,
            outputBaseCapacity,
            outputAccumulatorCapacity,
            maxPressureBar
        );
        IntegratedFluidNetwork blueOutputNetwork = IFNTestSupport.newNetwork(
            fluid,
            outputBaseCapacity,
            outputAccumulatorCapacity,
            maxPressureBar
        );

        IFNTestSupport.SeededState inputSeed = IFNTestSupport.seedNetworkAtPressureAndTemperature(
            inputNetwork,
            fluid,
            inputStartPressureBar,
            startTemperatureK
        );
        IFNTestSupport.SeededState redSeed = IFNTestSupport.seedNetworkAtPressureAndTemperature(
            redOutputNetwork,
            fluid,
            redOutputStartPressureBar,
            startTemperatureK
        );
        IFNTestSupport.SeededState blueSeed = IFNTestSupport.seedNetworkAtPressureAndTemperature(
            blueOutputNetwork,
            fluid,
            blueOutputStartPressureBar,
            startTemperatureK
        );

        return new SplitNetworkSet(inputNetwork, redOutputNetwork, blueOutputNetwork, inputSeed, redSeed, blueSeed);
    }

    static DualNetworkSet createSeededDualNetworks(Fluid fluid, double redInputStartPressureBar,
        double blueInputStartPressureBar, double redOutputStartPressureBar, double blueOutputStartPressureBar,
        double redInputTemperatureK, double blueInputTemperatureK, int inputBaseCapacity, int inputAccumulatorCapacity,
        int outputBaseCapacity, int outputAccumulatorCapacity, float maxPressureBar) {
        IntegratedFluidNetwork redInputNetwork = IFNTestSupport.newNetwork(
            fluid,
            inputBaseCapacity,
            inputAccumulatorCapacity,
            maxPressureBar
        );
        IntegratedFluidNetwork blueInputNetwork = IFNTestSupport.newNetwork(
            fluid,
            inputBaseCapacity,
            inputAccumulatorCapacity,
            maxPressureBar
        );
        IntegratedFluidNetwork redOutputNetwork = IFNTestSupport.newNetwork(
            fluid,
            outputBaseCapacity,
            outputAccumulatorCapacity,
            maxPressureBar
        );
        IntegratedFluidNetwork blueOutputNetwork = IFNTestSupport.newNetwork(
            fluid,
            outputBaseCapacity,
            outputAccumulatorCapacity,
            maxPressureBar
        );

        IFNTestSupport.SeededState redInputSeed = IFNTestSupport.seedNetworkAtPressureAndTemperature(
            redInputNetwork,
            fluid,
            redInputStartPressureBar,
            redInputTemperatureK
        );
        IFNTestSupport.SeededState blueInputSeed = IFNTestSupport.seedNetworkAtPressureAndTemperature(
            blueInputNetwork,
            fluid,
            blueInputStartPressureBar,
            blueInputTemperatureK
        );
        IFNTestSupport.SeededState redOutputSeed = IFNTestSupport.seedNetworkAtPressureAndTemperature(
            redOutputNetwork,
            fluid,
            redOutputStartPressureBar,
            redInputTemperatureK
        );
        IFNTestSupport.SeededState blueOutputSeed = IFNTestSupport.seedNetworkAtPressureAndTemperature(
            blueOutputNetwork,
            fluid,
            blueOutputStartPressureBar,
            blueInputTemperatureK
        );

        return new DualNetworkSet(
            redInputNetwork,
            blueInputNetwork,
            redOutputNetwork,
            blueOutputNetwork,
            redInputSeed,
            blueInputSeed,
            redOutputSeed,
            blueOutputSeed
        );
    }

    static StepResult runNormalTargetTemperatureStep(IntegratedFluidNetwork inputNetwork,
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

        return StepResult.progressed(
            extracted.amountQ,
            predictedInputPressure,
            predictedOutputPressure,
            inputNetwork.getPressure(),
            outputNetwork.getPressure(),
            inputNetwork.getTemperature(),
            outputNetwork.getTemperature()
        );
    }

    static SplitStepResult runSplitTargetTemperatureStep(IntegratedFluidNetwork inputNetwork,
        IntegratedFluidNetwork redOutputNetwork, IntegratedFluidNetwork blueOutputNetwork, Fluid fluid,
        double targetTemperature, double splitRatio, int fluidAmountPerOperationMb) {
        long availableAmountQ = inputNetwork.getAmountQ();
        if (availableAmountQ <= 0L) {
            return SplitStepResult.notProgressed();
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
            return SplitStepResult.notProgressed();
        }

        long amountToProcessQ = computeProcessAmountQ(availableAmountQ, fluidAmountPerOperationMb, vFactor);
        if (amountToProcessQ < IntegratedFluidNetwork.AMOUNT_SCALE) {
            return SplitStepResult.notProgressed();
        }

        long hotAmountQ = (long) (amountToProcessQ * splitRatio);
        long coldAmountQ = amountToProcessQ - hotAmountQ;
        if (hotAmountQ < IntegratedFluidNetwork.AMOUNT_SCALE || coldAmountQ < IntegratedFluidNetwork.AMOUNT_SCALE) {
            return SplitStepResult.notProgressed();
        }

        double hotSpecificEnthalpy = FluidThermalProperties.getSpecificEnthalpyFromPT(
            fluid,
            redOutputNetwork.getPressure(),
            targetTemperature
        );
        float cop = FluidThermalProperties.calculateHeatPumpCOP(
            (float) Math.min(inputTemperature, targetTemperature),
            (float) Math.max(inputTemperature, targetTemperature)
        );
        float penalty = FluidThermalProperties
            .calculateTemperaturePenalty((float) Math.abs(targetTemperature - inputTemperature));
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
            return SplitStepResult.notProgressed();
        }

        var extracted = inputNetwork.extractProportional(plan.acceptedTotalAmountQ, false);
        if (extracted.amountQ < IntegratedFluidNetwork.AMOUNT_SCALE) {
            return SplitStepResult.notProgressed();
        }

        long acceptedHotQ = (long) (extracted.amountQ * splitRatio);
        long acceptedColdQ = extracted.amountQ - acceptedHotQ;
        if (acceptedHotQ < IntegratedFluidNetwork.AMOUNT_SCALE || acceptedColdQ < IntegratedFluidNetwork.AMOUNT_SCALE) {
            return SplitStepResult.notProgressed();
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

        return SplitStepResult.progressed(
            extracted.amountQ,
            acceptedHotQ,
            acceptedColdQ,
            inputNetwork.getPressure(),
            redOutputNetwork.getPressure(),
            blueOutputNetwork.getPressure(),
            inputNetwork.getTemperature(),
            redOutputNetwork.getTemperature(),
            blueOutputNetwork.getTemperature()
        );
    }

    static DualStepResult runHeatExchangerTargetTemperatureStep(IntegratedFluidNetwork redInputNetwork,
        IntegratedFluidNetwork blueInputNetwork, IntegratedFluidNetwork redOutputNetwork,
        IntegratedFluidNetwork blueOutputNetwork, Fluid fluid, double targetTemperature, int fluidAmountPerOperationMb) {
        long redAvailQ = redInputNetwork.getAmountQ();
        long blueAvailQ = blueInputNetwork.getAmountQ();
        if (redAvailQ <= 0L || blueAvailQ <= 0L) {
            return DualStepResult.notProgressed();
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
            return DualStepResult.notProgressed();
        }

        long redProcessQ = computeProcessAmountQ(redAvailQ, fluidAmountPerOperationMb, redVFactor);
        long blueProcessQ = computeProcessAmountQ(blueAvailQ, fluidAmountPerOperationMb, blueVFactor);
        if (redProcessQ < IntegratedFluidNetwork.AMOUNT_SCALE || blueProcessQ < IntegratedFluidNetwork.AMOUNT_SCALE) {
            return DualStepResult.notProgressed();
        }

        double redOutH = FluidThermalProperties.getSpecificEnthalpyFromPT(
            fluid,
            redOutputNetwork.getPressure(),
            targetTemperature
        );
        float cop = FluidThermalProperties.calculateHeatPumpCOP(
            (float) Math.min(blueInTemp, targetTemperature),
            (float) Math.max(redInTemp, targetTemperature)
        );
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
            return DualStepResult.notProgressed();
        }

        var extractedRed = redInputNetwork.extractProportional(plan.acceptedRedAmountQ, false);
        var extractedBlue = blueInputNetwork.extractProportional(plan.acceptedBlueAmountQ, false);
        if (extractedRed.amountQ < IntegratedFluidNetwork.AMOUNT_SCALE
            || extractedBlue.amountQ < IntegratedFluidNetwork.AMOUNT_SCALE) {
            return DualStepResult.notProgressed();
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

        return DualStepResult.progressed(
            extractedRed.amountQ,
            extractedBlue.amountQ,
            redInputNetwork.getPressure(),
            blueInputNetwork.getPressure(),
            redOutputNetwork.getPressure(),
            blueOutputNetwork.getPressure(),
            redInputNetwork.getTemperature(),
            blueInputNetwork.getTemperature(),
            redOutputNetwork.getTemperature(),
            blueOutputNetwork.getTemperature()
        );
    }

    static long computeProcessAmountQ(long availableAmountQ, int fluidAmountPerOperationMb, double vFactor) {
        long desiredAmountMb = (long) Math.floor(fluidAmountPerOperationMb / vFactor);
        return Math.min(availableAmountQ, desiredAmountMb * IntegratedFluidNetwork.AMOUNT_SCALE);
    }

    static final class NetworkPair {
        final IntegratedFluidNetwork inputNetwork;
        final IntegratedFluidNetwork outputNetwork;
        final IFNTestSupport.SeededState inputSeed;
        final IFNTestSupport.SeededState outputSeed;

        private NetworkPair(IntegratedFluidNetwork inputNetwork, IntegratedFluidNetwork outputNetwork,
            IFNTestSupport.SeededState inputSeed, IFNTestSupport.SeededState outputSeed) {
            this.inputNetwork = inputNetwork;
            this.outputNetwork = outputNetwork;
            this.inputSeed = inputSeed;
            this.outputSeed = outputSeed;
        }
    }

    static final class SplitNetworkSet {
        final IntegratedFluidNetwork inputNetwork;
        final IntegratedFluidNetwork redOutputNetwork;
        final IntegratedFluidNetwork blueOutputNetwork;
        final IFNTestSupport.SeededState inputSeed;
        final IFNTestSupport.SeededState redSeed;
        final IFNTestSupport.SeededState blueSeed;

        private SplitNetworkSet(IntegratedFluidNetwork inputNetwork, IntegratedFluidNetwork redOutputNetwork,
            IntegratedFluidNetwork blueOutputNetwork, IFNTestSupport.SeededState inputSeed,
            IFNTestSupport.SeededState redSeed, IFNTestSupport.SeededState blueSeed) {
            this.inputNetwork = inputNetwork;
            this.redOutputNetwork = redOutputNetwork;
            this.blueOutputNetwork = blueOutputNetwork;
            this.inputSeed = inputSeed;
            this.redSeed = redSeed;
            this.blueSeed = blueSeed;
        }
    }

    static final class DualNetworkSet {
        final IntegratedFluidNetwork redInputNetwork;
        final IntegratedFluidNetwork blueInputNetwork;
        final IntegratedFluidNetwork redOutputNetwork;
        final IntegratedFluidNetwork blueOutputNetwork;
        final IFNTestSupport.SeededState redInputSeed;
        final IFNTestSupport.SeededState blueInputSeed;
        final IFNTestSupport.SeededState redOutputSeed;
        final IFNTestSupport.SeededState blueOutputSeed;

        private DualNetworkSet(IntegratedFluidNetwork redInputNetwork, IntegratedFluidNetwork blueInputNetwork,
            IntegratedFluidNetwork redOutputNetwork, IntegratedFluidNetwork blueOutputNetwork,
            IFNTestSupport.SeededState redInputSeed, IFNTestSupport.SeededState blueInputSeed,
            IFNTestSupport.SeededState redOutputSeed, IFNTestSupport.SeededState blueOutputSeed) {
            this.redInputNetwork = redInputNetwork;
            this.blueInputNetwork = blueInputNetwork;
            this.redOutputNetwork = redOutputNetwork;
            this.blueOutputNetwork = blueOutputNetwork;
            this.redInputSeed = redInputSeed;
            this.blueInputSeed = blueInputSeed;
            this.redOutputSeed = redOutputSeed;
            this.blueOutputSeed = blueOutputSeed;
        }
    }

    static final class StepResult {
        final boolean progressed;
        final long acceptedAmountQ;
        final float predictedInputPressure;
        final float predictedOutputPressure;
        final float actualInputPressure;
        final float actualOutputPressure;
        final float actualInputTemperature;
        final float actualOutputTemperature;

        private StepResult(boolean progressed, long acceptedAmountQ, float predictedInputPressure,
            float predictedOutputPressure, float actualInputPressure, float actualOutputPressure,
            float actualInputTemperature, float actualOutputTemperature) {
            this.progressed = progressed;
            this.acceptedAmountQ = acceptedAmountQ;
            this.predictedInputPressure = predictedInputPressure;
            this.predictedOutputPressure = predictedOutputPressure;
            this.actualInputPressure = actualInputPressure;
            this.actualOutputPressure = actualOutputPressure;
            this.actualInputTemperature = actualInputTemperature;
            this.actualOutputTemperature = actualOutputTemperature;
        }

        static StepResult notProgressed() {
            return new StepResult(false, 0L, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
        }

        static StepResult progressed(long acceptedAmountQ, float predictedInputPressure,
            float predictedOutputPressure, float actualInputPressure, float actualOutputPressure,
            float actualInputTemperature, float actualOutputTemperature) {
            return new StepResult(
                true,
                acceptedAmountQ,
                predictedInputPressure,
                predictedOutputPressure,
                actualInputPressure,
                actualOutputPressure,
                actualInputTemperature,
                actualOutputTemperature
            );
        }

        String describe(int step, double startPressureBar, double startTemperatureK, double targetTemperature) {
            return "step=" + step
                + " startP=" + startPressureBar
                + " startT=" + startTemperatureK
                + " targetT=" + targetTemperature
                + " acceptedQ=" + acceptedAmountQ
                + " predictedPin=" + predictedInputPressure
                + " predictedPout=" + predictedOutputPressure
                + " actualPin=" + actualInputPressure
                + " actualPout=" + actualOutputPressure
                + " actualTin=" + actualInputTemperature
                + " actualTout=" + actualOutputTemperature;
        }
    }

    static final class SplitStepResult {
        final boolean progressed;
        final long acceptedTotalAmountQ;
        final long acceptedRedAmountQ;
        final long acceptedBlueAmountQ;
        final float actualInputPressure;
        final float actualRedOutputPressure;
        final float actualBlueOutputPressure;
        final float actualInputTemperature;
        final float actualRedOutputTemperature;
        final float actualBlueOutputTemperature;

        private SplitStepResult(boolean progressed, long acceptedTotalAmountQ, long acceptedRedAmountQ,
            long acceptedBlueAmountQ, float actualInputPressure, float actualRedOutputPressure,
            float actualBlueOutputPressure, float actualInputTemperature, float actualRedOutputTemperature,
            float actualBlueOutputTemperature) {
            this.progressed = progressed;
            this.acceptedTotalAmountQ = acceptedTotalAmountQ;
            this.acceptedRedAmountQ = acceptedRedAmountQ;
            this.acceptedBlueAmountQ = acceptedBlueAmountQ;
            this.actualInputPressure = actualInputPressure;
            this.actualRedOutputPressure = actualRedOutputPressure;
            this.actualBlueOutputPressure = actualBlueOutputPressure;
            this.actualInputTemperature = actualInputTemperature;
            this.actualRedOutputTemperature = actualRedOutputTemperature;
            this.actualBlueOutputTemperature = actualBlueOutputTemperature;
        }

        static SplitStepResult notProgressed() {
            return new SplitStepResult(false, 0L, 0L, 0L, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
        }

        static SplitStepResult progressed(long acceptedTotalAmountQ, long acceptedRedAmountQ, long acceptedBlueAmountQ,
            float actualInputPressure, float actualRedOutputPressure, float actualBlueOutputPressure,
            float actualInputTemperature, float actualRedOutputTemperature, float actualBlueOutputTemperature) {
            return new SplitStepResult(
                true,
                acceptedTotalAmountQ,
                acceptedRedAmountQ,
                acceptedBlueAmountQ,
                actualInputPressure,
                actualRedOutputPressure,
                actualBlueOutputPressure,
                actualInputTemperature,
                actualRedOutputTemperature,
                actualBlueOutputTemperature
            );
        }

        String describe(int step, double startPressureBar, double startTemperatureK, double targetTemperature,
            double splitRatio) {
            return "step=" + step
                + " startP=" + startPressureBar
                + " startT=" + startTemperatureK
                + " targetT=" + targetTemperature
                + " split=" + splitRatio
                + " acceptedTotalQ=" + acceptedTotalAmountQ
                + " acceptedRedQ=" + acceptedRedAmountQ
                + " acceptedBlueQ=" + acceptedBlueAmountQ
                + " actualPin=" + actualInputPressure
                + " actualPred=" + actualRedOutputPressure
                + " actualPblue=" + actualBlueOutputPressure
                + " actualTin=" + actualInputTemperature
                + " actualTred=" + actualRedOutputTemperature
                + " actualTblue=" + actualBlueOutputTemperature;
        }
    }

    static final class DualStepResult {
        final boolean progressed;
        final long acceptedRedAmountQ;
        final long acceptedBlueAmountQ;
        final float actualRedInputPressure;
        final float actualBlueInputPressure;
        final float actualRedOutputPressure;
        final float actualBlueOutputPressure;
        final float actualRedInputTemperature;
        final float actualBlueInputTemperature;
        final float actualRedOutputTemperature;
        final float actualBlueOutputTemperature;

        private DualStepResult(boolean progressed, long acceptedRedAmountQ, long acceptedBlueAmountQ,
            float actualRedInputPressure, float actualBlueInputPressure, float actualRedOutputPressure,
            float actualBlueOutputPressure, float actualRedInputTemperature, float actualBlueInputTemperature,
            float actualRedOutputTemperature, float actualBlueOutputTemperature) {
            this.progressed = progressed;
            this.acceptedRedAmountQ = acceptedRedAmountQ;
            this.acceptedBlueAmountQ = acceptedBlueAmountQ;
            this.actualRedInputPressure = actualRedInputPressure;
            this.actualBlueInputPressure = actualBlueInputPressure;
            this.actualRedOutputPressure = actualRedOutputPressure;
            this.actualBlueOutputPressure = actualBlueOutputPressure;
            this.actualRedInputTemperature = actualRedInputTemperature;
            this.actualBlueInputTemperature = actualBlueInputTemperature;
            this.actualRedOutputTemperature = actualRedOutputTemperature;
            this.actualBlueOutputTemperature = actualBlueOutputTemperature;
        }

        static DualStepResult notProgressed() {
            return new DualStepResult(false, 0L, 0L, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
        }

        static DualStepResult progressed(long acceptedRedAmountQ, long acceptedBlueAmountQ,
            float actualRedInputPressure, float actualBlueInputPressure, float actualRedOutputPressure,
            float actualBlueOutputPressure, float actualRedInputTemperature, float actualBlueInputTemperature,
            float actualRedOutputTemperature, float actualBlueOutputTemperature) {
            return new DualStepResult(
                true,
                acceptedRedAmountQ,
                acceptedBlueAmountQ,
                actualRedInputPressure,
                actualBlueInputPressure,
                actualRedOutputPressure,
                actualBlueOutputPressure,
                actualRedInputTemperature,
                actualBlueInputTemperature,
                actualRedOutputTemperature,
                actualBlueOutputTemperature
            );
        }

        String describe(int step, double redStartPressureBar, double blueStartPressureBar, double redInputTemperature,
            double blueInputTemperature, double targetTemperature) {
            return "step=" + step
                + " redStartP=" + redStartPressureBar
                + " blueStartP=" + blueStartPressureBar
                + " redStartT=" + redInputTemperature
                + " blueStartT=" + blueInputTemperature
                + " targetT=" + targetTemperature
                + " acceptedRedQ=" + acceptedRedAmountQ
                + " acceptedBlueQ=" + acceptedBlueAmountQ
                + " actualPinRed=" + actualRedInputPressure
                + " actualPinBlue=" + actualBlueInputPressure
                + " actualPoutRed=" + actualRedOutputPressure
                + " actualPoutBlue=" + actualBlueOutputPressure
                + " actualTinRed=" + actualRedInputTemperature
                + " actualTinBlue=" + actualBlueInputTemperature
                + " actualToutRed=" + actualRedOutputTemperature
                + " actualToutBlue=" + actualBlueOutputTemperature;
        }
    }

    private static double toAmount(long amountQ) {
        return amountQ / (double) IntegratedFluidNetwork.AMOUNT_SCALE;
    }
}
