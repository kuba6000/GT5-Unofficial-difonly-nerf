package gregtech.api.metatileentity.implementations.integratedfluid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraftforge.fluids.Fluid;

import gregtech.api.metatileentity.implementations.integratedfluid.machine.IFNBatchState;
import gregtech.api.metatileentity.implementations.integratedfluid.machine.IFNMachineOutputBuffer;

class IFNBufferedDualOutputProcessTest {

    @Test
    void extractsBothInputsAndStoresBothOutputBatchesWithoutMutatingOutputNetworks() {
        Fluid water = IFNTestSupport.waterFluid();
        IntegratedFluidNetwork firstInput = IFNTestSupport.newNetwork(water, 10_000, 0, 100.0f);
        IntegratedFluidNetwork secondInput = IFNTestSupport.newNetwork(water, 10_000, 0, 100.0f);
        IntegratedFluidNetwork firstOutput = IFNTestSupport.newNetwork(water, 10_000, 0, 100.0f);
        IntegratedFluidNetwork secondOutput = IFNTestSupport.newNetwork(water, 10_000, 0, 100.0f);
        IFNMachineOutputBuffer outputBuffer = new IFNMachineOutputBuffer();
        long storedAmountQ = 5L * IntegratedFluidNetwork.AMOUNT_SCALE;
        long processAmountQ = IntegratedFluidNetwork.AMOUNT_SCALE;
        firstInput.addState(water, storedAmountQ, IntegratedFluidNetwork.toEnthalpyQFromSpecific(300.0d, storedAmountQ));
        secondInput.addState(water, storedAmountQ, IntegratedFluidNetwork.toEnthalpyQFromSpecific(280.0d, storedAmountQ));

        IFNBufferedDualOutputProcess.Result result = IFNBufferedDualOutputProcess.execute(
            IFNBufferedDualOutputProcess.Request.of(
                firstInput,
                firstOutput,
                outputBuffer,
                "red",
                water,
                processAmountQ,
                secondInput,
                secondOutput,
                outputBuffer,
                "blue",
                water,
                processAmountQ,
                ignored -> 350.0d,
                ignored -> 250.0d,
                100.0f));

        assertEquals(IFNMachineProcessStatus.SUCCESS, result.getStatus());
        assertEquals(storedAmountQ - processAmountQ, firstInput.getAmountQ());
        assertEquals(storedAmountQ - processAmountQ, secondInput.getAmountQ());
        assertEquals(0L, firstOutput.getAmountQ());
        assertEquals(0L, secondOutput.getAmountQ());
        assertEquals(processAmountQ, outputBuffer.pendingOutput("red").amountQ());
        assertEquals(processAmountQ, outputBuffer.pendingOutput("blue").amountQ());
        assertEquals(350.0d, outputBuffer.pendingOutput("red").specificEnthalpy(), 1.0e-6d);
        assertEquals(250.0d, outputBuffer.pendingOutput("blue").specificEnthalpy(), 1.0e-6d);
    }

    @Test
    void incompatibleSecondPortRestoresBothInputsAndDoesNotWriteFirstPort() {
        Fluid water = IFNTestSupport.waterFluid();
        IntegratedFluidNetwork firstInput = IFNTestSupport.newNetwork(water, 10_000, 0, 100.0f);
        IntegratedFluidNetwork secondInput = IFNTestSupport.newNetwork(water, 10_000, 0, 100.0f);
        IntegratedFluidNetwork firstOutput = IFNTestSupport.newNetwork(water, 10_000, 0, 100.0f);
        IntegratedFluidNetwork secondOutput = IFNTestSupport.newNetwork(water, 10_000, 0, 100.0f);
        IFNMachineOutputBuffer outputBuffer = new IFNMachineOutputBuffer();
        long storedAmountQ = 5L * IntegratedFluidNetwork.AMOUNT_SCALE;
        firstInput.addState(water, storedAmountQ, IntegratedFluidNetwork.toEnthalpyQFromSpecific(300.0d, storedAmountQ));
        secondInput.addState(water, storedAmountQ, IntegratedFluidNetwork.toEnthalpyQFromSpecific(280.0d, storedAmountQ));
        outputBuffer.addPendingOutput("blue", IFNBatchState.of("steam", IntegratedFluidNetwork.AMOUNT_SCALE, 250.0d, 1.0f));

        IFNBufferedDualOutputProcess.Result result = IFNBufferedDualOutputProcess.execute(
            IFNBufferedDualOutputProcess.Request.of(
                firstInput,
                firstOutput,
                outputBuffer,
                "red",
                water,
                IntegratedFluidNetwork.AMOUNT_SCALE,
                secondInput,
                secondOutput,
                outputBuffer,
                "blue",
                water,
                IntegratedFluidNetwork.AMOUNT_SCALE,
                ignored -> 350.0d,
                ignored -> 250.0d,
                100.0f));

        assertEquals(IFNMachineProcessStatus.OUTPUT_BLOCKED, result.getStatus());
        assertEquals(storedAmountQ, firstInput.getAmountQ());
        assertEquals(storedAmountQ, secondInput.getAmountQ());
        assertTrue(outputBuffer.pendingOutput("red").isEmpty());
        assertEquals("steam", outputBuffer.pendingOutput("blue").fluidName());
    }
}
