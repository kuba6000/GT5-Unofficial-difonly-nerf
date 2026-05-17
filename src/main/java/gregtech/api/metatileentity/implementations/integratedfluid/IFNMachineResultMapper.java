package gregtech.api.metatileentity.implementations.integratedfluid;

import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.recipe.check.SimpleCheckRecipeResult;

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

    public static CheckRecipeResult toRecipeResult(IFNMachineProcessStatus status) {
        if (status == IFNMachineProcessStatus.OUTPUT_BLOCKED) {
            return CheckRecipeResultRegistry.ITEM_OUTPUT_FULL;
        }
        if (status == IFNMachineProcessStatus.SUCCESS) {
            return CheckRecipeResultRegistry.SUCCESSFUL;
        }
        return CheckRecipeResultRegistry.NO_RECIPE;
    }

    public static CheckRecipeResult toRecipeResult(IFNHeatPumpPlanStatus status) {
        if (status == IFNHeatPumpPlanStatus.INVALID_CONFIGURATION) {
            return SimpleCheckRecipeResult.ofFailure("awaiting_configuration");
        }
        if (status == IFNHeatPumpPlanStatus.READY || status == IFNHeatPumpPlanStatus.PASSTHROUGH) {
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
