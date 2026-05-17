package gregtech.api.metatileentity.implementations.integratedfluid.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.Test;

class IFNMachineOutputBufferTest {

    @Test
    void keepsIndependentPendingOutputPerPort() {
        IFNMachineOutputBuffer buffer = new IFNMachineOutputBuffer();

        assertEquals(
            IFNTransferPlan.Status.ACCEPTED,
            buffer.addPendingOutput("red", IFNBatchState.of("water", 1_000L, 300.0d, 1.0f)).status());
        assertEquals(
            IFNTransferPlan.Status.ACCEPTED,
            buffer.addPendingOutput("blue", IFNBatchState.of("water", 2_000L, 280.0d, 1.0f)).status());

        assertTrue(buffer.hasPendingOutput());
        assertEquals(1_000L, buffer.pendingOutput("red").amountQ());
        assertEquals(2_000L, buffer.pendingOutput("blue").amountQ());
    }

    @Test
    void drainsOnlySelectedPortAndKeepsRemainder() {
        IFNMachineOutputBuffer buffer = new IFNMachineOutputBuffer();
        buffer.addPendingOutput("red", IFNBatchState.of("water", 1_000L, 300.0d, 1.0f));
        buffer.addPendingOutput("blue", IFNBatchState.of("water", 2_000L, 280.0d, 1.0f));

        IFNTransferPlan drained = buffer.drainPendingOutput("blue", 750L);

        assertEquals(IFNTransferPlan.Status.ACCEPTED, drained.status());
        assertEquals(750L, drained.batch().amountQ());
        assertEquals(1_000L, buffer.pendingOutput("red").amountQ());
        assertEquals(1_250L, buffer.pendingOutput("blue").amountQ());
    }

    @Test
    void rejectsDifferentFluidsOnSamePort() {
        IFNMachineOutputBuffer buffer = new IFNMachineOutputBuffer();
        buffer.addPendingOutput("red", IFNBatchState.of("water", 1_000L, 300.0d, 1.0f));

        assertEquals(
            IFNTransferPlan.Status.INVALID_REQUEST,
            buffer.addPendingOutput("red", IFNBatchState.of("steam", 500L, 400.0d, 1.0f)).status());
        assertEquals(1_000L, buffer.pendingOutput("red").amountQ());
    }

    @Test
    void roundTripsThroughNbt() {
        IFNMachineOutputBuffer buffer = new IFNMachineOutputBuffer();
        buffer.addPendingOutput("red", IFNBatchState.of("water", 1_000L, 300.0d, 1.0f));
        buffer.addPendingOutput("blue", IFNBatchState.of("steam", 2_000L, 420.0d, 1.5f));

        NBTTagCompound tag = new NBTTagCompound();
        buffer.writeToNBT(tag);
        IFNMachineOutputBuffer restored = IFNMachineOutputBuffer.readFromNBT(tag);

        assertEquals(1_000L, restored.pendingOutput("red").amountQ());
        assertEquals(2_000L, restored.pendingOutput("blue").amountQ());
        assertTrue(restored.pendingOutput("green").isEmpty());
    }
}
