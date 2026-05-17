package gregtech.api.metatileentity.implementations.integratedfluid;

import net.minecraftforge.fluids.Fluid;

/**
 * Shared sizing rules for IFN machine batches.
 */
public final class IFNMachineBatchPlanner {

    private IFNMachineBatchPlanner() {}

    public static BatchPlan planInputBatch(Fluid fluid, float pressure, double specificEnthalpy, long availableAmountQ,
        double volumeLimit) {
        if (fluid == null) {
            return BatchPlan.invalid(specificEnthalpy);
        }

        double specificVolume = IntegratedFluidThermoModel
            .specificVolumeFromPressureAndSpecificEnthalpy(fluid, pressure, specificEnthalpy);
        long amountQ = computeAmountQForVolumeLimit(availableAmountQ, volumeLimit, specificVolume);
        if (amountQ <= 0L) {
            return BatchPlan.invalid(specificEnthalpy, specificVolume);
        }

        double temperature = FluidThermalProperties.getTemperatureFromPH(fluid, pressure, specificEnthalpy);
        if (Double.isNaN(temperature) || Double.isInfinite(temperature)) {
            return BatchPlan.invalid(specificEnthalpy, specificVolume);
        }
        return new BatchPlan(amountQ, specificEnthalpy, temperature, specificVolume);
    }

    public static long computeAmountQForVolumeLimit(long availableAmountQ, double volumeLimit,
        double specificVolume) {
        if (availableAmountQ <= 0L || volumeLimit <= 0.0d || specificVolume <= 0.0d ||
            Double.isNaN(volumeLimit) || Double.isNaN(specificVolume) ||
            Double.isInfinite(volumeLimit) || Double.isInfinite(specificVolume)) {
            return 0L;
        }

        double amount = Math.floor(volumeLimit / specificVolume);
        if (amount < 1.0d) {
            return 0L;
        }

        long limitedAmountQ = amount >= Long.MAX_VALUE / (double) IntegratedFluidNetwork.AMOUNT_SCALE
            ? Long.MAX_VALUE
            : (long) amount * IntegratedFluidNetwork.AMOUNT_SCALE;
        return Math.min(availableAmountQ, limitedAmountQ);
    }

    public static SplitAmounts computeSplitAmounts(long totalAmountQ, double firstRatio) {
        if (totalAmountQ <= 0L || firstRatio <= 0.0d || firstRatio >= 1.0d ||
            Double.isNaN(firstRatio) || Double.isInfinite(firstRatio)) {
            return SplitAmounts.invalid(totalAmountQ);
        }

        long firstAmountQ = (long) (totalAmountQ * firstRatio);
        long secondAmountQ = totalAmountQ - firstAmountQ;
        if (firstAmountQ < IntegratedFluidNetwork.AMOUNT_SCALE ||
            secondAmountQ < IntegratedFluidNetwork.AMOUNT_SCALE) {
            return SplitAmounts.invalid(totalAmountQ);
        }
        return new SplitAmounts(firstAmountQ, secondAmountQ);
    }

    public static final class BatchPlan {

        private final long amountQ;
        private final double specificEnthalpy;
        private final double temperature;
        private final double specificVolume;

        private BatchPlan(long amountQ, double specificEnthalpy, double temperature, double specificVolume) {
            this.amountQ = amountQ;
            this.specificEnthalpy = specificEnthalpy;
            this.temperature = temperature;
            this.specificVolume = specificVolume;
        }

        private static BatchPlan invalid(double specificEnthalpy) {
            return invalid(specificEnthalpy, 0.0d);
        }

        private static BatchPlan invalid(double specificEnthalpy, double specificVolume) {
            return new BatchPlan(0L, specificEnthalpy, 0.0d, specificVolume);
        }

        public boolean isValid() {
            return amountQ > 0L;
        }

        public long amountQ() {
            return amountQ;
        }

        public double specificEnthalpy() {
            return specificEnthalpy;
        }

        public double temperature() {
            return temperature;
        }

        public double specificVolume() {
            return specificVolume;
        }
    }

    public static final class SplitAmounts {

        private final long firstAmountQ;
        private final long secondAmountQ;

        private SplitAmounts(long firstAmountQ, long secondAmountQ) {
            this.firstAmountQ = firstAmountQ;
            this.secondAmountQ = secondAmountQ;
        }

        private static SplitAmounts invalid(long totalAmountQ) {
            return new SplitAmounts(0L, Math.max(0L, totalAmountQ));
        }

        public boolean isValid() {
            return firstAmountQ >= IntegratedFluidNetwork.AMOUNT_SCALE &&
                secondAmountQ >= IntegratedFluidNetwork.AMOUNT_SCALE;
        }

        public long firstAmountQ() {
            return firstAmountQ;
        }

        public long secondAmountQ() {
            return secondAmountQ;
        }

        public long totalAmountQ() {
            return firstAmountQ + secondAmountQ;
        }
    }
}
