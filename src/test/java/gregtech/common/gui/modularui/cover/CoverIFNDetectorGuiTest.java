package gregtech.common.gui.modularui.cover;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CoverIFNDetectorGuiTest {

    @Test
    void rangeInputAllowsNegativeDeltaThresholds() {
        assertEquals(-25.0d, CoverIFNDetectorGui.sanitizeRangeValue(-25.0d), 0.0d);
    }
}
