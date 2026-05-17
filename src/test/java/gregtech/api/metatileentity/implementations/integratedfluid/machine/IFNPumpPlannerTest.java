package gregtech.api.metatileentity.implementations.integratedfluid.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class IFNPumpPlannerTest {

    @Test
    void targetOutputPressureIsClampedByPumpDifferentialLimit() {
        IFNPumpPlanner.Plan plan = IFNPumpPlanner.plan(IFNPumpPlanner.Request.targetOutputPressure(
            1.0f,
            1.0f,
            20.0f,
            5.0f,
            1000.0d));

        assertEquals(IFNPumpPlanner.Status.READY, plan.status());
        assertEquals(6.0f, plan.machineOutputPressureBar(), 0.0001f);
        assertEquals(1000.0d, plan.flowLitersPerTick(), 0.0001d);
    }

    @Test
    void backpressureBlocksFlowWhenOutputPressureIsTooHigh() {
        IFNPumpPlanner.Plan plan = IFNPumpPlanner.plan(IFNPumpPlanner.Request.targetOutputPressure(
            1.0f,
            6.0f,
            6.0f,
            5.0f,
            1000.0d));

        assertEquals(IFNPumpPlanner.Status.BACKPRESSURE_BLOCKED, plan.status());
        assertEquals(0.0d, plan.flowLitersPerTick(), 0.0001d);
    }

    @Test
    void targetPressureDifferentialAddsToInputPressure() {
        IFNPumpPlanner.Plan plan = IFNPumpPlanner.plan(IFNPumpPlanner.Request.targetPressureDifferential(
            2.0f,
            3.0f,
            4.0f,
            10.0f,
            1000.0d));

        assertEquals(IFNPumpPlanner.Status.READY, plan.status());
        assertEquals(6.0f, plan.machineOutputPressureBar(), 0.0001f);
        assertEquals(300.0d, plan.flowLitersPerTick(), 0.0001d);
    }

    @Test
    void targetFlowCapsFlowAtConfiguredLimit() {
        IFNPumpPlanner.Plan plan = IFNPumpPlanner.plan(IFNPumpPlanner.Request.targetFlow(
            1.0f,
            1.0f,
            250.0d,
            5.0f,
            1000.0d));

        assertEquals(IFNPumpPlanner.Status.READY, plan.status());
        assertEquals(250.0d, plan.flowLitersPerTick(), 0.0001d);
    }
}
