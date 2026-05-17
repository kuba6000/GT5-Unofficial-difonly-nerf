package gregtech.api.metatileentity.implementations.integratedfluid;

import net.minecraftforge.fluids.Fluid;

/**
 * Shared process primitive for machines that move one IFN state from one input network to one output network.
 *
 * Machines provide the desired output enthalpy as a function of accepted amount. This keeps pressure planning and
 * mutation order centralized while leaving machine-specific thermodynamics outside this class.
 */
public final class IFNSingleOutputProcess {

    private static final int OUTPUT_STATE_CORRECTION_PASSES = 3;

    private IFNSingleOutputProcess() {}

    public static Result execute(Request request) {
        if (request == null || !request.isValid()) {
            return Result.failure(Status.INVALID_REQUEST);
        }
        if (!IFNNetworkTransferGate.isOperational(request.inputNetwork)) {
            return Result.failure(Status.INPUT_BLOCKED);
        }
        if (!IFNNetworkTransferGate.isOperational(request.outputNetwork)) {
            return Result.failure(Status.OUTPUT_BLOCKED);
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
                    return Result.failure(Status.OUTPUT_BLOCKED);
                }
                amountToProcessQ = plan.acceptedAmountQ;
                if (plan.acceptedAmountQ >= plannedAmountQ) {
                    break;
                }
                plannedAmountQ = plan.acceptedAmountQ;
            }
        }

        IntegratedFluidNetwork.ExtractedPayload extracted =
            request.inputNetwork.extractProportional(amountToProcessQ, false);
        if (extracted.amountQ <= 0L) {
            return Result.failure(Status.NO_INPUT);
        }

        outputSpecificEnthalpy = request.outputStateProvider.getOutputSpecificEnthalpy(extracted.amountQ);
        long outputEnthalpyQ = IntegratedFluidNetwork.toEnthalpyQFromSpecific(outputSpecificEnthalpy, extracted.amountQ);
        if (!request.outputNetwork.addState(request.fluid, extracted.amountQ, outputEnthalpyQ)) {
            request.inputNetwork.addState(request.fluid, extracted.amountQ, extracted.enthalpyQ);
            return Result.failure(Status.OUTPUT_BLOCKED);
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

    public enum Status {
        SUCCESS,
        INVALID_REQUEST,
        INPUT_BLOCKED,
        NO_INPUT,
        OUTPUT_BLOCKED
    }

    @FunctionalInterface
    public interface OutputStateProvider {

        double getOutputSpecificEnthalpy(long amountQ);
    }

    public static final class Request {

        private final IntegratedFluidNetwork inputNetwork;
        private final IntegratedFluidNetwork outputNetwork;
        private final Fluid fluid;
        private final long requestedAmountQ;
        private final OutputStateProvider outputStateProvider;
        private final float maxOutputToInputPressureRatio;

        private Request(IntegratedFluidNetwork inputNetwork, IntegratedFluidNetwork outputNetwork, Fluid fluid,
            long requestedAmountQ, OutputStateProvider outputStateProvider, float maxOutputToInputPressureRatio) {
            this.inputNetwork = inputNetwork;
            this.outputNetwork = outputNetwork;
            this.fluid = fluid;
            this.requestedAmountQ = requestedAmountQ;
            this.outputStateProvider = outputStateProvider;
            this.maxOutputToInputPressureRatio = maxOutputToInputPressureRatio;
        }

        public static Request of(IntegratedFluidNetwork inputNetwork, IntegratedFluidNetwork outputNetwork, Fluid fluid,
            long requestedAmountQ, OutputStateProvider outputStateProvider, float maxOutputToInputPressureRatio) {
            return new Request(
                inputNetwork,
                outputNetwork,
                fluid,
                requestedAmountQ,
                outputStateProvider,
                maxOutputToInputPressureRatio);
        }

        private boolean isValid() {
            return inputNetwork != null
                && outputNetwork != null
                && fluid != null
                && requestedAmountQ >= IntegratedFluidNetwork.AMOUNT_SCALE
                && outputStateProvider != null
                && maxOutputToInputPressureRatio > 0.0f;
        }
    }

    public static final class Result {

        private final Status status;
        private final long amountQ;
        private final double outputSpecificEnthalpy;
        private final float predictedInputPressureBar;
        private final float predictedOutputPressureBar;

        private Result(Status status, long amountQ, double outputSpecificEnthalpy, float predictedInputPressureBar,
            float predictedOutputPressureBar) {
            this.status = status;
            this.amountQ = amountQ;
            this.outputSpecificEnthalpy = outputSpecificEnthalpy;
            this.predictedInputPressureBar = predictedInputPressureBar;
            this.predictedOutputPressureBar = predictedOutputPressureBar;
        }

        private static Result success(long amountQ, double outputSpecificEnthalpy, float predictedInputPressureBar,
            float predictedOutputPressureBar) {
            return new Result(
                Status.SUCCESS,
                amountQ,
                outputSpecificEnthalpy,
                predictedInputPressureBar,
                predictedOutputPressureBar);
        }

        private static Result failure(Status status) {
            return new Result(status, 0L, 0.0d, 0.0f, 0.0f);
        }

        public Status getStatus() {
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
