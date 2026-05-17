package gregtech.api.metatileentity.implementations.integratedfluid.topology;

import gregtech.api.metatileentity.implementations.integratedfluid.IntegratedFluidNetwork;
import gregtech.api.metatileentity.implementations.integratedfluid.state.IFNCanonicalState;

public final class IFNPipeConnectionPolicy {

    private IFNPipeConnectionPolicy() {}

    public static boolean canConnectPipeNetworks(IntegratedFluidNetwork first, IntegratedFluidNetwork second) {
        if (first == null || second == null || first == second) {
            return true;
        }
        return canConnectPipeStates(first.getCanonicalState(), second.getCanonicalState());
    }

    public static boolean canConnectPipeStates(IFNCanonicalState first, IFNCanonicalState second) {
        return IFNMergePolicy.canMerge(first, second);
    }
}
