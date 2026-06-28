package gregtech.test.fullgame;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.Test;

import gtPlusPlus.core.tileentities.general.TileEntityInfiniteFluid;
import gregtech.api.metatileentity.implementations.integratedfluid.IFNFluidThermalRegistration;
import gregtech.api.metatileentity.implementations.integratedfluid.IFNFluidThermalRegistry;
import gregtech.api.metatileentity.implementations.integratedfluid.IntegratedFluidNetwork;
import gregtech.api.metatileentity.implementations.integratedfluid.MTEIntegratedFluidInjectorHatch;
import gregtech.api.metatileentity.implementations.integratedfluid.MTEIntegratedFluidInputHatch;
import gregtech.api.metatileentity.implementations.integratedfluid.MTEIntegratedFluidOutputHatch;
import gregtech.api.metatileentity.implementations.integratedfluid.MTEIntegratedFluidPipe;
import gregtech.api.metatileentity.implementations.integratedfluid.MTEIntegratedFluidPressureRegulator;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.common.tileentities.machines.multi.HeatPumpMode;
import gregtech.common.tileentities.machines.multi.MTEHeatPump;
import gregtech.common.tileentities.storage.MTEDigitalTankBase;

class FullGameServerSmokeTest {

    @Test
    void dedicatedServerHasLoadedOverworld() {
        MinecraftServer server = MinecraftServer.getServer();

        assertNotNull(server, "full-game tests must run inside a Minecraft server");
        assertTrue(server.isDedicatedServer(), "full-game server QA must run on the dedicated server side");

        WorldServer overworld = server.worldServerForDimension(0);
        assertNotNull(overworld, "full-game server QA needs a loaded overworld");
    }

    @Test
    void integratedFluidPipePlacedInServerWorldCreatesNetwork() {
        FullGameServerQaHarness qa = FullGameServerQaHarness.overworld();

        try {
            MTEIntegratedFluidPipe pipe = qa.placeMetaTile(
                0,
                200,
                0,
                FullGameServerQaHarness.INTEGRATED_FLUID_PIPE_ID,
                MTEIntegratedFluidPipe.class);

            qa.tickTrackedTiles(25);

            assertNotNull(pipe.getNetwork(), "server-ticked IFN pipe must join or create a VFN network");
        } finally {
            qa.clearTrackedBlocks();
        }
    }

    @Test
    void gtppCreativeTankFeedsSuperTankVfnHeatPumpAndOutputStorage() throws Exception {
        FullGameServerQaHarness qa = FullGameServerQaHarness.overworld();
        int x = 10;
        int y = 200;
        int z = 0;

        try {
            TileEntityInfiniteFluid creativeTank = qa.placeCreativeTank(x, y, z);
            MTEDigitalTankBase sourceSuperTank = qa.placeMetaTile(
                x + 1,
                y,
                z,
                FullGameServerQaHarness.SUPER_TANK_LV_ID,
                MTEDigitalTankBase.class);
            MTEIntegratedFluidInjectorHatch injector = qa.placeMetaTile(
                x + 2,
                y,
                z,
                FullGameServerQaHarness.INTEGRATED_FLUID_INJECTOR_HATCH_ID,
                MTEIntegratedFluidInjectorHatch.class);
            MTEIntegratedFluidPipe inputPipe = qa.placeMetaTile(
                x + 3,
                y,
                z,
                FullGameServerQaHarness.INTEGRATED_FLUID_PIPE_ID,
                MTEIntegratedFluidPipe.class);
            MTEIntegratedFluidInputHatch heatPumpInput = qa.placeMetaTile(
                x + 4,
                y,
                z,
                FullGameServerQaHarness.INTEGRATED_FLUID_INPUT_HATCH_ID,
                MTEIntegratedFluidInputHatch.class);
            MTEHeatPump heatPump = qa.placeMetaTile(
                x + 5,
                y,
                z,
                FullGameServerQaHarness.HEAT_PUMP_CONTROLLER_ID,
                MTEHeatPump.class);
            MTEIntegratedFluidOutputHatch heatPumpOutput = qa.placeMetaTile(
                x + 6,
                y,
                z,
                FullGameServerQaHarness.INTEGRATED_FLUID_OUTPUT_HATCH_ID,
                MTEIntegratedFluidOutputHatch.class);
            MTEIntegratedFluidPipe outputPipe = qa.placeMetaTile(
                x + 7,
                y,
                z,
                FullGameServerQaHarness.INTEGRATED_FLUID_PIPE_ID,
                MTEIntegratedFluidPipe.class);
            MTEDigitalTankBase outputSuperTank = qa.placeMetaTile(
                x + 8,
                y,
                z,
                FullGameServerQaHarness.SUPER_TANK_LV_ID,
                MTEDigitalTankBase.class);

            FullGameServerQaHarness.face(injector, ForgeDirection.EAST);
            FullGameServerQaHarness.face(heatPumpInput, ForgeDirection.WEST);
            FullGameServerQaHarness.face(heatPumpOutput, ForgeDirection.EAST);
            FullGameServerQaHarness.connect(inputPipe, ForgeDirection.WEST, ForgeDirection.EAST);
            FullGameServerQaHarness.connect(outputPipe, ForgeDirection.WEST, ForgeDirection.EAST);
            qa.rebuildNetwork(inputPipe);
            qa.rebuildNetwork(outputPipe);
            qa.tickTrackedTiles(40);

            IFNFluidThermalRegistration.init();
            creativeTank.setFluid(new FluidStack(FluidRegistry.WATER, 4_000));
            FluidStack waterFromCreativeTank = creativeTank.drain(ForgeDirection.EAST, 4_000, true);
            assertNotNull(waterFromCreativeTank, "GT++ creative tank must generate water");
            assertEquals(4_000, sourceSuperTank.fill(waterFromCreativeTank, true), "source Super Tank must accept water");

            FluidStack waterFromSuperTank = sourceSuperTank.drain(1_000, true);
            assertNotNull(waterFromSuperTank, "source Super Tank must pump water toward VFN injector");
            assertEquals(FluidRegistry.WATER, waterFromSuperTank.getFluid(), "source Super Tank must pump water");

            IntegratedFluidNetwork injectorNetwork = injector.getNetwork();
            assertNotNull(injectorNetwork, "VFN injector must have a network before accepting water");
            assertTrue(IFNFluidThermalRegistry.isRegistered(FluidRegistry.WATER), "water must be registered for IFN thermal transfer");
            int injectedAmount = injector.fill(ForgeDirection.WEST, waterFromSuperTank, true);
            assertTrue(
                injectedAmount > 0,
                "VFN injector must accept water; accepted="
                    + injectedAmount
                    + ", networkStatus="
                    + injectorNetwork.getNetworkStatus()
                    + ", pressure="
                    + injectorNetwork.getPressure()
                    + ", totalCapacity="
                    + injectorNetwork.getTotalCapacity()
                    + ", availableSpace="
                    + injectorNetwork.getAvailableSpace()
                    + ", members="
                    + injectorNetwork.getMemberCount());

            qa.tickTrackedTiles(40);

            IntegratedFluidNetwork inputNetwork = heatPumpInput.getNetwork();
            assertNotNull(inputNetwork, "heat pump input VFN must exist");
            assertTrue(inputNetwork.getStoredAmount() > 0, "heat pump input VFN must store injected water");
            assertTrue(inputPipe.getNetwork() == inputNetwork, "input pipe must be in the heat pump input VFN");

            IntegratedFluidNetwork outputNetwork = heatPumpOutput.getNetwork();
            assertNotNull(outputNetwork, "heat pump output VFN must exist");
            assertTrue(outputPipe.getNetwork() == outputNetwork, "output pipe must be in the heat pump output VFN");

            FullGameServerQaHarness.attachIntegratedHatches(
                heatPump,
                Collections.singletonList(heatPumpInput),
                Collections.singletonList(heatPumpOutput));
            heatPump.setOperatingMode(HeatPumpMode.TARGET_TEMPERATURE);
            heatPump.setTargetTemperatureDelta(310.0f);
            heatPump.setFluidAmountPerOperation(1_000);

            CheckRecipeResult result = heatPump.checkProcessing();
            assertTrue(result.wasSuccessful(), "heat pump must process water from input VFN into output VFN");
            for (int attempt = 0; attempt < 5 && outputNetwork.getStoredAmount() <= 0; attempt++) {
                heatPump.checkProcessing();
            }
            assertTrue(outputNetwork.getStoredAmount() > 0, "heat pump output VFN must store processed water");

            FluidStack waterAfterHeatPump = outputNetwork.drainFluid(1_000, true);
            assertNotNull(waterAfterHeatPump, "heat pump output VFN must expose processed water");
            int outputAccepted = outputSuperTank.fill(waterAfterHeatPump, true);
            assertTrue(outputAccepted > 0, "output Super Tank must store water after heat pump output");
            assertNotNull(outputSuperTank.getFluid(), "output Super Tank must contain water after the chain");
            assertEquals(FluidRegistry.WATER, outputSuperTank.getFluid()
                .getFluid(), "output Super Tank must contain water");
        } finally {
            qa.clearTrackedBlocks();
        }
    }

    @Test
    void vfnInjectorOnlyAcceptsFluidAfterPhysicalPipeAndHatchConnection() {
        FullGameServerQaHarness qa = FullGameServerQaHarness.overworld();
        int x = 30;
        int y = 200;
        int z = 0;

        try {
            MTEIntegratedFluidInjectorHatch injector = qa.placeMetaTile(
                x,
                y,
                z,
                FullGameServerQaHarness.INTEGRATED_FLUID_INJECTOR_HATCH_ID,
                MTEIntegratedFluidInjectorHatch.class);
            MTEIntegratedFluidPipe pipe = qa.placeMetaTile(
                x + 1,
                y,
                z,
                FullGameServerQaHarness.INTEGRATED_FLUID_PIPE_ID,
                MTEIntegratedFluidPipe.class);
            MTEIntegratedFluidInputHatch inputHatch = qa.placeMetaTile(
                x + 2,
                y,
                z,
                FullGameServerQaHarness.INTEGRATED_FLUID_INPUT_HATCH_ID,
                MTEIntegratedFluidInputHatch.class);

            qa.tickTrackedTiles(40);

            IFNFluidThermalRegistration.init();
            FluidStack water = new FluidStack(FluidRegistry.WATER, 1_000);
            assertEquals(0, injector.fill(ForgeDirection.WEST, water, false), "unconnected injector network has no capacity");

            FullGameServerQaHarness.face(injector, ForgeDirection.EAST);
            FullGameServerQaHarness.face(inputHatch, ForgeDirection.WEST);
            FullGameServerQaHarness.connect(pipe, ForgeDirection.WEST, ForgeDirection.EAST);
            qa.rebuildNetwork(pipe);
            qa.tickTrackedTiles(40);

            IntegratedFluidNetwork network = inputHatch.getNetwork();
            assertNotNull(network, "connected VFN input hatch must have a network");
            assertTrue(pipe.getNetwork() == network, "pipe must join the input hatch VFN");
            assertTrue(injector.getNetwork() == network, "injector must join the same VFN as the hatch");
            assertTrue(network.getTotalCapacity() > 0, "connected VFN must have hatch-backed capacity");
            assertTrue(injector.fill(ForgeDirection.WEST, water, true) > 0, "connected injector must accept water");
            assertTrue(network.getStoredAmount() > 0, "connected VFN must store injected water");
        } finally {
            qa.clearTrackedBlocks();
        }
    }

    @Test
    void pressureRegulatorMovesFluidBetweenSeparateVfnNetworksWithinSetpoint() {
        FullGameServerQaHarness qa = FullGameServerQaHarness.overworld();
        int x = 50;
        int y = 200;
        int z = 0;

        try {
            MTEIntegratedFluidInjectorHatch injector = qa.placeMetaTile(
                x,
                y,
                z,
                FullGameServerQaHarness.INTEGRATED_FLUID_INJECTOR_HATCH_ID,
                MTEIntegratedFluidInjectorHatch.class);
            MTEIntegratedFluidPipe inputPipe = qa.placeMetaTile(
                x + 1,
                y,
                z,
                FullGameServerQaHarness.INTEGRATED_FLUID_PIPE_ID,
                MTEIntegratedFluidPipe.class);
            MTEIntegratedFluidInputHatch inputHatch = qa.placeMetaTile(
                x + 1,
                y,
                z + 1,
                FullGameServerQaHarness.INTEGRATED_FLUID_INPUT_HATCH_ID,
                MTEIntegratedFluidInputHatch.class);
            MTEIntegratedFluidPressureRegulator regulator = qa.placeMetaTile(
                x + 2,
                y,
                z,
                FullGameServerQaHarness.INTEGRATED_FLUID_PRESSURE_REGULATOR_ID,
                MTEIntegratedFluidPressureRegulator.class);
            MTEIntegratedFluidPipe outputPipe = qa.placeMetaTile(
                x + 3,
                y,
                z,
                FullGameServerQaHarness.INTEGRATED_FLUID_PIPE_ID,
                MTEIntegratedFluidPipe.class);
            MTEIntegratedFluidOutputHatch outputHatch = qa.placeMetaTile(
                x + 4,
                y,
                z,
                FullGameServerQaHarness.INTEGRATED_FLUID_OUTPUT_HATCH_ID,
                MTEIntegratedFluidOutputHatch.class);

            FullGameServerQaHarness.face(injector, ForgeDirection.EAST);
            FullGameServerQaHarness.face(inputHatch, ForgeDirection.NORTH);
            FullGameServerQaHarness.face(regulator, ForgeDirection.EAST);
            FullGameServerQaHarness.face(outputHatch, ForgeDirection.WEST);
            FullGameServerQaHarness.connect(inputPipe, ForgeDirection.WEST, ForgeDirection.SOUTH);
            FullGameServerQaHarness.connect(outputPipe, ForgeDirection.WEST, ForgeDirection.EAST);
            qa.rebuildNetwork(inputPipe);
            qa.rebuildNetwork(outputPipe);
            qa.tickTrackedTiles(40);

            IFNFluidThermalRegistration.init();
            IntegratedFluidNetwork inputNetwork = inputPipe.getNetwork();
            IntegratedFluidNetwork outputNetwork = outputPipe.getNetwork();
            assertNotNull(inputNetwork, "input side VFN must exist before regulator transfer");
            assertNotNull(outputNetwork, "output side VFN must exist before regulator transfer");
            assertTrue(inputNetwork != outputNetwork, "pressure regulator must keep input and output VFNs separate");

            FluidStack water = new FluidStack(FluidRegistry.WATER, 1_000);
            assertTrue(injector.fill(ForgeDirection.WEST, water, true) > 0, "injector must seed regulator input VFN");
            assertTrue(inputNetwork.getStoredAmount() > 0, "input VFN must contain fluid before regulator transfer");
            assertEquals(0, outputNetwork.getStoredAmount(), "output VFN starts empty before regulator transfer");

            regulator.setSetpointPressureBar(2.0f);
            regulator.setOvershootToleranceBar(0.05f);
            regulator.setMaxPacketAmountQ(50L * IntegratedFluidNetwork.AMOUNT_SCALE);
            qa.tickTrackedTiles(80);

            assertTrue(outputNetwork.getStoredAmount() > 0, "pressure regulator must move fluid into output VFN");
            assertTrue(outputNetwork.getPressure() <= 2.05f, "pressure regulator must respect setpoint tolerance");
        } finally {
            qa.clearTrackedBlocks();
        }
    }
}
