package gregtech.api.metatileentity.implementations.integratedfluid.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraftforge.fluids.Fluid;
import org.junit.jupiter.api.Test;

import gregtech.api.metatileentity.implementations.integratedfluid.IFNMachineProcessStatus;
import gregtech.api.metatileentity.implementations.integratedfluid.IFNTestSupport;
import gregtech.api.metatileentity.implementations.integratedfluid.IntegratedFluidNetwork;

class IFNMachineOutputBufferPusherTest {

    @Test
    void pushesSelectedPortInAcceptedChunks() {
        Fluid water = IFNTestSupport.waterFluid();
        IntegratedFluidNetwork output = IFNTestSupport.newNetwork(water, 10_000, 0, 100.0f);
        IFNMachineOutputBuffer buffer = new IFNMachineOutputBuffer();
        buffer.addPendingOutput("red", IFNBatchState.of("water", 3_000_000L, 300.0d, 1.0f));
        buffer.addPendingOutput("blue", IFNBatchState.of("water", 2_000_000L, 280.0d, 1.0f));

        IFNMachineOutputBufferPusher.Result result = IFNMachineOutputBufferPusher.push(
            buffer,
            "red",
            output,
            water,
            1_000_000L);

        assertEquals(IFNMachineProcessStatus.SUCCESS, result.status());
        assertEquals(1_000_000L, result.pushedAmountQ());
        assertTrue(result.outputStillPending());
        assertEquals(2_000_000L, buffer.pendingOutput("red").amountQ());
        assertEquals(2_000_000L, buffer.pendingOutput("blue").amountQ());
        assertEquals(1_000_000L, output.getAmountQ());
    }

    @Test
    void blockedOutputLeavesSelectedPortUntouched() {
        Fluid water = IFNTestSupport.waterFluid();
        IntegratedFluidNetwork output = IFNTestSupport.newNetwork(water, 10_000, 0, 100.0f);
        output.freeze("test");
        IFNMachineOutputBuffer buffer = new IFNMachineOutputBuffer();
        buffer.addPendingOutput("red", IFNBatchState.of("water", 1_000_000L, 300.0d, 1.0f));

        IFNMachineOutputBufferPusher.Result result = IFNMachineOutputBufferPusher.push(
            buffer,
            "red",
            output,
            water,
            1_000_000L);

        assertEquals(IFNMachineProcessStatus.OUTPUT_BLOCKED, result.status());
        assertEquals(0L, result.pushedAmountQ());
        assertTrue(result.outputStillPending());
        assertEquals(1_000_000L, buffer.pendingOutput("red").amountQ());
        assertEquals(0L, output.getAmountQ());
    }

    @Test
    void emptySelectedPortReportsNothingPending() {
        Fluid water = IFNTestSupport.waterFluid();
        IntegratedFluidNetwork output = IFNTestSupport.newNetwork(water, 10_000, 0, 100.0f);
        IFNMachineOutputBuffer buffer = new IFNMachineOutputBuffer();

        IFNMachineOutputBufferPusher.Result result = IFNMachineOutputBufferPusher.push(
            buffer,
            "red",
            output,
            water,
            1_000_000L);

        assertEquals(IFNMachineProcessStatus.NO_INPUT, result.status());
        assertFalse(result.outputStillPending());
    }
}
