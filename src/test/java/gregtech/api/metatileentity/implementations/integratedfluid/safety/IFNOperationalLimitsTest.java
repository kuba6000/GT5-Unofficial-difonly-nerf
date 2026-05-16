package gregtech.api.metatileentity.implementations.integratedfluid.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import gregtech.api.metatileentity.implementations.integratedfluid.IFNPressurePolicy;
import gregtech.api.metatileentity.implementations.integratedfluid.IIntegratedFluidMember;
import gregtech.api.metatileentity.implementations.integratedfluid.IntegratedFluidNetwork;

class IFNOperationalLimitsTest {

    @Test
    void noAccumulatorUsesDefaultPressureLimit() {
        IFNOperationalLimits limits = IFNOperationalLimits.fromMembers(java.util.Collections.singleton(member(0, 2.0f)));

        assertEquals(IFNPressurePolicy.DEFAULT_MAX_PRESSURE_BAR, limits.accumulatorMaxPressureBar());
    }

    @Test
    void weakestAccumulatorPressureLimitWins() {
        IFNOperationalLimits limits = IFNOperationalLimits.fromMembers(java.util.Arrays.asList(
                member(1000, 8.0f),
                member(1000, 4.5f),
                member(0, 1.0f)));

        assertEquals(4.5f, limits.accumulatorMaxPressureBar());
    }

    @Test
    void weakestTemperatureLimitWins() {
        IFNOperationalLimits limits = IFNOperationalLimits.fromMembers(java.util.Arrays.asList(
                member(0, 8.0f, 1200.0f),
                member(0, 8.0f, 800.0f),
                member(0, 8.0f, Float.POSITIVE_INFINITY)));

        assertEquals(800.0f, limits.maxTemperatureKelvin());
    }

    private static IIntegratedFluidMember member(int accumulatorContribution, float accumulatorMaxPressureBar) {
        return member(accumulatorContribution, accumulatorMaxPressureBar, Float.POSITIVE_INFINITY);
    }

    private static IIntegratedFluidMember member(int accumulatorContribution, float accumulatorMaxPressureBar,
        float maxTemperatureKelvin) {
        return new IIntegratedFluidMember() {

            private IntegratedFluidNetwork network;

            @Override
            public IntegratedFluidNetwork getNetwork() {
                return network;
            }

            @Override
            public void setNetwork(IntegratedFluidNetwork network) {
                this.network = network;
            }

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
                return 0;
            }

            @Override
            public int getAccumulatorContribution() {
                return accumulatorContribution;
            }

            @Override
            public float getAccumulatorMaxPressureBar() {
                return accumulatorMaxPressureBar;
            }

            @Override
            public float getMaxTemperatureKelvin() {
                return maxTemperatureKelvin;
            }
        };
    }
}
