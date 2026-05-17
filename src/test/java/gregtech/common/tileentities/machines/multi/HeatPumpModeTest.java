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

    @Test
    void validatesModeSpecificConfiguration() {
        assertEquals(true, HeatPumpMode.TARGET_TEMPERATURE.isConfigurationValid(0.0f, 0));

        assertEquals(false, HeatPumpMode.TARGET_COP.isConfigurationValid(1.09f, 100));
        assertEquals(true, HeatPumpMode.TARGET_COP.isConfigurationValid(1.1f, 100));

        assertEquals(false, HeatPumpMode.TARGET_ENERGY.isConfigurationValid(5.0f, 0));
        assertEquals(true, HeatPumpMode.TARGET_ENERGY.isConfigurationValid(5.0f, 1));
    }
}
