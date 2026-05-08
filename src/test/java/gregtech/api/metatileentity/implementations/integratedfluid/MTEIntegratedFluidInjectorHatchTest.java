package gregtech.api.metatileentity.implementations.integratedfluid;

import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraftforge.fluids.Fluid;

import org.junit.jupiter.api.Test;

class MTEIntegratedFluidInjectorHatchTest {

    @Test
    void injectorClampKeepsPhysicalGasFillAtOrBelowOneBar() {
        Fluid vapor = IFNTestSupport.vaporFluid();
        IntegratedFluidNetwork network = IFNTestSupport.newNetwork(vapor, 1_000, 0, 10.0f);

        double incomingSpecificEnthalpy = 300.0d;
        long requestedAmountQ = 1_000L * IntegratedFluidNetwork.AMOUNT_SCALE;

        long stateLimitedAmountQ = network.getMaxAddableAmountQ(vapor, incomingSpecificEnthalpy, requestedAmountQ);
        long injectorLimitedAmountQ = MTEIntegratedFluidInjectorHatch.clampAcceptedPhysicalAddToInjectorPressure(
            network,
            vapor,
            incomingSpecificEnthalpy,
            stateLimitedAmountQ
        );

        assertTrue(injectorLimitedAmountQ > 0L);
        assertTrue(injectorLimitedAmountQ < stateLimitedAmountQ);

        long injectorEnthalpyQ = IntegratedFluidNetwork.toEnthalpyQFromSpecific(
            incomingSpecificEnthalpy,
            injectorLimitedAmountQ
        );
        float predictedPressure = network.predictPressureAfterAdd(vapor, injectorLimitedAmountQ, injectorEnthalpyQ);
        assertTrue(
            predictedPressure <= IFNPressurePolicy.injectorCutoffPressureBar(),
            "predicted injector fill pressure was " + predictedPressure + " bar"
        );

        network.add(vapor, injectorLimitedAmountQ, injectorEnthalpyQ);
        assertTrue(
            network.getPressure() <= IFNPressurePolicy.injectorCutoffPressureBar(),
            "actual injector fill pressure was " + network.getPressure() + " bar"
        );
    }
}
