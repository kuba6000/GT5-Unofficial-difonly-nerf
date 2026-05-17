package gregtech.common.tileentities.machines.multi;

import static org.junit.jupiter.api.Assertions.assertEquals;

import gregtech.api.metatileentity.implementations.integratedfluid.IFNHeatPumpMode;

import org.junit.jupiter.api.Test;

class HeatPumpModeTest {

    @Test
    void mapsMachineModesToIfnPlannerModes() {
        assertEquals(IFNHeatPumpMode.TARGET_TEMPERATURE, HeatPumpMode.TARGET_TEMPERATURE.toIFNMode());
        assertEquals(IFNHeatPumpMode.TARGET_COP, HeatPumpMode.TARGET_COP.toIFNMode());
        assertEquals(IFNHeatPumpMode.TARGET_ENERGY, HeatPumpMode.TARGET_ENERGY.toIFNMode());
    }
}
