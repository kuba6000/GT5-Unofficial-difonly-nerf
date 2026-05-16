package gregtech.api.metatileentity.implementations.integratedfluid.topology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import gregtech.api.metatileentity.implementations.integratedfluid.amount.EnergyAmount;
import gregtech.api.metatileentity.implementations.integratedfluid.amount.SubstanceAmount;
import gregtech.api.metatileentity.implementations.integratedfluid.state.IFNCanonicalState;

class IFNMergePolicyTest {

    @Test
    void emptyAndNonEmptyNetworksAreCompatible() {
        IFNCanonicalState merged = IFNMergePolicy.merge(
            IFNCanonicalState.empty(),
            state("water", 10, 100)
        );

        assertFalse(merged.isEmpty());
        assertEquals("water", merged.fluidId().get());
        assertEquals(10, merged.substanceAmount().toWholeRefLiters());
        assertEquals(100, merged.internalEnergy().toWholeEu());
    }

    @Test
    void sameFluidMergeSumsSubstanceAndEnergy() {
        IFNCanonicalState merged = IFNMergePolicy.merge(
            state("water", 10, 100),
            state("water", 5, 40)
        );

        assertEquals("water", merged.fluidId().get());
        assertEquals(15, merged.substanceAmount().toWholeRefLiters());
        assertEquals(140, merged.internalEnergy().toWholeEu());
    }

    @Test
    void differentNonEmptyFluidsAreIncompatible() {
        assertFalse(IFNMergePolicy.canMerge(state("water", 1, 1), state("hydrogen", 1, 1)));
    }

    @Test
    void emptyStatesMergeToEmpty() {
        IFNCanonicalState merged = IFNMergePolicy.merge(IFNCanonicalState.empty(), IFNCanonicalState.empty());

        assertTrue(merged.isEmpty());
    }

    private static IFNCanonicalState state(String fluidId, long refL, long eu) {
        return IFNCanonicalState.of(fluidId, SubstanceAmount.fromRefLiters(refL), EnergyAmount.fromEu(eu));
    }
}
