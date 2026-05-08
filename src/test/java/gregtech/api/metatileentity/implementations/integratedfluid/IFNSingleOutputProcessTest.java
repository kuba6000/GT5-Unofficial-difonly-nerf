package gregtech.api.metatileentity.implementations.integratedfluid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraftforge.fluids.Fluid;

import org.junit.jupiter.api.Test;

class IFNSingleOutputProcessTest {

    @Test
    void singleOutputProcessTransfersTargetStateAndReportsResult() {
        Fluid vapor = IFNTestSupport.vaporFluid();
        IntegratedFluidNetwork input = IFNTestSupport.newNetwork(vapor, 4_000, 100, 10.0f);
        IntegratedFluidNetwork output = IFNTestSupport.newNetwork(vapor, 4_000, 100, 10.0f);
        long initialInputAmountQ = 200L * IntegratedFluidNetwork.AMOUNT_SCALE;
        input.addState(vapor, initialInputAmountQ, IntegratedFluidNetwork.toEnthalpyQFromSpecific(350.0d, initialInputAmountQ));

        long requestedAmountQ = 50L * IntegratedFluidNetwork.AMOUNT_SCALE;
        double targetTemperature = 450.0d;

        IFNSingleOutputProcess.Result result = IFNSingleOutputProcess.execute(IFNSingleOutputProcess.Request.of(
            input,
            output,
            vapor,
            requestedAmountQ,
            amountQ -> IFNMachineThermo.computeTargetSpecificEnthalpyForStateAdd(output, vapor, targetTemperature, amountQ),
            IFNPressurePolicy.MACHINE_OUTPUT_TO_INPUT_PRESSURE_RATIO));

        assertEquals(IFNSingleOutputProcess.Status.SUCCESS, result.getStatus());
        assertTrue(result.getAmountQ() >= IntegratedFluidNetwork.AMOUNT_SCALE);
        assertEquals(initialInputAmountQ - result.getAmountQ(), input.getAmountQ());
        assertEquals(result.getAmountQ(), output.getAmountQ());
        assertEquals(result.getOutputSpecificEnthalpy(), output.getSpecificEnthalpy(), 0.0001d);
        assertTrue(result.getPredictedOutputPressureBar()
            <= result.getPredictedInputPressureBar() * IFNPressurePolicy.MACHINE_OUTPUT_TO_INPUT_PRESSURE_RATIO
                + IFNPressurePolicy.MIN_PRESSURE_BAR);
    }

    @Test
    void singleOutputProcessDoesNotMutateNetworksWhenOutputCannotAccept() {
        Fluid vapor = IFNTestSupport.vaporFluid();
        IntegratedFluidNetwork input = IFNTestSupport.newNetwork(vapor, 1_000, 0, 10.0f);
        IntegratedFluidNetwork output = IFNTestSupport.newNetwork(vapor, 1, 0, 1.0f);
        long initialInputAmountQ = 100L * IntegratedFluidNetwork.AMOUNT_SCALE;
        input.addState(vapor, initialInputAmountQ, IntegratedFluidNetwork.toEnthalpyQFromSpecific(350.0d, initialInputAmountQ));

        IFNSingleOutputProcess.Result result = IFNSingleOutputProcess.execute(IFNSingleOutputProcess.Request.of(
            input,
            output,
            vapor,
            50L * IntegratedFluidNetwork.AMOUNT_SCALE,
            amountQ -> 450.0d,
            IFNPressurePolicy.MACHINE_OUTPUT_TO_INPUT_PRESSURE_RATIO));

        assertEquals(IFNSingleOutputProcess.Status.OUTPUT_BLOCKED, result.getStatus());
        assertEquals(initialInputAmountQ, input.getAmountQ());
        assertEquals(0L, output.getAmountQ());
    }
}
