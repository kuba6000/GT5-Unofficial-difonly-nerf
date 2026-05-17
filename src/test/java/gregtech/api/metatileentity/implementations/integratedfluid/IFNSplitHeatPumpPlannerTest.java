package gregtech.api.metatileentity.implementations.integratedfluid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraftforge.fluids.Fluid;

import org.junit.jupiter.api.Test;

class IFNSplitHeatPumpPlannerTest {

    @Test
    void targetTemperatureHeatingPlansHotAndColdOutputs() {
        Fluid fluid = IFNTestSupport.liquidFluid();
        IntegratedFluidNetwork hotOutput = IFNTestSupport.newNetwork(fluid, 10_000, 10_000, 50.0f);
        IFNMachineBatchPlanner.BatchPlan inputBatch = batch(fluid, 1.0f, 300.0d, 10);

        IFNSplitHeatPumpPlanner.Plan plan = IFNSplitHeatPumpPlanner.plan(IFNSplitHeatPumpPlanner.Request.of(
            IFNHeatPumpMode.TARGET_TEMPERATURE,
            hotOutput,
            fluid,
            inputBatch,
            true,
            360.0f,
            5.0f,
            100,
            0.60d,
            0.5f,
            0.5f,
            300.0f));

        assertEquals(IFNHeatPumpPlanStatus.READY, plan.getStatus());
        assertFalse(plan.isPassthrough());
        assertEquals(10L * IntegratedFluidNetwork.AMOUNT_SCALE, plan.getAmountQ());
        assertEquals(6L * IntegratedFluidNetwork.AMOUNT_SCALE, plan.getHotAmountQ());
        assertEquals(4L * IntegratedFluidNetwork.AMOUNT_SCALE, plan.getColdAmountQ());
        assertTrue(plan.getHotOutputSpecificEnthalpy() > inputBatch.specificEnthalpy());
        assertTrue(plan.getColdOutputSpecificEnthalpy() <= inputBatch.specificEnthalpy());
        assertEquals(360.0d, plan.getHotOutputTemperature(), 0.001d);
        assertTrue(plan.getEnergyCostEu() > 0L);
    }

    @Test
    void targetTemperatureWithinToleranceBecomesPassthrough() {
        Fluid fluid = IFNTestSupport.liquidFluid();
        IntegratedFluidNetwork hotOutput = IFNTestSupport.newNetwork(fluid, 10_000, 10_000, 50.0f);
        IFNMachineBatchPlanner.BatchPlan inputBatch = batch(fluid, 1.0f, 300.0d, 10);

        IFNSplitHeatPumpPlanner.Plan plan = IFNSplitHeatPumpPlanner.plan(IFNSplitHeatPumpPlanner.Request.of(
            IFNHeatPumpMode.TARGET_TEMPERATURE,
            hotOutput,
            fluid,
            inputBatch,
            true,
            300.25f,
            5.0f,
            100,
            0.60d,
            0.5f,
            0.5f,
            300.0f));

        assertEquals(IFNHeatPumpPlanStatus.PASSTHROUGH, plan.getStatus());
        assertTrue(plan.isPassthrough());
        assertEquals(inputBatch.specificEnthalpy(), plan.getHotOutputSpecificEnthalpy(), 0.001d);
        assertEquals(inputBatch.specificEnthalpy(), plan.getColdOutputSpecificEnthalpy(), 0.001d);
        assertEquals(0L, plan.getEnergyCostEu());
        assertEquals(0.0f, plan.getMetrics().effectiveCop(), 0.001f);
    }

    @Test
    void targetCopUsesConfiguredCopForDisplayMetrics() {
        Fluid fluid = IFNTestSupport.liquidFluid();
        IntegratedFluidNetwork hotOutput = IFNTestSupport.newNetwork(fluid, 10_000, 10_000, 50.0f);
        IFNMachineBatchPlanner.BatchPlan inputBatch = batch(fluid, 1.0f, 300.0d, 10);

        IFNSplitHeatPumpPlanner.Plan plan = IFNSplitHeatPumpPlanner.plan(IFNSplitHeatPumpPlanner.Request.of(
            IFNHeatPumpMode.TARGET_COP,
            hotOutput,
            fluid,
            inputBatch,
            true,
            360.0f,
            4.0f,
            100,
            0.60d,
            0.5f,
            0.5f,
            300.0f));

        assertEquals(IFNHeatPumpPlanStatus.READY, plan.getStatus());
        assertTrue(plan.getHotOutputSpecificEnthalpy() > inputBatch.specificEnthalpy());
        assertEquals(4.0f, plan.getMetrics().cop(), 0.001f);
        assertEquals(4.0f / plan.getMetrics().efficiencyPenalty(), plan.getMetrics().effectiveCop(), 0.001f);
    }

    @Test
    void coolingTargetAboveInputIsInvalidConfiguration() {
        Fluid fluid = IFNTestSupport.liquidFluid();
        IntegratedFluidNetwork hotOutput = IFNTestSupport.newNetwork(fluid, 10_000, 10_000, 50.0f);
        IFNMachineBatchPlanner.BatchPlan inputBatch = batch(fluid, 1.0f, 300.0d, 10);

        IFNSplitHeatPumpPlanner.Plan plan = IFNSplitHeatPumpPlanner.plan(IFNSplitHeatPumpPlanner.Request.of(
            IFNHeatPumpMode.TARGET_TEMPERATURE,
            hotOutput,
            fluid,
            inputBatch,
            false,
            360.0f,
            5.0f,
            100,
            0.60d,
            0.5f,
            0.5f,
            300.0f));

        assertEquals(IFNHeatPumpPlanStatus.INVALID_CONFIGURATION, plan.getStatus());
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
