package gregtech.api.metatileentity.implementations.integratedfluid.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import net.minecraftforge.fluids.Fluid;

import gregtech.api.metatileentity.implementations.integratedfluid.FluidThermalProperties;
import gregtech.api.metatileentity.implementations.integratedfluid.IFNTestSupport;
import gregtech.api.metatileentity.implementations.integratedfluid.IntegratedFluidNetwork;

class IntegratedFluidNetworkStatusTest {

    @Test
    void expectedMissingMembersMakeNetworkPending() {
        IntegratedFluidNetwork network = new IntegratedFluidNetwork();

        network.setExpectedMemberCount(1);

        assertEquals(IFNNetworkStatus.PENDING, network.getNetworkStatus());
    }

    @Test
    void frozenStatusOverridesPendingStatus() {
        IntegratedFluidNetwork network = new IntegratedFluidNetwork();

        network.setExpectedMemberCount(1);
        network.freeze("fluid conflict");

        assertEquals(IFNNetworkStatus.FROZEN, network.getNetworkStatus());
        assertEquals("fluid conflict", network.getFrozenReason());
    }

    @Test
    void frozenNetworkRejectsInputAndOutput() {
        Fluid fluid = IFNTestSupport.liquidFluid();
        IntegratedFluidNetwork network = IFNTestSupport.newNetwork(fluid, 1000, 0, 100.0f);
        long amountQ = IntegratedFluidNetwork.AMOUNT_SCALE;
        long energyQ = IntegratedFluidNetwork.toEnthalpyQFromSpecific(
            FluidThermalProperties.getSpecificEnthalpyFromPT(fluid, 1.0d, 300.0d),
            amountQ
        );

        network.freeze("test freeze");
        network.addState(fluid, amountQ, energyQ);

        assertEquals(0L, network.getAmountQ());
        assertNull(network.drainFluid(1, false));
    }

    @Test
    void pendingNetworkRejectsInputAndOutput() {
        Fluid fluid = IFNTestSupport.liquidFluid();
        IntegratedFluidNetwork network = IFNTestSupport.newNetwork(fluid, 1000, 0, 100.0f);
        long amountQ = IntegratedFluidNetwork.AMOUNT_SCALE;
        long energyQ = IntegratedFluidNetwork.toEnthalpyQFromSpecific(
            FluidThermalProperties.getSpecificEnthalpyFromPT(fluid, 1.0d, 300.0d),
            amountQ
        );

        network.setPending(true);
        network.addState(fluid, amountQ, energyQ);

        assertEquals(0L, network.getAmountQ());
        assertNull(network.drainFluid(1, false));
    }

    @Test
    void incompatibleMergeFreezesBothNetworksAndPreservesState() {
        Fluid liquid = IFNTestSupport.liquidFluid();
        Fluid vapor = IFNTestSupport.vaporFluid();
        IntegratedFluidNetwork liquidNetwork = IFNTestSupport.newNetwork(liquid, 1000, 0, 100.0f);
        IntegratedFluidNetwork vaporNetwork = IFNTestSupport.newNetwork(vapor, 1000, 0, 100.0f);
        long liquidAmountQ = 2L * IntegratedFluidNetwork.AMOUNT_SCALE;
        long vaporAmountQ = 3L * IntegratedFluidNetwork.AMOUNT_SCALE;

        liquidNetwork.addState(liquid, liquidAmountQ, IntegratedFluidNetwork.toEnthalpyQFromSpecific(300.0d, liquidAmountQ));
        vaporNetwork.addState(vapor, vaporAmountQ, IntegratedFluidNetwork.toEnthalpyQFromSpecific(350.0d, vaporAmountQ));

        liquidNetwork.merge(vaporNetwork);

        assertEquals(IFNNetworkStatus.FROZEN, liquidNetwork.getNetworkStatus());
        assertEquals(IFNNetworkStatus.FROZEN, vaporNetwork.getNetworkStatus());
        assertEquals(liquidAmountQ, liquidNetwork.getAmountQ());
        assertEquals(vaporAmountQ, vaporNetwork.getAmountQ());
        assertEquals(liquid.getName(), liquidNetwork.getFluidName());
        assertEquals(vapor.getName(), vaporNetwork.getFluidName());
    }
}
