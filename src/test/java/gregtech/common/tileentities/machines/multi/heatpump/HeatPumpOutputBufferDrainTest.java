package gregtech.common.tileentities.machines.multi.heatpump;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraftforge.fluids.Fluid;
import org.junit.jupiter.api.Test;

import gregtech.api.metatileentity.implementations.integratedfluid.IFNMachineProcessStatus;
import gregtech.api.metatileentity.implementations.integratedfluid.IFNTestSupport;
import gregtech.api.metatileentity.implementations.integratedfluid.IntegratedFluidNetwork;
import gregtech.api.metatileentity.implementations.integratedfluid.machine.IFNBatchState;
import gregtech.api.metatileentity.implementations.integratedfluid.machine.IFNMachineOutputBuffer;

class HeatPumpOutputBufferDrainTest {

    @Test
    void pushesAllConfiguredPortsInChunks() {
        Fluid water = IFNTestSupport.waterFluid();
        IntegratedFluidNetwork normalOutput = IFNTestSupport.newNetwork(water, 10_000, 0, 100.0f);
        IntegratedFluidNetwork redOutput = IFNTestSupport.newNetwork(water, 10_000, 0, 100.0f);
        IFNMachineOutputBuffer buffer = new IFNMachineOutputBuffer();
        buffer.addPendingOutput(HeatPumpOutputPorts.NORMAL, IFNBatchState.of("water", 2_000_000L, 300.0d, 1.0f));
        buffer.addPendingOutput(HeatPumpOutputPorts.RED, IFNBatchState.of("water", 3_000_000L, 320.0d, 1.0f));

        HeatPumpOutputBufferDrain.Result result = HeatPumpOutputBufferDrain.push(
            buffer,
            1_000_000L,
            HeatPumpOutputBufferDrain.target(HeatPumpOutputPorts.NORMAL, normalOutput),
            HeatPumpOutputBufferDrain.target(HeatPumpOutputPorts.RED, redOutput));

        assertEquals(IFNMachineProcessStatus.SUCCESS, result.status());
        assertTrue(result.outputStillPending());
        assertEquals(1_000_000L, normalOutput.getAmountQ());
        assertEquals(1_000_000L, redOutput.getAmountQ());
        assertEquals(1_000_000L, buffer.pendingOutput(HeatPumpOutputPorts.NORMAL).amountQ());
        assertEquals(2_000_000L, buffer.pendingOutput(HeatPumpOutputPorts.RED).amountQ());
    }

    @Test
    void blockedPortDoesNotPreventOtherPortsFromDraining() {
        Fluid water = IFNTestSupport.waterFluid();
        IntegratedFluidNetwork redOutput = IFNTestSupport.newNetwork(water, 10_000, 0, 100.0f);
        IntegratedFluidNetwork blueOutput = IFNTestSupport.newNetwork(water, 10_000, 0, 100.0f);
        redOutput.freeze("test");
        IFNMachineOutputBuffer buffer = new IFNMachineOutputBuffer();
        buffer.addPendingOutput(HeatPumpOutputPorts.RED, IFNBatchState.of("water", 1_000_000L, 320.0d, 1.0f));
        buffer.addPendingOutput(HeatPumpOutputPorts.BLUE, IFNBatchState.of("water", 1_000_000L, 280.0d, 1.0f));

        HeatPumpOutputBufferDrain.Result result = HeatPumpOutputBufferDrain.push(
            buffer,
            1_000_000L,
            HeatPumpOutputBufferDrain.target(HeatPumpOutputPorts.RED, redOutput),
            HeatPumpOutputBufferDrain.target(HeatPumpOutputPorts.BLUE, blueOutput));

        assertEquals(IFNMachineProcessStatus.OUTPUT_BLOCKED, result.status());
        assertTrue(result.outputStillPending());
        assertEquals(0L, redOutput.getAmountQ());
        assertEquals(1_000_000L, blueOutput.getAmountQ());
        assertEquals(1_000_000L, buffer.pendingOutput(HeatPumpOutputPorts.RED).amountQ());
        assertTrue(buffer.pendingOutput(HeatPumpOutputPorts.BLUE).isEmpty());
    }
}
