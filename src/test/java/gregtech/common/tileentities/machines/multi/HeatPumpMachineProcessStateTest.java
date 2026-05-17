package gregtech.common.tileentities.machines.multi;

import static org.junit.jupiter.api.Assertions.assertFalse;

import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.Test;

import gregtech.api.metatileentity.implementations.integratedfluid.machine.IFNMachineProcess;
import gregtech.common.tileentities.machines.multi.heatpump.HeatPumpMachineProcessState;

class HeatPumpMachineProcessStateTest {

    @Test
    void machineProcessStateRoundTripsThroughHeatPumpNbt() {
        IFNMachineProcess process = IFNMachineProcess.create();
        process.setEnabled(false);
        NBTTagCompound processTag = new NBTTagCompound();
        process.writeToNBT(processTag);

        NBTTagCompound inputTag = new NBTTagCompound();
        inputTag.setTag("ifnMachineProcess", processTag);
        HeatPumpMachineProcessState state = new HeatPumpMachineProcessState();
        state.load(inputTag);

        NBTTagCompound outputTag = new NBTTagCompound();
        state.save(outputTag);
        IFNMachineProcess restored = IFNMachineProcess.readFromNBT(outputTag.getCompoundTag("ifnMachineProcess"));

        assertFalse(restored.isEnabled());
    }
}
