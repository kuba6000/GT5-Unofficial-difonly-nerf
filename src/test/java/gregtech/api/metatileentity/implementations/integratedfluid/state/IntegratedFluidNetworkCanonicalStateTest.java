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
}
