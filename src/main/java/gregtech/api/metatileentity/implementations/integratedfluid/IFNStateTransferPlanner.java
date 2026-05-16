package gregtech.api.metatileentity.implementations.integratedfluid;

import net.minecraftforge.fluids.Fluid;

import gregtech.api.metatileentity.implementations.integratedfluid.state.IFNNetworkStatus;

/**
 * Shared planning helpers for common IFN state transfers.
 *
 * This layer does not mutate networks. It only computes a safe transferable
 * amount using the same pressure and acceptance rules for every machine that
 * performs a simple input-to-output state transfer.
 */
public final class IFNStateTransferPlanner {

    private IFNStateTransferPlanner() {}

    public static PlannedStateTransfer planSingleOutputStateAdd(IntegratedFluidNetwork inputNetwork,
        IntegratedFluidNetwork outputNetwork, Fluid fluid, double outputSpecificEnthalpy, long requestedAmountQ,
        float maxOutputToInputPressureRatio) {
        return planSingleOutputStateAdd(
            inputNetwork,
            outputNetwork,
            fluid,
            outputSpecificEnthalpy,
            requestedAmountQ,
            maxOutputToInputPressureRatio,
            0.0f
        );
    }

    public static PlannedStateTransfer planSingleOutputStateAdd(IntegratedFluidNetwork inputNetwork,
        IntegratedFluidNetwork outputNetwork, Fluid fluid, double outputSpecificEnthalpy, long requestedAmountQ,
        float maxOutputToInputPressureRatio, float sourcePressureDropBar) {
        if (inputNetwork == null || outputNetwork == null || fluid == null
            || requestedAmountQ < IntegratedFluidNetwork.AMOUNT_SCALE) {
            return PlannedStateTransfer.empty();
        }
        Status blockedStatus = blockedStatus(
            new IntegratedFluidNetwork[] { inputNetwork },
            new IntegratedFluidNetwork[] { outputNetwork });
        if (blockedStatus != Status.ACCEPTED) {
            return PlannedStateTransfer.failure(blockedStatus);
        }

        if (outputNetwork == inputNetwork) {
            return PlannedStateTransfer.accepted(
                requestedAmountQ,
                inputNetwork.getPressure(),
                outputNetwork.getPressure()
            );
        }

        long maxAddableQ = outputNetwork.getMaxAddableAmountQ(fluid, outputSpecificEnthalpy, requestedAmountQ);
        if (maxAddableQ < IntegratedFluidNetwork.AMOUNT_SCALE) {
            return PlannedStateTransfer.failure(Status.OUTPUT_BLOCKED);
        }

        long low = 0L;
        long high = Math.min(requestedAmountQ, maxAddableQ);
        long bestAmountQ = 0L;
        float bestInputPressure = inputNetwork.getPressure();
        float bestOutputPressure = outputNetwork.getPressure();

        for (int i = 0; i < IFNPressurePolicy.TRANSFER_SEARCH_ITERATIONS; i++) {
            if (low > high) {
                break;
            }

            long mid = (low + high) / 2L;
            if (mid < IntegratedFluidNetwork.AMOUNT_SCALE) {
                low = mid + 1L;
                continue;
            }

            long testEnthalpyQ = IntegratedFluidNetwork.toEnthalpyQFromSpecific(outputSpecificEnthalpy, mid);
            float pIn = IFNPressurePolicy.clampMinimum(inputNetwork.predictPressureAfterExtract(mid) - sourcePressureDropBar);
            float pOut = outputNetwork.predictPressureAfterStateAdd(fluid, mid, testEnthalpyQ);

            if (pOut <= pIn * maxOutputToInputPressureRatio) {
                bestAmountQ = mid;
                bestInputPressure = pIn;
                bestOutputPressure = pOut;
                low = mid + 1L;
            } else {
                high = mid - 1L;
            }
        }

        if (bestAmountQ < IntegratedFluidNetwork.AMOUNT_SCALE) {
            return PlannedStateTransfer.failure(Status.OUTPUT_BLOCKED);
        }

        return PlannedStateTransfer.accepted(bestAmountQ, bestInputPressure, bestOutputPressure);
    }

    public static PlannedSplitTransfer planSplitStateAdd(IntegratedFluidNetwork inputNetwork,
        IntegratedFluidNetwork redOutputNetwork, Fluid redFluid, double redSpecificEnthalpy,
        IntegratedFluidNetwork blueOutputNetwork, Fluid blueFluid, double blueSpecificEnthalpy, long requestedAmountQ,
        double splitRatio, float maxOutputToInputPressureRatio) {
        if (inputNetwork == null || redOutputNetwork == null || blueOutputNetwork == null
            || redFluid == null || blueFluid == null
            || requestedAmountQ < IntegratedFluidNetwork.AMOUNT_SCALE) {
            return PlannedSplitTransfer.empty();
        }
        Status blockedStatus = blockedStatus(
            new IntegratedFluidNetwork[] { inputNetwork },
            new IntegratedFluidNetwork[] { redOutputNetwork, blueOutputNetwork });
        if (blockedStatus != Status.ACCEPTED) {
            return PlannedSplitTransfer.failure(blockedStatus);
        }

        long requestedRedQ = (long) (requestedAmountQ * splitRatio);
        long requestedBlueQ = requestedAmountQ - requestedRedQ;
        if (requestedRedQ < IntegratedFluidNetwork.AMOUNT_SCALE || requestedBlueQ < IntegratedFluidNetwork.AMOUNT_SCALE) {
            return PlannedSplitTransfer.failure(Status.INVALID_REQUEST);
        }

        long maxRedAddableQ = redOutputNetwork != inputNetwork
            ? redOutputNetwork.getMaxAddableAmountQ(redFluid, redSpecificEnthalpy, requestedRedQ)
            : requestedRedQ;
        long maxBlueAddableQ = blueOutputNetwork != inputNetwork
            ? blueOutputNetwork.getMaxAddableAmountQ(blueFluid, blueSpecificEnthalpy, requestedBlueQ)
            : requestedBlueQ;

        if ((redOutputNetwork != inputNetwork && maxRedAddableQ < IntegratedFluidNetwork.AMOUNT_SCALE)
            || (blueOutputNetwork != inputNetwork && maxBlueAddableQ < IntegratedFluidNetwork.AMOUNT_SCALE)) {
            return PlannedSplitTransfer.failure(Status.OUTPUT_BLOCKED);
        }

        long low = 0L;
        long high = requestedAmountQ;
        long bestTotalQ = 0L;
        long bestRedQ = 0L;
        long bestBlueQ = 0L;

        for (int i = 0; i < IFNPressurePolicy.TRANSFER_SEARCH_ITERATIONS; i++) {
            if (low > high) {
                break;
            }

            long mid = (low + high) / 2L;
            if (mid < IntegratedFluidNetwork.AMOUNT_SCALE) {
                low = mid + 1L;
                continue;
            }

            long testRedQ = (long) (mid * splitRatio);
            long testBlueQ = mid - testRedQ;
            if (testRedQ < IntegratedFluidNetwork.AMOUNT_SCALE || testBlueQ < IntegratedFluidNetwork.AMOUNT_SCALE) {
                high = mid - 1L;
                continue;
            }

            boolean ok = true;
            if (redOutputNetwork != inputNetwork && testRedQ > maxRedAddableQ) ok = false;
            if (blueOutputNetwork != inputNetwork && testBlueQ > maxBlueAddableQ) ok = false;

            if (ok) {
                float pIn = inputNetwork.predictPressureAfterExtract(mid);
                if (redOutputNetwork != inputNetwork) {
                    long redEnthalpyQ = IntegratedFluidNetwork.toEnthalpyQFromSpecific(redSpecificEnthalpy, testRedQ);
                    float pRed = redOutputNetwork.predictPressureAfterStateAdd(redFluid, testRedQ, redEnthalpyQ);
                    if (pRed > pIn * maxOutputToInputPressureRatio) ok = false;
                }
                if (ok && blueOutputNetwork != inputNetwork) {
                    long blueEnthalpyQ = IntegratedFluidNetwork.toEnthalpyQFromSpecific(blueSpecificEnthalpy, testBlueQ);
                    float pBlue = blueOutputNetwork.predictPressureAfterStateAdd(blueFluid, testBlueQ, blueEnthalpyQ);
                    if (pBlue > pIn * maxOutputToInputPressureRatio) ok = false;
                }
            }

            if (ok) {
                bestTotalQ = mid;
                bestRedQ = testRedQ;
                bestBlueQ = testBlueQ;
                low = mid + 1L;
            } else {
                high = mid - 1L;
            }
        }

        if (bestTotalQ < IntegratedFluidNetwork.AMOUNT_SCALE
            || bestRedQ < IntegratedFluidNetwork.AMOUNT_SCALE
            || bestBlueQ < IntegratedFluidNetwork.AMOUNT_SCALE) {
            return PlannedSplitTransfer.failure(Status.OUTPUT_BLOCKED);
        }

        return PlannedSplitTransfer.accepted(bestTotalQ, bestRedQ, bestBlueQ);
    }

    public static PlannedDualTransfer planDualStateAddWithSharedRatio(IntegratedFluidNetwork redInputNetwork,
        IntegratedFluidNetwork redOutputNetwork, Fluid redFluid, double redSpecificEnthalpy, long requestedRedQ,
        IntegratedFluidNetwork blueInputNetwork, IntegratedFluidNetwork blueOutputNetwork, Fluid blueFluid,
        double blueSpecificEnthalpy, long requestedBlueQ, float maxOutputToInputPressureRatio) {
        if (redInputNetwork == null || redOutputNetwork == null || redFluid == null
            || blueInputNetwork == null || blueOutputNetwork == null || blueFluid == null
            || requestedRedQ < IntegratedFluidNetwork.AMOUNT_SCALE
            || requestedBlueQ < IntegratedFluidNetwork.AMOUNT_SCALE) {
            return PlannedDualTransfer.empty();
        }
        Status blockedStatus = blockedStatus(
            new IntegratedFluidNetwork[] { redInputNetwork, blueInputNetwork },
            new IntegratedFluidNetwork[] { redOutputNetwork, blueOutputNetwork });
        if (blockedStatus != Status.ACCEPTED) {
            return PlannedDualTransfer.failure(blockedStatus);
        }

        long maxRedAddableQ = redOutputNetwork != redInputNetwork
            ? redOutputNetwork.getMaxAddableAmountQ(redFluid, redSpecificEnthalpy, requestedRedQ)
            : requestedRedQ;
        long maxBlueAddableQ = blueOutputNetwork != blueInputNetwork
            ? blueOutputNetwork.getMaxAddableAmountQ(blueFluid, blueSpecificEnthalpy, requestedBlueQ)
            : requestedBlueQ;

        if ((redOutputNetwork != redInputNetwork && maxRedAddableQ < IntegratedFluidNetwork.AMOUNT_SCALE)
            || (blueOutputNetwork != blueInputNetwork && maxBlueAddableQ < IntegratedFluidNetwork.AMOUNT_SCALE)) {
            return PlannedDualTransfer.failure(Status.OUTPUT_BLOCKED);
        }

        double low = 0.0d;
        double high = 1.0d;
        double bestRatio = 0.0d;
        long bestRedQ = 0L;
        long bestBlueQ = 0L;

        for (int i = 0; i < IFNPressurePolicy.TRANSFER_SEARCH_ITERATIONS; i++) {
            double mid = (low + high) / 2.0d;

            long testRedQ = (long) (requestedRedQ * mid);
            long testBlueQ = (long) (requestedBlueQ * mid);

            if (testRedQ < IntegratedFluidNetwork.AMOUNT_SCALE || testBlueQ < IntegratedFluidNetwork.AMOUNT_SCALE) {
                low = mid;
                continue;
            }

            boolean ok = true;
            if (redOutputNetwork != redInputNetwork && testRedQ > maxRedAddableQ) ok = false;
            if (blueOutputNetwork != blueInputNetwork && testBlueQ > maxBlueAddableQ) ok = false;

            if (ok) {
                if (redOutputNetwork != redInputNetwork) {
                    long redEnthalpyQ = IntegratedFluidNetwork.toEnthalpyQFromSpecific(redSpecificEnthalpy, testRedQ);
                    float pIn = redInputNetwork.predictPressureAfterExtract(testRedQ);
                    float pOut = redOutputNetwork.predictPressureAfterStateAdd(redFluid, testRedQ, redEnthalpyQ);
                    if (pOut > pIn * maxOutputToInputPressureRatio) ok = false;
                }
                if (ok && blueOutputNetwork != blueInputNetwork) {
                    long blueEnthalpyQ = IntegratedFluidNetwork.toEnthalpyQFromSpecific(blueSpecificEnthalpy, testBlueQ);
                    float pIn = blueInputNetwork.predictPressureAfterExtract(testBlueQ);
                    float pOut = blueOutputNetwork.predictPressureAfterStateAdd(blueFluid, testBlueQ, blueEnthalpyQ);
                    if (pOut > pIn * maxOutputToInputPressureRatio) ok = false;
                }
            }

            if (ok) {
                bestRatio = mid;
                bestRedQ = testRedQ;
                bestBlueQ = testBlueQ;
                low = mid;
            } else {
                high = mid;
            }
        }

        if (bestRatio <= 0.0d
            || bestRedQ < IntegratedFluidNetwork.AMOUNT_SCALE
            || bestBlueQ < IntegratedFluidNetwork.AMOUNT_SCALE) {
            return PlannedDualTransfer.failure(Status.OUTPUT_BLOCKED);
        }

        return PlannedDualTransfer.accepted(bestRatio, bestRedQ, bestBlueQ);
    }

    private static Status blockedStatus(IntegratedFluidNetwork[] inputNetworks, IntegratedFluidNetwork[] outputNetworks) {
        for (IntegratedFluidNetwork network : inputNetworks) {
            if (network == null || network.getNetworkStatus() != IFNNetworkStatus.NORMAL) {
                return Status.INPUT_BLOCKED;
            }
        }
        for (IntegratedFluidNetwork network : outputNetworks) {
            if (network == null || network.getNetworkStatus() != IFNNetworkStatus.NORMAL) {
                return Status.OUTPUT_BLOCKED;
            }
        }
        return Status.ACCEPTED;
    }

    public enum Status {
        ACCEPTED,
        INVALID_REQUEST,
        INPUT_BLOCKED,
        OUTPUT_BLOCKED
    }

    public static final class PlannedStateTransfer {
        public final Status status;
        public final long acceptedAmountQ;
        public final float predictedInputPressure;
        public final float predictedOutputPressure;

        private PlannedStateTransfer(Status status, long acceptedAmountQ, float predictedInputPressure,
            float predictedOutputPressure) {
            this.status = status;
            this.acceptedAmountQ = acceptedAmountQ;
            this.predictedInputPressure = predictedInputPressure;
            this.predictedOutputPressure = predictedOutputPressure;
        }

        public static PlannedStateTransfer empty() {
            return failure(Status.INVALID_REQUEST);
        }

        public static PlannedStateTransfer failure(Status status) {
            return new PlannedStateTransfer(
                status,
                0L,
                IntegratedFluidNetwork.DEFAULT_PRESSURE,
                IntegratedFluidNetwork.DEFAULT_PRESSURE);
        }

        public static PlannedStateTransfer accepted(long acceptedAmountQ, float predictedInputPressure,
            float predictedOutputPressure) {
            return new PlannedStateTransfer(Status.ACCEPTED, acceptedAmountQ, predictedInputPressure, predictedOutputPressure);
        }
    }

    public static final class PlannedSplitTransfer {
        public final Status status;
        public final long acceptedTotalAmountQ;
        public final long acceptedRedAmountQ;
        public final long acceptedBlueAmountQ;

        private PlannedSplitTransfer(Status status, long acceptedTotalAmountQ, long acceptedRedAmountQ,
            long acceptedBlueAmountQ) {
            this.status = status;
            this.acceptedTotalAmountQ = acceptedTotalAmountQ;
            this.acceptedRedAmountQ = acceptedRedAmountQ;
            this.acceptedBlueAmountQ = acceptedBlueAmountQ;
        }

        public static PlannedSplitTransfer empty() {
            return failure(Status.INVALID_REQUEST);
        }

        public static PlannedSplitTransfer failure(Status status) {
            return new PlannedSplitTransfer(status, 0L, 0L, 0L);
        }

        public static PlannedSplitTransfer accepted(long acceptedTotalAmountQ, long acceptedRedAmountQ,
            long acceptedBlueAmountQ) {
            return new PlannedSplitTransfer(Status.ACCEPTED, acceptedTotalAmountQ, acceptedRedAmountQ, acceptedBlueAmountQ);
        }
    }

    public static final class PlannedDualTransfer {
        public final Status status;
        public final double acceptedRatio;
        public final long acceptedRedAmountQ;
        public final long acceptedBlueAmountQ;

        private PlannedDualTransfer(Status status, double acceptedRatio, long acceptedRedAmountQ,
            long acceptedBlueAmountQ) {
            this.status = status;
            this.acceptedRatio = acceptedRatio;
            this.acceptedRedAmountQ = acceptedRedAmountQ;
            this.acceptedBlueAmountQ = acceptedBlueAmountQ;
        }

        public static PlannedDualTransfer empty() {
            return failure(Status.INVALID_REQUEST);
        }

        public static PlannedDualTransfer failure(Status status) {
            return new PlannedDualTransfer(status, 0.0d, 0L, 0L);
        }

        public static PlannedDualTransfer accepted(double acceptedRatio, long acceptedRedAmountQ,
            long acceptedBlueAmountQ) {
            return new PlannedDualTransfer(Status.ACCEPTED, acceptedRatio, acceptedRedAmountQ, acceptedBlueAmountQ);
        }
    }
}
