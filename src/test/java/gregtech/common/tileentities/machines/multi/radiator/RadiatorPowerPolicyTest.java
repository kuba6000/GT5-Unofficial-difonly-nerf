package gregtech.common.tileentities.machines.multi.radiator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class RadiatorPowerPolicyTest {

    @Test
    void radiatorUsesNoEuBecauseFlowIsDrivenByPressureDrop() {
        assertFalse(RadiatorPowerPolicy.requiresEnergyInput());
        assertEquals(0, RadiatorPowerPolicy.computeEUt(10_000L, 20));
        assertEquals(0, RadiatorPowerPolicy.computeEUt(0L, 1));
    }
}
