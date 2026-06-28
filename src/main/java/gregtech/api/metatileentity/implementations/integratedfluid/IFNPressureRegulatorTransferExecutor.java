package gregtech.api.metatileentity.implementations.integratedfluid;

import net.minecraftforge.fluids.Fluid;

public final class IFNPressureRegulatorTransferExecutor {

    private IFNPressureRegulatorTransferExecutor() {}

    public static IFNPressureRegulatorTransferPlanner.Plan transfer(IntegratedFluidNetwork inputNetwork,
        IntegratedFluidNetwork outputNetwork, float setpointPressureBar, float overshootToleranceBar,
        long maxPacketAmountQ) {
        if (inputNetwork == null || outputNetwork == null) {
            return IFNPressureRegulatorTransferPlanner.Plan
                .rejected(IFNPressureRegulatorTransferPlanner.Status.INVALID_REQUEST);
        }

        Fluid fluid = inputNetwork.getFluid();
        if (fluid == null) {
            return IFNPressureRegulatorTransferPlanner.Plan
                .rejected(IFNPressureRegulatorTransferPlanner.Status.INVALID_REQUEST);
        }

        double sourceSpecificEnthalpy = inputNetwork.getSpecificEnthalpy();
        IFNPressureRegulatorTransferPlanner.Plan plan = IFNPressureRegulatorTransferPlanner.plan(
            inputNetwork,
            outputNetwork,
            fluid,
            sourceSpecificEnthalpy,
            setpointPressureBar,
            overshootToleranceBar,
            inputNetwork.getAmountQ(),
            maxPacketAmountQ);
        if (plan.status() != IFNPressureRegulatorTransferPlanner.Status.ACCEPTED) {
            return plan;
        }

        IntegratedFluidNetwork.ExtractedPayload extracted = inputNetwork.extractProportional(plan.acceptedAmountQ(), false);
        if (extracted.amountQ < IntegratedFluidNetwork.AMOUNT_SCALE) {
            return IFNPressureRegulatorTransferPlanner.Plan
                .rejected(IFNPressureRegulatorTransferPlanner.Status.INPUT_BLOCKED);
        }
        if (!IFNStateMutationApplier.addOutputOrRestoreInput(
            inputNetwork,
            outputNetwork,
            fluid,
            extracted,
            extracted.enthalpyQ)) {
            return IFNPressureRegulatorTransferPlanner.Plan
                .rejected(IFNPressureRegulatorTransferPlanner.Status.OUTPUT_BLOCKED);
        }
        return plan;
    }
}
