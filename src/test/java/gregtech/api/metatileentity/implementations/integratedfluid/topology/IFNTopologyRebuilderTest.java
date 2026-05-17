package gregtech.api.metatileentity.implementations.integratedfluid.topology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import gregtech.api.metatileentity.implementations.integratedfluid.IIntegratedFluidMember;
import gregtech.api.metatileentity.implementations.integratedfluid.IntegratedFluidNetwork;

class IFNTopologyRebuilderTest {

    @Test
    void firstCompleteRebuildUsesObservedMemberCountAsExpectedCount() {
        IFNTopologyRebuilder.Result result = IFNTopologyRebuilder.rebuild(
            Arrays.asList(member(100, 0), member(10000, 5000)),
            0
        );

        assertFalse(result.pending());
        assertEquals(2, result.expectedMemberCount());
        assertEquals(10100, result.snapshot().baseVolume().toWholeLiters());
        assertEquals(5000, result.snapshot().accumulatorVolume().toWholeLiters());
    }

    @Test
    void missingExpectedMembersKeepsTopologyPending() {
        IFNTopologyRebuilder.Result result = IFNTopologyRebuilder.rebuild(
            Arrays.asList(member(100, 0), member(100, 0)),
            3
        );

        assertTrue(result.pending());
        assertTrue(result.snapshot().incomplete());
        assertEquals(3, result.expectedMemberCount());
    }

    @Test
    void fullTopologyGrowthIsANormalRebuild() {
        IFNTopologyRebuilder.Result result = IFNTopologyRebuilder.rebuild(
            Arrays.asList(member(100, 0), member(100, 0), member(100, 0)),
            2
        );

        assertFalse(result.pending());
        assertFalse(result.snapshot().incomplete());
        assertEquals(3, result.expectedMemberCount());
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
