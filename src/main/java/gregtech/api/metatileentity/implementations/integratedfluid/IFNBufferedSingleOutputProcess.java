package gregtech.api.metatileentity.implementations.integratedfluid;

import net.minecraftforge.fluids.Fluid;

import gregtech.api.metatileentity.implementations.integratedfluid.machine.IFNBatchState;
import gregtech.api.metatileentity.implementations.integratedfluid.machine.IFNMachineOutputBuffer;
import gregtech.api.metatileentity.implementations.integratedfluid.machine.IFNTransferPlan;

public final class IFNBufferedSingleOutputProcess {

    private static final int OUTPUT_STATE_CORRECTION_PASSES = 4;

    private IFNBufferedSingleOutputProcess() {}

    public static Result execute(Request request) {
        if (request == null || !request.isValid()) {
            return Result.failure(IFNMachineProcessStatus.INVALID_REQUEST);
        }
        if (!IFNNetworkTransferGate.isOperational(request.inputNetwork)) {
            return Result.failure(IFNMachineProcessStatus.INPUT_BLOCKED);
        }
        if (!IFNNetworkTransferGate.isOperational(request.outputNetwork)) {
            return Result.failure(IFNMachineProcessStatus.OUTPUT_BLOCKED);
        }

        long amountToProcessQ = request.requestedAmountQ;
        double outputSpecificEnthalpy = request.outputStateProvider.getOutputSpecificEnthalpy(amountToProcessQ);
        IFNStateTransferPlanner.PlannedStateTransfer plan = IFNStateTransferPlanner.PlannedStateTransfer.empty();

        if (request.outputNetwork != request.inputNetwork) {
            long plannedAmountQ = amountToProcessQ;
            for (int pass = 0; pass < OUTPUT_STATE_CORRECTION_PASSES; pass++) {
                outputSpecificEnthalpy = request.outputStateProvider.getOutputSpecificEnthalpy(plannedAmountQ);
                plan = IFNStateTransferPlanner.planSingleOutputStateAdd(
                    request.inputNetwork,
                    request.outputNetwork,
                    request.fluid,
                    outputSpecificEnthalpy,
                    plannedAmountQ,
                    request.maxOutputToInputPressureRatio);
                if (plan.acceptedAmountQ < IntegratedFluidNetwork.AMOUNT_SCALE) {
                    return Result.failure(IFNMachineProcessStatus.OUTPUT_BLOCKED);
                }
                amountToProcessQ = plan.acceptedAmountQ;
                if (plan.acceptedAmountQ >= plannedAmountQ) {
                    break;
                }
                plannedAmountQ = plan.acceptedAmountQ;
            }
        }

        IntegratedFluidNetwork.ExtractedPayload extracted = request.inputNetwork.extractProportional(amountToProcessQ, false);
        if (extracted.amountQ <= 0L) {
            return Result.failure(IFNMachineProcessStatus.NO_INPUT);
        }

        outputSpecificEnthalpy = request.outputStateProvider.getOutputSpecificEnthalpy(extracted.amountQ);
        IFNBatchState outputBatch = IFNBatchState.of(
            request.fluid.getName(),
            extracted.amountQ,
            outputSpecificEnthalpy,
            request.outputNetwork == request.inputNetwork ? request.inputNetwork.getPressure() : plan.predictedOutputPressure);
        IFNTransferPlan transfer = request.outputBuffer.addPendingOutput(request.outputPortName, outputBatch);
        if (transfer.status() != IFNTransferPlan.Status.ACCEPTED) {
            request.inputNetwork.addState(request.fluid, extracted.amountQ, extracted.enthalpyQ);
            return Result.failure(IFNMachineProcessStatus.OUTPUT_BLOCKED);
        }

        if (request.outputNetwork == request.inputNetwork) {
            return Result.success(
                extracted.amountQ,
                outputSpecificEnthalpy,
                request.inputNetwork.getPressure(),
                request.outputNetwork.getPressure());
        }

        return Result.success(
            extracted.amountQ,
            outputSpecificEnthalpy,
            plan.predictedInputPressure,
            plan.predictedOutputPressure);
    }

    @FunctionalInterface
    public interface OutputStateProvider {

        double getOutputSpecificEnthalpy(long amountQ);
    }

    public static final class Request {

        private final IntegratedFluidNetwork inputNetwork;
        private final IntegratedFluidNetwork outputNetwork;
        private final IFNMachineOutputBuffer outputBuffer;
        private final String outputPortName;
        private final Fluid fluid;
        private final long requestedAmountQ;
        private final OutputStateProvider outputStateProvider;
        private final float maxOutputToInputPressureRatio;

        private Request(
            IntegratedFluidNetwork inputNetwork,
            IntegratedFluidNetwork outputNetwork,
            IFNMachineOutputBuffer outputBuffer,
            String outputPortName,
            Fluid fluid,
            long requestedAmountQ,
            OutputStateProvider outputStateProvider,
            float maxOutputToInputPressureRatio) {
            this.inputNetwork = inputNetwork;
            this.outputNetwork = outputNetwork;
            this.outputBuffer = outputBuffer;
            this.outputPortName = outputPortName;
            this.fluid = fluid;
            this.requestedAmountQ = requestedAmountQ;
            this.outputStateProvider = outputStateProvider;
            this.maxOutputToInputPressureRatio = maxOutputToInputPressureRatio;
        }

        public static Request of(
            IntegratedFluidNetwork inputNetwork,
            IntegratedFluidNetwork outputNetwork,
            IFNMachineOutputBuffer outputBuffer,
            String outputPortName,
            Fluid fluid,
            long requestedAmountQ,
            OutputStateProvider outputStateProvider,
            float maxOutputToInputPressureRatio) {
            return new Request(
                inputNetwork,
                outputNetwork,
                outputBuffer,
                outputPortName,
                fluid,
                requestedAmountQ,
                outputStateProvider,
                maxOutputToInputPressureRatio);
        }

        private boolean isValid() {
            return inputNetwork != null
                && outputNetwork != null
                && outputBuffer != null
                && outputPortName != null
                && !outputPortName.trim().isEmpty()
                && fluid != null
                && requestedAmountQ >= IntegratedFluidNetwork.AMOUNT_SCALE
                && outputStateProvider != null
                && maxOutputToInputPressureRatio > 0.0f;
        }
    }

    public static final class Result {

        private final IFNMachineProcessStatus status;
        private final long amountQ;
        private final double outputSpecificEnthalpy;
        private final float predictedInputPressureBar;
        private final float predictedOutputPressureBar;

        private Result(IFNMachineProcessStatus status, long amountQ, double outputSpecificEnthalpy,
            float predictedInputPressureBar, float predictedOutputPressureBar) {
            this.status = status;
            this.amountQ = amountQ;
            this.outputSpecificEnthalpy = outputSpecificEnthalpy;
            this.predictedInputPressureBar = predictedInputPressureBar;
            this.predictedOutputPressureBar = predictedOutputPressureBar;
        }

        private static Result success(long amountQ, double outputSpecificEnthalpy, float predictedInputPressureBar,
            float predictedOutputPressureBar) {
            return new Result(
                IFNMachineProcessStatus.SUCCESS,
                amountQ,
                outputSpecificEnthalpy,
                predictedInputPressureBar,
                predictedOutputPressureBar);
        }

        private static Result failure(IFNMachineProcessStatus status) {
            return new Result(status, 0L, 0.0d, 0.0f, 0.0f);
        }

        public IFNMachineProcessStatus getStatus() {
            return status;
        }

        public long getAmountQ() {
            return amountQ;
        }

        public double getOutputSpecificEnthalpy() {
            return outputSpecificEnthalpy;
        }

        public float getPredictedInputPressureBar() {
            return predictedInputPressureBar;
        }

        public float getPredictedOutputPressureBar() {
            return predictedOutputPressureBar;
        }
    }
}
