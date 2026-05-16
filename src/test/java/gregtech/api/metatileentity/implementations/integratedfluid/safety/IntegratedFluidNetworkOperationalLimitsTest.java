package gregtech.api.metatileentity.implementations.integratedfluid.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import gregtech.api.metatileentity.implementations.integratedfluid.IIntegratedFluidMember;
import gregtech.api.metatileentity.implementations.integratedfluid.IntegratedFluidNetwork;

class IntegratedFluidNetworkOperationalLimitsTest {

    @Test
    void networkExposesOperationalLimitsFromMembers() {
        IntegratedFluidNetwork network = new IntegratedFluidNetwork();

        network.addMember(member(1000, 9.0f));
        network.addMember(member(1000, 6.0f));

        assertEquals(6.0f, network.getOperationalLimits().accumulatorMaxPressureBar());
        assertEquals(6.0f, network.getAccumulatorMaxPressureBar());
    }

    @Test
    void networkExposesWeakestTemperatureLimit() {
        IntegratedFluidNetwork network = new IntegratedFluidNetwork();

        network.addMember(member(1000, 9.0f, 1100.0f));
        network.addMember(member(1000, 9.0f, 750.0f));

        assertEquals(750.0f, network.getOperationalLimits().maxTemperatureKelvin());
    }

    @Test
    void networkEvaluatesCurrentTemperatureAgainstOperationalLimits() {
        IntegratedFluidNetwork network = new IntegratedFluidNetwork();

        network.addMember(member(1000, 9.0f, 250.0f));

        assertEquals(IFNTemperatureLimitStatus.RUPTURE, network.getTemperatureLimitEvaluation().status());
    }

    @Test
    void networkEvaluatesCurrentPressureAgainstOperationalLimits() {
        IntegratedFluidNetwork network = new IntegratedFluidNetwork();

        network.addMember(member(1000, 6.0f));
        network.setPressure(7.0f);

        assertEquals(IFNPressureLimitStatus.RUPTURE, network.getPressureLimitEvaluation().status());
    }

    @Test
    void networkTracksPersistentPressureWarnings() {
        IntegratedFluidNetwork network = new IntegratedFluidNetwork();

        network.addMember(member(1000, 10.0f));
        network.setPressure(10.5f);

        for (int tick = 0; tick < 199; tick++) {
            network.tickPressureSafety();
            assertEquals(tick + 1, network.getPressureWarningTicks());
            assertEquals(false, network.shouldRollPressureFailureThisTick());
        }

        network.tickPressureSafety();
        assertEquals(200, network.getPressureWarningTicks());
        assertEquals(true, network.shouldRollPressureFailureThisTick());
    }

    @Test
    void networkResetsPressureWarningTicksWhenPressureReturnsToNormal() {
        IntegratedFluidNetwork network = new IntegratedFluidNetwork();

        network.addMember(member(1000, 10.0f));
        network.setPressure(10.5f);
        network.tickPressureSafety();
        network.setPressure(10.0f);
        network.tickPressureSafety();

        assertEquals(0, network.getPressureWarningTicks());
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
