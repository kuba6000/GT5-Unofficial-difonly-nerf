package gregtech.api.metatileentity.implementations.integratedfluid;

import gregtech.api.metatileentity.implementations.integratedfluid.state.IFNNetworkStatus;

public final class IFNNetworkTransferGate {

    private IFNNetworkTransferGate() {}

    public static boolean isOperational(IntegratedFluidNetwork network) {
        return network != null && network.getNetworkStatus() == IFNNetworkStatus.NORMAL;
    }

    public static boolean areOperational(IntegratedFluidNetwork[] networks) {
        if (networks == null) {
            return false;
        }
        for (IntegratedFluidNetwork network : networks) {
            if (!isOperational(network)) {
                return false;
            }
        }
        return true;
    }

    public static boolean canInjectFromGtPipe(IntegratedFluidNetwork network) {
        return isOperational(network) && network.getPressure() <= IFNPressurePolicy.injectorCutoffPressureBar();
    }

    public static boolean canExtractToGtPipe(IntegratedFluidNetwork network) {
        return isOperational(network);
    }
}
