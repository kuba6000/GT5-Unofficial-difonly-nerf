package gregtech.common.tileentities.machines.multi.heatpump;

import net.minecraftforge.fluids.Fluid;

import gregtech.api.metatileentity.implementations.integratedfluid.IFNMachineProcessStatus;
import gregtech.api.metatileentity.implementations.integratedfluid.IntegratedFluidNetwork;
import gregtech.api.metatileentity.implementations.integratedfluid.machine.IFNBatchState;
import gregtech.api.metatileentity.implementations.integratedfluid.machine.IFNMachineOutputBuffer;
import gregtech.api.metatileentity.implementations.integratedfluid.machine.IFNMachineOutputBufferPusher;

public final class HeatPumpOutputBufferDrain {

    private HeatPumpOutputBufferDrain() {}

    public static OutputTarget target(String portName, IntegratedFluidNetwork outputNetwork) {
        return new OutputTarget(portName, outputNetwork);
    }

    public static Result push(IFNMachineOutputBuffer buffer, long maxAmountQ, OutputTarget... targets) {
        if (buffer == null || maxAmountQ <= 0L || targets == null) {
            return Result.blocked(IFNMachineProcessStatus.INVALID_REQUEST, buffer);
        }

        long pushedAmountQ = 0L;
        IFNMachineProcessStatus blockedStatus = null;
        for (OutputTarget target : targets) {
            if (target == null || target.portName == null || target.outputNetwork == null) {
                blockedStatus = IFNMachineProcessStatus.INVALID_REQUEST;
                continue;
            }

            IFNBatchState pending = buffer.pendingOutput(target.portName);
            if (pending.isEmpty()) {
                continue;
            }

            Fluid outputFluid = new Fluid(pending.fluidName());
            IFNMachineOutputBufferPusher.Result portResult = IFNMachineOutputBufferPusher.push(
                buffer,
                target.portName,
                target.outputNetwork,
                outputFluid,
                maxAmountQ);
            pushedAmountQ += portResult.pushedAmountQ();
            if (portResult.status() != IFNMachineProcessStatus.SUCCESS
                && portResult.status() != IFNMachineProcessStatus.NO_INPUT) {
                blockedStatus = portResult.status();
            }
        }

        if (blockedStatus != null) {
            return Result.blocked(blockedStatus, buffer);
        }
        if (pushedAmountQ > 0L) {
            return Result.success(pushedAmountQ, buffer);
        }
        if (buffer.hasPendingOutput()) {
            return Result.blocked(IFNMachineProcessStatus.OUTPUT_BLOCKED, buffer);
        }
        return Result.nothingPending();
    }

    public static final class OutputTarget {

        private final String portName;
        private final IntegratedFluidNetwork outputNetwork;

        private OutputTarget(String portName, IntegratedFluidNetwork outputNetwork) {
            this.portName = portName;
            this.outputNetwork = outputNetwork;
        }
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

        private static Result success(long pushedAmountQ, IFNMachineOutputBuffer buffer) {
            return new Result(IFNMachineProcessStatus.SUCCESS, pushedAmountQ, buffer != null && buffer.hasPendingOutput());
        }

        private static Result blocked(IFNMachineProcessStatus status, IFNMachineOutputBuffer buffer) {
            return new Result(status, 0L, buffer != null && buffer.hasPendingOutput());
        }

        private static Result nothingPending() {
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
