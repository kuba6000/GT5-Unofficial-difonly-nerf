package gregtech.api.metatileentity.implementations.integratedfluid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraftforge.fluids.Fluid;

import org.junit.jupiter.api.Test;

class IFNConservationTest {

    @Test
    void oneGtLiterInputIsOneReferenceLiter() {
        Fluid fluid = IFNTestSupport.liquidFluid();
        IntegratedFluidNetwork network = IFNTestSupport.newNetwork(fluid, 1_000, 0, 10.0f);
        long oneRefLiterQ = IntegratedFluidNetwork.AMOUNT_SCALE;
        long enthalpyQ = enthalpyFor(fluid, 1.0d, 300.0d, oneRefLiterQ);

        assertTrue(network.addState(fluid, oneRefLiterQ, enthalpyQ));
        assertEquals(oneRefLiterQ, network.getAmountQ());

        IntegratedFluidNetwork.ExtractedPayload simulated = network.extractProportional(oneRefLiterQ, true);
        assertEquals(oneRefLiterQ, simulated.amountQ);
        assertEquals(oneRefLiterQ, network.getAmountQ());
    }

    @Test
    void changingThermalEnergyDoesNotChangeExtractableSubstance() {
        Fluid fluid = IFNTestSupport.vaporFluid();
        IntegratedFluidNetwork network = IFNTestSupport.newNetwork(fluid, 100_000, 0, 50.0f);
        long amountQ = 100L * IntegratedFluidNetwork.AMOUNT_SCALE;

        assertTrue(network.addState(fluid, amountQ, enthalpyFor(fluid, 2.0d, 300.0d, amountQ)));
        long originalAmountQ = network.getAmountQ();

        network.clearFluid();
        assertTrue(network.addState(fluid, amountQ, enthalpyFor(fluid, 2.0d, 600.0d, amountQ)));

        assertEquals(originalAmountQ, network.getAmountQ());
    }

    @Test
    void networkMergeSumsSubstanceAndEnergy() {
        Fluid fluid = IFNTestSupport.liquidFluid();
        IntegratedFluidNetwork first = IFNTestSupport.newNetwork(fluid, 10_000, 0, 20.0f);
        IntegratedFluidNetwork second = IFNTestSupport.newNetwork(fluid, 10_000, 0, 20.0f);
        long firstAmountQ = 10L * IntegratedFluidNetwork.AMOUNT_SCALE;
        long secondAmountQ = 5L * IntegratedFluidNetwork.AMOUNT_SCALE;
        long firstEnthalpyQ = enthalpyFor(fluid, 1.0d, 300.0d, firstAmountQ);
        long secondEnthalpyQ = enthalpyFor(fluid, 1.0d, 360.0d, secondAmountQ);

        assertTrue(first.addState(fluid, firstAmountQ, firstEnthalpyQ));
        assertTrue(second.addState(fluid, secondAmountQ, secondEnthalpyQ));

        first.merge(second);

        assertEquals(firstAmountQ + secondAmountQ, first.getAmountQ());
        assertEquals(firstEnthalpyQ + secondEnthalpyQ, first.getEnthalpyQ());
        assertEquals(0L, second.getAmountQ());
    }

    private static long enthalpyFor(Fluid fluid, double pressureBar, double temperatureK, long amountQ) {
        double specificEnthalpy = FluidThermalProperties.getSpecificEnthalpyFromPT(fluid, pressureBar, temperatureK);
        return IntegratedFluidNetwork.toEnthalpyQFromSpecific(specificEnthalpy, amountQ);
    }
}
