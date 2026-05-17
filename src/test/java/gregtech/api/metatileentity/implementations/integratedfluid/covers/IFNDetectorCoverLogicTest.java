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

    @Test
    void absoluteSourceModeEvaluatesRawValue() {
        assertEquals(
            15,
            IFNDetectorCoverLogic.evaluate(
                305.0d,
                300.0d,
                300.0d,
                310.0d,
                IFNDetectorCoverLogic.Mode.BINARY,
                IFNDetectorCoverLogic.SourceMode.ABSOLUTE));
    }

    @Test
    void deltaSourceModeSubtractsReferenceBeforeEvaluatingRange() {
        assertEquals(
            15,
            IFNDetectorCoverLogic.evaluate(
                306.0d,
                300.0d,
                5.0d,
                10.0d,
                IFNDetectorCoverLogic.Mode.BINARY,
                IFNDetectorCoverLogic.SourceMode.DELTA));
        assertEquals(
            8,
            IFNDetectorCoverLogic.evaluate(
                306.0d,
                300.0d,
                0.0d,
                12.0d,
                IFNDetectorCoverLogic.Mode.LINEAR,
                IFNDetectorCoverLogic.SourceMode.DELTA));
    }
}
