package gregtech.api.metatileentity.implementations.integratedfluid.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Random;

import org.junit.jupiter.api.Test;

import gregtech.api.metatileentity.implementations.integratedfluid.IIntegratedFluidMember;
import gregtech.api.metatileentity.implementations.integratedfluid.IntegratedFluidNetwork;

class IFNSafetyEvaluatorTest {

    @Test
    void safetyLimitsUseWeakestMemberLimits() {
        IFNSafetyLimits limits = IFNSafetyLimits.fromMembers(Arrays.asList(
            member(1000, 9.0f, 1200.0f),
            member(1000, 6.0f, 750.0f)
        ));

        assertEquals(6.0f, limits.maxPressureDifferentialBar());
        assertEquals(750.0f, limits.maxTemperatureKelvin());
    }

    @Test
    void readOnlyEvaluationDoesNotAdvanceWarningTicks() {
        IntegratedFluidNetwork network = new IntegratedFluidNetwork();
        network.addMember(member(1000, 10.0f, 1200.0f));
        network.setPressure(10.5f);

        IFNOperationalSafetyEvaluation evaluation = IFNSafetyEvaluator.evaluate(network);

        assertEquals(IFNPressureLimitStatus.WARNING, evaluation.pressure().status());
        assertEquals(0, network.getPressureWarningTicks());
        assertFalse(evaluation.shouldRollFailureThisTick());
    }

    @Test
    void ruptureRuntimeUsesInjectedRandom() {
        IFNOperationalSafetyEvaluation evaluation = new IFNOperationalSafetyEvaluation(
            IFNPressureLimitEvaluation.evaluate(10.5d, IFNOperationalLimits.ofAccumulatorMaxPressureBar(10.0f), false),
            IFNTemperatureLimitEvaluation.evaluate(300.0d, 1000.0f, false),
            true,
            false);

        assertFalse(new IFNRuptureRuntime(fixedRandom(0.5d)).shouldRupture(evaluation));
        assertTrue(new IFNRuptureRuntime(fixedRandom(0.0d)).shouldRupture(evaluation));
    }

    private static Random fixedRandom(double value) {
        return new Random() {

            @Override
            public double nextDouble() {
                return value;
            }
        };
    }

    private static IIntegratedFluidMember member(int accumulator, float maxPressure, float maxTemperature) {
        return new IIntegratedFluidMember() {

            @Override
            public IntegratedFluidNetwork getNetwork() {
                return null;
            }

            @Override
            public void setNetwork(IntegratedFluidNetwork network) {}

            @Override
            public java.util.UUID getNetworkId() {
                return null;
            }

            @Override
            public void setNetworkId(java.util.UUID id) {}

            @Override
            public void onNetworkUpdate() {}

            @Override
            public int getCapacityContribution() {
                return 1000;
            }

            @Override
            public int getAccumulatorContribution() {
                return accumulator;
            }

            @Override
            public float getAccumulatorMaxPressureBar() {
                return maxPressure;
            }

            @Override
            public float getMaxTemperatureKelvin() {
                return maxTemperature;
            }
        };
    }
}
