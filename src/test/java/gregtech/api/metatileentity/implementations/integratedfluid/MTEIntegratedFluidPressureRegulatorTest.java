package gregtech.api.metatileentity.implementations.integratedfluid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;

import org.junit.jupiter.api.Test;

class MTEIntegratedFluidPressureRegulatorTest {

    @Test
    void persistedConfigurationRoundTrips() {
        MTEIntegratedFluidPressureRegulator regulator = newRegulator();
        regulator.setSetpointPressureBar(6.0f);
        regulator.setOvershootToleranceBar(0.25f);
        regulator.setMaxPacketAmountQ(25L * IntegratedFluidNetwork.AMOUNT_SCALE);
        NBTTagCompound tag = new NBTTagCompound();

        regulator.saveNBTData(tag);
        MTEIntegratedFluidPressureRegulator loaded = newRegulator();
        loaded.loadNBTData(tag);

        assertEquals(6.0f, loaded.getSetpointPressureBar(), 0.0001f);
        assertEquals(0.25f, loaded.getOvershootToleranceBar(), 0.0001f);
        assertEquals(25L * IntegratedFluidNetwork.AMOUNT_SCALE, loaded.getMaxPacketAmountQ());
    }

    @Test
    void transferBetweenNetworksUsesConfiguredLimits() {
        Fluid fluid = IFNTestSupport.pressureSensitiveLiquidFluid();
        IntegratedFluidNetwork input = networkAtPressure(fluid, 4.0d);
        IntegratedFluidNetwork output = networkAtPressure(fluid, 2.0d);
        MTEIntegratedFluidPressureRegulator regulator = newRegulator();
        regulator.setSetpointPressureBar(3.0f);
        regulator.setOvershootToleranceBar(0.05f);
        regulator.setMaxPacketAmountQ(25L * IntegratedFluidNetwork.AMOUNT_SCALE);

        IFNPressureRegulatorTransferPlanner.Plan plan = regulator.transferBetweenNetworks(input, output);

        assertEquals(IFNPressureRegulatorTransferPlanner.Status.ACCEPTED, plan.status());
        assertTrue(plan.acceptedAmountQ() <= 25L * IntegratedFluidNetwork.AMOUNT_SCALE);
        assertTrue(output.getPressure() <= 3.05f);
    }

    @Test
    void guiConfigurationUsesPlayerFacingUnits() {
        MTEIntegratedFluidPressureRegulator regulator = newRegulator();

        regulator.setSetpointPressureCentibar(325);
        regulator.setOvershootToleranceCentibar(7);
        regulator.setMaxPacketAmountL(25);

        assertEquals(3.25f, regulator.getSetpointPressureBar(), 0.0001f);
        assertEquals(0.07f, regulator.getOvershootToleranceBar(), 0.0001f);
        assertEquals(25L * IntegratedFluidNetwork.AMOUNT_SCALE, regulator.getMaxPacketAmountQ());
        assertEquals(325, regulator.getSetpointPressureCentibar());
        assertEquals(7, regulator.getOvershootToleranceCentibar());
        assertEquals(25, regulator.getMaxPacketAmountL());
    }

    private static MTEIntegratedFluidPressureRegulator newRegulator() {
        return new MTEIntegratedFluidPressureRegulator(
            "test.pressure_regulator",
            1,
            new String[] { "Test Pressure Regulator" },
            null);
    }

    private static IntegratedFluidNetwork networkAtPressure(Fluid fluid, double pressureBar) {
        IntegratedFluidNetwork network = IFNTestSupport.newNetwork(fluid, 10_000, 10_000, 100.0f);
        IFNTestSupport.seedNetworkAtPressureAndTemperature(network, fluid, pressureBar, 300.0d);
        return network;
    }
}
