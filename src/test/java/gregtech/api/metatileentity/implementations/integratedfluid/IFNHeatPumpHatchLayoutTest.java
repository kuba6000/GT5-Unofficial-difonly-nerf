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
}
