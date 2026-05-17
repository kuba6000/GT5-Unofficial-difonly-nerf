package gregtech.api.metatileentity.implementations.integratedfluid.machine;

import net.minecraftforge.fluids.Fluid;

import gregtech.api.metatileentity.implementations.integratedfluid.IFNMachineProcessStatus;
import gregtech.api.metatileentity.implementations.integratedfluid.IntegratedFluidNetwork;
import gregtech.api.metatileentity.implementations.integratedfluid.fluid.IFNFluidRegistry;

public final class IFNMachineOutputBufferPusher {

    private IFNMachineOutputBufferPusher() {}

    public static Result push(IFNMachineOutputBuffer buffer, String portName, IntegratedFluidNetwork outputNetwork,
        Fluid outputFluid, long maxAmountQ) {
        if (buffer == null || portName == null || outputNetwork == null || outputFluid == null || maxAmountQ <= 0L) {
            return Result.blocked(IFNMachineProcessStatus.INVALID_REQUEST, buffer);
        }
        IFNBatchState pending = buffer.pendingOutput(portName);
        if (pending.isEmpty()) {
            return Result.nothingPending(buffer);
        }
        if (!IFNFluidRegistry.isSameSubstance(pending.fluidName(), outputFluid.getName())) {
            return Result.blocked(IFNMachineProcessStatus.OUTPUT_BLOCKED, buffer);
        }

        long requestedAmountQ = Math.min(maxAmountQ, pending.amountQ());
        long maxAddableQ = outputNetwork.getMaxAddableAmountQ(outputFluid, pending.specificEnthalpy(), requestedAmountQ);
        long amountQ = Math.min(requestedAmountQ, maxAddableQ);
        if (amountQ < IntegratedFluidNetwork.AMOUNT_SCALE) {
            return Result.blocked(IFNMachineProcessStatus.OUTPUT_BLOCKED, buffer);
        }

        IFNTransferPlan drained = buffer.drainPendingOutput(portName, amountQ);
        if (drained.status() != IFNTransferPlan.Status.ACCEPTED) {
            return Result.blocked(IFNMachineProcessStatus.OUTPUT_BLOCKED, buffer);
        }

        IFNBatchState batch = drained.batch();
        long enthalpyQ = IntegratedFluidNetwork.toEnthalpyQFromSpecific(batch.specificEnthalpy(), batch.amountQ());
        if (!outputNetwork.addState(outputFluid, batch.amountQ(), enthalpyQ)) {
            buffer.addPendingOutput(portName, batch);
            return Result.blocked(IFNMachineProcessStatus.OUTPUT_BLOCKED, buffer);
        }
        return Result.pushed(batch.amountQ(), buffer);
    }

    public static final class Result {

        private final IFNMachineProcessStatus status;
        private final long pushedAmountQ;
        private final boolean outputStillPending;

        private Result(IFNMachineProcessStatus status, long pushedAmountQ, boolean outputStillPending) {
            this.status = status;
            this.pushedAmountQ = pushedAmountQ;
            this.outputStillPending = outputStillPending;
        }

        private static Result pushed(long pushedAmountQ, IFNMachineOutputBuffer buffer) {
            return new Result(IFNMachineProcessStatus.SUCCESS, pushedAmountQ, buffer != null && buffer.hasPendingOutput());
        }

        private static Result blocked(IFNMachineProcessStatus status, IFNMachineOutputBuffer buffer) {
            return new Result(status, 0L, buffer != null && buffer.hasPendingOutput());
        }

        private static Result nothingPending(IFNMachineOutputBuffer buffer) {
            return new Result(IFNMachineProcessStatus.NO_INPUT, 0L, buffer != null && buffer.hasPendingOutput());
        }

        public IFNMachineProcessStatus status() {
            return status;
        }

        public long pushedAmountQ() {
            return pushedAmountQ;
        }

        public boolean outputStillPending() {
            return outputStillPending;
        }
    }
}
