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
}
