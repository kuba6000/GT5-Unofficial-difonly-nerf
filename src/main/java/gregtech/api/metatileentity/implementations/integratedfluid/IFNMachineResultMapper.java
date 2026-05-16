package gregtech.api.metatileentity.implementations.integratedfluid;

import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.metatileentity.implementations.integratedfluid.state.IFNNetworkStatus;

public final class IFNMachineResultMapper {

    private IFNMachineResultMapper() {}

    public static CheckRecipeResult toRecipeResult(IFNStateTransferPlanner.Status status) {
        if (status == IFNStateTransferPlanner.Status.OUTPUT_BLOCKED) {
            return CheckRecipeResultRegistry.ITEM_OUTPUT_FULL;
        }
        if (status == IFNStateTransferPlanner.Status.ACCEPTED) {
            return CheckRecipeResultRegistry.SUCCESSFUL;
        }
        return CheckRecipeResultRegistry.NO_RECIPE;
    }

    public static CheckRecipeResult toRecipeResult(IFNSingleOutputProcess.Status status) {
        if (status == IFNSingleOutputProcess.Status.OUTPUT_BLOCKED) {
            return CheckRecipeResultRegistry.ITEM_OUTPUT_FULL;
        }
        if (status == IFNSingleOutputProcess.Status.SUCCESS) {
            return CheckRecipeResultRegistry.SUCCESSFUL;
        }
        return CheckRecipeResultRegistry.NO_RECIPE;
    }

    public static CheckRecipeResult requireOperationalNetworks(IntegratedFluidNetwork[] inputNetworks,
        IntegratedFluidNetwork[] outputNetworks) {
        if (!areOperational(inputNetworks)) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }
        if (!areOperational(outputNetworks)) {
            return CheckRecipeResultRegistry.ITEM_OUTPUT_FULL;
        }
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    private static boolean areOperational(IntegratedFluidNetwork[] networks) {
        if (networks == null) {
            return false;
        }
        for (IntegratedFluidNetwork network : networks) {
            if (network == null || network.getNetworkStatus() != IFNNetworkStatus.NORMAL) {
                return false;
            }
        }
        return true;
    }
}
