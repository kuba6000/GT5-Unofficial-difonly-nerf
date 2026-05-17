package gregtech.api.metatileentity.implementations.integratedfluid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraftforge.fluids.Fluid;

import org.junit.jupiter.api.Test;

class IFNMachineBatchPlannerTest {

    @Test
    void amountForVolumeLimitUsesSpecificVolumeAndAvailability() {
        long amountQ = IFNMachineBatchPlanner.computeAmountQForVolumeLimit(
            10L * IntegratedFluidNetwork.AMOUNT_SCALE,
            12.0d,
            2.5d
        );
        long cappedAmountQ = IFNMachineBatchPlanner.computeAmountQForVolumeLimit(
            3L * IntegratedFluidNetwork.AMOUNT_SCALE,
            12.0d,
            2.5d
        );

        assertEquals(4L * IntegratedFluidNetwork.AMOUNT_SCALE, amountQ);
        assertEquals(3L * IntegratedFluidNetwork.AMOUNT_SCALE, cappedAmountQ);
    }

    @Test
    void amountForVolumeLimitRejectsInvalidOrSubReferenceBatches() {
        assertEquals(0L, IFNMachineBatchPlanner.computeAmountQForVolumeLimit(10L, 12.0d, 0.0d));
        assertEquals(0L, IFNMachineBatchPlanner.computeAmountQForVolumeLimit(10L, 12.0d, Double.NaN));
        assertEquals(0L, IFNMachineBatchPlanner.computeAmountQForVolumeLimit(10L, 1.0d, 2.0d));
    }

    @Test
    void splitAmountsPreserveTotalWithFloorOnFirstSide() {
        IFNMachineBatchPlanner.SplitAmounts split = IFNMachineBatchPlanner.computeSplitAmounts(
            11L * IntegratedFluidNetwork.AMOUNT_SCALE,
            0.35d
        );

        assertTrue(split.isValid());
        assertEquals(3L * IntegratedFluidNetwork.AMOUNT_SCALE + 849_999L, split.firstAmountQ());
        assertEquals(7L * IntegratedFluidNetwork.AMOUNT_SCALE + 150_001L, split.secondAmountQ());
        assertEquals(11L * IntegratedFluidNetwork.AMOUNT_SCALE, split.totalAmountQ());
    }

    @Test
    void splitAmountsRejectWhenEitherSideIsEmpty() {
        assertFalse(IFNMachineBatchPlanner.computeSplitAmounts(10L, 0.5d).isValid());
        assertFalse(IFNMachineBatchPlanner.computeSplitAmounts(10L * IntegratedFluidNetwork.AMOUNT_SCALE, 0.0d)
            .isValid());
        assertFalse(IFNMachineBatchPlanner.computeSplitAmounts(10L * IntegratedFluidNetwork.AMOUNT_SCALE, 1.0d)
            .isValid());
    }

    @Test
    void inputBatchPlanCarriesThermalStateAndSizedAmount() {
        Fluid fluid = IFNTestSupport.liquidFluid();
        float pressure = 2.0f;
        double specificEnthalpy = FluidThermalProperties.getSpecificEnthalpyFromPT(fluid, pressure, 320.0d);
        double specificVolume = IntegratedFluidThermoModel.specificVolumeFromPressureAndSpecificEnthalpy(
            fluid,
            pressure,
            specificEnthalpy
        );

        IFNMachineBatchPlanner.BatchPlan plan = IFNMachineBatchPlanner.planInputBatch(
            fluid,
            pressure,
            specificEnthalpy,
            10L * IntegratedFluidNetwork.AMOUNT_SCALE,
            specificVolume * 4.5d
        );

        assertTrue(plan.isValid());
        assertEquals(4L * IntegratedFluidNetwork.AMOUNT_SCALE, plan.amountQ());
        assertEquals(specificEnthalpy, plan.specificEnthalpy(), 0.001d);
        assertEquals(320.0d, plan.temperature(), 0.001d);
        assertEquals(specificVolume, plan.specificVolume(), 0.001d);
    }

    @Test
    void inputBatchPlanIsInvalidWhenNoWholeReferenceAmountFits() {
        Fluid fluid = IFNTestSupport.liquidFluid();
        float pressure = 2.0f;
        double specificEnthalpy = FluidThermalProperties.getSpecificEnthalpyFromPT(fluid, pressure, 320.0d);
        double specificVolume = IntegratedFluidThermoModel.specificVolumeFromPressureAndSpecificEnthalpy(
            fluid,
            pressure,
            specificEnthalpy
        );

        IFNMachineBatchPlanner.BatchPlan plan = IFNMachineBatchPlanner.planInputBatch(
            fluid,
            pressure,
            specificEnthalpy,
            10L * IntegratedFluidNetwork.AMOUNT_SCALE,
            specificVolume * 0.5d
        );

        assertFalse(plan.isValid());
        assertEquals(0L, plan.amountQ());
    }

    @Test
    void networkInputBatchPlanReadsNetworkFluidAndThermalState() {
        Fluid fluid = IFNTestSupport.liquidFluid();
        IntegratedFluidNetwork network = IFNTestSupport.newNetwork(fluid, 10_000, 0, 100.0f);
        long availableAmountQ = 10L * IntegratedFluidNetwork.AMOUNT_SCALE;
        double specificEnthalpy = FluidThermalProperties.getSpecificEnthalpyFromPT(fluid, 2.0d, 320.0d);
        network.addState(
            fluid,
            availableAmountQ,
            IntegratedFluidNetwork.toEnthalpyQFromSpecific(specificEnthalpy, availableAmountQ)
        );
        double specificVolume = IntegratedFluidThermoModel.specificVolumeFromPressureAndSpecificEnthalpy(
            fluid,
            network.getPressure(),
            network.getSpecificEnthalpy()
        );
        double expectedTemperature = FluidThermalProperties.getTemperatureFromPH(
            fluid,
            network.getPressure(),
            network.getSpecificEnthalpy()
        );

        IFNMachineBatchPlanner.NetworkInputBatchPlan plan = IFNMachineBatchPlanner.planNetworkInputBatch(
            network,
            specificVolume * 4.5d
        );

        assertTrue(plan.isValid());
        assertSame(fluid, plan.fluid());
        assertEquals(4L * IntegratedFluidNetwork.AMOUNT_SCALE, plan.batch().amountQ());
        assertEquals(network.getSpecificEnthalpy(), plan.specificEnthalpy(), 0.001d);
        assertEquals(expectedTemperature, plan.batch().temperature(), 0.001d);
    }

    @Test
    void networkInputBatchPlanRejectsEmptyNetwork() {
        IFNMachineBatchPlanner.NetworkInputBatchPlan plan = IFNMachineBatchPlanner.planNetworkInputBatch(
            new IntegratedFluidNetwork(),
            100.0d
        );

        assertFalse(plan.isValid());
        assertEquals(0L, plan.batch().amountQ());
    }
}
