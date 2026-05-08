package gregtech.api.metatileentity.implementations.integratedfluid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraftforge.fluids.Fluid;

import org.junit.jupiter.api.Test;

class IntegratedFluidNetworkStateTest {

    @Test
    void addStateDoesNotApplyExtraGasHeating() {
        Fluid vapor = IFNTestSupport.vaporFluid();
        long amountQ = 1_000L * IntegratedFluidNetwork.AMOUNT_SCALE;
        long enthalpyQ = IntegratedFluidNetwork.toEnthalpyQFromSpecific(500.0d, amountQ);

        IntegratedFluidNetwork stateNetwork = IFNTestSupport.newNetwork(vapor, 1_000, 0, 10.0f);
        stateNetwork.addState(vapor, amountQ, enthalpyQ);

        IntegratedFluidNetwork flowWorkNetwork = IFNTestSupport.newNetwork(vapor, 1_000, 0, 10.0f);
        flowWorkNetwork.add(vapor, amountQ, enthalpyQ);

        assertEquals(500.0f, stateNetwork.getTemperature(), 0.001f);
        assertTrue(flowWorkNetwork.getTemperature() > 600.0f);
    }

    @Test
    void liquidCanAcceptRejectsStatesAbovePressureLimit() {
        Fluid liquid = IFNTestSupport.liquidFluid();
        IntegratedFluidNetwork network = IFNTestSupport.newNetwork(liquid, 100, 100, 1.5f);

        long acceptedAmountQ = 130L * IntegratedFluidNetwork.AMOUNT_SCALE;
        long rejectedAmountQ = 150L * IntegratedFluidNetwork.AMOUNT_SCALE;
        long acceptedEnthalpyQ = IntegratedFluidNetwork.toEnthalpyQFromSpecific(300.0d, acceptedAmountQ);
        long rejectedEnthalpyQ = IntegratedFluidNetwork.toEnthalpyQFromSpecific(300.0d, rejectedAmountQ);

        assertTrue(network.canAccept(liquid, acceptedAmountQ, acceptedEnthalpyQ));
        assertFalse(network.canAccept(liquid, rejectedAmountQ, rejectedEnthalpyQ));
    }

    @Test
    void getMaxAddableAmountQMatchesLiquidPressureLimit() {
        Fluid liquid = IFNTestSupport.liquidFluid();
        IntegratedFluidNetwork network = IFNTestSupport.newNetwork(liquid, 100, 100, 1.5f);

        long requestedAmountQ = 200L * IntegratedFluidNetwork.AMOUNT_SCALE;
        long maxAddableQ = network.getMaxAddableAmountQ(liquid, 300.0d, requestedAmountQ);

        assertTrue(maxAddableQ >= 133L * IntegratedFluidNetwork.AMOUNT_SCALE - 1L);
        assertTrue(maxAddableQ <= 134L * IntegratedFluidNetwork.AMOUNT_SCALE);
    }

    @Test
    void fluidStateSnapshotCanDerivePredictedStatesWithoutMutatingOriginal() {
        Fluid vapor = IFNTestSupport.vaporFluid();
        IntegratedFluidNetwork network = IFNTestSupport.newNetwork(vapor, 1_000, 0, 10.0f);
        long initialAmountQ = 100L * IntegratedFluidNetwork.AMOUNT_SCALE;
        long initialEnthalpyQ = IntegratedFluidNetwork.toEnthalpyQFromSpecific(350.0d, initialAmountQ);
        network.addState(vapor, initialAmountQ, initialEnthalpyQ);

        IFNFluidState original = network.snapshotState();
        long addAmountQ = 50L * IntegratedFluidNetwork.AMOUNT_SCALE;
        long addEnthalpyQ = IntegratedFluidNetwork.toEnthalpyQFromSpecific(450.0d, addAmountQ);

        IFNFluidState predicted = original.withAddedState(vapor, addAmountQ, addEnthalpyQ, 2.0f);

        assertEquals(initialAmountQ, original.getAmountQ());
        assertEquals(initialEnthalpyQ, original.getEnthalpyQ());
        assertEquals(network.getFluidName(), original.getFluidName());
        assertEquals(network.getPressure(), original.getPressureBar(), 0.0001f);
        assertEquals(initialAmountQ + addAmountQ, predicted.getAmountQ());
        assertEquals(initialEnthalpyQ + addEnthalpyQ, predicted.getEnthalpyQ());
        assertEquals(vapor.getName(), predicted.getFluidName());
        assertEquals(2.0f, predicted.getPressureBar(), 0.0001f);
        assertEquals(initialAmountQ, network.getAmountQ());
        assertEquals(initialEnthalpyQ, network.getEnthalpyQ());
    }

    @Test
    void pressurePredictionsDoNotMutateNetworkState() {
        Fluid vapor = IFNTestSupport.vaporFluid();
        IntegratedFluidNetwork network = IFNTestSupport.newNetwork(vapor, 1_000, 0, 10.0f);
        long initialAmountQ = 100L * IntegratedFluidNetwork.AMOUNT_SCALE;
        long initialEnthalpyQ = IntegratedFluidNetwork.toEnthalpyQFromSpecific(350.0d, initialAmountQ);
        network.addState(vapor, initialAmountQ, initialEnthalpyQ);
        IFNFluidState before = network.snapshotState();

        long addAmountQ = 25L * IntegratedFluidNetwork.AMOUNT_SCALE;
        long addEnthalpyQ = IntegratedFluidNetwork.toEnthalpyQFromSpecific(450.0d, addAmountQ);
        network.predictPressureAfterAdd(vapor, addAmountQ, addEnthalpyQ);
        network.predictPressureAfterStateAdd(vapor, addAmountQ, addEnthalpyQ);
        network.predictPressureAfterExtract(25L * IntegratedFluidNetwork.AMOUNT_SCALE);

        assertEquals(before.getFluidName(), network.getFluidName());
        assertEquals(before.getAmountQ(), network.getAmountQ());
        assertEquals(before.getEnthalpyQ(), network.getEnthalpyQ());
        assertEquals(before.getPressureBar(), network.getPressure(), 0.0001f);
    }
}
