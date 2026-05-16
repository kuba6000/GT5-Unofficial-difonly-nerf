package gregtech.api.metatileentity.implementations.integratedfluid.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraftforge.fluids.Fluid;

import gregtech.api.metatileentity.implementations.integratedfluid.IFNTestSupport;
import gregtech.api.metatileentity.implementations.integratedfluid.IntegratedFluidNetwork;

class IntegratedFluidNetworkCanonicalStateTest {

    @Test
    void clearFluidExposesEmptyCanonicalState() {
        IntegratedFluidNetwork network = new IntegratedFluidNetwork();

        network.clearFluid();

        assertTrue(network.getCanonicalState().isEmpty());
    }

    @Test
    void loadedTraceSubstanceRemainsCanonicalState() {
        Fluid fluid = IFNTestSupport.liquidFluid();
        IntegratedFluidNetwork network = IFNTestSupport.newNetwork(fluid, 1000, 0, 100.0f);

        network.loadState(fluid.getName(), 1L, 2L, IntegratedFluidNetwork.DEFAULT_PRESSURE, 0);

        IFNCanonicalState state = network.getCanonicalState();
        assertFalse(state.isEmpty());
        assertEquals(fluid.getName(), state.fluidId().get());
        assertEquals(1L, state.substanceAmount().rawUnits());
        assertEquals(2L, state.internalEnergy().rawUnits());
    }

    @Test
    void replaceCanonicalStateCommitsNonEmptyState() {
        Fluid fluid = IFNTestSupport.liquidFluid();
        IntegratedFluidNetwork network = IFNTestSupport.newNetwork(fluid, 1000, 0, 100.0f);

        network.replaceCanonicalState(IFNCanonicalState.of(
            fluid.getName(),
            gregtech.api.metatileentity.implementations.integratedfluid.amount.SubstanceAmount.fromRawUnits(12L),
            gregtech.api.metatileentity.implementations.integratedfluid.amount.EnergyAmount.fromRawUnits(34L)
        ));

        IFNCanonicalState state = network.getCanonicalState();
        assertFalse(state.isEmpty());
        assertEquals(fluid.getName(), state.fluidId().get());
        assertEquals(12L, state.substanceAmount().rawUnits());
        assertEquals(34L, state.internalEnergy().rawUnits());
    }

    @Test
    void replaceCanonicalStateWithEmptyClearsNetwork() {
        Fluid fluid = IFNTestSupport.liquidFluid();
        IntegratedFluidNetwork network = IFNTestSupport.newNetwork(fluid, 1000, 0, 100.0f);
        network.loadState(fluid.getName(), 10L, 20L, IntegratedFluidNetwork.DEFAULT_PRESSURE, 0);

        network.replaceCanonicalState(IFNCanonicalState.empty());

        assertTrue(network.getCanonicalState().isEmpty());
        assertEquals(0L, network.getAmountQ());
        assertEquals(0L, network.getEnthalpyQ());
    }
}
