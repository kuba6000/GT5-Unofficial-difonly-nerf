package gregtech.api.metatileentity.implementations.integratedfluid.machine;

public final class IFNTransferPlan {

    private final Status status;
    private final IFNBatchState batch;

    private IFNTransferPlan(Status status, IFNBatchState batch) {
        this.status = status;
        this.batch = batch == null ? IFNBatchState.empty() : batch;
    }

    public static IFNTransferPlan accepted(IFNBatchState batch) {
        return new IFNTransferPlan(Status.ACCEPTED, batch);
    }

    public static IFNTransferPlan rejected(Status status) {
        return new IFNTransferPlan(status, IFNBatchState.empty());
    }

    public Status status() {
        return status;
    }

    public IFNBatchState batch() {
        return batch;
    }

    public enum Status {
        ACCEPTED,
        ACTIVE_BATCH_PRESENT,
        OUTPUT_PENDING,
        DISABLED,
        INVALID_REQUEST,
        NOTHING_TO_TRANSFER
    }
}
