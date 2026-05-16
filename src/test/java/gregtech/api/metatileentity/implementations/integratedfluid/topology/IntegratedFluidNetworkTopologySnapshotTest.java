package gregtech.api.metatileentity.implementations.integratedfluid.topology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import gregtech.api.metatileentity.implementations.integratedfluid.IIntegratedFluidMember;
import gregtech.api.metatileentity.implementations.integratedfluid.IntegratedFluidNetwork;

class IntegratedFluidNetworkTopologySnapshotTest {

    @Test
    void networkExposesTopologySnapshotFromMembers() {
        IntegratedFluidNetwork network = new IntegratedFluidNetwork();

        network.addMember(member(100, 0));
        network.addMember(member(10000, 5000));

        IFNTopologySnapshot snapshot = network.getTopologySnapshot();
        assertEquals(2, snapshot.memberCount());
        assertEquals(10100, snapshot.baseVolume().toWholeLiters());
        assertEquals(5000, snapshot.accumulatorVolume().toWholeLiters());
        assertEquals(15100, snapshot.totalVolume().toWholeLiters());
    }

    @Test
    void networkSnapshotReflectsExpectedMemberPendingState() {
        IntegratedFluidNetwork network = new IntegratedFluidNetwork();

        network.addMember(member(100, 0));
        network.setExpectedMemberCount(2);

        assertTrue(network.getTopologySnapshot().incomplete());
    }

    private static IIntegratedFluidMember member(int capacity, int accumulator) {
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
                return capacity;
            }

            @Override
            public int getAccumulatorContribution() {
                return accumulator;
            }
        };
    }
}
