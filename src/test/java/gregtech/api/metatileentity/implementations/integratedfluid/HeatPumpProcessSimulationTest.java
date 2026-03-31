package gregtech.api.metatileentity.implementations.integratedfluid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraftforge.fluids.Fluid;

import org.junit.jupiter.api.Test;

class HeatPumpProcessSimulationTest {

    private static final float PRESSURE_RATIO_LIMIT = 1.00f;

    @Test
    void twoNetworkHeatingSimulationReachesTargetTemperature() {
        Fluid fluid = IFNTestSupport.pressureSensitiveLiquidFluid();
        IntegratedFluidNetwork inputNetwork = IFNTestSupport.newNetwork(fluid, 1_000, 100, 10.0f);
        IntegratedFluidNetwork outputNetwork = IFNTestSupport.newNetwork(fluid, 100, 100, 10.0f);

        long initialAmountQ = 500L * IntegratedFluidNetwork.AMOUNT_SCALE;
        inputNetwork.addState(fluid, initialAmountQ, IntegratedFluidNetwork.toEnthalpyQFromSpecific(301.0d, initialAmountQ));

        long requestedAmountQ = 130L * IntegratedFluidNetwork.AMOUNT_SCALE;
        double targetTemperature = 320.0d;

        double outputSpecificEnthalpy = IFNMachineThermo.computeTargetSpecificEnthalpyForStateAdd(
            outputNetwork,
            fluid,
            targetTemperature,
            requestedAmountQ
        );

        var plan = IFNStateTransferPlanner.planSingleOutputStateAdd(
            inputNetwork,
            outputNetwork,
            fluid,
            outputSpecificEnthalpy,
            requestedAmountQ,
            10.0f
        );

        assertTrue(plan.acceptedAmountQ >= IntegratedFluidNetwork.AMOUNT_SCALE);

        applySingleOutputTransfer(inputNetwork, outputNetwork, fluid, outputSpecificEnthalpy, plan.acceptedAmountQ);

        assertEquals(targetTemperature, outputNetwork.getTemperature(), 0.05d);
    }

    @Test
    void twoNetworkCoolingSimulationReachesTargetTemperature() {
        Fluid fluid = IFNTestSupport.pressureSensitiveLiquidFluid();
        IntegratedFluidNetwork inputNetwork = IFNTestSupport.newNetwork(fluid, 1_000, 100, 10.0f);
        IntegratedFluidNetwork outputNetwork = IFNTestSupport.newNetwork(fluid, 100, 100, 10.0f);

        long initialAmountQ = 500L * IntegratedFluidNetwork.AMOUNT_SCALE;
        inputNetwork.addState(fluid, initialAmountQ, IntegratedFluidNetwork.toEnthalpyQFromSpecific(361.0d, initialAmountQ));

        long requestedAmountQ = 130L * IntegratedFluidNetwork.AMOUNT_SCALE;
        double targetTemperature = 280.0d;

        double outputSpecificEnthalpy = IFNMachineThermo.computeTargetSpecificEnthalpyForStateAdd(
            outputNetwork,
            fluid,
            targetTemperature,
            requestedAmountQ
        );

        var plan = IFNStateTransferPlanner.planSingleOutputStateAdd(
            inputNetwork,
            outputNetwork,
            fluid,
            outputSpecificEnthalpy,
            requestedAmountQ,
            10.0f
        );

        assertTrue(plan.acceptedAmountQ >= IntegratedFluidNetwork.AMOUNT_SCALE);

        applySingleOutputTransfer(inputNetwork, outputNetwork, fluid, outputSpecificEnthalpy, plan.acceptedAmountQ);

        assertEquals(targetTemperature, outputNetwork.getTemperature(), 0.05d);
    }

    @Test
    void twoNetworkSimulationStopsAtPressureGate() {
        Fluid fluid = IFNTestSupport.liquidFluid();
        IntegratedFluidNetwork inputNetwork = IFNTestSupport.newNetwork(fluid, 1_000, 100, 10.0f);
        IntegratedFluidNetwork outputNetwork = IFNTestSupport.newNetwork(fluid, 100, 100, 10.0f);

        long initialAmountQ = 500L * IntegratedFluidNetwork.AMOUNT_SCALE;
        inputNetwork.addState(fluid, initialAmountQ, IntegratedFluidNetwork.toEnthalpyQFromSpecific(300.0d, initialAmountQ));

        long requestedAmountQ = 130L * IntegratedFluidNetwork.AMOUNT_SCALE;
        double outputSpecificEnthalpy = IFNMachineThermo.computeTargetSpecificEnthalpyForStateAdd(
            outputNetwork,
            fluid,
            300.0d,
            requestedAmountQ
        );

        var plan = IFNStateTransferPlanner.planSingleOutputStateAdd(
            inputNetwork,
            outputNetwork,
            fluid,
            outputSpecificEnthalpy,
            requestedAmountQ,
            PRESSURE_RATIO_LIMIT
        );

        assertTrue(plan.acceptedAmountQ < requestedAmountQ);
        assertTrue(plan.predictedOutputPressure <= plan.predictedInputPressure * PRESSURE_RATIO_LIMIT + 1.0e-4f);

        applySingleOutputTransfer(inputNetwork, outputNetwork, fluid, outputSpecificEnthalpy, plan.acceptedAmountQ);

        assertTrue(outputNetwork.getPressure() <= inputNetwork.getPressure() * PRESSURE_RATIO_LIMIT + 1.0e-4f);
    }

    private static void applySingleOutputTransfer(IntegratedFluidNetwork inputNetwork, IntegratedFluidNetwork outputNetwork,
        Fluid fluid, double outputSpecificEnthalpy, long amountQ) {
        IntegratedFluidNetwork.ExtractedPayload extracted = inputNetwork.extractProportional(amountQ, false);
        long outputEnthalpyQ = IntegratedFluidNetwork.toEnthalpyQFromSpecific(outputSpecificEnthalpy, extracted.amountQ);
        outputNetwork.addState(fluid, extracted.amountQ, outputEnthalpyQ);
    }
}
