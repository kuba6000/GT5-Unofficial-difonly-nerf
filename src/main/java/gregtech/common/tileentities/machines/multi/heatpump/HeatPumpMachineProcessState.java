package gregtech.common.tileentities.machines.multi.heatpump;

import net.minecraft.nbt.NBTTagCompound;

import gregtech.api.metatileentity.implementations.integratedfluid.machine.IFNMachineProcess;
import gregtech.api.metatileentity.implementations.integratedfluid.machine.IFNMachineOutputBuffer;

public final class HeatPumpMachineProcessState {

    public static final String NBT_KEY = "ifnMachineProcess";
    public static final String OUTPUT_BUFFER_NBT_KEY = "ifnMachineOutputBuffer";

    private IFNMachineProcess process = IFNMachineProcess.create();
    private IFNMachineOutputBuffer outputBuffer = new IFNMachineOutputBuffer();

    public IFNMachineProcess process() {
        return process;
    }

    public IFNMachineOutputBuffer outputBuffer() {
        return outputBuffer;
    }

    public void save(NBTTagCompound tag) {
        NBTTagCompound processTag = new NBTTagCompound();
        process.writeToNBT(processTag);
        tag.setTag(NBT_KEY, processTag);
        NBTTagCompound outputBufferTag = new NBTTagCompound();
        outputBuffer.writeToNBT(outputBufferTag);
        tag.setTag(OUTPUT_BUFFER_NBT_KEY, outputBufferTag);
    }

    public void load(NBTTagCompound tag) {
        if (tag != null && tag.hasKey(NBT_KEY)) {
            process = IFNMachineProcess.readFromNBT(tag.getCompoundTag(NBT_KEY));
        }
        if (tag != null && tag.hasKey(OUTPUT_BUFFER_NBT_KEY)) {
            outputBuffer = IFNMachineOutputBuffer.readFromNBT(tag.getCompoundTag(OUTPUT_BUFFER_NBT_KEY));
        }
    }
}
