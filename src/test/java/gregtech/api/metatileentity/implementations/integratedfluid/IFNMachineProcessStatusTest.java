package gregtech.api.metatileentity.implementations.integratedfluid;

import static org.junit.jupiter.api.Assertions.assertSame;

import gregtech.api.recipe.check.CheckRecipeResultRegistry;

import org.junit.jupiter.api.Test;

class IFNMachineProcessStatusTest {

    @Test
    void mapsSharedProcessStatusToRecipeResult() {
        assertSame(
            CheckRecipeResultRegistry.SUCCESSFUL,
            IFNMachineResultMapper.toRecipeResult(IFNMachineProcessStatus.SUCCESS));
        assertSame(
            CheckRecipeResultRegistry.ITEM_OUTPUT_FULL,
            IFNMachineResultMapper.toRecipeResult(IFNMachineProcessStatus.OUTPUT_BLOCKED));
        assertSame(
            CheckRecipeResultRegistry.NO_RECIPE,
            IFNMachineResultMapper.toRecipeResult(IFNMachineProcessStatus.INPUT_BLOCKED));
        assertSame(
            CheckRecipeResultRegistry.NO_RECIPE,
            IFNMachineResultMapper.toRecipeResult(IFNMachineProcessStatus.NO_INPUT));
        assertSame(
            CheckRecipeResultRegistry.NO_RECIPE,
            IFNMachineResultMapper.toRecipeResult(IFNMachineProcessStatus.INVALID_REQUEST));
    }
}
