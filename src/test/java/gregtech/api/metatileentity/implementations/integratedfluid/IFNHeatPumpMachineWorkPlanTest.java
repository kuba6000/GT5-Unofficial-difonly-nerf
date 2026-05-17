package gregtech.api.metatileentity.implementations.integratedfluid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IFNHeatPumpMachineWorkPlanTest {

    @Test
    void passthroughUsesShortFreeCycleButStillReportsEnergyCost() {
        IFNHeatPumpMachineWorkPlan plan = IFNHeatPumpMachineWorkPlan.of(true, false, 41L, 500);

        assertEquals(5, plan.getMaxProgressTime());
        assertEquals(0, plan.getEuT());
        assertFalse(plan.shouldSetEfficiency());
        assertEquals(3, plan.getEnergyCostPerTick());
    }

    @Test
    void normalWorkUsesComputedEnergyPerTick() {
        IFNHeatPumpMachineWorkPlan plan = IFNHeatPumpMachineWorkPlan.of(false, false, 41L, 500);

        assertEquals(20, plan.getMaxProgressTime());
        assertEquals(-3, plan.getEuT());
        assertTrue(plan.shouldSetEfficiency());
        assertEquals(10000, plan.getEfficiency());
        assertEquals(3, plan.getEnergyCostPerTick());
    }

    @Test
    void targetEnergyModeUsesRequestedEuT() {
        IFNHeatPumpMachineWorkPlan plan = IFNHeatPumpMachineWorkPlan.of(false, true, 41L, 500);

        assertEquals(20, plan.getMaxProgressTime());
        assertEquals(-500, plan.getEuT());
        assertTrue(plan.shouldSetEfficiency());
        assertEquals(10000, plan.getEfficiency());
        assertEquals(3, plan.getEnergyCostPerTick());
    }
}
