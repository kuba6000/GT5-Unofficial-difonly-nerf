package gregtech.common.tileentities.machines.multi.heatpump;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraftforge.fluids.Fluid;
import org.junit.jupiter.api.Test;

import gregtech.api.metatileentity.implementations.integratedfluid.IFNMachineProcessStatus;
import gregtech.api.metatileentity.implementations.integratedfluid.IFNTestSupport;
import gregtech.api.metatileentity.implementations.integratedfluid.IntegratedFluidNetwork;
import gregtech.api.metatileentity.implementations.integratedfluid.machine.IFNBatchState;
import gregtech.api.metatileentity.implementations.integratedfluid.machine.IFNMachineProcess;

class HeatPumpPendingOutputPusherTest {

    @Test
    void pushesPendingOutputInAcceptedChunks() {
        Fluid water = IFNTestSupport.waterFluid();
        IntegratedFluidNetwork output = IFNTestSupport.newNetwork(water, 10_000, 0, 100.0f);
        IFNMachineProcess process = finishedProcess(IFNBatchState.of("water", 3_000_000L, 300.0d, 1.0f));

        HeatPumpPendingOutputPusher.Result result = HeatPumpPendingOutputPusher.push(process, output, water, 1_000_000L);

        assertEquals(IFNMachineProcessStatus.SUCCESS, result.status());
        assertEquals(1_000_000L, result.pushedAmountQ());
        assertTrue(result.outputStillPending());
        assertEquals(2_000_000L, process.pendingOutput().amountQ());
        assertEquals(1_000_000L, output.getAmountQ());
    }

    @Test
    void blockedOutputLeavesPendingBatchUntouched() {
        Fluid water = IFNTestSupport.waterFluid();
        IntegratedFluidNetwork output = IFNTestSupport.newNetwork(water, 10_000, 0, 100.0f);
        output.freeze("test");
        IFNMachineProcess process = finishedProcess(IFNBatchState.of("water", 1_000_000L, 300.0d, 1.0f));

        HeatPumpPendingOutputPusher.Result result = HeatPumpPendingOutputPusher.push(process, output, water, 1_000_000L);

        assertEquals(IFNMachineProcessStatus.OUTPUT_BLOCKED, result.status());
        assertEquals(0L, result.pushedAmountQ());
        assertTrue(result.outputStillPending());
        assertEquals(1_000_000L, process.pendingOutput().amountQ());
        assertEquals(0L, output.getAmountQ());
    }

    @Test
    void emptyPendingOutputReportsNothingPending() {
        Fluid water = IFNTestSupport.waterFluid();
        IntegratedFluidNetwork output = IFNTestSupport.newNetwork(water, 10_000, 0, 100.0f);
        IFNMachineProcess process = IFNMachineProcess.create();

        HeatPumpPendingOutputPusher.Result result = HeatPumpPendingOutputPusher.push(process, output, water, 1_000_000L);

        assertEquals(IFNMachineProcessStatus.NO_INPUT, result.status());
        assertFalse(result.outputStillPending());
    }

    private static IFNMachineProcess finishedProcess(IFNBatchState output) {
        IFNMachineProcess process = IFNMachineProcess.create();
        assertEquals(
            gregtech.api.metatileentity.implementations.integratedfluid.machine.IFNTransferPlan.Status.ACCEPTED,
            process.start(IFNBatchState.of("water", output.amountQ(), 300.0d, 1.0f)).status());
        assertEquals(
            gregtech.api.metatileentity.implementations.integratedfluid.machine.IFNTransferPlan.Status.ACCEPTED,
            process.finish(output).status());
        return process;
    }
}
