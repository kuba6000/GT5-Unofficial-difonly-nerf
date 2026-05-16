package gregtech.api.metatileentity.implementations.integratedfluid;

import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import gregtech.api.recipe.check.CheckRecipeResultRegistry;

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
}
