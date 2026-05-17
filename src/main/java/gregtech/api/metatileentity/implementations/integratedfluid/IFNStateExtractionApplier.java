package gregtech.api.metatileentity.implementations.integratedfluid;

import net.minecraftforge.fluids.Fluid;

/**
 * Applies multi-input IFN extractions without leaving a partially extracted input behind when a later extraction fails.
 */
public final class IFNStateExtractionApplier {

    private IFNStateExtractionApplier() {}

    public static TwoInputExtraction extractTwoOrRestoreFirst(IntegratedFluidNetwork firstInputNetwork, Fluid firstFluid,
        long firstAmountQ, IntegratedFluidNetwork secondInputNetwork, Fluid secondFluid, long secondAmountQ) {
        if (firstInputNetwork == null || secondInputNetwork == null || firstFluid == null || secondFluid == null
            || firstAmountQ <= 0L || secondAmountQ <= 0L) {
            return TwoInputExtraction.failure();
        }

        IntegratedFluidNetwork.ExtractedPayload firstExtracted =
            firstInputNetwork.extractProportional(firstAmountQ, false);
        if (firstExtracted.amountQ <= 0L) {
            return TwoInputExtraction.failure();
        }

        IntegratedFluidNetwork.ExtractedPayload secondExtracted =
            secondInputNetwork.extractProportional(secondAmountQ, false);
        if (secondExtracted.amountQ <= 0L) {
            firstInputNetwork.addState(firstFluid, firstExtracted.amountQ, firstExtracted.enthalpyQ);
            return TwoInputExtraction.failure();
        }

        return TwoInputExtraction.success(firstExtracted, secondExtracted);
    }

    public static final class TwoInputExtraction {

        private final boolean success;
        private final IntegratedFluidNetwork.ExtractedPayload first;
        private final IntegratedFluidNetwork.ExtractedPayload second;

        private TwoInputExtraction(boolean success, IntegratedFluidNetwork.ExtractedPayload first,
            IntegratedFluidNetwork.ExtractedPayload second) {
            this.success = success;
            this.first = first;
            this.second = second;
        }

        private static TwoInputExtraction success(IntegratedFluidNetwork.ExtractedPayload first,
            IntegratedFluidNetwork.ExtractedPayload second) {
            return new TwoInputExtraction(true, first, second);
        }

        private static TwoInputExtraction failure() {
            return new TwoInputExtraction(
                false,
                IntegratedFluidNetwork.ExtractedPayload.empty(),
                IntegratedFluidNetwork.ExtractedPayload.empty());
        }

        public boolean isSuccess() {
            return success;
        }

        public IntegratedFluidNetwork.ExtractedPayload getFirst() {
            return first;
        }

        public IntegratedFluidNetwork.ExtractedPayload getSecond() {
            return second;
        }
    }
}
