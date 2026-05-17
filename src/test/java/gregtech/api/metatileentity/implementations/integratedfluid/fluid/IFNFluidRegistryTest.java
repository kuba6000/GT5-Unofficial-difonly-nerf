package gregtech.api.metatileentity.implementations.integratedfluid.fluid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraftforge.fluids.Fluid;

import org.junit.jupiter.api.Test;

import gregtech.api.metatileentity.implementations.integratedfluid.FluidThermalProperties;
import gregtech.api.metatileentity.implementations.integratedfluid.IFNFluidThermalRegistry;

class IFNFluidRegistryTest {

    @Test
    void hardcodedBasicFluidsAreSupportedByName() {
        IFNFluidRegistry.init();

        assertSupported("water");
        assertSupported("steam");
        assertSupported("hydrogen");
        assertSupported("helium");
        assertSupported("oxygen");
        assertSupported("nitrogen");
    }

    @Test
    void waterAndSteamResolveToTheSameSubstanceDefinition() {
        IFNFluidRegistry.init();

        IFNFluidDefinition water = IFNFluidRegistry.require("water");
        IFNFluidDefinition steam = IFNFluidRegistry.require("steam");

        assertEquals("water", water.substanceId());
        assertEquals(water.substanceId(), steam.substanceId());
    }

    @Test
    void supportedFluidsAreThermallyRegistered() {
        IFNFluidRegistry.init();
        Fluid hydrogen = new Fluid("hydrogen");

        assertTrue(IFNFluidThermalRegistry.isRegistered(hydrogen));
        assertTrue(FluidThermalProperties.getCriticalTemperature(hydrogen) > 0.0d);
        assertTrue(FluidThermalProperties.getCriticalPressure(hydrogen) > 0.0d);
    }

    private static void assertSupported(String fluidId) {
        Fluid fluid = new Fluid(fluidId);

        assertNotNull(IFNFluidRegistry.get(fluidId));
        assertTrue(IFNFluidRegistry.isSupported(fluid), fluidId);
    }
}
