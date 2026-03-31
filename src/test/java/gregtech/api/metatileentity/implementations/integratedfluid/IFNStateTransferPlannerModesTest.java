package gregtech.api.metatileentity.implementations.integratedfluid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraftforge.fluids.Fluid;

import org.junit.jupiter.api.Test;

class IFNStateTransferPlannerModesTest {

    @Test
    void splitPlanningRespectsTheMostConstrainedOutput() {
        Fluid fluid = IFNTestSupport.liquidFluid();
        IntegratedFluidNetwork input = IFNTestSupport.newNetwork(fluid, 1_000, 100, 10.0f);
        IntegratedFluidNetwork redOutput = IFNTestSupport.newNetwork(fluid, 100, 100, 1.5f);
        IntegratedFluidNetwork blueOutput = IFNTestSupport.newNetwork(fluid, 100, 100, 2.0f);

        long inputAmountQ = 600L * IntegratedFluidNetwork.AMOUNT_SCALE;
        input.addState(fluid, inputAmountQ, IntegratedFluidNetwork.toEnthalpyQFromSpecific(300.0d, inputAmountQ));

        long requestedAmountQ = 400L * IntegratedFluidNetwork.AMOUNT_SCALE;
        double splitRatio = 0.5d;

        IFNStateTransferPlanner.PlannedSplitTransfer plan = IFNStateTransferPlanner.planSplitStateAdd(
            input,
            redOutput,
            fluid,
            300.0d,
            blueOutput,
            fluid,
            300.0d,
            requestedAmountQ,
            splitRatio,
            10.0f
        );

        long maxRedQ = redOutput.getMaxAddableAmountQ(fluid, 300.0d, (long) (requestedAmountQ * splitRatio));
        assertEquals(maxRedQ, plan.acceptedRedAmountQ);
        assertEquals(plan.acceptedTotalAmountQ - plan.acceptedRedAmountQ, plan.acceptedBlueAmountQ);
        assertTrue(plan.acceptedTotalAmountQ < requestedAmountQ);
    }

    @Test
    void dualPlanningAppliesSharedRatioAcrossBothSides() {
        Fluid fluid = IFNTestSupport.liquidFluid();
        IntegratedFluidNetwork redInput = IFNTestSupport.newNetwork(fluid, 1_000, 100, 10.0f);
        IntegratedFluidNetwork blueInput = IFNTestSupport.newNetwork(fluid, 1_000, 100, 10.0f);
        IntegratedFluidNetwork redOutput = IFNTestSupport.newNetwork(fluid, 100, 100, 1.5f);
        IntegratedFluidNetwork blueOutput = IFNTestSupport.newNetwork(fluid, 300, 100, 10.0f);

        long requestedRedQ = 200L * IntegratedFluidNetwork.AMOUNT_SCALE;
        long requestedBlueQ = 400L * IntegratedFluidNetwork.AMOUNT_SCALE;

        redInput.addState(fluid, requestedRedQ, IntegratedFluidNetwork.toEnthalpyQFromSpecific(300.0d, requestedRedQ));
        blueInput.addState(fluid, requestedBlueQ, IntegratedFluidNetwork.toEnthalpyQFromSpecific(300.0d, requestedBlueQ));

        IFNStateTransferPlanner.PlannedDualTransfer plan = IFNStateTransferPlanner.planDualStateAddWithSharedRatio(
            redInput,
            redOutput,
            fluid,
            300.0d,
            requestedRedQ,
            blueInput,
            blueOutput,
            fluid,
            300.0d,
            requestedBlueQ,
            10.0f
        );

        double reconstructedRatio = plan.acceptedRedAmountQ / (double) requestedRedQ;
        assertEquals(reconstructedRatio, plan.acceptedBlueAmountQ / (double) requestedBlueQ, 1.0e-6d);
        assertEquals(reconstructedRatio, plan.acceptedRatio, 1.0e-6d);
        assertTrue(plan.acceptedRatio < 1.0d);
    }
}
