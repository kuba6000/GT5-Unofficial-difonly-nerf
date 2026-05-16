package gregtech.api.metatileentity.implementations.integratedfluid.safety;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

class IFNOperationalFailurePolicyTest {

    @Test
    void immediateRuptureAlwaysFailsWithoutUsingRandomRoll() {
        IFNOperationalSafetyEvaluation evaluation = new IFNOperationalSafetyEvaluation(
                pressure(11.0d, 10.0f),
                temperature(300.0d, Float.POSITIVE_INFINITY),
                false,
                false);

        assertTrue(IFNOperationalFailurePolicy.shouldFailNow(evaluation, fixedRandom(0.99d)));
    }

    @Test
    void noRollEligibilityDoesNotFail() {
        IFNOperationalSafetyEvaluation evaluation = new IFNOperationalSafetyEvaluation(
                pressure(10.5d, 10.0f),
                temperature(300.0d, Float.POSITIVE_INFINITY),
                false,
                false);

        assertFalse(IFNOperationalFailurePolicy.shouldFailNow(evaluation, fixedRandom(0.0d)));
    }

    @Test
    void eligibleRollFailsOnlyBelowOnePercent() {
        IFNOperationalSafetyEvaluation evaluation = new IFNOperationalSafetyEvaluation(
                pressure(10.5d, 10.0f),
                temperature(300.0d, Float.POSITIVE_INFINITY),
                true,
                false);

        assertTrue(IFNOperationalFailurePolicy.shouldFailNow(evaluation, fixedRandom(0.009d)));
        assertFalse(IFNOperationalFailurePolicy.shouldFailNow(evaluation, fixedRandom(0.01d)));
    }

    private static IFNPressureLimitEvaluation pressure(double pressureBar, float maxPressureBar) {
        return IFNPressureLimitEvaluation.evaluate(
                pressureBar,
                IFNOperationalLimits.ofAccumulatorMaxPressureBar(maxPressureBar),
                false);
    }

    private static IFNTemperatureLimitEvaluation temperature(double temperatureKelvin, float maxTemperatureKelvin) {
        return IFNTemperatureLimitEvaluation.evaluate(temperatureKelvin, maxTemperatureKelvin, false);
    }

    private static Random fixedRandom(double value) {
        return new Random(0L) {

            @Override
            public double nextDouble() {
                return value;
            }
        };
    }
}
