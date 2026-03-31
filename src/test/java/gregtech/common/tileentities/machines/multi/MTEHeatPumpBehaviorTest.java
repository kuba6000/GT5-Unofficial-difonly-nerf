package gregtech.common.tileentities.machines.multi;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.minecraftforge.fluids.Fluid;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.integratedfluid.IFNTestSupport;
import gregtech.api.metatileentity.implementations.integratedfluid.IntegratedFluidNetwork;
import gregtech.api.metatileentity.implementations.integratedfluid.MTEIntegratedFluidInputHatch;
import gregtech.api.metatileentity.implementations.integratedfluid.MTEIntegratedFluidOutputHatch;
import gregtech.api.recipe.check.CheckRecipeResult;

@Disabled("Requires full GregTech multiblock bootstrap; keep Heat Pump coverage at IFN/planner level in unit tests.")
class MTEHeatPumpBehaviorTest {

    @Test
    void normalModeHeatsFluidTowardTargetTemperature() throws Exception {
        Fluid fluid = IFNTestSupport.liquidFluid();
        TestHeatPump heatPump = new TestHeatPump();
        heatPump.setOperatingMode(HeatPumpMode.TARGET_TEMPERATURE);
        heatPump.setTargetTemperatureDelta(350.0f);
        heatPump.setTargetHeating(true);
        heatPump.setFluidAmountPerOperation(1_000);

        IntegratedFluidNetwork inputNetwork = IFNTestSupport.newNetwork(fluid, 1_000, 100, 10.0f);
        IntegratedFluidNetwork outputNetwork = IFNTestSupport.newNetwork(fluid, 1_000, 100, 10.0f);
        long inputAmountQ = 1_000L * IntegratedFluidNetwork.AMOUNT_SCALE;
        inputNetwork.addState(fluid, inputAmountQ, IntegratedFluidNetwork.toEnthalpyQFromSpecific(300.0d, inputAmountQ));

        setIntegratedHatches(
            heatPump,
            Collections.singletonList(mockInputHatch(inputNetwork, (byte) 0)),
            Collections.singletonList(mockOutputHatch(outputNetwork, (byte) 0))
        );

        CheckRecipeResult result = heatPump.checkProcessing();

        assertTrue(result.wasSuccessful());
        assertTrue(outputNetwork.getAmountQ() > 0L);
        assertTrue(inputNetwork.getAmountQ() < inputAmountQ);
        assertTrue(outputNetwork.getTemperature() > 340.0f);
    }

    @Test
    void splitFlowModeProducesHotAndColdOutputs() throws Exception {
        Fluid fluid = IFNTestSupport.liquidFluid();
        TestHeatPump heatPump = new TestHeatPump();
        heatPump.setSplitFlowMode(true);
        heatPump.setOperatingMode(HeatPumpMode.TARGET_TEMPERATURE);
        heatPump.setTargetTemperatureDelta(350.0f);
        heatPump.setTargetHeating(true);
        heatPump.setSplitRatio(0.25f);
        heatPump.setFluidAmountPerOperation(1_000);

        IntegratedFluidNetwork inputNetwork = IFNTestSupport.newNetwork(fluid, 1_000, 100, 10.0f);
        IntegratedFluidNetwork redOutputNetwork = IFNTestSupport.newNetwork(fluid, 1_000, 100, 10.0f);
        IntegratedFluidNetwork blueOutputNetwork = IFNTestSupport.newNetwork(fluid, 1_000, 100, 10.0f);
        long inputAmountQ = 1_000L * IntegratedFluidNetwork.AMOUNT_SCALE;
        inputNetwork.addState(fluid, inputAmountQ, IntegratedFluidNetwork.toEnthalpyQFromSpecific(300.0d, inputAmountQ));

        setIntegratedHatches(
            heatPump,
            Collections.singletonList(mockInputHatch(inputNetwork, (byte) 0)),
            Arrays.asList(mockOutputHatch(redOutputNetwork, (byte) 1), mockOutputHatch(blueOutputNetwork, (byte) 4))
        );

        CheckRecipeResult result = heatPump.checkProcessing();

        assertTrue(result.wasSuccessful());
        assertTrue(redOutputNetwork.getAmountQ() > 0L);
        assertTrue(blueOutputNetwork.getAmountQ() > 0L);
        assertTrue(redOutputNetwork.getTemperature() > 340.0f);
        assertTrue(blueOutputNetwork.getTemperature() < 300.0f);
    }

    @Test
    void heatExchangerModeHeatsRedAndCoolsBlue() throws Exception {
        Fluid fluid = IFNTestSupport.liquidFluid();
        TestHeatPump heatPump = new TestHeatPump();
        heatPump.setHeatExchangerMode(true);
        heatPump.setConfiguringHotStream(true);
        heatPump.setOperatingMode(HeatPumpMode.TARGET_TEMPERATURE);
        heatPump.setTargetTemperatureDelta(320.0f);
        heatPump.setFluidAmountPerOperation(1_000);

        IntegratedFluidNetwork redInputNetwork = IFNTestSupport.newNetwork(fluid, 1_000, 100, 10.0f);
        IntegratedFluidNetwork blueInputNetwork = IFNTestSupport.newNetwork(fluid, 1_000, 100, 10.0f);
        IntegratedFluidNetwork redOutputNetwork = IFNTestSupport.newNetwork(fluid, 1_000, 100, 10.0f);
        IntegratedFluidNetwork blueOutputNetwork = IFNTestSupport.newNetwork(fluid, 1_000, 100, 10.0f);

        long amountQ = 1_000L * IntegratedFluidNetwork.AMOUNT_SCALE;
        redInputNetwork.addState(fluid, amountQ, IntegratedFluidNetwork.toEnthalpyQFromSpecific(300.0d, amountQ));
        blueInputNetwork.addState(fluid, amountQ, IntegratedFluidNetwork.toEnthalpyQFromSpecific(290.0d, amountQ));

        setIntegratedHatches(
            heatPump,
            Arrays.asList(mockInputHatch(redInputNetwork, (byte) 1), mockInputHatch(blueInputNetwork, (byte) 4)),
            Arrays.asList(mockOutputHatch(redOutputNetwork, (byte) 1), mockOutputHatch(blueOutputNetwork, (byte) 4))
        );

        CheckRecipeResult result = heatPump.checkProcessing();

        assertTrue(result.wasSuccessful());
        assertTrue(redOutputNetwork.getAmountQ() > 0L);
        assertTrue(blueOutputNetwork.getAmountQ() > 0L);
        assertTrue(redOutputNetwork.getTemperature() > 300.0f);
        assertTrue(blueOutputNetwork.getTemperature() < 290.0f);
    }

    private static void setIntegratedHatches(MTEHeatPump heatPump, List<MTEIntegratedFluidInputHatch> inputs,
        List<MTEIntegratedFluidOutputHatch> outputs) throws Exception {
        setFinalListField(heatPump, "mIntegratedInputHatches", inputs);
        setFinalListField(heatPump, "mIntegratedOutputHatches", outputs);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void setFinalListField(Object target, String fieldName, List values) throws Exception {
        Field field = MTEHeatPump.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        List existing = (List) field.get(target);
        existing.clear();
        existing.addAll(values);
    }

    private static MTEIntegratedFluidInputHatch mockInputHatch(IntegratedFluidNetwork network, byte color) {
        MTEIntegratedFluidInputHatch hatch = mock(MTEIntegratedFluidInputHatch.class);
        IGregTechTileEntity baseTile = mock(IGregTechTileEntity.class);
        when(baseTile.getColorization()).thenReturn(color);
        when(hatch.getBaseMetaTileEntity()).thenReturn(baseTile);
        when(hatch.getNetwork()).thenReturn(network);
        return hatch;
    }

    private static MTEIntegratedFluidOutputHatch mockOutputHatch(IntegratedFluidNetwork network, byte color) {
        MTEIntegratedFluidOutputHatch hatch = mock(MTEIntegratedFluidOutputHatch.class);
        IGregTechTileEntity baseTile = mock(IGregTechTileEntity.class);
        when(baseTile.getColorization()).thenReturn(color);
        when(hatch.getBaseMetaTileEntity()).thenReturn(baseTile);
        when(hatch.getNetwork()).thenReturn(network);
        return hatch;
    }

    private static final class TestHeatPump extends MTEHeatPump {
        private TestHeatPump() {
            super("test.heatpump");
        }

        @Override
        public boolean drainEnergyInput(long aEU) {
            return true;
        }

        @Override
        public boolean hasValidSplitFlowHatches() {
            return true;
        }

        @Override
        public boolean hasValidHeatExchangerHatches() {
            return true;
        }
    }
}
