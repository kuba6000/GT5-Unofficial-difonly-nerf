package gregtech.api.metatileentity.implementations.integratedfluid;

import net.minecraftforge.fluids.Fluid;

/**
 * Pure thermodynamic planner for two-stream IFN heat exchanger mode.
 *
 * The planner decides target/source output states and energy cost, but never mutates networks. Actual extraction and
 * output pressure acceptance belong to IFNDualOutputProcess.
 */
public final class IFNHeatExchangerPlanner {

    private IFNHeatExchangerPlanner() {}

    public static Plan plan(Request request) {
        if (request == null || !request.isValid()) {
            return Plan.invalid();
        }

        double redInH = request.redInputBatch.specificEnthalpy();
        double blueInH = request.blueInputBatch.specificEnthalpy();
        double redInTemp = request.redInputBatch.temperature();
        double blueInTemp = request.blueInputBatch.temperature();

        boolean configureRed = request.configureRed;
        StreamState target = configureRed
            ? new StreamState(
                request.redOutputNetwork,
                request.redFluid,
                redInH,
                redInTemp,
                request.redInputBatch.amountQ())
            : new StreamState(
                request.blueOutputNetwork,
                request.blueFluid,
                blueInH,
                blueInTemp,
                request.blueInputBatch.amountQ());
        StreamState source = configureRed
            ? new StreamState(
                request.blueOutputNetwork,
                request.blueFluid,
                blueInH,
                blueInTemp,
                request.blueInputBatch.amountQ())
            : new StreamState(
                request.redOutputNetwork,
                request.redFluid,
                redInH,
                redInTemp,
                request.redInputBatch.amountQ());

        double targetOutTemp = target.inputTemperature;
        double targetOutH = target.inputSpecificEnthalpy;
        long energyCost = 0L;
        IFNMachineThermo.HeatPumpMetrics metrics = zeroMetrics();
        boolean passthroughMode = false;

        switch (request.mode) {
            case TARGET_TEMPERATURE:
                targetOutTemp = request.targetTemperature;
                double temperatureDelta = targetOutTemp - target.inputTemperature;
                if ((configureRed && targetOutTemp < target.inputTemperature)
                    || (!configureRed && targetOutTemp > target.inputTemperature)) {
                    return Plan.invalid();
                }

                if (Math.abs(temperatureDelta) <= (configureRed
                    ? request.lowerTemperatureTolerance
                    : request.upperTemperatureTolerance)) {
                    passthroughMode = true;
                    targetOutTemp = target.inputTemperature;
                    targetOutH = target.inputSpecificEnthalpy;
                    energyCost = 0L;
                    metrics = zeroMetrics();
                } else {
                    metrics = IFNMachineThermo.computeHeatExchangerMetrics(
                        redInTemp,
                        blueInTemp,
                        configureRed,
                        target.inputTemperature,
                        targetOutTemp
                    );
                    targetOutH = IFNMachineThermo.computeTargetSpecificEnthalpyForStateAdd(
                        target.outputNetwork,
                        target.fluid,
                        targetOutTemp,
                        target.amountQ
                    );
                    energyCost = IFNMachineThermo.computeHeatPumpEnergyCost(
                        target.inputSpecificEnthalpy,
                        targetOutH,
                        target.amountQ,
                        metrics.cop(),
                        metrics.efficiencyPenalty()
                    );
                }
                break;
            case TARGET_COP:
                float targetCop = request.targetCOP <= 1.0f ? 1.1f : request.targetCOP;
                targetOutTemp = IFNMachineThermo.computeHeatExchangerTargetCopOutputTemperature(
                    redInTemp,
                    blueInTemp,
                    targetCop,
                    configureRed
                );
                metrics = IFNMachineThermo.computeHeatExchangerMetrics(
                    redInTemp,
                    blueInTemp,
                    configureRed,
                    target.inputTemperature,
                    targetOutTemp
                );
                metrics = IFNMachineThermo.heatPumpMetrics(
                    targetCop,
                    metrics.efficiencyPenalty(),
                    metrics.temperatureDelta(),
                    targetCop / metrics.efficiencyPenalty()
                );
                targetOutH = IFNMachineThermo.computeTargetSpecificEnthalpyForStateAdd(
                    target.outputNetwork,
                    target.fluid,
                    targetOutTemp,
                    target.amountQ
                );
                energyCost = IFNMachineThermo.computeHeatPumpEnergyCost(
                    target.inputSpecificEnthalpy,
                    targetOutH,
                    target.amountQ,
                    metrics.cop(),
                    metrics.efficiencyPenalty()
                );
                break;
            case TARGET_ENERGY:
                long targetTotalEnergy = (long) request.targetEnergyPerTick * 20L;
                if (targetTotalEnergy <= 0L) {
                    return Plan.invalid();
                }
                IFNMachineThermo.TargetEnergyState targetEnergyState =
                    IFNMachineThermo.computeHeatExchangerTargetEnergyOutputState(
                        target.fluid,
                        target.outputNetwork.getPressure(),
                        redInTemp,
                        blueInTemp,
                        configureRed,
                        target.inputTemperature,
                        target.inputSpecificEnthalpy,
                        target.amountQ,
                        targetTotalEnergy,
                        configureRed
                    );
                targetOutH = targetEnergyState.specificEnthalpy();
                targetOutTemp = targetEnergyState.temperature();
                energyCost = targetTotalEnergy;
                metrics = targetEnergyState.metrics();
                break;
            default:
                return Plan.invalid();
        }

        if (!passthroughMode) {
            if (configureRed && targetOutH < target.inputSpecificEnthalpy - 1e-6d) {
                return Plan.invalid();
            }
            if (!configureRed && targetOutH > target.inputSpecificEnthalpy + 1e-6d) {
                return Plan.invalid();
            }
        }

        double targetAmount = toAmount(target.amountQ);
        double sourceAmount = toAmount(source.amountQ);
        double qTargetTotal = Math.abs(targetOutH - target.inputSpecificEnthalpy) * targetAmount;
        double qSourceTotal;
        double sourceOutH;
        if (configureRed) {
            qSourceTotal = Math.max(0.0d, qTargetTotal - energyCost);
            sourceOutH = source.inputSpecificEnthalpy - (qSourceTotal / sourceAmount);
        } else {
            qSourceTotal = qTargetTotal + energyCost;
            sourceOutH = source.inputSpecificEnthalpy + (qSourceTotal / sourceAmount);
        }

        double redOutH = configureRed ? targetOutH : sourceOutH;
        double blueOutH = configureRed ? sourceOutH : targetOutH;
        long redAmountQ = configureRed ? target.amountQ : source.amountQ;
        long blueAmountQ = configureRed ? source.amountQ : target.amountQ;

        return new Plan(
            passthroughMode ? Status.PASSTHROUGH : Status.READY,
            redAmountQ,
            blueAmountQ,
            redOutH,
            blueOutH,
            targetOutTemp,
            energyCost,
            metrics,
            passthroughMode
        );
    }

    private static double toAmount(long amountQ) {
        return amountQ / (double) IntegratedFluidNetwork.AMOUNT_SCALE;
    }

    private static IFNMachineThermo.HeatPumpMetrics zeroMetrics() {
        return IFNMachineThermo.heatPumpMetrics(0.0f, 1.0f, 0.0d, 0.0f);
    }

    public enum Mode {
        TARGET_TEMPERATURE,
        TARGET_COP,
        TARGET_ENERGY
    }

    public enum Status {
        READY,
        PASSTHROUGH,
        INVALID_CONFIGURATION
    }

    private static final class StreamState {

        private final IntegratedFluidNetwork outputNetwork;
        private final Fluid fluid;
        private final double inputSpecificEnthalpy;
        private final double inputTemperature;
        private final long amountQ;

        private StreamState(IntegratedFluidNetwork outputNetwork, Fluid fluid, double inputSpecificEnthalpy,
            double inputTemperature, long amountQ) {
            this.outputNetwork = outputNetwork;
            this.fluid = fluid;
            this.inputSpecificEnthalpy = inputSpecificEnthalpy;
            this.inputTemperature = inputTemperature;
            this.amountQ = amountQ;
        }
    }

    public static final class Request {

        private final Mode mode;
        private final IntegratedFluidNetwork redOutputNetwork;
        private final IntegratedFluidNetwork blueOutputNetwork;
        private final Fluid redFluid;
        private final Fluid blueFluid;
        private final IFNMachineBatchPlanner.BatchPlan redInputBatch;
        private final IFNMachineBatchPlanner.BatchPlan blueInputBatch;
        private final boolean configureRed;
        private final float targetTemperature;
        private final float targetCOP;
        private final int targetEnergyPerTick;
        private final float lowerTemperatureTolerance;
        private final float upperTemperatureTolerance;

        private Request(Mode mode, IntegratedFluidNetwork redOutputNetwork, IntegratedFluidNetwork blueOutputNetwork,
            Fluid redFluid, Fluid blueFluid, IFNMachineBatchPlanner.BatchPlan redInputBatch,
            IFNMachineBatchPlanner.BatchPlan blueInputBatch, boolean configureRed, float targetTemperature,
            float targetCOP, int targetEnergyPerTick, float lowerTemperatureTolerance, float upperTemperatureTolerance) {
            this.mode = mode;
            this.redOutputNetwork = redOutputNetwork;
            this.blueOutputNetwork = blueOutputNetwork;
            this.redFluid = redFluid;
            this.blueFluid = blueFluid;
            this.redInputBatch = redInputBatch;
            this.blueInputBatch = blueInputBatch;
            this.configureRed = configureRed;
            this.targetTemperature = targetTemperature;
            this.targetCOP = targetCOP;
            this.targetEnergyPerTick = targetEnergyPerTick;
            this.lowerTemperatureTolerance = lowerTemperatureTolerance;
            this.upperTemperatureTolerance = upperTemperatureTolerance;
        }

        public static Request of(Mode mode, IntegratedFluidNetwork redOutputNetwork,
            IntegratedFluidNetwork blueOutputNetwork, Fluid redFluid, Fluid blueFluid,
            IFNMachineBatchPlanner.BatchPlan redInputBatch, IFNMachineBatchPlanner.BatchPlan blueInputBatch,
            boolean configureRed, float targetTemperature, float targetCOP, int targetEnergyPerTick,
            float lowerTemperatureTolerance, float upperTemperatureTolerance) {
            return new Request(
                mode,
                redOutputNetwork,
                blueOutputNetwork,
                redFluid,
                blueFluid,
                redInputBatch,
                blueInputBatch,
                configureRed,
                targetTemperature,
                targetCOP,
                targetEnergyPerTick,
                lowerTemperatureTolerance,
                upperTemperatureTolerance
            );
        }

        private boolean isValid() {
            return mode != null
                && redOutputNetwork != null
                && blueOutputNetwork != null
                && redFluid != null
                && blueFluid != null
                && redInputBatch != null
                && redInputBatch.isValid()
                && blueInputBatch != null
                && blueInputBatch.isValid()
                && lowerTemperatureTolerance >= 0.0f
                && upperTemperatureTolerance >= 0.0f
                && (mode != Mode.TARGET_COP || targetCOP >= 1.1f)
                && (mode != Mode.TARGET_ENERGY || targetEnergyPerTick > 0);
        }
    }

    public static final class Plan {

        private final Status status;
        private final long redAmountQ;
        private final long blueAmountQ;
        private final double redOutputSpecificEnthalpy;
        private final double blueOutputSpecificEnthalpy;
        private final double targetOutputTemperature;
        private final long energyCostEu;
        private final IFNMachineThermo.HeatPumpMetrics metrics;
        private final boolean passthrough;

        private Plan(Status status, long redAmountQ, long blueAmountQ, double redOutputSpecificEnthalpy,
            double blueOutputSpecificEnthalpy, double targetOutputTemperature, long energyCostEu,
            IFNMachineThermo.HeatPumpMetrics metrics, boolean passthrough) {
            this.status = status;
            this.redAmountQ = redAmountQ;
            this.blueAmountQ = blueAmountQ;
            this.redOutputSpecificEnthalpy = redOutputSpecificEnthalpy;
            this.blueOutputSpecificEnthalpy = blueOutputSpecificEnthalpy;
            this.targetOutputTemperature = targetOutputTemperature;
            this.energyCostEu = energyCostEu;
            this.metrics = metrics;
            this.passthrough = passthrough;
        }

        private static Plan invalid() {
            return new Plan(
                Status.INVALID_CONFIGURATION,
                0L,
                0L,
                0.0d,
                0.0d,
                0.0d,
                0L,
                zeroMetrics(),
                false
            );
        }

        public Status getStatus() {
            return status;
        }

        public long getRedAmountQ() {
            return redAmountQ;
        }

        public long getBlueAmountQ() {
            return blueAmountQ;
        }

        public double getRedOutputSpecificEnthalpy() {
            return redOutputSpecificEnthalpy;
        }

        public double getBlueOutputSpecificEnthalpy() {
            return blueOutputSpecificEnthalpy;
        }

        public double getTargetOutputTemperature() {
            return targetOutputTemperature;
        }

        public long getEnergyCostEu() {
            return energyCostEu;
        }

        public IFNMachineThermo.HeatPumpMetrics getMetrics() {
            return metrics;
        }

        public boolean isPassthrough() {
            return passthrough;
        }
    }
}
