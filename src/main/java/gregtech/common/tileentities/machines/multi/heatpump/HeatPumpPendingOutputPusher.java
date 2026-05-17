package gregtech.common.tileentities.machines.multi.heatpump;

import net.minecraftforge.fluids.Fluid;

import gregtech.api.metatileentity.implementations.integratedfluid.IFNMachineProcessStatus;
import gregtech.api.metatileentity.implementations.integratedfluid.IntegratedFluidNetwork;
import gregtech.api.metatileentity.implementations.integratedfluid.fluid.IFNFluidRegistry;
import gregtech.api.metatileentity.implementations.integratedfluid.machine.IFNBatchState;
import gregtech.api.metatileentity.implementations.integratedfluid.machine.IFNMachineProcess;
import gregtech.api.metatileentity.implementations.integratedfluid.machine.IFNTransferPlan;

public final class HeatPumpPendingOutputPusher {

    private HeatPumpPendingOutputPusher() {}

    public static Result push(IFNMachineProcess process, IntegratedFluidNetwork outputNetwork, Fluid outputFluid,
        long maxAmountQ) {
        if (process == null || outputNetwork == null || outputFluid == null || maxAmountQ <= 0L) {
            return Result.blocked(IFNMachineProcessStatus.INVALID_REQUEST);
        }
        IFNBatchState pending = process.pendingOutput();
        if (pending.isEmpty()) {
            return Result.nothingPending();
        }
        if (!IFNFluidRegistry.isSameSubstance(pending.fluidName(), outputFluid.getName())) {
            return Result.blocked(IFNMachineProcessStatus.OUTPUT_BLOCKED);
        }

        long requestedAmountQ = Math.min(maxAmountQ, pending.amountQ());
        long maxAddableQ = outputNetwork.getMaxAddableAmountQ(outputFluid, pending.specificEnthalpy(), requestedAmountQ);
        long amountQ = Math.min(requestedAmountQ, maxAddableQ);
        if (amountQ < IntegratedFluidNetwork.AMOUNT_SCALE) {
            return Result.blocked(IFNMachineProcessStatus.OUTPUT_BLOCKED);
        }

        IFNTransferPlan drained = process.drainPendingOutput(amountQ);
        if (drained.status() != IFNTransferPlan.Status.ACCEPTED) {
            return Result.blocked(IFNMachineProcessStatus.OUTPUT_BLOCKED);
        }

        IFNBatchState batch = drained.batch();
        long enthalpyQ = IntegratedFluidNetwork.toEnthalpyQFromSpecific(batch.specificEnthalpy(), batch.amountQ());
        if (!outputNetwork.addState(outputFluid, batch.amountQ(), enthalpyQ)) {
            process.requeuePendingOutput(batch);
            return Result.blocked(IFNMachineProcessStatus.OUTPUT_BLOCKED);
        }
        return Result.pushed(batch.amountQ(), process.hasPendingOutput());
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

        public static Result pushed(long pushedAmountQ, boolean outputStillPending) {
            return new Result(IFNMachineProcessStatus.SUCCESS, pushedAmountQ, outputStillPending);
        }

        public static Result blocked(IFNMachineProcessStatus status) {
            return new Result(status, 0L, true);
        }

        public static Result nothingPending() {
            return new Result(IFNMachineProcessStatus.NO_INPUT, 0L, false);
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
