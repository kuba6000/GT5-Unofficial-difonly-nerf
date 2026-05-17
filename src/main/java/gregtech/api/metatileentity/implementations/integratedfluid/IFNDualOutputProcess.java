package gregtech.api.metatileentity.implementations.integratedfluid;

import net.minecraftforge.fluids.Fluid;

/**
 * Shared process primitive for machines that move two IFN input states into two output states.
 */
public final class IFNDualOutputProcess {

    private IFNDualOutputProcess() {}

    public static Result execute(Request request) {
        if (request == null || !request.isValid()) {
            return Result.failure(Status.INVALID_REQUEST);
        }
        if (!IFNNetworkTransferGate.areOperational(new IntegratedFluidNetwork[] {
            request.firstInputNetwork,
            request.secondInputNetwork
        })) {
            return Result.failure(Status.INPUT_BLOCKED);
        }
        if (!IFNNetworkTransferGate.areOperational(new IntegratedFluidNetwork[] {
            request.firstOutputNetwork,
            request.secondOutputNetwork
        })) {
            return Result.failure(Status.OUTPUT_BLOCKED);
        }

        long firstAmountQ = request.requestedFirstAmountQ;
        long secondAmountQ = request.requestedSecondAmountQ;
        double firstSpecificEnthalpy = request.firstOutputStateProvider.getOutputSpecificEnthalpy(firstAmountQ);
        double secondSpecificEnthalpy = request.secondOutputStateProvider.getOutputSpecificEnthalpy(secondAmountQ);

        IFNStateTransferPlanner.PlannedDualTransfer plan = IFNStateTransferPlanner.PlannedDualTransfer.empty();
        if (request.firstOutputNetwork != request.firstInputNetwork ||
            request.secondOutputNetwork != request.secondInputNetwork) {
            plan = IFNStateTransferPlanner.planDualStateAddWithSharedRatio(
                request.firstInputNetwork,
                request.firstOutputNetwork,
                request.firstFluid,
                firstSpecificEnthalpy,
                firstAmountQ,
                request.secondInputNetwork,
                request.secondOutputNetwork,
                request.secondFluid,
                secondSpecificEnthalpy,
                secondAmountQ,
                request.maxOutputToInputPressureRatio
            );
            if (plan.acceptedRatio <= 0.0d
                || plan.acceptedRedAmountQ < IntegratedFluidNetwork.AMOUNT_SCALE
                || plan.acceptedBlueAmountQ < IntegratedFluidNetwork.AMOUNT_SCALE) {
                return Result.failure(Status.OUTPUT_BLOCKED);
            }
            firstAmountQ = plan.acceptedRedAmountQ;
            secondAmountQ = plan.acceptedBlueAmountQ;
        }

        IFNStateExtractionApplier.TwoInputExtraction extraction = IFNStateExtractionApplier.extractTwoOrRestoreFirst(
            request.firstInputNetwork,
            request.firstFluid,
            firstAmountQ,
            request.secondInputNetwork,
            request.secondFluid,
            secondAmountQ);
        if (!extraction.isSuccess()) {
            return Result.failure(Status.NO_INPUT);
        }

        IntegratedFluidNetwork.ExtractedPayload firstExtracted = extraction.getFirst();
        IntegratedFluidNetwork.ExtractedPayload secondExtracted = extraction.getSecond();
        firstSpecificEnthalpy = request.firstOutputStateProvider.getOutputSpecificEnthalpy(firstExtracted.amountQ);
        secondSpecificEnthalpy = request.secondOutputStateProvider.getOutputSpecificEnthalpy(secondExtracted.amountQ);

        long firstOutputEnthalpyQ = IntegratedFluidNetwork
            .toEnthalpyQFromSpecific(firstSpecificEnthalpy, firstExtracted.amountQ);
        long secondOutputEnthalpyQ = IntegratedFluidNetwork
            .toEnthalpyQFromSpecific(secondSpecificEnthalpy, secondExtracted.amountQ);

        if (!IFNStateMutationApplier.addTwoOutputsOrRestoreInputs(
            request.firstInputNetwork,
            request.firstOutputNetwork,
            request.firstFluid,
            firstExtracted,
            firstOutputEnthalpyQ,
            request.secondInputNetwork,
            request.secondOutputNetwork,
            request.secondFluid,
            secondExtracted,
            secondOutputEnthalpyQ)) {
            return Result.failure(Status.OUTPUT_BLOCKED);
        }

        return Result.success(
            firstExtracted.amountQ,
            secondExtracted.amountQ,
            firstSpecificEnthalpy,
            secondSpecificEnthalpy);
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

        private final IntegratedFluidNetwork firstInputNetwork;
        private final IntegratedFluidNetwork firstOutputNetwork;
        private final Fluid firstFluid;
        private final long requestedFirstAmountQ;
        private final IntegratedFluidNetwork secondInputNetwork;
        private final IntegratedFluidNetwork secondOutputNetwork;
        private final Fluid secondFluid;
        private final long requestedSecondAmountQ;
        private final OutputStateProvider firstOutputStateProvider;
        private final OutputStateProvider secondOutputStateProvider;
        private final float maxOutputToInputPressureRatio;

        private Request(IntegratedFluidNetwork firstInputNetwork, IntegratedFluidNetwork firstOutputNetwork,
            Fluid firstFluid, long requestedFirstAmountQ, IntegratedFluidNetwork secondInputNetwork,
            IntegratedFluidNetwork secondOutputNetwork, Fluid secondFluid, long requestedSecondAmountQ,
            OutputStateProvider firstOutputStateProvider, OutputStateProvider secondOutputStateProvider,
            float maxOutputToInputPressureRatio) {
            this.firstInputNetwork = firstInputNetwork;
            this.firstOutputNetwork = firstOutputNetwork;
            this.firstFluid = firstFluid;
            this.requestedFirstAmountQ = requestedFirstAmountQ;
            this.secondInputNetwork = secondInputNetwork;
            this.secondOutputNetwork = secondOutputNetwork;
            this.secondFluid = secondFluid;
            this.requestedSecondAmountQ = requestedSecondAmountQ;
            this.firstOutputStateProvider = firstOutputStateProvider;
            this.secondOutputStateProvider = secondOutputStateProvider;
            this.maxOutputToInputPressureRatio = maxOutputToInputPressureRatio;
        }

        public static Request of(IntegratedFluidNetwork firstInputNetwork, IntegratedFluidNetwork firstOutputNetwork,
            Fluid firstFluid, long requestedFirstAmountQ, IntegratedFluidNetwork secondInputNetwork,
            IntegratedFluidNetwork secondOutputNetwork, Fluid secondFluid, long requestedSecondAmountQ,
            OutputStateProvider firstOutputStateProvider, OutputStateProvider secondOutputStateProvider,
            float maxOutputToInputPressureRatio) {
            return new Request(
                firstInputNetwork,
                firstOutputNetwork,
                firstFluid,
                requestedFirstAmountQ,
                secondInputNetwork,
                secondOutputNetwork,
                secondFluid,
                requestedSecondAmountQ,
                firstOutputStateProvider,
                secondOutputStateProvider,
                maxOutputToInputPressureRatio);
        }

        private boolean isValid() {
            return firstInputNetwork != null
                && firstOutputNetwork != null
                && firstFluid != null
                && requestedFirstAmountQ >= IntegratedFluidNetwork.AMOUNT_SCALE
                && secondInputNetwork != null
                && secondOutputNetwork != null
                && secondFluid != null
                && requestedSecondAmountQ >= IntegratedFluidNetwork.AMOUNT_SCALE
                && firstOutputStateProvider != null
                && secondOutputStateProvider != null
                && maxOutputToInputPressureRatio > 0.0f;
        }
    }

    public static final class Result {

        private final Status status;
        private final long firstAmountQ;
        private final long secondAmountQ;
        private final double firstOutputSpecificEnthalpy;
        private final double secondOutputSpecificEnthalpy;

        private Result(Status status, long firstAmountQ, long secondAmountQ, double firstOutputSpecificEnthalpy,
            double secondOutputSpecificEnthalpy) {
            this.status = status;
            this.firstAmountQ = firstAmountQ;
            this.secondAmountQ = secondAmountQ;
            this.firstOutputSpecificEnthalpy = firstOutputSpecificEnthalpy;
            this.secondOutputSpecificEnthalpy = secondOutputSpecificEnthalpy;
        }

        private static Result success(long firstAmountQ, long secondAmountQ, double firstOutputSpecificEnthalpy,
            double secondOutputSpecificEnthalpy) {
            return new Result(
                Status.SUCCESS,
                firstAmountQ,
                secondAmountQ,
                firstOutputSpecificEnthalpy,
                secondOutputSpecificEnthalpy);
        }

        private static Result failure(Status status) {
            return new Result(status, 0L, 0L, 0.0d, 0.0d);
        }

        public Status getStatus() {
            return status;
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
    }
}
