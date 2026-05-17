package gregtech.api.metatileentity.implementations.integratedfluid;

import net.minecraftforge.fluids.Fluid;

/**
 * Applies IFN state mutations in the order used by machines: input is already reserved/extracted, then output is
 * attempted. If the output mutation is rejected, the extracted input payload is restored.
 */
public final class IFNStateMutationApplier {

    private IFNStateMutationApplier() {}

    public static boolean addOutputOrRestoreInput(IntegratedFluidNetwork inputNetwork, IntegratedFluidNetwork outputNetwork,
        Fluid fluid, IntegratedFluidNetwork.ExtractedPayload extracted, long outputEnthalpyQ) {
        if (inputNetwork == null || outputNetwork == null || fluid == null || extracted == null || extracted.amountQ <= 0L) {
            return false;
        }
        if (outputNetwork.addState(fluid, extracted.amountQ, outputEnthalpyQ)) {
            return true;
        }

        inputNetwork.addState(fluid, extracted.amountQ, extracted.enthalpyQ);
        return false;
    }

    public static boolean addTwoOutputsOrRestoreInputs(IntegratedFluidNetwork firstInputNetwork,
        IntegratedFluidNetwork firstOutputNetwork, Fluid firstFluid, IntegratedFluidNetwork.ExtractedPayload firstExtracted,
        long firstOutputEnthalpyQ, IntegratedFluidNetwork secondInputNetwork, IntegratedFluidNetwork secondOutputNetwork,
        Fluid secondFluid, IntegratedFluidNetwork.ExtractedPayload secondExtracted, long secondOutputEnthalpyQ) {
        if (!isValidPayload(firstInputNetwork, firstOutputNetwork, firstFluid, firstExtracted)
            || !isValidPayload(secondInputNetwork, secondOutputNetwork, secondFluid, secondExtracted)) {
            return false;
        }

        IFNFluidState firstOutputSnapshot = firstOutputNetwork.snapshotState();
        IFNFluidState secondOutputSnapshot = secondOutputNetwork.snapshotState();
        if (firstOutputNetwork.addState(firstFluid, firstExtracted.amountQ, firstOutputEnthalpyQ)
            && secondOutputNetwork.addState(secondFluid, secondExtracted.amountQ, secondOutputEnthalpyQ)) {
            return true;
        }

        firstOutputNetwork.restoreSnapshot(firstOutputSnapshot);
        if (secondOutputNetwork != firstOutputNetwork) {
            secondOutputNetwork.restoreSnapshot(secondOutputSnapshot);
        }
        firstInputNetwork.addState(firstFluid, firstExtracted.amountQ, firstExtracted.enthalpyQ);
        secondInputNetwork.addState(secondFluid, secondExtracted.amountQ, secondExtracted.enthalpyQ);
        return false;
    }

    private static boolean isValidPayload(IntegratedFluidNetwork inputNetwork, IntegratedFluidNetwork outputNetwork,
        Fluid fluid, IntegratedFluidNetwork.ExtractedPayload extracted) {
        return inputNetwork != null
            && outputNetwork != null
            && fluid != null
            && extracted != null
            && extracted.amountQ > 0L;
    }
}
