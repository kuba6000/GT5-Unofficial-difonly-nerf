package gregtech.api.metatileentity.implementations.integratedfluid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraftforge.fluids.Fluid;

import org.junit.jupiter.api.Test;

class IFNPressureModelTest {

    @Test
    void pressureModelMatchesNetworkGasStateAddPrediction() {
        Fluid vapor = IFNTestSupport.vaporFluid();
        IntegratedFluidNetwork network = IFNTestSupport.newNetwork(vapor, 1_000, 100, 10.0f);
        long initialAmountQ = 100L * IntegratedFluidNetwork.AMOUNT_SCALE;
        long initialEnthalpyQ = IntegratedFluidNetwork.toEnthalpyQFromSpecific(350.0d, initialAmountQ);
        network.addState(vapor, initialAmountQ, initialEnthalpyQ);

        long addAmountQ = 50L * IntegratedFluidNetwork.AMOUNT_SCALE;
        long addEnthalpyQ = IntegratedFluidNetwork.toEnthalpyQFromSpecific(450.0d, addAmountQ);
        IFNFluidState predictedState = network.snapshotState()
            .withAddedState(vapor, addAmountQ, addEnthalpyQ, network.getPressure());
        IFNPressureModel.NetworkLimits limits = IFNPressureModel.NetworkLimits.of(
            network.getBaseCapacity(),
            network.getAccumulatorCapacity(),
            network.getTotalCapacity(),
            network.getAccumulatorMaxPressureBar(),
            false);

        IFNPressureModel.PressureResult result = IFNPressureModel.compute(vapor, predictedState, limits);

        assertEquals(network.predictPressureAfterStateAdd(vapor, addAmountQ, addEnthalpyQ), result.getPressureBar(), 0.0001f);
    }

    @Test
    void pressureModelFlagsLiquidRuptureWhenCompleteNetworkExceedsLimit() {
        Fluid liquid = IFNTestSupport.liquidFluid();
        long amountQ = 250L * IntegratedFluidNetwork.AMOUNT_SCALE;
        long enthalpyQ = IntegratedFluidNetwork.toEnthalpyQFromSpecific(300.0d, amountQ);
        IFNFluidState state = new IFNFluidState(liquid.getName(), amountQ, enthalpyQ, 1.0f);
        IFNPressureModel.NetworkLimits limits = IFNPressureModel.NetworkLimits.of(100, 100, 200, 1.5f, false);

        IFNPressureModel.PressureResult result = IFNPressureModel.compute(liquid, state, limits);

        assertTrue(result.isRuptureRequired());
    }

    @Test
    void networkPressureUpdateDoesNotRuptureImmediately() {
        Fluid liquid = IFNTestSupport.liquidFluid();
        IntegratedFluidNetwork network = IFNTestSupport.newNetwork(liquid, 100, 100, 1.5f);
        long amountQ = 250L * IntegratedFluidNetwork.AMOUNT_SCALE;
        long enthalpyQ = IntegratedFluidNetwork.toEnthalpyQFromSpecific(300.0d, amountQ);

        network.addState(liquid, amountQ, enthalpyQ);

        assertTrue(!network.getCanonicalState().isEmpty());
        assertTrue(network.getPressureLimitEvaluation().isRuptureRequired());
    }
}
