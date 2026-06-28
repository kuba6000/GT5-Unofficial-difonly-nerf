package gregtech.api.metatileentity.implementations.integratedfluid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraftforge.fluids.Fluid;

import org.junit.jupiter.api.Test;

class IFNPressureRegulatorTransferExecutorTest {

    @Test
    void acceptedTransferMovesStateWithoutExceedingSetpointTolerance() {
        Fluid fluid = IFNTestSupport.pressureSensitiveLiquidFluid();
        IntegratedFluidNetwork input = networkAtPressure(fluid, 4.0d);
        IntegratedFluidNetwork output = networkAtPressure(fluid, 2.0d);
        long inputAmountBefore = input.getAmountQ();
        long outputAmountBefore = output.getAmountQ();
        float outputPressureBefore = output.getPressure();

        IFNPressureRegulatorTransferPlanner.Plan plan = IFNPressureRegulatorTransferExecutor.transfer(
            input,
            output,
            3.0f,
            0.05f,
            100L * IntegratedFluidNetwork.AMOUNT_SCALE);

        assertEquals(IFNPressureRegulatorTransferPlanner.Status.ACCEPTED, plan.status());
        assertTrue(plan.acceptedAmountQ() > 0L);
        assertEquals(inputAmountBefore - plan.acceptedAmountQ(), input.getAmountQ());
        assertEquals(outputAmountBefore + plan.acceptedAmountQ(), output.getAmountQ());
        assertTrue(output.getPressure() > outputPressureBefore);
        assertTrue(output.getPressure() <= 3.05f);
    }

    private static IntegratedFluidNetwork networkAtPressure(Fluid fluid, double pressureBar) {
        IntegratedFluidNetwork network = IFNTestSupport.newNetwork(fluid, 10_000, 10_000, 100.0f);
        IFNTestSupport.seedNetworkAtPressureAndTemperature(network, fluid, pressureBar, 300.0d);
        return network;
    }
}
