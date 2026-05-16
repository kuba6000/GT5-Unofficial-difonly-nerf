package gregtech.api.metatileentity.implementations.integratedfluid.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IFNTemperatureLimitEvaluationTest {

    @Test
    void temperatureAtLimitIsNormal() {
        IFNTemperatureLimitEvaluation evaluation = IFNTemperatureLimitEvaluation.evaluate(800.0d, 800.0f, false);

        assertEquals(IFNTemperatureLimitStatus.NORMAL, evaluation.status());
        assertFalse(evaluation.isOverLimit());
        assertFalse(evaluation.isRuptureRequired());
    }

    @Test
    void temperatureAboveLimitWarnsBeforeRuptureThreshold() {
        IFNTemperatureLimitEvaluation evaluation = IFNTemperatureLimitEvaluation.evaluate(840.0d, 800.0f, false);

        assertEquals(IFNTemperatureLimitStatus.WARNING, evaluation.status());
        assertTrue(evaluation.isOverLimit());
        assertFalse(evaluation.isRuptureRequired());
    }

    @Test
    void temperatureAtTenPercentOverLimitRequiresImmediateRupture() {
        IFNTemperatureLimitEvaluation evaluation = IFNTemperatureLimitEvaluation.evaluate(880.0d, 800.0f, false);

        assertEquals(IFNTemperatureLimitStatus.RUPTURE, evaluation.status());
        assertTrue(evaluation.isOverLimit());
        assertTrue(evaluation.isRuptureRequired());
    }

    @Test
    void incompleteNetworkDoesNotRuptureImmediately() {
        IFNTemperatureLimitEvaluation evaluation = IFNTemperatureLimitEvaluation.evaluate(1000.0d, 800.0f, true);

        assertEquals(IFNTemperatureLimitStatus.WARNING, evaluation.status());
        assertTrue(evaluation.isOverLimit());
        assertFalse(evaluation.isRuptureRequired());
    }

    @Test
    void unlimitedTemperatureLimitIsAlwaysNormal() {
        IFNTemperatureLimitEvaluation evaluation = IFNTemperatureLimitEvaluation.evaluate(
                10_000.0d,
                Float.POSITIVE_INFINITY,
                false);

        assertEquals(IFNTemperatureLimitStatus.NORMAL, evaluation.status());
        assertFalse(evaluation.isOverLimit());
    }
}
