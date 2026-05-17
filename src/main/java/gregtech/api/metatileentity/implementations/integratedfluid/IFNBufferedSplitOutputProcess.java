package gregtech.api.metatileentity.implementations.integratedfluid;

import net.minecraftforge.fluids.Fluid;

import gregtech.api.metatileentity.implementations.integratedfluid.machine.IFNBatchState;
import gregtech.api.metatileentity.implementations.integratedfluid.machine.IFNMachineOutputBuffer;
import gregtech.api.metatileentity.implementations.integratedfluid.machine.IFNTransferPlan;

public final class IFNBufferedSplitOutputProcess {

    private IFNBufferedSplitOutputProcess() {}

    public static Result execute(Request request) {
        if (request == null || !request.isValid()) {
            return Result.failure(IFNMachineProcessStatus.INVALID_REQUEST);
        }
        if (!IFNNetworkTransferGate.isOperational(request.inputNetwork)) {
            return Result.failure(IFNMachineProcessStatus.INPUT_BLOCKED);
        }
        long amountToProcessQ = request.requestedAmountQ;
        IFNMachineBatchPlanner.SplitAmounts split = IFNMachineBatchPlanner.computeSplitAmounts(
            amountToProcessQ,
            request.splitRatio
        );
        if (!split.isValid()) {
            return Result.failure(IFNMachineProcessStatus.INVALID_REQUEST);
        }

        double firstSpecificEnthalpy = request.firstOutputStateProvider
            .getOutputSpecificEnthalpy(split.firstAmountQ());
        double secondSpecificEnthalpy = request.secondOutputStateProvider
            .getOutputSpecificEnthalpy(split.secondAmountQ());

        IntegratedFluidNetwork.ExtractedPayload extracted = request.inputNetwork.extractProportional(
            amountToProcessQ,
            false
        );
        if (extracted.amountQ <= 0L) {
            return Result.failure(IFNMachineProcessStatus.NO_INPUT);
        }

        split = IFNMachineBatchPlanner.computeSplitAmounts(extracted.amountQ, request.splitRatio);
        if (!split.isValid()) {
            request.inputNetwork.addState(request.firstFluid, extracted.amountQ, extracted.enthalpyQ);
            return Result.failure(IFNMachineProcessStatus.NO_INPUT);
        }

        firstSpecificEnthalpy = request.firstOutputStateProvider.getOutputSpecificEnthalpy(split.firstAmountQ());
        secondSpecificEnthalpy = request.secondOutputStateProvider.getOutputSpecificEnthalpy(split.secondAmountQ());

        IFNBatchState firstOutputBatch = IFNBatchState.of(
            request.firstFluid.getName(),
            split.firstAmountQ(),
            firstSpecificEnthalpy,
            Math.max(request.firstOutputNetwork.getPressure(), request.secondOutputNetwork.getPressure()));
        IFNBatchState secondOutputBatch = IFNBatchState.of(
            request.secondFluid.getName(),
            split.secondAmountQ(),
            secondSpecificEnthalpy,
            Math.max(request.firstOutputNetwork.getPressure(), request.secondOutputNetwork.getPressure()));

        if (!canAcceptPendingOutput(request.firstOutputBuffer, request.firstOutputPortName, firstOutputBatch)
            || !canAcceptPendingOutput(request.secondOutputBuffer, request.secondOutputPortName, secondOutputBatch)) {
            request.inputNetwork.addState(request.firstFluid, extracted.amountQ, extracted.enthalpyQ);
            return Result.failure(IFNMachineProcessStatus.OUTPUT_BLOCKED);
        }

        IFNTransferPlan firstTransfer = request.firstOutputBuffer.addPendingOutput(
            request.firstOutputPortName,
            firstOutputBatch);
        IFNTransferPlan secondTransfer = request.secondOutputBuffer.addPendingOutput(
            request.secondOutputPortName,
            secondOutputBatch);
        if (firstTransfer.status() != IFNTransferPlan.Status.ACCEPTED
            || secondTransfer.status() != IFNTransferPlan.Status.ACCEPTED) {
            request.inputNetwork.addState(request.firstFluid, extracted.amountQ, extracted.enthalpyQ);
            return Result.failure(IFNMachineProcessStatus.OUTPUT_BLOCKED);
        }

        return Result.success(
            extracted.amountQ,
            split.firstAmountQ(),
            split.secondAmountQ(),
            firstSpecificEnthalpy,
            secondSpecificEnthalpy,
            request.inputNetwork.getPressure(),
            Math.max(request.firstOutputNetwork.getPressure(), request.secondOutputNetwork.getPressure()));
    }

    private static boolean canAcceptPendingOutput(IFNMachineOutputBuffer buffer, String portName, IFNBatchState batch) {
        return !buffer.pendingOutput(portName).mergeWith(batch).isEmpty();
    }

    @FunctionalInterface
    public interface OutputStateProvider {

        double getOutputSpecificEnthalpy(long amountQ);
    }

    public static final class Request {

        private final IntegratedFluidNetwork inputNetwork;
        private final IntegratedFluidNetwork firstOutputNetwork;
        private final IFNMachineOutputBuffer firstOutputBuffer;
        private final String firstOutputPortName;
        private final Fluid firstFluid;
        private final IntegratedFluidNetwork secondOutputNetwork;
        private final IFNMachineOutputBuffer secondOutputBuffer;
        private final String secondOutputPortName;
        private final Fluid secondFluid;
        private final long requestedAmountQ;
        private final double splitRatio;
        private final OutputStateProvider firstOutputStateProvider;
        private final OutputStateProvider secondOutputStateProvider;
        private final float maxOutputToInputPressureRatio;

        private Request(
            IntegratedFluidNetwork inputNetwork,
            IntegratedFluidNetwork firstOutputNetwork,
            IFNMachineOutputBuffer firstOutputBuffer,
            String firstOutputPortName,
            Fluid firstFluid,
            IntegratedFluidNetwork secondOutputNetwork,
            IFNMachineOutputBuffer secondOutputBuffer,
            String secondOutputPortName,
            Fluid secondFluid,
            long requestedAmountQ,
            double splitRatio,
            OutputStateProvider firstOutputStateProvider,
            OutputStateProvider secondOutputStateProvider,
            float maxOutputToInputPressureRatio) {
            this.inputNetwork = inputNetwork;
            this.firstOutputNetwork = firstOutputNetwork;
            this.firstOutputBuffer = firstOutputBuffer;
            this.firstOutputPortName = firstOutputPortName;
            this.firstFluid = firstFluid;
            this.secondOutputNetwork = secondOutputNetwork;
            this.secondOutputBuffer = secondOutputBuffer;
            this.secondOutputPortName = secondOutputPortName;
            this.secondFluid = secondFluid;
            this.requestedAmountQ = requestedAmountQ;
            this.splitRatio = splitRatio;
            this.firstOutputStateProvider = firstOutputStateProvider;
            this.secondOutputStateProvider = secondOutputStateProvider;
            this.maxOutputToInputPressureRatio = maxOutputToInputPressureRatio;
        }

        public static Request of(
            IntegratedFluidNetwork inputNetwork,
            IntegratedFluidNetwork firstOutputNetwork,
            IFNMachineOutputBuffer firstOutputBuffer,
            String firstOutputPortName,
            Fluid firstFluid,
            IntegratedFluidNetwork secondOutputNetwork,
            IFNMachineOutputBuffer secondOutputBuffer,
            String secondOutputPortName,
            Fluid secondFluid,
            long requestedAmountQ,
            double splitRatio,
            OutputStateProvider firstOutputStateProvider,
            OutputStateProvider secondOutputStateProvider,
            float maxOutputToInputPressureRatio) {
            return new Request(
                inputNetwork,
                firstOutputNetwork,
                firstOutputBuffer,
                firstOutputPortName,
                firstFluid,
                secondOutputNetwork,
                secondOutputBuffer,
                secondOutputPortName,
                secondFluid,
                requestedAmountQ,
                splitRatio,
                firstOutputStateProvider,
                secondOutputStateProvider,
                maxOutputToInputPressureRatio);
        }

        private boolean isValid() {
            return inputNetwork != null
                && firstOutputNetwork != null
                && firstOutputBuffer != null
                && firstOutputPortName != null
                && !firstOutputPortName.trim().isEmpty()
                && firstFluid != null
                && secondOutputNetwork != null
                && secondOutputBuffer != null
                && secondOutputPortName != null
                && !secondOutputPortName.trim().isEmpty()
                && secondFluid != null
                && requestedAmountQ >= IntegratedFluidNetwork.AMOUNT_SCALE
                && splitRatio > 0.0d
                && splitRatio < 1.0d
                && firstOutputStateProvider != null
                && secondOutputStateProvider != null
                && maxOutputToInputPressureRatio > 0.0f;
        }
    }

    public static final class Result {

        private final IFNMachineProcessStatus status;
        private final long amountQ;
        private final long firstAmountQ;
        private final long secondAmountQ;
        private final double firstOutputSpecificEnthalpy;
        private final double secondOutputSpecificEnthalpy;
        private final float predictedInputPressureBar;
        private final float predictedOutputPressureBar;

        private Result(IFNMachineProcessStatus status, long amountQ, long firstAmountQ, long secondAmountQ,
            double firstOutputSpecificEnthalpy, double secondOutputSpecificEnthalpy, float predictedInputPressureBar,
            float predictedOutputPressureBar) {
            this.status = status;
            this.amountQ = amountQ;
            this.firstAmountQ = firstAmountQ;
            this.secondAmountQ = secondAmountQ;
            this.firstOutputSpecificEnthalpy = firstOutputSpecificEnthalpy;
            this.secondOutputSpecificEnthalpy = secondOutputSpecificEnthalpy;
            this.predictedInputPressureBar = predictedInputPressureBar;
            this.predictedOutputPressureBar = predictedOutputPressureBar;
        }

        private static Result success(long amountQ, long firstAmountQ, long secondAmountQ,
            double firstOutputSpecificEnthalpy, double secondOutputSpecificEnthalpy, float predictedInputPressureBar,
            float predictedOutputPressureBar) {
            return new Result(
                IFNMachineProcessStatus.SUCCESS,
                amountQ,
                firstAmountQ,
                secondAmountQ,
                firstOutputSpecificEnthalpy,
                secondOutputSpecificEnthalpy,
                predictedInputPressureBar,
                predictedOutputPressureBar);
        }

        private static Result failure(IFNMachineProcessStatus status) {
            return new Result(status, 0L, 0L, 0L, 0.0d, 0.0d, 0.0f, 0.0f);
        }

        public IFNMachineProcessStatus getStatus() {
            return status;
        }

        public long getAmountQ() {
            return amountQ;
        }

        public long getFirstAmountQ() {
            return firstAmountQ;
        }

        public long getSecondAmountQ() {
            return secondAmountQ;
        }

        public double getFirstOutputSpecificEnthalpy() {
            return firstOutputSpecificEnthalpy;
        }

        public double getSecondOutputSpecificEnthalpy() {
            return secondOutputSpecificEnthalpy;
        }

        public float getPredictedInputPressureBar() {
            return predictedInputPressureBar;
        }

        public float getPredictedOutputPressureBar() {
            return predictedOutputPressureBar;
        }
    }
}
