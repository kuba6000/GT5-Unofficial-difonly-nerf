package gregtech.api.metatileentity.implementations.integratedfluid.topology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import gregtech.api.metatileentity.implementations.integratedfluid.IIntegratedFluidMember;
import gregtech.api.metatileentity.implementations.integratedfluid.IntegratedFluidNetwork;

class IFNTopologySnapshotTest {

    @Test
    void sumsBaseAndAccumulatorVolumes() {
        IFNTopologySnapshot snapshot = IFNTopologySnapshot.fromMembers(
            Arrays.asList(member(100, 0), member(10000, 5000)),
            0
        );

        assertEquals(2, snapshot.memberCount());
        assertEquals(10100, snapshot.baseVolume().toWholeLiters());
        assertEquals(5000, snapshot.accumulatorVolume().toWholeLiters());
        assertEquals(15100, snapshot.totalVolume().toWholeLiters());
    }

    @Test
    void separatesPipeHatchAndHydrophoreContributions() {
        IFNTopologySnapshot snapshot = IFNTopologySnapshot.fromMembers(
            Arrays.asList(
                member(100, 0),
                member(10000, 0),
                member(0, 5000)),
            0
        );

        assertEquals(3, snapshot.memberCount());
        assertEquals(10100, snapshot.baseVolume().toWholeLiters());
        assertEquals(5000, snapshot.accumulatorVolume().toWholeLiters());
        assertEquals(15100, snapshot.totalVolume().toWholeLiters());
    }

    @Test
    void expectedMemberCountMarksIncompleteTopology() {
        IFNTopologySnapshot snapshot = IFNTopologySnapshot.fromMembers(
            Arrays.asList(member(100, 0), member(100, 0)),
            3
        );

        assertTrue(snapshot.incomplete());
    }

    private static IIntegratedFluidMember member(int capacity, int accumulator) {
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
                return capacity;
            }

            @Override
            public int getAccumulatorContribution() {
                return accumulator;
            }
        };
    }
}
