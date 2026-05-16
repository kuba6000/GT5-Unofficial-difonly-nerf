package gregtech.api.metatileentity.implementations.integratedfluid.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IFNPressureWarningTrackerTest {

    @Test
    void normalPressureResetsWarningTicks() {
        IFNPressureWarningTracker tracker = new IFNPressureWarningTracker();

        tracker.update(warning());
        tracker.update(warning());
        tracker.update(normal());

        assertEquals(0, tracker.warningTicks());
        assertFalse(tracker.shouldRollFailureThisTick());
    }

    @Test
    void warningMustPersistForTwoHundredTicksBeforeFailureRolls() {
        IFNPressureWarningTracker tracker = new IFNPressureWarningTracker();

        for (int tick = 0; tick < 199; tick++) {
            tracker.update(warning());
            assertFalse(tracker.shouldRollFailureThisTick());
        }

        tracker.update(warning());

        assertEquals(200, tracker.warningTicks());
        assertTrue(tracker.shouldRollFailureThisTick());
    }

    @Test
    void failureRollRepeatsEveryTwentyWarningTicks() {
        IFNPressureWarningTracker tracker = new IFNPressureWarningTracker();

        for (int tick = 0; tick < 219; tick++) {
            tracker.update(warning());
        }
        assertFalse(tracker.shouldRollFailureThisTick());

        tracker.update(warning());
        assertEquals(220, tracker.warningTicks());
        assertTrue(tracker.shouldRollFailureThisTick());
    }

    @Test
    void rupturePressureDoesNotAccumulateWarningTicks() {
        IFNPressureWarningTracker tracker = new IFNPressureWarningTracker();

        tracker.update(warning());
        tracker.update(rupture());

        assertEquals(0, tracker.warningTicks());
        assertFalse(tracker.shouldRollFailureThisTick());
    }

    private static IFNPressureLimitEvaluation normal() {
        return IFNPressureLimitEvaluation.evaluate(10.0d, IFNOperationalLimits.ofAccumulatorMaxPressureBar(10.0f), false);
    }

    private static IFNPressureLimitEvaluation warning() {
        return IFNPressureLimitEvaluation.evaluate(10.5d, IFNOperationalLimits.ofAccumulatorMaxPressureBar(10.0f), false);
    }

    private static IFNPressureLimitEvaluation rupture() {
        return IFNPressureLimitEvaluation.evaluate(11.0d, IFNOperationalLimits.ofAccumulatorMaxPressureBar(10.0f), false);
    }
}
