package gregtech.api.metatileentity.implementations.integratedfluid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraftforge.fluids.Fluid;

import org.junit.jupiter.api.Test;

class IFNNormalHeatPumpPlannerTest {

    @Test
    void targetTemperatureHeatingPlansOutputStateAndEnergy() {
        Fluid fluid = IFNTestSupport.liquidFluid();
        IntegratedFluidNetwork outputNetwork = IFNTestSupport.newNetwork(fluid, 10_000, 10_000, 50.0f);
        IFNMachineBatchPlanner.BatchPlan inputBatch = batch(fluid, 1.0f, 300.0d, 5);

        IFNNormalHeatPumpPlanner.Plan plan = IFNNormalHeatPumpPlanner.plan(IFNNormalHeatPumpPlanner.Request.of(
            IFNHeatPumpMode.TARGET_TEMPERATURE,
            outputNetwork,
            fluid,
            inputBatch,
            true,
            360.0f,
            5.0f,
            100,
            0.5f,
            0.5f,
            300.0f));

        assertEquals(IFNNormalHeatPumpPlanner.Status.READY, plan.getStatus());
        assertFalse(plan.isPassthrough());
        assertTrue(plan.isTargetOutputState());
        assertEquals(5L * IntegratedFluidNetwork.AMOUNT_SCALE, plan.getAmountQ());
        assertTrue(plan.getOutputSpecificEnthalpy() > inputBatch.specificEnthalpy());
        assertEquals(360.0d, plan.getOutputTemperature(), 0.001d);
        assertTrue(plan.getEnergyCostEu() > 0L);
        assertTrue(plan.getMetrics().effectiveCop() > 0.0f);
    }

    @Test
    void targetTemperatureWithinToleranceBecomesPassthrough() {
        Fluid fluid = IFNTestSupport.liquidFluid();
        IntegratedFluidNetwork outputNetwork = IFNTestSupport.newNetwork(fluid, 10_000, 10_000, 50.0f);
        IFNMachineBatchPlanner.BatchPlan inputBatch = batch(fluid, 1.0f, 300.0d, 5);

        IFNNormalHeatPumpPlanner.Plan plan = IFNNormalHeatPumpPlanner.plan(IFNNormalHeatPumpPlanner.Request.of(
            IFNHeatPumpMode.TARGET_TEMPERATURE,
            outputNetwork,
            fluid,
            inputBatch,
            true,
            300.25f,
            5.0f,
            100,
            0.5f,
            0.5f,
            300.0f));

        assertEquals(IFNNormalHeatPumpPlanner.Status.PASSTHROUGH, plan.getStatus());
        assertTrue(plan.isPassthrough());
        assertFalse(plan.isTargetOutputState());
        assertEquals(inputBatch.specificEnthalpy(), plan.getOutputSpecificEnthalpy(), 0.001d);
        assertEquals(300.0d, plan.getOutputTemperature(), 0.001d);
        assertEquals(0L, plan.getEnergyCostEu());
        assertEquals(0.0f, plan.getMetrics().effectiveCop(), 0.001f);
    }

    @Test
    void targetCopUsesConfiguredCopForDisplayMetrics() {
        Fluid fluid = IFNTestSupport.liquidFluid();
        IntegratedFluidNetwork outputNetwork = IFNTestSupport.newNetwork(fluid, 10_000, 10_000, 50.0f);
        IFNMachineBatchPlanner.BatchPlan inputBatch = batch(fluid, 1.0f, 300.0d, 5);

        IFNNormalHeatPumpPlanner.Plan plan = IFNNormalHeatPumpPlanner.plan(IFNNormalHeatPumpPlanner.Request.of(
            IFNHeatPumpMode.TARGET_COP,
            outputNetwork,
            fluid,
            inputBatch,
            true,
            360.0f,
            4.0f,
            100,
            0.5f,
            0.5f,
            300.0f));

        assertEquals(IFNNormalHeatPumpPlanner.Status.READY, plan.getStatus());
        assertTrue(plan.getOutputSpecificEnthalpy() > inputBatch.specificEnthalpy());
        assertEquals(4.0f, plan.getMetrics().cop(), 0.001f);
        assertEquals(4.0f / plan.getMetrics().efficiencyPenalty(), plan.getMetrics().effectiveCop(), 0.001f);
    }

    @Test
    void targetEnergyMovesOutputInConfiguredDirection() {
        Fluid fluid = IFNTestSupport.liquidFluid();
        IntegratedFluidNetwork outputNetwork = IFNTestSupport.newNetwork(fluid, 10_000, 10_000, 50.0f);
        IFNMachineBatchPlanner.BatchPlan inputBatch = batch(fluid, 1.0f, 360.0d, 5);

        IFNNormalHeatPumpPlanner.Plan plan = IFNNormalHeatPumpPlanner.plan(IFNNormalHeatPumpPlanner.Request.of(
            IFNHeatPumpMode.TARGET_ENERGY,
            outputNetwork,
            fluid,
            inputBatch,
            false,
            300.0f,
            5.0f,
            100,
            0.5f,
            0.5f,
            300.0f));

        assertEquals(IFNNormalHeatPumpPlanner.Status.READY, plan.getStatus());
        assertFalse(plan.isTargetOutputState());
        assertEquals(2_000L, plan.getEnergyCostEu());
        assertTrue(plan.getOutputSpecificEnthalpy() < inputBatch.specificEnthalpy());
        assertTrue(plan.getOutputTemperature() < inputBatch.temperature());
    }

    private static IFNMachineBatchPlanner.BatchPlan batch(Fluid fluid, float pressure, double temperature, int refL) {
        double specificEnthalpy = FluidThermalProperties.getSpecificEnthalpyFromPT(fluid, pressure, temperature);
        long amountQ = refL * IntegratedFluidNetwork.AMOUNT_SCALE;
        return IFNMachineBatchPlanner.planInputBatch(
            fluid,
            pressure,
            specificEnthalpy,
            amountQ,
            1_000.0d
        );
    }
}
