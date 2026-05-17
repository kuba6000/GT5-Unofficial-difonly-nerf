package gregtech.common.tileentities.machines.multi;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.Test;

import gregtech.api.metatileentity.implementations.integratedfluid.machine.IFNBatchState;
import gregtech.api.metatileentity.implementations.integratedfluid.machine.IFNMachineProcess;
import gregtech.api.metatileentity.implementations.integratedfluid.machine.IFNMachineOutputBuffer;
import gregtech.common.tileentities.machines.multi.heatpump.HeatPumpMachineProcessState;

class HeatPumpMachineProcessStateTest {

    @Test
    void machineProcessStateRoundTripsThroughHeatPumpNbt() {
        IFNMachineProcess process = IFNMachineProcess.create();
        process.setEnabled(false);
        NBTTagCompound processTag = new NBTTagCompound();
        process.writeToNBT(processTag);
        IFNMachineOutputBuffer outputBuffer = new IFNMachineOutputBuffer();
        outputBuffer.addPendingOutput("red", IFNBatchState.of("water", 1_000L, 300.0d, 1.0f));
        NBTTagCompound outputBufferTag = new NBTTagCompound();
        outputBuffer.writeToNBT(outputBufferTag);

        NBTTagCompound inputTag = new NBTTagCompound();
        inputTag.setTag("ifnMachineProcess", processTag);
        inputTag.setTag("ifnMachineOutputBuffer", outputBufferTag);
        HeatPumpMachineProcessState state = new HeatPumpMachineProcessState();
        state.load(inputTag);

        NBTTagCompound outputTag = new NBTTagCompound();
        state.save(outputTag);
        IFNMachineProcess restored = IFNMachineProcess.readFromNBT(outputTag.getCompoundTag("ifnMachineProcess"));
        IFNMachineOutputBuffer restoredOutputBuffer = IFNMachineOutputBuffer
            .readFromNBT(outputTag.getCompoundTag("ifnMachineOutputBuffer"));

        assertFalse(restored.isEnabled());
        assertEquals(1_000L, restoredOutputBuffer.pendingOutput("red").amountQ());
    }
}
