package gregtech.api.metatileentity.implementations.integratedfluid.topology;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import gregtech.api.metatileentity.implementations.integratedfluid.amount.EnergyAmount;
import gregtech.api.metatileentity.implementations.integratedfluid.amount.SubstanceAmount;
import gregtech.api.metatileentity.implementations.integratedfluid.fluid.IFNFluidRegistry;
import gregtech.api.metatileentity.implementations.integratedfluid.state.IFNCanonicalState;

class IFNPipeConnectionPolicyTest {

    @Test
    void incompatibleNonEmptySubstancesPreventPipeConnection() {
        IFNFluidRegistry.init();

        assertFalse(IFNPipeConnectionPolicy.canConnectPipeStates(
            state("water"),
            state("hydrogen")
        ));
    }

    @Test
    void differentPhaseIdsOfSameSubstanceCanConnectAsPipes() {
        IFNFluidRegistry.init();

        assertTrue(IFNPipeConnectionPolicy.canConnectPipeStates(
            state("water"),
            state("steam")
        ));
    }

    @Test
    void emptyPipeNetworkCanConnectToNonEmptyPipeNetwork() {
        assertTrue(IFNPipeConnectionPolicy.canConnectPipeStates(
            IFNCanonicalState.empty(),
            state("water")
        ));
    }

    private static IFNCanonicalState state(String fluidId) {
        return IFNCanonicalState.of(fluidId, SubstanceAmount.fromRefLiters(1), EnergyAmount.fromEu(1));
    }
}
