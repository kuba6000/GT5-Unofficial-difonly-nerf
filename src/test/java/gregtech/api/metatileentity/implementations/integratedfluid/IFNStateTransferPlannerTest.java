package gregtech.api.metatileentity.implementations.integratedfluid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraftforge.fluids.Fluid;

import org.junit.jupiter.api.Test;

class IFNStateTransferPlannerTest {

    @Test
    void singleOutputPlanningIsBoundedByOutputAcceptance() {
        Fluid liquid = IFNTestSupport.liquidFluid();
        IntegratedFluidNetwork input = IFNTestSupport.newNetwork(liquid, 1_000, 100, 10.0f);
        IntegratedFluidNetwork output = IFNTestSupport.newNetwork(liquid, 100, 100, 1.5f);

        long inputAmountQ = 500L * IntegratedFluidNetwork.AMOUNT_SCALE;
        input.addState(
            liquid,
            inputAmountQ,
            IntegratedFluidNetwork.toEnthalpyQFromSpecific(300.0d, inputAmountQ)
        );

        long requestedAmountQ = 200L * IntegratedFluidNetwork.AMOUNT_SCALE;
        long maxAddableQ = output.getMaxAddableAmountQ(liquid, 300.0d, requestedAmountQ);

        IFNStateTransferPlanner.PlannedStateTransfer plan = IFNStateTransferPlanner.planSingleOutputStateAdd(
            input,
            output,
            liquid,
            300.0d,
            requestedAmountQ,
            10.0f
        );

        assertEquals(maxAddableQ, plan.acceptedAmountQ);
        assertTrue(plan.acceptedAmountQ < requestedAmountQ);
    }
}
