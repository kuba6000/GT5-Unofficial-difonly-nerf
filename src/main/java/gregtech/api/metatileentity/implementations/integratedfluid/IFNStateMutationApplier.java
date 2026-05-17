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
}
