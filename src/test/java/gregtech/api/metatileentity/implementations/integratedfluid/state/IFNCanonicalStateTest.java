package gregtech.api.metatileentity.implementations.integratedfluid.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import gregtech.api.metatileentity.implementations.integratedfluid.amount.EnergyAmount;
import gregtech.api.metatileentity.implementations.integratedfluid.amount.SubstanceAmount;

class IFNCanonicalStateTest {

    @Test
    void emptyStateHasNoFluidSubstanceOrEnergy() {
        IFNCanonicalState state = IFNCanonicalState.empty();

        assertTrue(state.isEmpty());
        assertFalse(state.fluidId().isPresent());
        assertEquals(0L, state.substanceAmount().rawUnits());
        assertEquals(0L, state.internalEnergy().rawUnits());
    }

    @Test
    void zeroSubstanceNormalizesToEmptyEvenIfFluidAndEnergyWereProvided() {
        IFNCanonicalState state = IFNCanonicalState.of(
            "water",
            SubstanceAmount.ZERO,
            EnergyAmount.fromEu(100)
        );

        assertTrue(state.isEmpty());
        assertFalse(state.fluidId().isPresent());
        assertEquals(0L, state.internalEnergy().rawUnits());
    }

    @Test
    void nonEmptyStatePreservesFluidSubstanceAndEnergy() {
        IFNCanonicalState state = IFNCanonicalState.of(
            "water",
            SubstanceAmount.fromRefLiters(10),
            EnergyAmount.fromEu(250)
        );

        assertFalse(state.isEmpty());
        assertEquals("water", state.fluidId().get());
        assertEquals(10, state.substanceAmount().toWholeRefLiters());
        assertEquals(250, state.internalEnergy().toWholeEu());
    }
}
