package gregtech.api.metatileentity.implementations.integratedfluid.machine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;

public final class IFNMachineOutputBuffer {

    private static final String KEY_PORTS = "ports";

    private final Map<String, IFNBatchState> pendingOutputs = new LinkedHashMap<>();

    public boolean hasPendingOutput() {
        return pendingOutputs.values()
            .stream()
            .anyMatch(batch -> batch != null && !batch.isEmpty());
    }

    public boolean hasPendingOutput(String portName) {
        return !pendingOutput(portName).isEmpty();
    }

    public IFNBatchState pendingOutput(String portName) {
        if (portName == null) {
            return IFNBatchState.empty();
        }
        IFNBatchState batch = pendingOutputs.get(portName);
        return batch == null ? IFNBatchState.empty() : batch;
    }

    public Map<String, IFNBatchState> pendingOutputs() {
        return Collections.unmodifiableMap(pendingOutputs);
    }

    public IFNTransferPlan addPendingOutput(String portName, IFNBatchState batch) {
        if (portName == null || portName.trim().isEmpty() || batch == null || batch.isEmpty()) {
            return IFNTransferPlan.rejected(IFNTransferPlan.Status.INVALID_REQUEST);
        }
        IFNBatchState merged = pendingOutput(portName).mergeWith(batch);
        if (merged.isEmpty()) {
            return IFNTransferPlan.rejected(IFNTransferPlan.Status.INVALID_REQUEST);
        }
        pendingOutputs.put(portName, merged);
        return IFNTransferPlan.accepted(merged);
    }

    public IFNTransferPlan drainPendingOutput(String portName, long maxAmountQ) {
        IFNBatchState pending = pendingOutput(portName);
        if (pending.isEmpty() || maxAmountQ <= 0L) {
            return IFNTransferPlan.rejected(IFNTransferPlan.Status.NOTHING_TO_TRANSFER);
        }
        long drainedAmountQ = Math.min(maxAmountQ, pending.amountQ());
        IFNBatchState drained = pending.withAmountQ(drainedAmountQ);
        IFNBatchState remaining = pending.withAmountQ(pending.amountQ() - drainedAmountQ);
        if (remaining.isEmpty()) {
            pendingOutputs.remove(portName);
        } else {
            pendingOutputs.put(portName, remaining);
        }
        return IFNTransferPlan.accepted(drained);
    }

    public void writeToNBT(NBTTagCompound tag) {
        NBTTagCompound portsTag = new NBTTagCompound();
        for (Map.Entry<String, IFNBatchState> entry : pendingOutputs.entrySet()) {
            IFNBatchState batch = entry.getValue();
            if (batch == null || batch.isEmpty()) {
                continue;
            }
            NBTTagCompound batchTag = new NBTTagCompound();
            batch.writeToNBT(batchTag);
            portsTag.setTag(entry.getKey(), batchTag);
        }
        tag.setTag(KEY_PORTS, portsTag);
    }

    public static IFNMachineOutputBuffer readFromNBT(NBTTagCompound tag) {
        IFNMachineOutputBuffer buffer = new IFNMachineOutputBuffer();
        if (tag == null || !tag.hasKey(KEY_PORTS)) {
            return buffer;
        }
        NBTTagCompound portsTag = tag.getCompoundTag(KEY_PORTS);
        for (Object keyObject : portsTag.func_150296_c()) {
            String portName = String.valueOf(keyObject);
            IFNBatchState batch = IFNBatchState.readFromNBT(portsTag.getCompoundTag(portName));
            if (!batch.isEmpty()) {
                buffer.pendingOutputs.put(portName, batch);
            }
        }
        return buffer;
    }
}
