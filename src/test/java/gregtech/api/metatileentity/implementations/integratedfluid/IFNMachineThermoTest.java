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
}
