package gregtech.api.metatileentity.implementations.integratedfluid;

import net.minecraftforge.fluids.Fluid;

public final class IFNPressureRegulatorTransferPlanner {

    private IFNPressureRegulatorTransferPlanner() {}

    public static Plan plan(IntegratedFluidNetwork inputNetwork, IntegratedFluidNetwork outputNetwork, Fluid fluid,
        double outputSpecificEnthalpy, float setpointPressureBar, float overshootToleranceBar, long requestedAmountQ,
        long maxPacketAmountQ) {
        if (inputNetwork == null || outputNetwork == null || fluid == null
            || requestedAmountQ < IntegratedFluidNetwork.AMOUNT_SCALE
            || maxPacketAmountQ < IntegratedFluidNetwork.AMOUNT_SCALE) {
            return Plan.rejected(Status.INVALID_REQUEST);
        }
        if (!IFNNetworkTransferGate.isOperational(inputNetwork)) {
            return Plan.rejected(Status.INPUT_BLOCKED);
        }
        if (!IFNNetworkTransferGate.isOperational(outputNetwork)) {
            return Plan.rejected(Status.OUTPUT_BLOCKED);
        }
        if (outputNetwork.getPressure() >= setpointPressureBar) {
            return Plan.rejected(Status.OUTPUT_BLOCKED);
        }

        long high = Math.min(requestedAmountQ, Math.min(maxPacketAmountQ, inputNetwork.getAmountQ()));
        high = outputNetwork.getMaxAddableAmountQ(fluid, outputSpecificEnthalpy, high);
        if (high < IntegratedFluidNetwork.AMOUNT_SCALE) {
            return Plan.rejected(Status.OUTPUT_BLOCKED);
        }

        float maxAllowedPressure = setpointPressureBar + Math.max(0.0f, overshootToleranceBar);
        long low = IntegratedFluidNetwork.AMOUNT_SCALE;
        long bestAmountQ = 0L;
        float bestOutputPressure = outputNetwork.getPressure();
        for (int i = 0; i < IFNPressurePolicy.TRANSFER_SEARCH_ITERATIONS && low <= high; i++) {
            long mid = (low + high) / 2L;
            if (mid < IntegratedFluidNetwork.AMOUNT_SCALE) {
                mid = IntegratedFluidNetwork.AMOUNT_SCALE;
            }

            long enthalpyQ = IntegratedFluidNetwork.toEnthalpyQFromSpecific(outputSpecificEnthalpy, mid);
            float predictedOutputPressure = outputNetwork.predictPressureAfterStateAdd(fluid, mid, enthalpyQ);
            if (predictedOutputPressure <= maxAllowedPressure) {
                bestAmountQ = mid;
                bestOutputPressure = predictedOutputPressure;
                low = mid + 1L;
            } else {
                high = mid - 1L;
            }
        }

        if (bestAmountQ < IntegratedFluidNetwork.AMOUNT_SCALE) {
            return Plan.rejected(Status.OUTPUT_BLOCKED);
        }
        return Plan.accepted(bestAmountQ, bestOutputPressure);
    }

    public enum Status {
        ACCEPTED,
        INVALID_REQUEST,
        INPUT_BLOCKED,
        OUTPUT_BLOCKED
    }

    public static final class Plan {

        private final Status status;
        private final long acceptedAmountQ;
        private final float predictedOutputPressure;

        private Plan(Status status, long acceptedAmountQ, float predictedOutputPressure) {
            this.status = status;
            this.acceptedAmountQ = acceptedAmountQ;
            this.predictedOutputPressure = predictedOutputPressure;
        }

        public static Plan rejected(Status status) {
            return new Plan(status, 0L, IntegratedFluidNetwork.DEFAULT_PRESSURE);
        }

        public static Plan accepted(long acceptedAmountQ, float predictedOutputPressure) {
            return new Plan(Status.ACCEPTED, acceptedAmountQ, predictedOutputPressure);
        }

        public Status status() {
            return status;
        }

        public long acceptedAmountQ() {
            return acceptedAmountQ;
        }

        public float predictedOutputPressure() {
            return predictedOutputPressure;
        }
    }
}
