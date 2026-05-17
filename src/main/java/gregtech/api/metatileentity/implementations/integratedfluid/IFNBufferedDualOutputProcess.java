package gregtech.api.metatileentity.implementations.integratedfluid;

import net.minecraftforge.fluids.Fluid;

import gregtech.api.metatileentity.implementations.integratedfluid.machine.IFNBatchState;
import gregtech.api.metatileentity.implementations.integratedfluid.machine.IFNMachineOutputBuffer;
import gregtech.api.metatileentity.implementations.integratedfluid.machine.IFNTransferPlan;

public final class IFNBufferedDualOutputProcess {

    private IFNBufferedDualOutputProcess() {}

    public static Result execute(Request request) {
        if (request == null || !request.isValid()) {
            return Result.failure(IFNMachineProcessStatus.INVALID_REQUEST);
        }
        if (!IFNNetworkTransferGate.areOperational(new IntegratedFluidNetwork[] {
            request.firstInputNetwork,
            request.secondInputNetwork
        })) {
            return Result.failure(IFNMachineProcessStatus.INPUT_BLOCKED);
        }

        IFNStateExtractionApplier.TwoInputExtraction extraction = IFNStateExtractionApplier.extractTwoOrRestoreFirst(
            request.firstInputNetwork,
            request.firstFluid,
            request.requestedFirstAmountQ,
            request.secondInputNetwork,
            request.secondFluid,
            request.requestedSecondAmountQ);
        if (!extraction.isSuccess()) {
            return Result.failure(IFNMachineProcessStatus.NO_INPUT);
        }

        IntegratedFluidNetwork.ExtractedPayload firstExtracted = extraction.getFirst();
        IntegratedFluidNetwork.ExtractedPayload secondExtracted = extraction.getSecond();
        double firstSpecificEnthalpy = request.firstOutputStateProvider.getOutputSpecificEnthalpy(firstExtracted.amountQ);
        double secondSpecificEnthalpy = request.secondOutputStateProvider.getOutputSpecificEnthalpy(secondExtracted.amountQ);

        IFNBatchState firstOutputBatch = IFNBatchState.of(
            request.firstFluid.getName(),
            firstExtracted.amountQ,
            firstSpecificEnthalpy,
            request.firstOutputNetwork.getPressure());
        IFNBatchState secondOutputBatch = IFNBatchState.of(
            request.secondFluid.getName(),
            secondExtracted.amountQ,
            secondSpecificEnthalpy,
            request.secondOutputNetwork.getPressure());

        if (!canAcceptPendingOutput(request.firstOutputBuffer, request.firstOutputPortName, firstOutputBatch)
            || !canAcceptPendingOutput(request.secondOutputBuffer, request.secondOutputPortName, secondOutputBatch)) {
            restoreInputs(request, firstExtracted, secondExtracted);
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
            restoreInputs(request, firstExtracted, secondExtracted);
            return Result.failure(IFNMachineProcessStatus.OUTPUT_BLOCKED);
        }

        return Result.success(
            firstExtracted.amountQ,
            secondExtracted.amountQ,
            firstSpecificEnthalpy,
            secondSpecificEnthalpy);
    }

    private static boolean canAcceptPendingOutput(IFNMachineOutputBuffer buffer, String portName, IFNBatchState batch) {
        return !buffer.pendingOutput(portName).mergeWith(batch).isEmpty();
    }

    private static void restoreInputs(Request request, IntegratedFluidNetwork.ExtractedPayload firstExtracted,
        IntegratedFluidNetwork.ExtractedPayload secondExtracted) {
        request.firstInputNetwork.addState(request.firstFluid, firstExtracted.amountQ, firstExtracted.enthalpyQ);
        request.secondInputNetwork.addState(request.secondFluid, secondExtracted.amountQ, secondExtracted.enthalpyQ);
    }

    @FunctionalInterface
    public interface OutputStateProvider {

        double getOutputSpecificEnthalpy(long amountQ);
    }

    public static final class Request {

        private final IntegratedFluidNetwork firstInputNetwork;
        private final IntegratedFluidNetwork firstOutputNetwork;
        private final IFNMachineOutputBuffer firstOutputBuffer;
        private final String firstOutputPortName;
        private final Fluid firstFluid;
        private final long requestedFirstAmountQ;
        private final IntegratedFluidNetwork secondInputNetwork;
        private final IntegratedFluidNetwork secondOutputNetwork;
        private final IFNMachineOutputBuffer secondOutputBuffer;
        private final String secondOutputPortName;
        private final Fluid secondFluid;
        private final long requestedSecondAmountQ;
        private final OutputStateProvider firstOutputStateProvider;
        private final OutputStateProvider secondOutputStateProvider;
        @SuppressWarnings("unused")
        private final float maxOutputToInputPressureRatio;

        private Request(
            IntegratedFluidNetwork firstInputNetwork,
            IntegratedFluidNetwork firstOutputNetwork,
            IFNMachineOutputBuffer firstOutputBuffer,
            String firstOutputPortName,
            Fluid firstFluid,
            long requestedFirstAmountQ,
            IntegratedFluidNetwork secondInputNetwork,
            IntegratedFluidNetwork secondOutputNetwork,
            IFNMachineOutputBuffer secondOutputBuffer,
            String secondOutputPortName,
            Fluid secondFluid,
            long requestedSecondAmountQ,
            OutputStateProvider firstOutputStateProvider,
            OutputStateProvider secondOutputStateProvider,
            float maxOutputToInputPressureRatio) {
            this.firstInputNetwork = firstInputNetwork;
            this.firstOutputNetwork = firstOutputNetwork;
            this.firstOutputBuffer = firstOutputBuffer;
            this.firstOutputPortName = firstOutputPortName;
            this.firstFluid = firstFluid;
            this.requestedFirstAmountQ = requestedFirstAmountQ;
            this.secondInputNetwork = secondInputNetwork;
            this.secondOutputNetwork = secondOutputNetwork;
            this.secondOutputBuffer = secondOutputBuffer;
            this.secondOutputPortName = secondOutputPortName;
            this.secondFluid = secondFluid;
            this.requestedSecondAmountQ = requestedSecondAmountQ;
            this.firstOutputStateProvider = firstOutputStateProvider;
            this.secondOutputStateProvider = secondOutputStateProvider;
            this.maxOutputToInputPressureRatio = maxOutputToInputPressureRatio;
        }

        public static Request of(
            IntegratedFluidNetwork firstInputNetwork,
            IntegratedFluidNetwork firstOutputNetwork,
            IFNMachineOutputBuffer firstOutputBuffer,
            String firstOutputPortName,
            Fluid firstFluid,
            long requestedFirstAmountQ,
            IntegratedFluidNetwork secondInputNetwork,
            IntegratedFluidNetwork secondOutputNetwork,
            IFNMachineOutputBuffer secondOutputBuffer,
            String secondOutputPortName,
            Fluid secondFluid,
            long requestedSecondAmountQ,
            OutputStateProvider firstOutputStateProvider,
            OutputStateProvider secondOutputStateProvider,
            float maxOutputToInputPressureRatio) {
            return new Request(
                firstInputNetwork,
                firstOutputNetwork,
                firstOutputBuffer,
                firstOutputPortName,
                firstFluid,
                requestedFirstAmountQ,
                secondInputNetwork,
                secondOutputNetwork,
                secondOutputBuffer,
                secondOutputPortName,
                secondFluid,
                requestedSecondAmountQ,
                firstOutputStateProvider,
                secondOutputStateProvider,
                maxOutputToInputPressureRatio);
        }

        private boolean isValid() {
            return firstInputNetwork != null
                && firstOutputNetwork != null
                && firstOutputBuffer != null
                && firstOutputPortName != null
                && !firstOutputPortName.trim().isEmpty()
                && firstFluid != null
                && requestedFirstAmountQ >= IntegratedFluidNetwork.AMOUNT_SCALE
                && secondInputNetwork != null
                && secondOutputNetwork != null
                && secondOutputBuffer != null
                && secondOutputPortName != null
                && !secondOutputPortName.trim().isEmpty()
                && secondFluid != null
                && requestedSecondAmountQ >= IntegratedFluidNetwork.AMOUNT_SCALE
                && firstOutputStateProvider != null
                && secondOutputStateProvider != null
                && maxOutputToInputPressureRatio > 0.0f;
        }
    }

    public static final class Result {

        private final IFNMachineProcessStatus status;
        private final long firstAmountQ;
        private final long secondAmountQ;
        private final double firstOutputSpecificEnthalpy;
        private final double secondOutputSpecificEnthalpy;

        private Result(IFNMachineProcessStatus status, long firstAmountQ, long secondAmountQ,
            double firstOutputSpecificEnthalpy, double secondOutputSpecificEnthalpy) {
            this.status = status;
            this.firstAmountQ = firstAmountQ;
            this.secondAmountQ = secondAmountQ;
            this.firstOutputSpecificEnthalpy = firstOutputSpecificEnthalpy;
            this.secondOutputSpecificEnthalpy = secondOutputSpecificEnthalpy;
        }

        private static Result success(long firstAmountQ, long secondAmountQ, double firstOutputSpecificEnthalpy,
            double secondOutputSpecificEnthalpy) {
            return new Result(
                IFNMachineProcessStatus.SUCCESS,
                firstAmountQ,
                secondAmountQ,
                firstOutputSpecificEnthalpy,
                secondOutputSpecificEnthalpy);
        }

        private static Result failure(IFNMachineProcessStatus status) {
            return new Result(status, 0L, 0L, 0.0d, 0.0d);
        }

        public IFNMachineProcessStatus getStatus() {
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
