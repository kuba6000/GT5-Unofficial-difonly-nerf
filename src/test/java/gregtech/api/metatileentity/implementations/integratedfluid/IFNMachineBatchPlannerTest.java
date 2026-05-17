package gregtech.api.metatileentity.implementations.integratedfluid;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class IFNMachineBatchPlannerTest {

    @Test
    void amountForVolumeLimitUsesSpecificVolumeAndAvailability() {
        long amountQ = IFNMachineBatchPlanner.computeAmountQForVolumeLimit(
            10L * IntegratedFluidNetwork.AMOUNT_SCALE,
            12.0d,
            2.5d
        );
        long cappedAmountQ = IFNMachineBatchPlanner.computeAmountQForVolumeLimit(
            3L * IntegratedFluidNetwork.AMOUNT_SCALE,
            12.0d,
            2.5d
        );

        assertEquals(4L * IntegratedFluidNetwork.AMOUNT_SCALE, amountQ);
        assertEquals(3L * IntegratedFluidNetwork.AMOUNT_SCALE, cappedAmountQ);
    }

    @Test
    void amountForVolumeLimitRejectsInvalidOrSubReferenceBatches() {
        assertEquals(0L, IFNMachineBatchPlanner.computeAmountQForVolumeLimit(10L, 12.0d, 0.0d));
        assertEquals(0L, IFNMachineBatchPlanner.computeAmountQForVolumeLimit(10L, 12.0d, Double.NaN));
        assertEquals(0L, IFNMachineBatchPlanner.computeAmountQForVolumeLimit(10L, 1.0d, 2.0d));
    }
}
