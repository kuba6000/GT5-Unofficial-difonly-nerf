package gregtech.api.metatileentity.implementations.integratedfluid;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraftforge.fluids.Fluid;

import org.junit.jupiter.api.Test;

class IFNNetworkTransferGateTest {

    @Test
    void operationalGateRejectsNullPendingAndFrozenNetworks() {
        Fluid fluid = IFNTestSupport.liquidFluid();
        IntegratedFluidNetwork network = IFNTestSupport.newNetwork(fluid, 1_000, 0, 1.0f);

        assertTrue(IFNNetworkTransferGate.isOperational(network));
        assertFalse(IFNNetworkTransferGate.isOperational(null));

        network.setPending(true);

        assertFalse(IFNNetworkTransferGate.isOperational(network));

        network.setPending(false);
        network.freeze("test freeze");

        assertFalse(IFNNetworkTransferGate.isOperational(network));
    }

    @Test
    void injectorGateAlsoRequiresPressureAtCutoff() {
        Fluid fluid = IFNTestSupport.vaporFluid();
        IntegratedFluidNetwork network = IFNTestSupport.newNetwork(fluid, 1_000, 0, 1.0f);

        assertTrue(IFNNetworkTransferGate.canInjectFromGtPipe(network));

        network.setPressure(IFNPressurePolicy.injectorCutoffPressureBar() + 0.01f);

        assertFalse(IFNNetworkTransferGate.canInjectFromGtPipe(network));
    }
}
