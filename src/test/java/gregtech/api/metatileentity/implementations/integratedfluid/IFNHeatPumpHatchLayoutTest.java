package gregtech.api.metatileentity.implementations.integratedfluid;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IFNHeatPumpHatchLayoutTest {

    @Test
    void splitFlowRequiresOneInputAndRedBlueOutputs() {
        assertTrue(IFNHeatPumpHatchLayout.isValidSplitFlowLayout(1, new int[] { 1, 4 }));
        assertTrue(IFNHeatPumpHatchLayout.isValidSplitFlowLayout(1, new int[] { 4, 1 }));

        assertFalse(IFNHeatPumpHatchLayout.isValidSplitFlowLayout(0, new int[] { 1, 4 }));
        assertFalse(IFNHeatPumpHatchLayout.isValidSplitFlowLayout(2, new int[] { 1, 4 }));
        assertFalse(IFNHeatPumpHatchLayout.isValidSplitFlowLayout(1, new int[] { 1, 1 }));
        assertFalse(IFNHeatPumpHatchLayout.isValidSplitFlowLayout(1, new int[] { 1, 4, 4 }));
    }

    @Test
    void heatExchangerRequiresRedAndBlueInputsAndOutputs() {
        assertTrue(IFNHeatPumpHatchLayout.isValidHeatExchangerLayout(
            new int[] { 1, 4 },
            new int[] { 4, 1 }));
        assertTrue(IFNHeatPumpHatchLayout.isValidHeatExchangerLayout(
            new int[] { 1, 4, 1 },
            new int[] { 4, 1, 4 }));

        assertFalse(IFNHeatPumpHatchLayout.isValidHeatExchangerLayout(
            new int[] { 1 },
            new int[] { 1, 4 }));
        assertFalse(IFNHeatPumpHatchLayout.isValidHeatExchangerLayout(
            new int[] { 1, 4 },
            new int[] { 4 }));
        assertFalse(IFNHeatPumpHatchLayout.isValidHeatExchangerLayout(
            new int[] { 0, 1 },
            new int[] { 4, 1 }));
    }
}
