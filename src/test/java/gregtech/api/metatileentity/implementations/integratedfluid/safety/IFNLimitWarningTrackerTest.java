package gregtech.api.metatileentity.implementations.integratedfluid.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IFNLimitWarningTrackerTest {

    @Test
    void inactiveLimitResetsWarningTicks() {
        IFNLimitWarningTracker tracker = new IFNLimitWarningTracker();

        tracker.update(true);
        tracker.update(false);

        assertEquals(0, tracker.warningTicks());
        assertFalse(tracker.shouldRollFailureThisTick());
    }

    @Test
    void activeLimitRollsAfterTwoHundredTicksAndEveryTwentyTicksAfterwards() {
        IFNLimitWarningTracker tracker = new IFNLimitWarningTracker();

        for (int tick = 0; tick < 199; tick++) {
            tracker.update(true);
            assertFalse(tracker.shouldRollFailureThisTick());
        }

        tracker.update(true);
        assertEquals(200, tracker.warningTicks());
        assertTrue(tracker.shouldRollFailureThisTick());

        for (int tick = 0; tick < 19; tick++) {
            tracker.update(true);
            assertFalse(tracker.shouldRollFailureThisTick());
        }

        tracker.update(true);
        assertEquals(220, tracker.warningTicks());
        assertTrue(tracker.shouldRollFailureThisTick());
    }
}
