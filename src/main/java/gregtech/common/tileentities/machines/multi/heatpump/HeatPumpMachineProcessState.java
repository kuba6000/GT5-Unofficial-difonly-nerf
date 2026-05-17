package gregtech.common.tileentities.machines.multi.heatpump;

import net.minecraft.nbt.NBTTagCompound;

import gregtech.api.metatileentity.implementations.integratedfluid.machine.IFNMachineProcess;

public final class HeatPumpMachineProcessState {

    public static final String NBT_KEY = "ifnMachineProcess";

    private IFNMachineProcess process = IFNMachineProcess.create();

    public IFNMachineProcess process() {
        return process;
    }

    public void save(NBTTagCompound tag) {
        NBTTagCompound processTag = new NBTTagCompound();
        process.writeToNBT(processTag);
        tag.setTag(NBT_KEY, processTag);
    }

    public void load(NBTTagCompound tag) {
        if (tag != null && tag.hasKey(NBT_KEY)) {
            process = IFNMachineProcess.readFromNBT(tag.getCompoundTag(NBT_KEY));
        }
    }
}
