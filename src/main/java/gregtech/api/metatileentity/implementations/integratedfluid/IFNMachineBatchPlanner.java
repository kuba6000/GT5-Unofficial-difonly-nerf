package gregtech.api.metatileentity.implementations.integratedfluid;

/**
 * Shared sizing rules for IFN machine batches.
 */
public final class IFNMachineBatchPlanner {

    private IFNMachineBatchPlanner() {}

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
}
