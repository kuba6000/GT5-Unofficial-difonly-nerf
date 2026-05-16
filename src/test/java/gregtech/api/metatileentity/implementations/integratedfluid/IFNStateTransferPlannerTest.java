package gregtech.api.metatileentity.implementations.integratedfluid;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import net.minecraftforge.fluids.Fluid;

class IFNStateTransferPlannerTest {

    @Test
    void blockedSameNetworkTransferIsEmpty() {
        Fluid fluid = IFNTestSupport.liquidFluid();
        IntegratedFluidNetwork network = IFNTestSupport.newNetwork(fluid, 10_000, 0, 100.0f);

        network.freeze("test freeze");

        IFNStateTransferPlanner.PlannedStateTransfer plan = IFNStateTransferPlanner.planSingleOutputStateAdd(
            network,
            network,
            fluid,
            300.0d,
            IntegratedFluidNetwork.AMOUNT_SCALE,
            1.0f);

        assertEquals(0L, plan.acceptedAmountQ);
        assertEquals(IFNStateTransferPlanner.Status.INPUT_BLOCKED, plan.status);
    }

    @Test
    void blockedSplitOutputTransferIsEmpty() {
        Fluid fluid = IFNTestSupport.liquidFluid();
        IntegratedFluidNetwork input = IFNTestSupport.newNetwork(fluid, 10_000, 0, 100.0f);
        IntegratedFluidNetwork redOutput = IFNTestSupport.newNetwork(fluid, 10_000, 0, 100.0f);
        IntegratedFluidNetwork blueOutput = IFNTestSupport.newNetwork(fluid, 10_000, 0, 100.0f);

        redOutput.freeze("test freeze");

        IFNStateTransferPlanner.PlannedSplitTransfer plan = IFNStateTransferPlanner.planSplitStateAdd(
            input,
            redOutput,
            fluid,
            300.0d,
            blueOutput,
            fluid,
            300.0d,
            2L * IntegratedFluidNetwork.AMOUNT_SCALE,
            0.5d,
            1.0f);

        assertEquals(0L, plan.acceptedTotalAmountQ);
        assertEquals(IFNStateTransferPlanner.Status.OUTPUT_BLOCKED, plan.status);
    }

    @Test
    void blockedDualInputTransferIsEmpty() {
        Fluid fluid = IFNTestSupport.liquidFluid();
        IntegratedFluidNetwork redInput = IFNTestSupport.newNetwork(fluid, 10_000, 0, 100.0f);
        IntegratedFluidNetwork redOutput = IFNTestSupport.newNetwork(fluid, 10_000, 0, 100.0f);
        IntegratedFluidNetwork blueInput = IFNTestSupport.newNetwork(fluid, 10_000, 0, 100.0f);
        IntegratedFluidNetwork blueOutput = IFNTestSupport.newNetwork(fluid, 10_000, 0, 100.0f);

        blueInput.setPending(true);

        IFNStateTransferPlanner.PlannedDualTransfer plan = IFNStateTransferPlanner.planDualStateAddWithSharedRatio(
            redInput,
            redOutput,
            fluid,
            300.0d,
            IntegratedFluidNetwork.AMOUNT_SCALE,
            blueInput,
            blueOutput,
            fluid,
            300.0d,
            IntegratedFluidNetwork.AMOUNT_SCALE,
            1.0f);

        assertEquals(0.0d, plan.acceptedRatio);
        assertEquals(IFNStateTransferPlanner.Status.INPUT_BLOCKED, plan.status);
    }
}
