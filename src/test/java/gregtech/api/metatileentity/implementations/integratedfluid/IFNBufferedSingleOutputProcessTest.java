package gregtech.api.metatileentity.implementations.integratedfluid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraftforge.fluids.Fluid;

import gregtech.api.metatileentity.implementations.integratedfluid.machine.IFNBatchState;
import gregtech.api.metatileentity.implementations.integratedfluid.machine.IFNMachineOutputBuffer;

class IFNBufferedSingleOutputProcessTest {

    @Test
    void extractsInputAndStoresOutputBatchWithoutMutatingOutputNetwork() {
        Fluid fluid = IFNTestSupport.waterFluid();
        IntegratedFluidNetwork input = IFNTestSupport.newNetwork(fluid, 10_000, 0, 100.0f);
        IntegratedFluidNetwork output = IFNTestSupport.newNetwork(fluid, 10_000, 0, 100.0f);
        IFNMachineOutputBuffer outputBuffer = new IFNMachineOutputBuffer();
        long storedAmountQ = 5L * IntegratedFluidNetwork.AMOUNT_SCALE;
        long processAmountQ = IntegratedFluidNetwork.AMOUNT_SCALE;
        input.addState(fluid, storedAmountQ, IntegratedFluidNetwork.toEnthalpyQFromSpecific(300.0d, storedAmountQ));

        IFNBufferedSingleOutputProcess.Result result = IFNBufferedSingleOutputProcess.execute(
            IFNBufferedSingleOutputProcess.Request.of(
                input,
                output,
                outputBuffer,
                "normal",
                fluid,
                processAmountQ,
                ignored -> 350.0d,
                100.0f));

        assertEquals(IFNMachineProcessStatus.SUCCESS, result.getStatus());
        assertEquals(storedAmountQ - processAmountQ, input.getAmountQ());
        assertEquals(0L, output.getAmountQ());
        IFNBatchState pending = outputBuffer.pendingOutput("normal");
        assertEquals(processAmountQ, pending.amountQ());
        assertEquals(350.0d, pending.specificEnthalpy(), 1.0e-6d);
    }

    @Test
    void incompatiblePendingOutputRestoresExtractedInput() {
        Fluid fluid = IFNTestSupport.waterFluid();
        IntegratedFluidNetwork input = IFNTestSupport.newNetwork(fluid, 10_000, 0, 100.0f);
        IntegratedFluidNetwork output = IFNTestSupport.newNetwork(fluid, 10_000, 0, 100.0f);
        IFNMachineOutputBuffer outputBuffer = new IFNMachineOutputBuffer();
        long storedAmountQ = 5L * IntegratedFluidNetwork.AMOUNT_SCALE;
        input.addState(fluid, storedAmountQ, IntegratedFluidNetwork.toEnthalpyQFromSpecific(300.0d, storedAmountQ));
        outputBuffer.addPendingOutput("normal", IFNBatchState.of("steam", IntegratedFluidNetwork.AMOUNT_SCALE, 350.0d, 1.0f));

        IFNBufferedSingleOutputProcess.Result result = IFNBufferedSingleOutputProcess.execute(
            IFNBufferedSingleOutputProcess.Request.of(
                input,
                output,
                outputBuffer,
                "normal",
                fluid,
                IntegratedFluidNetwork.AMOUNT_SCALE,
                ignored -> 350.0d,
                100.0f));

        assertEquals(IFNMachineProcessStatus.OUTPUT_BLOCKED, result.getStatus());
        assertEquals(storedAmountQ, input.getAmountQ());
        assertEquals(0L, output.getAmountQ());
        assertEquals("steam", outputBuffer.pendingOutput("normal").fluidName());
        assertTrue(outputBuffer.pendingOutput("normal").amountQ() > 0L);
    }
}
