package gregtech.api.metatileentity.implementations.integratedfluid;

import net.minecraftforge.fluids.Fluid;

/**
 * Central pressure solver for Integrated Fluid Network state transitions.
 *
 * The model is pure: it computes pressure and safety flags, while callers decide whether to apply side effects
 * such as rupturing a network.
 */
public final class IFNPressureModel {

    private static final double LIQUID_BULK_MODULUS_BAR = 22000.0d;
    private static final int GAS_PRESSURE_RELAXATION_ITERATIONS = 20;

    private IFNPressureModel() {}

    public static PressureResult compute(Fluid fluid, IFNFluidState state, NetworkLimits limits) {
        if (fluid == null || state == null || state.getAmountQ() <= 0L || limits == null || limits.totalCapacity <= 0) {
            return PressureResult.safe(IntegratedFluidThermoModel.BASE_PRESSURE);
        }

        double amount = toAmount(state.getAmountQ());
        double specificEnthalpy = state.getSpecificEnthalpy();
        double pGuess = IFNPressurePolicy.clampMinimum((double) state.getPressureBar());
        FluidThermalProperties.PhaseResult phase =
            FluidThermalProperties.getPhaseFromPH(fluid, pGuess, specificEnthalpy);

        if (phase.phase == FluidThermalProperties.Phase.VAPOR
            || phase.phase == FluidThermalProperties.Phase.SUPERCRITICAL) {
            double pGas = computeGasPressureBar(fluid, amount, specificEnthalpy, limits.totalCapacity, pGuess);
            float clampedPressure = (float) IFNPressurePolicy.clampMinimum(
                Math.min(pGas, (double) limits.maxPressureBar));
            boolean pressureLimitExceeded = pGas > (double) limits.maxPressureBar;
            boolean ruptureRequired = !limits.incompleteNetwork
                && IFNPressurePolicy.exceedsRuptureLimit(pGas, (double) limits.maxPressureBar);
            return new PressureResult(clampedPressure, pressureLimitExceeded, ruptureRequired);
        }

        if (limits.accumulatorCapacity <= 0) {
            return PressureResult.safe(IntegratedFluidThermoModel.BASE_PRESSURE);
        }

        double specificVolume = getLiquidSpecificVolumeAtBasePressure(fluid, specificEnthalpy);
        double vLiq = amount * specificVolume;
        double vExcess = vLiq - limits.baseCapacity;
        double pressure;

        if (vExcess <= 0.0d) {
            pressure = IntegratedFluidThermoModel.BASE_PRESSURE;
        } else if (vExcess < limits.accumulatorCapacity) {
            double vGas = limits.accumulatorCapacity - vExcess;
            pressure = IntegratedFluidThermoModel.BASE_PRESSURE * (limits.accumulatorCapacity / vGas);
        } else {
            double vOver = vExcess - limits.accumulatorCapacity;
            double pMaxAcc = IntegratedFluidThermoModel.BASE_PRESSURE * (limits.accumulatorCapacity / 0.001d);
            pressure = pMaxAcc + LIQUID_BULK_MODULUS_BAR * (vOver / limits.totalCapacity);
        }

        float clampedPressure = (float) Math.max(1.0d, Math.min(pressure, (double) limits.maxPressureBar));
        boolean pressureLimitExceeded = pressure > (double) limits.maxPressureBar;
        boolean ruptureRequired = !limits.incompleteNetwork
            && IFNPressurePolicy.exceedsRuptureLimit(pressure, limits.maxPressureBar);
        return new PressureResult(clampedPressure, pressureLimitExceeded, ruptureRequired);
    }

    public static double getLiquidSpecificVolumeAtBasePressure(Fluid fluid, double specificEnthalpy) {
        double specificVolume = IntegratedFluidThermoModel.specificVolumeFromPressureAndSpecificEnthalpy(
            fluid,
            IntegratedFluidThermoModel.BASE_PRESSURE,
            specificEnthalpy
        );
        if (specificVolume <= 0.0d || Double.isNaN(specificVolume) || Double.isInfinite(specificVolume)) {
            return 1.0d;
        }
        return specificVolume;
    }

    public static double computeMaxSafeLiquidOccupiedVolume(int baseCapacity, int accumulatorCapacity, int totalCapacity,
        double maxPressure) {
        if (totalCapacity <= 0) {
            return 0.0d;
        }
        if (accumulatorCapacity <= 0) {
            return totalCapacity;
        }

        double pBase = IntegratedFluidThermoModel.BASE_PRESSURE;
        double pLimit = Math.max(maxPressure, pBase);
        if (pLimit <= pBase) {
            return Math.max(0.0d, Math.min((double) totalCapacity, (double) baseCapacity));
        }

        double pMaxAcc = pBase * (accumulatorCapacity / 0.001d);
        if (pLimit < pMaxAcc) {
            double accumulatorFill = accumulatorCapacity * (1.0d - pBase / pLimit);
            return Math.max(0.0d, Math.min((double) totalCapacity, baseCapacity + accumulatorFill));
        }

        double overfill = (pLimit - pMaxAcc) * totalCapacity / LIQUID_BULK_MODULUS_BAR;
        return Math.max(0.0d, Math.min((double) totalCapacity, baseCapacity + accumulatorCapacity + overfill));
    }

    private static double computeGasPressureBar(Fluid fluid, double amountStd, double specificEnthalpy, int totalCapacity,
        double pInit) {
        if (totalCapacity <= 0) {
            return IntegratedFluidThermoModel.BASE_PRESSURE;
        }

        double p = IFNPressurePolicy.clampMinimum(pInit);
        for (int it = 0; it < GAS_PRESSURE_RELAXATION_ITERATIONS; it++) {
            double temperature = FluidThermalProperties.getTemperatureFromPH(fluid, p, specificEnthalpy);
            double pNew = IntegratedFluidThermoModel.BASE_PRESSURE
                * (amountStd / (double) totalCapacity)
                * (temperature / IntegratedFluidThermoModel.BASE_TEMPERATURE);

            if (Double.isNaN(pNew) || Double.isInfinite(pNew)) {
                return p;
            }
            if (Math.abs(pNew - p) < IFNPressurePolicy.MIN_PRESSURE_BAR) {
                return pNew;
            }
            p = 0.5d * p + 0.5d * pNew;
        }

        return p;
    }

    private static double toAmount(long amountQ) {
        return (double) amountQ / (double) IntegratedFluidNetwork.AMOUNT_SCALE;
    }

    public static final class NetworkLimits {

        private final int baseCapacity;
        private final int accumulatorCapacity;
        private final int totalCapacity;
        private final float maxPressureBar;
        private final boolean incompleteNetwork;

        private NetworkLimits(int baseCapacity, int accumulatorCapacity, int totalCapacity, float maxPressureBar,
            boolean incompleteNetwork) {
            this.baseCapacity = Math.max(0, baseCapacity);
            this.accumulatorCapacity = Math.max(0, accumulatorCapacity);
            this.totalCapacity = Math.max(0, totalCapacity);
            this.maxPressureBar = IFNPressurePolicy.clampMinimum(maxPressureBar);
            this.incompleteNetwork = incompleteNetwork;
        }

        public static NetworkLimits of(int baseCapacity, int accumulatorCapacity, int totalCapacity, float maxPressureBar,
            boolean incompleteNetwork) {
            return new NetworkLimits(baseCapacity, accumulatorCapacity, totalCapacity, maxPressureBar, incompleteNetwork);
        }
    }

    public static final class PressureResult {

        private final float pressureBar;
        private final boolean pressureLimitExceeded;
        private final boolean ruptureRequired;

        private PressureResult(float pressureBar, boolean pressureLimitExceeded, boolean ruptureRequired) {
            this.pressureBar = pressureBar;
            this.pressureLimitExceeded = pressureLimitExceeded;
            this.ruptureRequired = ruptureRequired;
        }

        private static PressureResult safe(float pressureBar) {
            return new PressureResult(pressureBar, false, false);
        }

        public float getPressureBar() {
            return pressureBar;
        }

        public boolean isRuptureRequired() {
            return ruptureRequired;
        }

        public boolean isPressureLimitExceeded() {
            return pressureLimitExceeded;
        }
    }
}
