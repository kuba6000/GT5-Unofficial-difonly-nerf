package gregtech.api.metatileentity.implementations.integratedfluid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraftforge.fluids.Fluid;

import gregtech.api.metatileentity.implementations.integratedfluid.machine.IFNBatchState;
import gregtech.api.metatileentity.implementations.integratedfluid.machine.IFNMachineOutputBuffer;

class IFNBufferedSplitOutputProcessTest {

    @Test
    void extractsInputAndStoresBothOutputBatchesWithoutMutatingOutputNetworks() {
        Fluid water = IFNTestSupport.waterFluid();
        IntegratedFluidNetwork input = IFNTestSupport.newNetwork(water, 10_000, 0, 100.0f);
        IntegratedFluidNetwork firstOutput = IFNTestSupport.newNetwork(water, 1_000_000, 0, 1_000_000.0f);
        IntegratedFluidNetwork secondOutput = IFNTestSupport.newNetwork(water, 1_000_000, 0, 1_000_000.0f);
        IFNMachineOutputBuffer outputBuffer = new IFNMachineOutputBuffer();
        long storedAmountQ = 5L * IntegratedFluidNetwork.AMOUNT_SCALE;
        long processAmountQ = 2L * IntegratedFluidNetwork.AMOUNT_SCALE;
        input.addState(water, storedAmountQ, IntegratedFluidNetwork.toEnthalpyQFromSpecific(300.0d, storedAmountQ));

        IFNBufferedSplitOutputProcess.Result result = IFNBufferedSplitOutputProcess.execute(
            IFNBufferedSplitOutputProcess.Request.of(
                input,
                firstOutput,
                outputBuffer,
                "red",
                water,
                secondOutput,
                outputBuffer,
                "blue",
                water,
                processAmountQ,
                0.5d,
                ignored -> 350.0d,
                ignored -> 250.0d,
                1_000_000.0f));

        assertEquals(IFNMachineProcessStatus.SUCCESS, result.getStatus());
        assertEquals(storedAmountQ - processAmountQ, input.getAmountQ());
        assertEquals(0L, firstOutput.getAmountQ());
        assertEquals(0L, secondOutput.getAmountQ());
        assertEquals(IntegratedFluidNetwork.AMOUNT_SCALE, outputBuffer.pendingOutput("red").amountQ());
        assertEquals(IntegratedFluidNetwork.AMOUNT_SCALE, outputBuffer.pendingOutput("blue").amountQ());
        assertEquals(350.0d, outputBuffer.pendingOutput("red").specificEnthalpy(), 1.0e-6d);
        assertEquals(250.0d, outputBuffer.pendingOutput("blue").specificEnthalpy(), 1.0e-6d);
    }

    @Test
    void incompatibleSecondPortRestoresInputAndDoesNotWriteFirstPort() {
        Fluid water = IFNTestSupport.waterFluid();
        IntegratedFluidNetwork input = IFNTestSupport.newNetwork(water, 10_000, 0, 100.0f);
        IntegratedFluidNetwork firstOutput = IFNTestSupport.newNetwork(water, 1_000_000, 0, 1_000_000.0f);
        IntegratedFluidNetwork secondOutput = IFNTestSupport.newNetwork(water, 1_000_000, 0, 1_000_000.0f);
        IFNMachineOutputBuffer outputBuffer = new IFNMachineOutputBuffer();
        long storedAmountQ = 5L * IntegratedFluidNetwork.AMOUNT_SCALE;
        input.addState(water, storedAmountQ, IntegratedFluidNetwork.toEnthalpyQFromSpecific(300.0d, storedAmountQ));
        outputBuffer.addPendingOutput("blue", IFNBatchState.of("steam", IntegratedFluidNetwork.AMOUNT_SCALE, 250.0d, 1.0f));

        IFNBufferedSplitOutputProcess.Result result = IFNBufferedSplitOutputProcess.execute(
            IFNBufferedSplitOutputProcess.Request.of(
                input,
                firstOutput,
                outputBuffer,
                "red",
                water,
                secondOutput,
                outputBuffer,
                "blue",
                water,
                2L * IntegratedFluidNetwork.AMOUNT_SCALE,
                0.5d,
                ignored -> 350.0d,
                ignored -> 250.0d,
                1_000_000.0f));

        assertEquals(IFNMachineProcessStatus.OUTPUT_BLOCKED, result.getStatus());
        assertEquals(storedAmountQ, input.getAmountQ());
        assertTrue(outputBuffer.pendingOutput("red").isEmpty());
        assertEquals("steam", outputBuffer.pendingOutput("blue").fluidName());
    }
}
