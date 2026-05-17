package gregtech.api.metatileentity.implementations.integratedfluid.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.Test;

class IFNMachineProcessTest {

    @Test
    void portsKeepNameAndDirection() {
        IFNPort input = IFNPort.input("cold");
        IFNPort output = IFNPort.output("hot");

        assertEquals("cold", input.name());
        assertEquals(IFNPort.Direction.INPUT, input.direction());
        assertTrue(input.isInput());
        assertEquals("hot", output.name());
        assertEquals(IFNPort.Direction.OUTPUT, output.direction());
        assertTrue(output.isOutput());
    }

    @Test
    void pendingOutputBlocksNewProcessUntilFullyDrained() {
        IFNMachineProcess process = IFNMachineProcess.create();
        IFNBatchState input = IFNBatchState.of("water", 1_000L, 120.0d, 1.0f);
        IFNBatchState output = IFNBatchState.of("steam", 1_000L, 320.0d, 1.0f);

        assertEquals(IFNTransferPlan.Status.ACCEPTED, process.start(input).status());
        assertEquals(IFNTransferPlan.Status.ACCEPTED, process.finish(output).status());
        assertTrue(process.hasPendingOutput());

        assertEquals(IFNTransferPlan.Status.OUTPUT_PENDING, process.start(input).status());
        assertEquals(400L, process.drainPendingOutput(400L).batch().amountQ());
        assertEquals(600L, process.pendingOutput().amountQ());
        assertEquals(IFNTransferPlan.Status.OUTPUT_PENDING, process.start(input).status());

        assertEquals(600L, process.drainPendingOutput(1_000L).batch().amountQ());
        assertFalse(process.hasPendingOutput());
        assertEquals(IFNTransferPlan.Status.ACCEPTED, process.start(input).status());
    }

    @Test
    void disabledProcessBlocksInputAndOutputTransfer() {
        IFNMachineProcess process = IFNMachineProcess.create();
        IFNBatchState input = IFNBatchState.of("water", 1_000L, 120.0d, 1.0f);
        IFNBatchState output = IFNBatchState.of("steam", 1_000L, 320.0d, 1.0f);

        assertEquals(IFNTransferPlan.Status.ACCEPTED, process.start(input).status());
        assertEquals(IFNTransferPlan.Status.ACCEPTED, process.finish(output).status());
        process.setEnabled(false);

        assertEquals(IFNTransferPlan.Status.DISABLED, process.start(input).status());
        assertEquals(IFNTransferPlan.Status.DISABLED, process.drainPendingOutput(1_000L).status());
        assertEquals(1_000L, process.pendingOutput().amountQ());
    }

    @Test
    void drainedPendingOutputCanBeRequeuedAfterBlockedOutput() {
        IFNMachineProcess process = IFNMachineProcess.create();
        IFNBatchState input = IFNBatchState.of("water", 1_000L, 120.0d, 1.0f);
        IFNBatchState output = IFNBatchState.of("steam", 1_000L, 320.0d, 1.0f);

        assertEquals(IFNTransferPlan.Status.ACCEPTED, process.start(input).status());
        assertEquals(IFNTransferPlan.Status.ACCEPTED, process.finish(output).status());

        IFNBatchState drained = process.drainPendingOutput(400L).batch();
        assertEquals(400L, drained.amountQ());
        assertEquals(600L, process.pendingOutput().amountQ());

        assertEquals(IFNTransferPlan.Status.ACCEPTED, process.requeuePendingOutput(drained).status());
        assertEquals(1_000L, process.pendingOutput().amountQ());
        assertEquals(320.0d, process.pendingOutput().specificEnthalpy());
    }

    @Test
    void processStateRoundTripsThroughNbt() {
        IFNMachineProcess process = IFNMachineProcess.create();
        IFNBatchState active = IFNBatchState.of("water", 800L, 180.0d, 2.0f);
        IFNBatchState output = IFNBatchState.of("steam", 400L, 420.0d, 1.5f);
        process.start(active);
        process.finish(output);
        process.setEnabled(false);

        NBTTagCompound tag = new NBTTagCompound();
        process.writeToNBT(tag);
        IFNMachineProcess restored = IFNMachineProcess.readFromNBT(tag);

        assertFalse(restored.isEnabled());
        assertFalse(restored.hasActiveBatch());
        assertTrue(restored.hasPendingOutput());
        assertEquals(output, restored.pendingOutput());
    }
}
