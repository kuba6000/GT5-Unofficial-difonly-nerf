package gregtech.api.metatileentity.implementations.integratedfluid;

import net.minecraftforge.fluids.Fluid;

/**
 * Pure thermodynamic planner for split-flow IFN heat pump mode.
 *
 * The first split output is the configured hot/primary stream and the second split output is the secondary stream
 * derived from the energy balance.
 */
public final class IFNSplitHeatPumpPlanner {

    private IFNSplitHeatPumpPlanner() {}

    public static Plan plan(Request request) {
        if (request == null || !request.isValid()) {
            return Plan.invalid();
        }

        long amountQ = request.inputBatch.amountQ();
        double inputTemperature = request.inputBatch.temperature();
        if (inputTemperature <= 0.0d) {
            inputTemperature = request.coldReservoirTemperature;
        }
        double inputSpecificEnthalpy = request.inputBatch.specificEnthalpy();

        IFNMachineBatchPlanner.SplitAmounts split = IFNMachineBatchPlanner.computeSplitAmounts(
            amountQ,
            request.splitRatio
        );
        if (!split.isValid()) {
            return Plan.invalid();
        }

        long hotAmountQ = split.firstAmountQ();
        long coldAmountQ = split.secondAmountQ();
        double hotAmount = toAmount(hotAmountQ);
        double coldAmount = toAmount(coldAmountQ);

        double hotSpecificEnthalpy = inputSpecificEnthalpy;
        double hotTemperature = inputTemperature;
        long energyCost = 0L;
        boolean passthroughMode = false;
        IFNMachineThermo.HeatPumpMetrics metrics = zeroMetrics();

        switch (request.mode) {
            case TARGET_TEMPERATURE:
                hotTemperature = request.targetTemperature;
                if (inputTemperature >= request.targetTemperature - request.lowerTemperatureTolerance
                    && inputTemperature <= request.targetTemperature + request.upperTemperatureTolerance) {
                    passthroughMode = true;
                    hotTemperature = inputTemperature;
                    hotSpecificEnthalpy = inputSpecificEnthalpy;
                    energyCost = 0L;
                    metrics = zeroMetrics();
                } else {
                    metrics = IFNMachineThermo.computeHeatPumpMetrics(inputTemperature, hotTemperature);
                    hotSpecificEnthalpy = IFNMachineThermo.computeTargetSpecificEnthalpyForStateAdd(
                        request.hotOutputNetwork,
                        request.fluid,
                        hotTemperature,
                        hotAmountQ
                    );
                    energyCost = IFNMachineThermo.computeHeatPumpEnergyCost(
                        inputSpecificEnthalpy,
                        hotSpecificEnthalpy,
                        hotAmountQ,
                        metrics.cop(),
                        metrics.efficiencyPenalty()
                    );
                }
                break;
            case TARGET_COP:
                hotTemperature = IFNMachineThermo.computeTargetCopOutputTemperature(
                    inputTemperature,
                    request.targetCOP,
                    request.targetHeating
                );
                double temperatureDelta = hotTemperature - inputTemperature;
                float penalty = FluidThermalProperties.calculateTemperaturePenalty((float) Math.abs(temperatureDelta));
                metrics = IFNMachineThermo.heatPumpMetrics(
                    request.targetCOP,
                    penalty,
                    Math.abs(temperatureDelta),
                    request.targetCOP / penalty
                );
                hotSpecificEnthalpy = IFNMachineThermo.computeTargetSpecificEnthalpyForStateAdd(
                    request.hotOutputNetwork,
                    request.fluid,
                    hotTemperature,
                    hotAmountQ
                );
                energyCost = IFNMachineThermo.computeHeatPumpEnergyCost(
                    inputSpecificEnthalpy,
                    hotSpecificEnthalpy,
                    hotAmountQ,
                    metrics.cop(),
                    metrics.efficiencyPenalty()
                );
                break;
            case TARGET_ENERGY:
                long targetTotalEnergy = (long) request.targetEnergyPerTick * 20L;
                if (targetTotalEnergy <= 0L) {
                    return Plan.invalid();
                }
                energyCost = targetTotalEnergy;
                IFNMachineThermo.TargetEnergyState targetEnergyState = IFNMachineThermo.computeTargetEnergyOutputState(
                    request.fluid,
                    request.hotOutputNetwork.getPressure(),
                    inputTemperature,
                    inputSpecificEnthalpy,
                    hotAmountQ,
                    targetTotalEnergy,
                    request.targetHeating
                );
                hotSpecificEnthalpy = targetEnergyState.specificEnthalpy();
                hotTemperature = targetEnergyState.temperature();
                metrics = IFNMachineThermo.computeHeatPumpMetrics(inputTemperature, hotTemperature);
                break;
            default:
                return Plan.invalid();
        }

        if (!passthroughMode) {
            if (request.targetHeating && hotSpecificEnthalpy < inputSpecificEnthalpy - 1e-6d) {
                return Plan.invalid();
            }
            if (!request.targetHeating && hotSpecificEnthalpy > inputSpecificEnthalpy + 1e-6d) {
                return Plan.invalid();
            }
        }

        double qHotTotal = Math.abs(hotSpecificEnthalpy - inputSpecificEnthalpy) * hotAmount;
        double qColdTotal = Math.max(0.0d, qHotTotal - energyCost);
        double coldSpecificEnthalpy = request.targetHeating
            ? inputSpecificEnthalpy - (qColdTotal / coldAmount)
            : inputSpecificEnthalpy + (qColdTotal / coldAmount);

        return new Plan(
            passthroughMode ? Status.PASSTHROUGH : Status.READY,
            amountQ,
            hotAmountQ,
            coldAmountQ,
            hotSpecificEnthalpy,
            coldSpecificEnthalpy,
            hotTemperature,
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

    public static final class Request {

        private final Mode mode;
        private final IntegratedFluidNetwork hotOutputNetwork;
        private final Fluid fluid;
        private final IFNMachineBatchPlanner.BatchPlan inputBatch;
        private final boolean targetHeating;
        private final float targetTemperature;
        private final float targetCOP;
        private final int targetEnergyPerTick;
        private final double splitRatio;
        private final float lowerTemperatureTolerance;
        private final float upperTemperatureTolerance;
        private final float coldReservoirTemperature;

        private Request(Mode mode, IntegratedFluidNetwork hotOutputNetwork, Fluid fluid,
            IFNMachineBatchPlanner.BatchPlan inputBatch, boolean targetHeating, float targetTemperature, float targetCOP,
            int targetEnergyPerTick, double splitRatio, float lowerTemperatureTolerance, float upperTemperatureTolerance,
            float coldReservoirTemperature) {
            this.mode = mode;
            this.hotOutputNetwork = hotOutputNetwork;
            this.fluid = fluid;
            this.inputBatch = inputBatch;
            this.targetHeating = targetHeating;
            this.targetTemperature = targetTemperature;
            this.targetCOP = targetCOP;
            this.targetEnergyPerTick = targetEnergyPerTick;
            this.splitRatio = splitRatio;
            this.lowerTemperatureTolerance = lowerTemperatureTolerance;
            this.upperTemperatureTolerance = upperTemperatureTolerance;
            this.coldReservoirTemperature = coldReservoirTemperature;
        }

        public static Request of(Mode mode, IntegratedFluidNetwork hotOutputNetwork, Fluid fluid,
            IFNMachineBatchPlanner.BatchPlan inputBatch, boolean targetHeating, float targetTemperature, float targetCOP,
            int targetEnergyPerTick, double splitRatio, float lowerTemperatureTolerance, float upperTemperatureTolerance,
            float coldReservoirTemperature) {
            return new Request(
                mode,
                hotOutputNetwork,
                fluid,
                inputBatch,
                targetHeating,
                targetTemperature,
                targetCOP,
                targetEnergyPerTick,
                splitRatio,
                lowerTemperatureTolerance,
                upperTemperatureTolerance,
                coldReservoirTemperature
            );
        }

        private boolean isValid() {
            return mode != null
                && hotOutputNetwork != null
                && fluid != null
                && inputBatch != null
                && inputBatch.isValid()
                && lowerTemperatureTolerance >= 0.0f
                && upperTemperatureTolerance >= 0.0f
                && coldReservoirTemperature > 0.0f
                && (mode != Mode.TARGET_COP || targetCOP >= 1.1f)
                && (mode != Mode.TARGET_ENERGY || targetEnergyPerTick > 0);
        }
    }

    public static final class Plan {

        private final Status status;
        private final long amountQ;
        private final long hotAmountQ;
        private final long coldAmountQ;
        private final double hotOutputSpecificEnthalpy;
        private final double coldOutputSpecificEnthalpy;
        private final double hotOutputTemperature;
        private final long energyCostEu;
        private final IFNMachineThermo.HeatPumpMetrics metrics;
        private final boolean passthrough;

        private Plan(Status status, long amountQ, long hotAmountQ, long coldAmountQ,
            double hotOutputSpecificEnthalpy, double coldOutputSpecificEnthalpy, double hotOutputTemperature,
            long energyCostEu, IFNMachineThermo.HeatPumpMetrics metrics, boolean passthrough) {
            this.status = status;
            this.amountQ = amountQ;
            this.hotAmountQ = hotAmountQ;
            this.coldAmountQ = coldAmountQ;
            this.hotOutputSpecificEnthalpy = hotOutputSpecificEnthalpy;
            this.coldOutputSpecificEnthalpy = coldOutputSpecificEnthalpy;
            this.hotOutputTemperature = hotOutputTemperature;
            this.energyCostEu = energyCostEu;
            this.metrics = metrics;
            this.passthrough = passthrough;
        }

        private static Plan invalid() {
            return new Plan(
                Status.INVALID_CONFIGURATION,
                0L,
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

        public long getAmountQ() {
            return amountQ;
        }

        public long getHotAmountQ() {
            return hotAmountQ;
        }

        public long getColdAmountQ() {
            return coldAmountQ;
        }

        public double getHotOutputSpecificEnthalpy() {
            return hotOutputSpecificEnthalpy;
        }

        public double getColdOutputSpecificEnthalpy() {
            return coldOutputSpecificEnthalpy;
        }

        public double getHotOutputTemperature() {
            return hotOutputTemperature;
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
