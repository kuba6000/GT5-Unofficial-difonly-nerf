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

    private static IIntegratedFluidMember member(int accumulatorContribution, float accumulatorMaxPressureBar) {
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
        };
    }
}
