package gregtech.api.metatileentity.implementations.integratedfluid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraftforge.fluids.Fluid;

import org.junit.jupiter.api.Test;

class IFNMachineThermoTest {

    @Test
    void targetSpecificEnthalpyForStateAddUsesPredictedOutputPressure() {
        Fluid fluid = IFNTestSupport.pressureSensitiveLiquidFluid();
        IntegratedFluidNetwork network = IFNTestSupport.newNetwork(fluid, 100, 100, 1.5f);

        long addAmountQ = 130L * IntegratedFluidNetwork.AMOUNT_SCALE;
        double initialSpecificEnthalpy = FluidThermalProperties.getSpecificEnthalpyFromPT(fluid, network.getPressure(), 300.0d);
        double correctedSpecificEnthalpy = IFNMachineThermo.computeTargetSpecificEnthalpyForStateAdd(
            network,
            fluid,
            300.0d,
            addAmountQ
        );
        float correctedPressure = network.predictPressureAfterStateAdd(
            fluid,
            addAmountQ,
            IntegratedFluidNetwork.toEnthalpyQFromSpecific(correctedSpecificEnthalpy, addAmountQ)
        );

        assertTrue(correctedSpecificEnthalpy > initialSpecificEnthalpy);
        assertEquals(
            FluidThermalProperties.getSpecificEnthalpyFromPT(fluid, correctedPressure, 300.0d),
            correctedSpecificEnthalpy,
            0.02d
        );
    }

    @Test
    void scaleEnergyCostScalesLinearlyWithTransferredAmount() {
        long scaled = IFNMachineThermo.scaleEnergyCost(
            1_000L,
            10L * IntegratedFluidNetwork.AMOUNT_SCALE,
            4L * IntegratedFluidNetwork.AMOUNT_SCALE
        );

        assertEquals(400L, scaled);
    }

    @Test
    void heatPumpMetricsUseAbsoluteTemperatureDelta() {
        IFNMachineThermo.HeatPumpMetrics metrics = IFNMachineThermo.computeHeatPumpMetrics(400.0d, 300.0d);

        assertEquals(100.0d, metrics.temperatureDelta(), 0.001d);
        assertEquals(FluidThermalProperties.calculateHeatPumpCOP(300.0f, 400.0f), metrics.cop(), 0.001d);
        assertEquals(FluidThermalProperties.calculateTemperaturePenalty(100.0f), metrics.efficiencyPenalty(), 0.001d);
        assertEquals(metrics.cop() / metrics.efficiencyPenalty(), metrics.effectiveCop(), 0.001d);
    }

    @Test
    void targetCopTemperatureUsesDirectionAndMinimumTemperatureStep() {
        assertEquals(375.0d, IFNMachineThermo.computeTargetCopOutputTemperature(300.0d, 5.0f, true), 0.001d);
        assertEquals(240.0d, IFNMachineThermo.computeTargetCopOutputTemperature(300.0d, 5.0f, false), 0.001d);
        assertEquals(300.1d, IFNMachineThermo.computeTargetCopOutputTemperature(300.0d, 10_000.0f, true), 0.001d);
        assertEquals(299.9d, IFNMachineThermo.computeTargetCopOutputTemperature(300.0d, 10_000.0f, false), 0.001d);
    }

    @Test
    void heatExchangerTargetCopTemperatureUsesOppositeStreamReference() {
        assertEquals(
            375.0d,
            IFNMachineThermo.computeHeatExchangerTargetCopOutputTemperature(360.0d, 300.0d, 5.0f, true),
            0.001d
        );
        assertEquals(
            288.0d,
            IFNMachineThermo.computeHeatExchangerTargetCopOutputTemperature(360.0d, 300.0d, 5.0f, false),
            0.001d
        );
        assertEquals(
            300.1d,
            IFNMachineThermo.computeHeatExchangerTargetCopOutputTemperature(300.0d, 300.0d, 10_000.0f, true),
            0.001d
        );
        assertEquals(
            299.9d,
            IFNMachineThermo.computeHeatExchangerTargetCopOutputTemperature(300.0d, 300.0d, 10_000.0f, false),
            0.001d
        );
    }

    @Test
    void heatExchangerMetricsUseRedBlueReferenceTemperaturesAndTargetDelta() {
        IFNMachineThermo.HeatPumpMetrics redConfigured = IFNMachineThermo.computeHeatExchangerMetrics(
            360.0d,
            300.0d,
            true,
            360.0d,
            380.0d
        );
        IFNMachineThermo.HeatPumpMetrics blueConfigured = IFNMachineThermo.computeHeatExchangerMetrics(
            360.0d,
            300.0d,
            false,
            300.0d,
            280.0d
        );

        assertEquals(20.0d, redConfigured.temperatureDelta(), 0.001d);
        assertEquals(FluidThermalProperties.calculateHeatPumpCOP(300.0f, 380.0f), redConfigured.cop(), 0.001d);
        assertEquals(20.0d, blueConfigured.temperatureDelta(), 0.001d);
        assertEquals(FluidThermalProperties.calculateHeatPumpCOP(300.0f, 360.0f), blueConfigured.cop(), 0.001d);
    }

    @Test
    void targetEnergyOutputStateMovesSpecificEnthalpyInRequestedDirection() {
        Fluid fluid = IFNTestSupport.liquidFluid();
        double inputSpecificEnthalpy = FluidThermalProperties.getSpecificEnthalpyFromPT(fluid, 1.0f, 300.0d);
        long amountQ = 5L * IntegratedFluidNetwork.AMOUNT_SCALE;

        IFNMachineThermo.TargetEnergyState heating = IFNMachineThermo.computeTargetEnergyOutputState(
            fluid,
            1.0f,
            300.0d,
            inputSpecificEnthalpy,
            amountQ,
            2_000L,
            true);
        IFNMachineThermo.TargetEnergyState cooling = IFNMachineThermo.computeTargetEnergyOutputState(
            fluid,
            1.0f,
            300.0d,
            inputSpecificEnthalpy,
            amountQ,
            2_000L,
            false);

        assertTrue(heating.specificEnthalpy() > inputSpecificEnthalpy);
        assertTrue(heating.temperature() > 300.0d);
        assertTrue(cooling.specificEnthalpy() < inputSpecificEnthalpy);
        assertTrue(cooling.temperature() < 300.0d);
        assertTrue(heating.metrics().effectiveCop() > 0.0f);
    }
}
