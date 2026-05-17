package gregtech.api.metatileentity.implementations.integratedfluid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraftforge.fluids.Fluid;

import org.junit.jupiter.api.Test;

class IFNHeatExchangerPlannerTest {

    @Test
    void targetTemperatureHeatingRedPlansBothOutputStates() {
        Fluid fluid = IFNTestSupport.liquidFluid();
        IntegratedFluidNetwork redOut = IFNTestSupport.newNetwork(fluid, 10_000, 10_000, 50.0f);
        IntegratedFluidNetwork blueOut = IFNTestSupport.newNetwork(fluid, 10_000, 10_000, 50.0f);
        IFNMachineBatchPlanner.BatchPlan redInput = batch(fluid, 1.0f, 420.0d, 5);
        IFNMachineBatchPlanner.BatchPlan blueInput = batch(fluid, 1.0f, 340.0d, 5);

        IFNHeatExchangerPlanner.Plan plan = IFNHeatExchangerPlanner.plan(IFNHeatExchangerPlanner.Request.of(
            IFNHeatPumpMode.TARGET_TEMPERATURE,
            redOut,
            blueOut,
            fluid,
            fluid,
            redInput,
            blueInput,
            true,
            500.0f,
            5.0f,
            100,
            0.5f,
            0.5f));

        assertEquals(IFNHeatPumpPlanStatus.READY, plan.getStatus());
        assertFalse(plan.isPassthrough());
        assertEquals(5L * IntegratedFluidNetwork.AMOUNT_SCALE, plan.getRedAmountQ());
        assertEquals(5L * IntegratedFluidNetwork.AMOUNT_SCALE, plan.getBlueAmountQ());
        assertTrue(plan.getRedOutputSpecificEnthalpy() > redInput.specificEnthalpy());
        assertTrue(plan.getBlueOutputSpecificEnthalpy() < blueInput.specificEnthalpy());
        assertTrue(plan.getEnergyCostEu() > 0L);
        assertEquals(500.0d, plan.getTargetOutputTemperature(), 0.001d);
        assertTrue(plan.getMetrics().effectiveCop() > 0.0f);
    }

    @Test
    void targetCopCoolingBlueUsesConfiguredCopForDisplayMetrics() {
        Fluid fluid = IFNTestSupport.liquidFluid();
        IntegratedFluidNetwork redOut = IFNTestSupport.newNetwork(fluid, 10_000, 10_000, 50.0f);
        IntegratedFluidNetwork blueOut = IFNTestSupport.newNetwork(fluid, 10_000, 10_000, 50.0f);
        IFNMachineBatchPlanner.BatchPlan redInput = batch(fluid, 1.0f, 360.0d, 5);
        IFNMachineBatchPlanner.BatchPlan blueInput = batch(fluid, 1.0f, 330.0d, 5);

        IFNHeatExchangerPlanner.Plan plan = IFNHeatExchangerPlanner.plan(IFNHeatExchangerPlanner.Request.of(
            IFNHeatPumpMode.TARGET_COP,
            redOut,
            blueOut,
            fluid,
            fluid,
            redInput,
            blueInput,
            false,
            300.0f,
            4.0f,
            100,
            0.5f,
            0.5f));

        assertEquals(IFNHeatPumpPlanStatus.READY, plan.getStatus());
        assertTrue(plan.getBlueOutputSpecificEnthalpy() < blueInput.specificEnthalpy());
        assertTrue(plan.getRedOutputSpecificEnthalpy() > redInput.specificEnthalpy());
        assertEquals(4.0f, plan.getMetrics().cop(), 0.001f);
        assertEquals(4.0f / plan.getMetrics().efficiencyPenalty(), plan.getMetrics().effectiveCop(), 0.001f);
    }

    @Test
    void targetTemperatureWithinToleranceBecomesPassthrough() {
        Fluid fluid = IFNTestSupport.liquidFluid();
        IntegratedFluidNetwork redOut = IFNTestSupport.newNetwork(fluid, 10_000, 10_000, 50.0f);
        IntegratedFluidNetwork blueOut = IFNTestSupport.newNetwork(fluid, 10_000, 10_000, 50.0f);
        IFNMachineBatchPlanner.BatchPlan redInput = batch(fluid, 1.0f, 300.0d, 5);
        IFNMachineBatchPlanner.BatchPlan blueInput = batch(fluid, 1.0f, 360.0d, 5);

        IFNHeatExchangerPlanner.Plan plan = IFNHeatExchangerPlanner.plan(IFNHeatExchangerPlanner.Request.of(
            IFNHeatPumpMode.TARGET_TEMPERATURE,
            redOut,
            blueOut,
            fluid,
            fluid,
            redInput,
            blueInput,
            true,
            300.25f,
            5.0f,
            100,
            0.5f,
            0.5f));

        assertEquals(IFNHeatPumpPlanStatus.PASSTHROUGH, plan.getStatus());
        assertTrue(plan.isPassthrough());
        assertEquals(redInput.specificEnthalpy(), plan.getRedOutputSpecificEnthalpy(), 0.001d);
        assertEquals(blueInput.specificEnthalpy(), plan.getBlueOutputSpecificEnthalpy(), 0.001d);
        assertEquals(0L, plan.getEnergyCostEu());
        assertEquals(0.0f, plan.getMetrics().effectiveCop(), 0.001f);
    }

    @Test
    void physicallyImpossibleTargetIsInvalidConfiguration() {
        Fluid fluid = IFNTestSupport.liquidFluid();
        IntegratedFluidNetwork redOut = IFNTestSupport.newNetwork(fluid, 10_000, 10_000, 50.0f);
        IntegratedFluidNetwork blueOut = IFNTestSupport.newNetwork(fluid, 10_000, 10_000, 50.0f);
        IFNMachineBatchPlanner.BatchPlan redInput = batch(fluid, 1.0f, 330.0d, 5);
        IFNMachineBatchPlanner.BatchPlan blueInput = batch(fluid, 1.0f, 360.0d, 5);

        IFNHeatExchangerPlanner.Plan plan = IFNHeatExchangerPlanner.plan(IFNHeatExchangerPlanner.Request.of(
            IFNHeatPumpMode.TARGET_TEMPERATURE,
            redOut,
            blueOut,
            fluid,
            fluid,
            redInput,
            blueInput,
            true,
            320.0f,
            5.0f,
            100,
            0.5f,
            0.5f));

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
