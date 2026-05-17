package gregtech.api.metatileentity.implementations.integratedfluid;

import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;

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

    public static CheckRecipeResult toRecipeResult(IFNSplitOutputProcess.Status status) {
        if (status == IFNSplitOutputProcess.Status.OUTPUT_BLOCKED) {
            return CheckRecipeResultRegistry.ITEM_OUTPUT_FULL;
        }
        if (status == IFNSplitOutputProcess.Status.SUCCESS) {
            return CheckRecipeResultRegistry.SUCCESSFUL;
        }
        return CheckRecipeResultRegistry.NO_RECIPE;
    }

    public static CheckRecipeResult requireOperationalNetworks(IntegratedFluidNetwork[] inputNetworks,
        IntegratedFluidNetwork[] outputNetworks) {
        if (!IFNNetworkTransferGate.areOperational(inputNetworks)) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }
        if (!IFNNetworkTransferGate.areOperational(outputNetworks)) {
            return CheckRecipeResultRegistry.ITEM_OUTPUT_FULL;
        }
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }
}
