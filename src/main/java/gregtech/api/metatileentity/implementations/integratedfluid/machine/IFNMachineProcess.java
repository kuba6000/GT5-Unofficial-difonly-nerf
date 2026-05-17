package gregtech.api.metatileentity.implementations.integratedfluid.machine;

import net.minecraft.nbt.NBTTagCompound;

public final class IFNMachineProcess {

    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_ACTIVE_BATCH = "activeBatch";
    private static final String KEY_PENDING_OUTPUT = "pendingOutput";

    private boolean enabled = true;
    private IFNBatchState activeBatch = IFNBatchState.empty();
    private IFNBatchState pendingOutput = IFNBatchState.empty();

    private IFNMachineProcess() {}

    public static IFNMachineProcess create() {
        return new IFNMachineProcess();
    }

    public IFNTransferPlan start(IFNBatchState batch) {
        if (!enabled) {
            return IFNTransferPlan.rejected(IFNTransferPlan.Status.DISABLED);
        }
        if (hasPendingOutput()) {
            return IFNTransferPlan.rejected(IFNTransferPlan.Status.OUTPUT_PENDING);
        }
        if (hasActiveBatch()) {
            return IFNTransferPlan.rejected(IFNTransferPlan.Status.ACTIVE_BATCH_PRESENT);
        }
        if (batch == null || batch.isEmpty()) {
            return IFNTransferPlan.rejected(IFNTransferPlan.Status.INVALID_REQUEST);
        }
        activeBatch = batch;
        return IFNTransferPlan.accepted(batch);
    }

    public IFNTransferPlan finish(IFNBatchState output) {
        if (!enabled) {
            return IFNTransferPlan.rejected(IFNTransferPlan.Status.DISABLED);
        }
        if (!hasActiveBatch() || output == null || output.isEmpty()) {
            return IFNTransferPlan.rejected(IFNTransferPlan.Status.INVALID_REQUEST);
        }
        activeBatch = IFNBatchState.empty();
        pendingOutput = output;
        return IFNTransferPlan.accepted(output);
    }

    public IFNTransferPlan drainPendingOutput(long maxAmountQ) {
        if (!enabled) {
            return IFNTransferPlan.rejected(IFNTransferPlan.Status.DISABLED);
        }
        if (!hasPendingOutput() || maxAmountQ <= 0L) {
            return IFNTransferPlan.rejected(IFNTransferPlan.Status.NOTHING_TO_TRANSFER);
        }

        long drainedAmountQ = Math.min(maxAmountQ, pendingOutput.amountQ());
        IFNBatchState drained = pendingOutput.withAmountQ(drainedAmountQ);
        pendingOutput = pendingOutput.withAmountQ(pendingOutput.amountQ() - drainedAmountQ);
        return IFNTransferPlan.accepted(drained);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean hasActiveBatch() {
        return !activeBatch.isEmpty();
    }

    public IFNBatchState activeBatch() {
        return activeBatch;
    }

    public boolean hasPendingOutput() {
        return !pendingOutput.isEmpty();
    }

    public IFNBatchState pendingOutput() {
        return pendingOutput;
    }

    public void writeToNBT(NBTTagCompound tag) {
        tag.setBoolean(KEY_ENABLED, enabled);
        NBTTagCompound activeTag = new NBTTagCompound();
        activeBatch.writeToNBT(activeTag);
        tag.setTag(KEY_ACTIVE_BATCH, activeTag);
        NBTTagCompound pendingTag = new NBTTagCompound();
        pendingOutput.writeToNBT(pendingTag);
        tag.setTag(KEY_PENDING_OUTPUT, pendingTag);
    }

    public static IFNMachineProcess readFromNBT(NBTTagCompound tag) {
        IFNMachineProcess process = create();
        if (tag == null) {
            return process;
        }
        process.enabled = tag.getBoolean(KEY_ENABLED);
        process.activeBatch = IFNBatchState.readFromNBT(tag.getCompoundTag(KEY_ACTIVE_BATCH));
        process.pendingOutput = IFNBatchState.readFromNBT(tag.getCompoundTag(KEY_PENDING_OUTPUT));
        return process;
    }
}
