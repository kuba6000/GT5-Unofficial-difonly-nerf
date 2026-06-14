package gregtech.test.fullgame;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.minecraftforge.fluids.FluidRegistry;

import org.junit.jupiter.api.Test;

import gregtech.api.metatileentity.implementations.integratedfluid.IFNFluidThermalRegistration;
import gregtech.api.metatileentity.implementations.integratedfluid.IntegratedFluidNetwork;
import gregtech.api.metatileentity.implementations.integratedfluid.MTEIntegratedFluidInputHatch;
import gregtech.api.metatileentity.implementations.integratedfluid.MTEIntegratedFluidOutputHatch;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.common.tileentities.machines.multi.HeatPumpMode;
import gregtech.common.tileentities.machines.multi.MTEHeatPump;

final class FullGameServerHeatPumpModeTest {

    private static final byte RED = 1;
    private static final byte BLUE = 4;

    @Test
    void heatPumpTargetCopModeProcessesWaterInServerWorld() throws Exception {
        FullGameServerQaHarness qa = FullGameServerQaHarness.overworld();
        try {
            HeatPumpRig rig = HeatPumpRig.singleInputSingleOutput(qa, 30, 72, 30);
            seedInput(rig.input(0), 1_000);

            rig.heatPump.setOperatingMode(HeatPumpMode.TARGET_COP);
            rig.heatPump.setTargetCOP(4.0f);
            rig.heatPump.setFluidAmountPerOperation(1_000);

            assertAnyOutputAfterProcessing(rig.heatPump, rig.outputs);
        } finally {
            qa.clearTrackedBlocks();
        }
    }

    @Test
    void heatPumpTargetEnergyModeProcessesWaterInServerWorld() throws Exception {
        FullGameServerQaHarness qa = FullGameServerQaHarness.overworld();
        try {
            HeatPumpRig rig = HeatPumpRig.singleInputSingleOutput(qa, 40, 72, 30);
            seedInput(rig.input(0), 1_000);

            rig.heatPump.setOperatingMode(HeatPumpMode.TARGET_ENERGY);
            rig.heatPump.setTargetEnergy(2_000);
            rig.heatPump.setFluidAmountPerOperation(1_000);

            assertAnyOutputAfterProcessing(rig.heatPump, rig.outputs);
        } finally {
            qa.clearTrackedBlocks();
        }
    }

    @Test
    void splitFlowModeWritesHotAndColdOutputNetworksInServerWorld() throws Exception {
        FullGameServerQaHarness qa = FullGameServerQaHarness.overworld();
        try {
            MTEHeatPump heatPump = qa.placeMetaTile(
                50,
                72,
                30,
                FullGameServerQaHarness.HEAT_PUMP_CONTROLLER_ID,
                MTEHeatPump.class);
            MTEIntegratedFluidInputHatch input = qa.placeMetaTile(
                51,
                72,
                30,
                FullGameServerQaHarness.INTEGRATED_FLUID_INPUT_HATCH_ID,
                MTEIntegratedFluidInputHatch.class);
            MTEIntegratedFluidOutputHatch redOutput = qa.placeMetaTile(
                52,
                72,
                30,
                FullGameServerQaHarness.INTEGRATED_FLUID_OUTPUT_HATCH_ID,
                MTEIntegratedFluidOutputHatch.class);
            MTEIntegratedFluidOutputHatch blueOutput = qa.placeMetaTile(
                53,
                72,
                30,
                FullGameServerQaHarness.INTEGRATED_FLUID_OUTPUT_HATCH_ID,
                MTEIntegratedFluidOutputHatch.class);
            FullGameServerQaHarness.color(redOutput, RED);
            FullGameServerQaHarness.color(blueOutput, BLUE);
            qa.tickTrackedTiles(40);

            seedInput(input, 1_000);
            List<MTEIntegratedFluidOutputHatch> outputs = Arrays.asList(redOutput, blueOutput);
            FullGameServerQaHarness.attachIntegratedHatches(heatPump, Collections.singletonList(input), outputs);

            heatPump.setSplitFlowMode(true);
            heatPump.setOperatingMode(HeatPumpMode.TARGET_TEMPERATURE);
            heatPump.setTargetTemperatureDelta(350.0f);
            heatPump.setTargetHeating(true);
            heatPump.setSplitRatio(0.25f);
            heatPump.setFluidAmountPerOperation(1_000);

            assertAnyOutputAfterProcessing(heatPump, outputs);
            assertTrue(redOutput.getNetwork()
                .getStoredAmount() > 0, "split-flow red output VFN must receive processed water");
            assertTrue(blueOutput.getNetwork()
                .getStoredAmount() > 0, "split-flow blue output VFN must receive processed water");
        } finally {
            qa.clearTrackedBlocks();
        }
    }

    @Test
    void heatExchangerModeWritesRedAndBlueOutputNetworksInServerWorld() throws Exception {
        FullGameServerQaHarness qa = FullGameServerQaHarness.overworld();
        try {
            MTEHeatPump heatPump = qa.placeMetaTile(
                60,
                72,
                30,
                FullGameServerQaHarness.HEAT_PUMP_CONTROLLER_ID,
                MTEHeatPump.class);
            MTEIntegratedFluidInputHatch redInput = qa.placeMetaTile(
                61,
                72,
                30,
                FullGameServerQaHarness.INTEGRATED_FLUID_INPUT_HATCH_ID,
                MTEIntegratedFluidInputHatch.class);
            MTEIntegratedFluidInputHatch blueInput = qa.placeMetaTile(
                62,
                72,
                30,
                FullGameServerQaHarness.INTEGRATED_FLUID_INPUT_HATCH_ID,
                MTEIntegratedFluidInputHatch.class);
            MTEIntegratedFluidOutputHatch redOutput = qa.placeMetaTile(
                63,
                72,
                30,
                FullGameServerQaHarness.INTEGRATED_FLUID_OUTPUT_HATCH_ID,
                MTEIntegratedFluidOutputHatch.class);
            MTEIntegratedFluidOutputHatch blueOutput = qa.placeMetaTile(
                64,
                72,
                30,
                FullGameServerQaHarness.INTEGRATED_FLUID_OUTPUT_HATCH_ID,
                MTEIntegratedFluidOutputHatch.class);
            FullGameServerQaHarness.color(redInput, RED);
            FullGameServerQaHarness.color(redOutput, RED);
            FullGameServerQaHarness.color(blueInput, BLUE);
            FullGameServerQaHarness.color(blueOutput, BLUE);
            qa.tickTrackedTiles(40);

            seedInput(redInput, 1_000, 360.0d);
            seedInput(blueInput, 1_000, 330.0d);
            List<MTEIntegratedFluidOutputHatch> outputs = Arrays.asList(redOutput, blueOutput);
            FullGameServerQaHarness.attachIntegratedHatches(heatPump, Arrays.asList(redInput, blueInput), outputs);

            heatPump.setHeatExchangerMode(true);
            heatPump.setConfiguringHotStream(false);
            heatPump.setOperatingMode(HeatPumpMode.TARGET_COP);
            heatPump.setTargetTemperatureDelta(300.0f);
            heatPump.setTargetCOP(4.0f);
            heatPump.setTargetEnergy(2_000);
            heatPump.setFluidAmountPerOperation(1_000);

            assertAnyOutputAfterProcessing(heatPump, outputs);
            assertTrue(redOutput.getNetwork()
                .getStoredAmount() > 0, "heat-exchanger red output VFN must receive processed water");
            assertTrue(blueOutput.getNetwork()
                .getStoredAmount() > 0, "heat-exchanger blue output VFN must receive processed water");
        } finally {
            qa.clearTrackedBlocks();
        }
    }

    private static void seedInput(MTEIntegratedFluidInputHatch input, int amount) {
        seedInput(input, amount, 300.0d);
    }

    private static void seedInput(MTEIntegratedFluidInputHatch input, int amount, double temperature) {
        IFNFluidThermalRegistration.init();
        IntegratedFluidNetwork network = input.getNetwork();
        assertNotNull(network, "input hatch must own a VFN before seeding fluid");
        long amountQ = amount * IntegratedFluidNetwork.AMOUNT_SCALE;
        network.addState(
            FluidRegistry.WATER,
            amountQ,
            IntegratedFluidNetwork.toEnthalpyQFromSpecific(temperature, amountQ));
        assertTrue(network.getStoredAmount() > 0, "input hatch VFN must store seeded water");
    }

    private static void assertAnyOutputAfterProcessing(MTEHeatPump heatPump, List<MTEIntegratedFluidOutputHatch> outputs) {
        CheckRecipeResult result = heatPump.checkProcessing();
        assertTrue(result.wasSuccessful(), "heat pump mode must process server-world VFN water: " + result.getDisplayString());
        for (int attempt = 0; attempt < 5 && totalStored(outputs) <= 0; attempt++) {
            heatPump.checkProcessing();
        }
        assertTrue(totalStored(outputs) > 0, "heat pump mode must write processed water to an output VFN");
    }

    private static long totalStored(List<MTEIntegratedFluidOutputHatch> outputs) {
        long total = 0L;
        for (MTEIntegratedFluidOutputHatch output : outputs) {
            IntegratedFluidNetwork network = output.getNetwork();
            if (network != null) {
                total += network.getStoredAmount();
            }
        }
        return total;
    }

    private static final class HeatPumpRig {

        private final MTEHeatPump heatPump;
        private final List<MTEIntegratedFluidInputHatch> inputs;
        private final List<MTEIntegratedFluidOutputHatch> outputs;

        private HeatPumpRig(MTEHeatPump heatPump, List<MTEIntegratedFluidInputHatch> inputs,
            List<MTEIntegratedFluidOutputHatch> outputs) throws ReflectiveOperationException {
            this.heatPump = heatPump;
            this.inputs = inputs;
            this.outputs = outputs;
            FullGameServerQaHarness.attachIntegratedHatches(heatPump, inputs, outputs);
        }

        private MTEIntegratedFluidInputHatch input(int index) {
            return inputs.get(index);
        }

        private static HeatPumpRig singleInputSingleOutput(FullGameServerQaHarness qa, int x, int y, int z)
            throws ReflectiveOperationException {
            MTEHeatPump heatPump = qa.placeMetaTile(
                x,
                y,
                z,
                FullGameServerQaHarness.HEAT_PUMP_CONTROLLER_ID,
                MTEHeatPump.class);
            MTEIntegratedFluidInputHatch input = qa.placeMetaTile(
                x + 1,
                y,
                z,
                FullGameServerQaHarness.INTEGRATED_FLUID_INPUT_HATCH_ID,
                MTEIntegratedFluidInputHatch.class);
            MTEIntegratedFluidOutputHatch output = qa.placeMetaTile(
                x + 2,
                y,
                z,
                FullGameServerQaHarness.INTEGRATED_FLUID_OUTPUT_HATCH_ID,
                MTEIntegratedFluidOutputHatch.class);
            qa.tickTrackedTiles(40);
            return new HeatPumpRig(heatPump, Collections.singletonList(input), Collections.singletonList(output));
        }
    }
}
