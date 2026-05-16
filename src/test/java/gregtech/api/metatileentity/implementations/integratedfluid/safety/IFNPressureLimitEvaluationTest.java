package gregtech.api.metatileentity.implementations.integratedfluid.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IFNPressureLimitEvaluationTest {

    @Test
    void pressureAtLimitIsNormal() {
        IFNPressureLimitEvaluation evaluation = IFNPressureLimitEvaluation.evaluate(
                10.0d,
                IFNOperationalLimits.ofAccumulatorMaxPressureBar(10.0f),
                false);

        assertEquals(IFNPressureLimitStatus.NORMAL, evaluation.status());
        assertFalse(evaluation.isOverLimit());
        assertFalse(evaluation.isRuptureRequired());
    }

    @Test
    void pressureAboveLimitWarnsBeforeRuptureThreshold() {
        IFNPressureLimitEvaluation evaluation = IFNPressureLimitEvaluation.evaluate(
                10.5d,
                IFNOperationalLimits.ofAccumulatorMaxPressureBar(10.0f),
                false);

        assertEquals(IFNPressureLimitStatus.WARNING, evaluation.status());
        assertTrue(evaluation.isOverLimit());
        assertFalse(evaluation.isRuptureRequired());
    }

    @Test
    void pressureAtTenPercentOverLimitRequiresImmediateRupture() {
        IFNPressureLimitEvaluation evaluation = IFNPressureLimitEvaluation.evaluate(
                11.0d,
                IFNOperationalLimits.ofAccumulatorMaxPressureBar(10.0f),
                false);

        assertEquals(IFNPressureLimitStatus.RUPTURE, evaluation.status());
        assertTrue(evaluation.isOverLimit());
        assertTrue(evaluation.isRuptureRequired());
    }

    @Test
    void incompleteNetworkDoesNotRuptureImmediately() {
        IFNPressureLimitEvaluation evaluation = IFNPressureLimitEvaluation.evaluate(
                20.0d,
                IFNOperationalLimits.ofAccumulatorMaxPressureBar(10.0f),
                true);

        assertEquals(IFNPressureLimitStatus.WARNING, evaluation.status());
        assertTrue(evaluation.isOverLimit());
        assertFalse(evaluation.isRuptureRequired());
    }
}
