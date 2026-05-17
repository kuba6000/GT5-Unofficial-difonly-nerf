package gregtech.api.metatileentity.implementations.integratedfluid;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.recipe.check.SimpleCheckRecipeResult;

class IFNMachineResultMapperTest {

    @Test
    void inputBlockedMapsToNoRecipe() {
        assertSame(
            CheckRecipeResultRegistry.NO_RECIPE,
            IFNMachineResultMapper.toRecipeResult(IFNStateTransferPlanner.Status.INPUT_BLOCKED));
    }

    @Test
    void outputBlockedMapsToOutputFull() {
        assertSame(
            CheckRecipeResultRegistry.ITEM_OUTPUT_FULL,
            IFNMachineResultMapper.toRecipeResult(IFNStateTransferPlanner.Status.OUTPUT_BLOCKED));
    }

    @Test
    void processInputBlockedMapsToNoRecipe() {
        assertSame(
            CheckRecipeResultRegistry.NO_RECIPE,
            IFNMachineResultMapper.toRecipeResult(IFNMachineProcessStatus.INPUT_BLOCKED));
    }

    @Test
    void processOutputBlockedMapsToOutputFull() {
        assertSame(
            CheckRecipeResultRegistry.ITEM_OUTPUT_FULL,
            IFNMachineResultMapper.toRecipeResult(IFNMachineProcessStatus.OUTPUT_BLOCKED));
    }

    @Test
    void readyPlanStatusMapsToSuccess() {
        assertSame(
            CheckRecipeResultRegistry.SUCCESSFUL,
            IFNMachineResultMapper.toRecipeResult(IFNHeatPumpPlanStatus.READY));
        assertSame(
            CheckRecipeResultRegistry.SUCCESSFUL,
            IFNMachineResultMapper.toRecipeResult(IFNHeatPumpPlanStatus.PASSTHROUGH));
    }

    @Test
    void invalidPlanStatusMapsToAwaitingConfiguration() {
        assertEquals(
            SimpleCheckRecipeResult.ofFailure("awaiting_configuration"),
            IFNMachineResultMapper.toRecipeResult(IFNHeatPumpPlanStatus.INVALID_CONFIGURATION));
    }

    @Test
    void blockedInputNetworkMapsToNoRecipeBeforeMachineWork() {
        IntegratedFluidNetwork input = new IntegratedFluidNetwork();
        IntegratedFluidNetwork output = new IntegratedFluidNetwork();

        input.freeze("test freeze");

        assertSame(
            CheckRecipeResultRegistry.NO_RECIPE,
            IFNMachineResultMapper.requireOperationalNetworks(
                new IntegratedFluidNetwork[] { input },
                new IntegratedFluidNetwork[] { output }));
    }

    @Test
    void blockedOutputNetworkMapsToOutputFullBeforeMachineWork() {
        IntegratedFluidNetwork input = new IntegratedFluidNetwork();
        IntegratedFluidNetwork output = new IntegratedFluidNetwork();

        output.setPending(true);

        assertSame(
            CheckRecipeResultRegistry.ITEM_OUTPUT_FULL,
            IFNMachineResultMapper.requireOperationalNetworks(
                new IntegratedFluidNetwork[] { input },
                new IntegratedFluidNetwork[] { output }));
    }
}
