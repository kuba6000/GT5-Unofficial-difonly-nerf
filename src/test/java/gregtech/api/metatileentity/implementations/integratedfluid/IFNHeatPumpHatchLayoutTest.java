package gregtech.api.metatileentity.implementations.integratedfluid;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IFNHeatPumpHatchLayoutTest {

    @Test
    void splitFlowRequiresOneInputAndRedBlueOutputs() {
        assertTrue(IFNHeatPumpHatchLayout.isValidSplitFlowLayout(
            1,
            new int[] { IFNHeatPumpHatchLayout.RED, IFNHeatPumpHatchLayout.BLUE }));
        assertTrue(IFNHeatPumpHatchLayout.isValidSplitFlowLayout(
            1,
            new int[] { IFNHeatPumpHatchLayout.BLUE, IFNHeatPumpHatchLayout.RED }));

        assertFalse(IFNHeatPumpHatchLayout.isValidSplitFlowLayout(
            0,
            new int[] { IFNHeatPumpHatchLayout.RED, IFNHeatPumpHatchLayout.BLUE }));
        assertFalse(IFNHeatPumpHatchLayout.isValidSplitFlowLayout(
            2,
            new int[] { IFNHeatPumpHatchLayout.RED, IFNHeatPumpHatchLayout.BLUE }));
        assertFalse(IFNHeatPumpHatchLayout.isValidSplitFlowLayout(
            1,
            new int[] { IFNHeatPumpHatchLayout.RED, IFNHeatPumpHatchLayout.RED }));
        assertFalse(IFNHeatPumpHatchLayout.isValidSplitFlowLayout(
            1,
            new int[] { IFNHeatPumpHatchLayout.RED, IFNHeatPumpHatchLayout.BLUE, IFNHeatPumpHatchLayout.BLUE }));
    }

    @Test
    void heatExchangerRequiresRedAndBlueInputsAndOutputs() {
        assertTrue(IFNHeatPumpHatchLayout.isValidHeatExchangerLayout(
            new int[] { IFNHeatPumpHatchLayout.RED, IFNHeatPumpHatchLayout.BLUE },
            new int[] { IFNHeatPumpHatchLayout.BLUE, IFNHeatPumpHatchLayout.RED }));
        assertTrue(IFNHeatPumpHatchLayout.isValidHeatExchangerLayout(
            new int[] { IFNHeatPumpHatchLayout.RED, IFNHeatPumpHatchLayout.BLUE, IFNHeatPumpHatchLayout.RED },
            new int[] { IFNHeatPumpHatchLayout.BLUE, IFNHeatPumpHatchLayout.RED, IFNHeatPumpHatchLayout.BLUE }));

        assertFalse(IFNHeatPumpHatchLayout.isValidHeatExchangerLayout(
            new int[] { IFNHeatPumpHatchLayout.RED },
            new int[] { IFNHeatPumpHatchLayout.RED, IFNHeatPumpHatchLayout.BLUE }));
        assertFalse(IFNHeatPumpHatchLayout.isValidHeatExchangerLayout(
            new int[] { IFNHeatPumpHatchLayout.RED, IFNHeatPumpHatchLayout.BLUE },
            new int[] { IFNHeatPumpHatchLayout.BLUE }));
        assertFalse(IFNHeatPumpHatchLayout.isValidHeatExchangerLayout(
            new int[] { 0, IFNHeatPumpHatchLayout.RED },
            new int[] { IFNHeatPumpHatchLayout.BLUE, IFNHeatPumpHatchLayout.RED }));
    }

    @Test
    void normalModeAllowsAtMostOneInputAndOneOutput() {
        assertFalse(IFNHeatPumpHatchLayout.hasTooManyNormalModeHatches(0, 0));
        assertFalse(IFNHeatPumpHatchLayout.hasTooManyNormalModeHatches(1, 0));
        assertFalse(IFNHeatPumpHatchLayout.hasTooManyNormalModeHatches(0, 1));
        assertFalse(IFNHeatPumpHatchLayout.hasTooManyNormalModeHatches(1, 1));

        assertTrue(IFNHeatPumpHatchLayout.hasTooManyNormalModeHatches(2, 0));
        assertTrue(IFNHeatPumpHatchLayout.hasTooManyNormalModeHatches(0, 2));
        assertTrue(IFNHeatPumpHatchLayout.hasTooManyNormalModeHatches(1, 2));
        assertTrue(IFNHeatPumpHatchLayout.hasTooManyNormalModeHatches(2, 1));
    }
}
