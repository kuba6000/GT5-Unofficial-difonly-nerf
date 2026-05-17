package gregtech.api.metatileentity.implementations.integratedfluid;

import net.minecraftforge.fluids.Fluid;

/**
 * Shared process primitive for machines that split one IFN input state into two output states.
 */
public final class IFNSplitOutputProcess {

    private IFNSplitOutputProcess() {}

    public static Result execute(Request request) {
        if (request == null || !request.isValid()) {
            return Result.failure(Status.INVALID_REQUEST);
        }
        if (!IFNNetworkTransferGate.isOperational(request.inputNetwork)) {
            return Result.failure(Status.INPUT_BLOCKED);
        }
        if (!IFNNetworkTransferGate.areOperational(new IntegratedFluidNetwork[] {
            request.firstOutputNetwork,
            request.secondOutputNetwork
        })) {
            return Result.failure(Status.OUTPUT_BLOCKED);
        }

        long amountToProcessQ = request.requestedAmountQ;
        IFNMachineBatchPlanner.SplitAmounts split = IFNMachineBatchPlanner.computeSplitAmounts(
            amountToProcessQ,
            request.splitRatio
        );
        if (!split.isValid()) {
            return Result.failure(Status.INVALID_REQUEST);
        }

        double firstSpecificEnthalpy = request.firstOutputStateProvider
            .getOutputSpecificEnthalpy(split.firstAmountQ());
        double secondSpecificEnthalpy = request.secondOutputStateProvider
            .getOutputSpecificEnthalpy(split.secondAmountQ());

        IFNStateTransferPlanner.PlannedSplitTransfer plan = IFNStateTransferPlanner.PlannedSplitTransfer.empty();
        if (request.firstOutputNetwork != request.inputNetwork || request.secondOutputNetwork != request.inputNetwork) {
            plan = IFNStateTransferPlanner.planSplitStateAdd(
                request.inputNetwork,
                request.firstOutputNetwork,
                request.firstFluid,
                firstSpecificEnthalpy,
                request.secondOutputNetwork,
                request.secondFluid,
                secondSpecificEnthalpy,
                amountToProcessQ,
                request.splitRatio,
                request.maxOutputToInputPressureRatio
            );
            if (plan.acceptedTotalAmountQ < IntegratedFluidNetwork.AMOUNT_SCALE) {
                return Result.failure(Status.OUTPUT_BLOCKED);
            }
            amountToProcessQ = plan.acceptedTotalAmountQ;
        }

        IntegratedFluidNetwork.ExtractedPayload extracted = request.inputNetwork.extractProportional(
            amountToProcessQ,
            false
        );
        if (extracted.amountQ <= 0L) {
            return Result.failure(Status.NO_INPUT);
        }

        split = IFNMachineBatchPlanner.computeSplitAmounts(extracted.amountQ, request.splitRatio);
        if (!split.isValid()) {
            request.inputNetwork.addState(request.firstFluid, extracted.amountQ, extracted.enthalpyQ);
            return Result.failure(Status.NO_INPUT);
        }

        firstSpecificEnthalpy = request.firstOutputStateProvider.getOutputSpecificEnthalpy(split.firstAmountQ());
        secondSpecificEnthalpy = request.secondOutputStateProvider.getOutputSpecificEnthalpy(split.secondAmountQ());

        long firstOutputEnthalpyQ = IntegratedFluidNetwork
            .toEnthalpyQFromSpecific(firstSpecificEnthalpy, split.firstAmountQ());
        long secondOutputEnthalpyQ = IntegratedFluidNetwork
            .toEnthalpyQFromSpecific(secondSpecificEnthalpy, split.secondAmountQ());
        long firstExtractedEnthalpyQ = splitEnthalpyQ(extracted.enthalpyQ, split.firstAmountQ(), extracted.amountQ);
        long secondExtractedEnthalpyQ = extracted.enthalpyQ - firstExtractedEnthalpyQ;

        if (!IFNStateMutationApplier.addTwoOutputsOrRestoreInputs(
            request.inputNetwork,
            request.firstOutputNetwork,
            request.firstFluid,
            IntegratedFluidNetwork.ExtractedPayload.of(split.firstAmountQ(), firstExtractedEnthalpyQ),
            firstOutputEnthalpyQ,
            request.inputNetwork,
            request.secondOutputNetwork,
            request.secondFluid,
            IntegratedFluidNetwork.ExtractedPayload.of(split.secondAmountQ(), secondExtractedEnthalpyQ),
            secondOutputEnthalpyQ)) {
            return Result.failure(Status.OUTPUT_BLOCKED);
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

    private static long splitEnthalpyQ(long totalEnthalpyQ, long partAmountQ, long totalAmountQ) {
        if (totalAmountQ <= 0L) {
            return 0L;
        }
        return Math.round(totalEnthalpyQ * (partAmountQ / (double) totalAmountQ));
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
        private final IntegratedFluidNetwork firstOutputNetwork;
        private final Fluid firstFluid;
        private final IntegratedFluidNetwork secondOutputNetwork;
        private final Fluid secondFluid;
        private final long requestedAmountQ;
        private final double splitRatio;
        private final OutputStateProvider firstOutputStateProvider;
        private final OutputStateProvider secondOutputStateProvider;
        private final float maxOutputToInputPressureRatio;

        private Request(IntegratedFluidNetwork inputNetwork, IntegratedFluidNetwork firstOutputNetwork,
            Fluid firstFluid, IntegratedFluidNetwork secondOutputNetwork, Fluid secondFluid, long requestedAmountQ,
            double splitRatio, OutputStateProvider firstOutputStateProvider,
            OutputStateProvider secondOutputStateProvider, float maxOutputToInputPressureRatio) {
            this.inputNetwork = inputNetwork;
            this.firstOutputNetwork = firstOutputNetwork;
            this.firstFluid = firstFluid;
            this.secondOutputNetwork = secondOutputNetwork;
            this.secondFluid = secondFluid;
            this.requestedAmountQ = requestedAmountQ;
            this.splitRatio = splitRatio;
            this.firstOutputStateProvider = firstOutputStateProvider;
            this.secondOutputStateProvider = secondOutputStateProvider;
            this.maxOutputToInputPressureRatio = maxOutputToInputPressureRatio;
        }

        public static Request of(IntegratedFluidNetwork inputNetwork, IntegratedFluidNetwork firstOutputNetwork,
            Fluid firstFluid, IntegratedFluidNetwork secondOutputNetwork, Fluid secondFluid, long requestedAmountQ,
            double splitRatio, OutputStateProvider firstOutputStateProvider,
            OutputStateProvider secondOutputStateProvider, float maxOutputToInputPressureRatio) {
            return new Request(
                inputNetwork,
                firstOutputNetwork,
                firstFluid,
                secondOutputNetwork,
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
                && secondOutputNetwork != null
                && firstFluid != null
                && secondFluid != null
                && requestedAmountQ >= IntegratedFluidNetwork.AMOUNT_SCALE
                && firstOutputStateProvider != null
                && secondOutputStateProvider != null
                && maxOutputToInputPressureRatio > 0.0f;
        }
    }

    public static final class Result {

        private final Status status;
        private final long amountQ;
        private final long firstAmountQ;
        private final long secondAmountQ;
        private final double firstOutputSpecificEnthalpy;
        private final double secondOutputSpecificEnthalpy;
        private final float predictedInputPressureBar;
        private final float predictedOutputPressureBar;

        private Result(Status status, long amountQ, long firstAmountQ, long secondAmountQ,
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
                Status.SUCCESS,
                amountQ,
                firstAmountQ,
                secondAmountQ,
                firstOutputSpecificEnthalpy,
                secondOutputSpecificEnthalpy,
                predictedInputPressureBar,
                predictedOutputPressureBar);
        }

        private static Result failure(Status status) {
            return new Result(status, 0L, 0L, 0L, 0.0d, 0.0d, 0.0f, 0.0f);
        }

        public Status getStatus() {
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
