package gregtech.api.metatileentity.implementations.integratedfluid.covers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class IFNDetectorCoverLogicTest {

    @Test
    void binaryModeOutputsFullSignalOnlyInsideRange() {
        assertEquals(15, IFNDetectorCoverLogic.evaluate(5.0d, 2.0d, 8.0d, IFNDetectorCoverLogic.Mode.BINARY));
        assertEquals(0, IFNDetectorCoverLogic.evaluate(1.0d, 2.0d, 8.0d, IFNDetectorCoverLogic.Mode.BINARY));
        assertEquals(0, IFNDetectorCoverLogic.evaluate(9.0d, 2.0d, 8.0d, IFNDetectorCoverLogic.Mode.BINARY));
    }

    @Test
    void linearModeMapsMinToMaxOntoRedstoneRange() {
        assertEquals(0, IFNDetectorCoverLogic.evaluate(2.0d, 2.0d, 8.0d, IFNDetectorCoverLogic.Mode.LINEAR));
        assertEquals(8, IFNDetectorCoverLogic.evaluate(5.0d, 2.0d, 8.0d, IFNDetectorCoverLogic.Mode.LINEAR));
        assertEquals(15, IFNDetectorCoverLogic.evaluate(8.0d, 2.0d, 8.0d, IFNDetectorCoverLogic.Mode.LINEAR));
    }

    @Test
    void invalidValuesReturnNoSignal() {
        assertEquals(0, IFNDetectorCoverLogic.evaluate(Double.NaN, 2.0d, 8.0d, IFNDetectorCoverLogic.Mode.LINEAR));
        assertEquals(0, IFNDetectorCoverLogic.evaluate(5.0d, 8.0d, 2.0d, IFNDetectorCoverLogic.Mode.LINEAR));
    }
}
