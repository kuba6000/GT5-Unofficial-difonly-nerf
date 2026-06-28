package gregtech.api.metatileentity.implementations.integratedfluid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraftforge.fluids.Fluid;

import org.junit.jupiter.api.Test;

class IFNPressureRegulatorTransferPlannerTest {

    @Test
    void outputPressureAboveSetpointBlocksTransfer() {
        Fluid fluid = IFNTestSupport.pressureSensitiveLiquidFluid();
        IntegratedFluidNetwork input = networkAtPressure(fluid, 4.0d);
        IntegratedFluidNetwork output = networkAtPressure(fluid, 8.0d);
        double outputSpecificEnthalpy = specificEnthalpy(fluid, output.getPressure());

        IFNPressureRegulatorTransferPlanner.Plan plan = IFNPressureRegulatorTransferPlanner.plan(
            input,
            output,
            fluid,
            outputSpecificEnthalpy,
            6.0f,
            0.1f,
            1_000L * IntegratedFluidNetwork.AMOUNT_SCALE,
            100L * IntegratedFluidNetwork.AMOUNT_SCALE);

        assertEquals(IFNPressureRegulatorTransferPlanner.Status.OUTPUT_BLOCKED, plan.status());
        assertEquals(0L, plan.acceptedAmountQ());
    }

    @Test
    void smallestPacketAboveOvershootToleranceBlocksTransfer() {
        Fluid fluid = IFNTestSupport.pressureSensitiveLiquidFluid();
        IntegratedFluidNetwork input = networkAtPressure(fluid, 4.0d);
        IntegratedFluidNetwork output = networkAtPressure(fluid, 5.99d, 1, 1);
        double outputSpecificEnthalpy = specificEnthalpy(fluid, output.getPressure());
        long smallestPacketQ = IntegratedFluidNetwork.AMOUNT_SCALE;
        float setpoint = 6.0f;
        float tolerance = 0.001f;
        float predictedPressure = output.predictPressureAfterStateAdd(
            fluid,
            smallestPacketQ,
            IntegratedFluidNetwork.toEnthalpyQFromSpecific(outputSpecificEnthalpy, smallestPacketQ));

        assertTrue(
            predictedPressure > setpoint + tolerance,
            "test fixture must make the smallest packet exceed regulator tolerance");

        IFNPressureRegulatorTransferPlanner.Plan plan = IFNPressureRegulatorTransferPlanner.plan(
            input,
            output,
            fluid,
            outputSpecificEnthalpy,
            setpoint,
            tolerance,
            100L * IntegratedFluidNetwork.AMOUNT_SCALE,
            smallestPacketQ);

        assertEquals(IFNPressureRegulatorTransferPlanner.Status.OUTPUT_BLOCKED, plan.status());
        assertEquals(0L, plan.acceptedAmountQ());
    }

    @Test
    void frozenInputNetworkBlocksTransfer() {
        Fluid fluid = IFNTestSupport.pressureSensitiveLiquidFluid();
        IntegratedFluidNetwork input = networkAtPressure(fluid, 4.0d);
        IntegratedFluidNetwork output = networkAtPressure(fluid, 2.0d);
        double outputSpecificEnthalpy = specificEnthalpy(fluid, output.getPressure());
        input.freeze("pressure regulator test");

        IFNPressureRegulatorTransferPlanner.Plan plan = IFNPressureRegulatorTransferPlanner.plan(
            input,
            output,
            fluid,
            outputSpecificEnthalpy,
            6.0f,
            0.1f,
            100L * IntegratedFluidNetwork.AMOUNT_SCALE,
            10L * IntegratedFluidNetwork.AMOUNT_SCALE);

        assertEquals(IFNPressureRegulatorTransferPlanner.Status.INPUT_BLOCKED, plan.status());
        assertEquals(0L, plan.acceptedAmountQ());
    }

    private static IntegratedFluidNetwork networkAtPressure(Fluid fluid, double pressureBar) {
        return networkAtPressure(fluid, pressureBar, 10_000, 10_000);
    }

    private static IntegratedFluidNetwork networkAtPressure(Fluid fluid, double pressureBar, int baseCapacity,
        int accumulatorCapacity) {
        IntegratedFluidNetwork network = IFNTestSupport.newNetwork(fluid, baseCapacity, accumulatorCapacity, 100.0f);
        IFNTestSupport.seedNetworkAtPressureAndTemperature(network, fluid, pressureBar, 300.0d);
        return network;
    }

    private static double specificEnthalpy(Fluid fluid, float pressureBar) {
        return FluidThermalProperties.getSpecificEnthalpyFromPT(fluid, pressureBar, 300.0d);
    }
}
