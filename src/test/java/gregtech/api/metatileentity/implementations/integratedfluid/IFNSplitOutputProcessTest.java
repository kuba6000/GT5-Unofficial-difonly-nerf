package gregtech.api.metatileentity.implementations.integratedfluid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import net.minecraftforge.fluids.Fluid;

class IFNSplitOutputProcessTest {

    @Test
    void frozenInputNetworkStopsProcessBeforeThermoWork() {
        Fluid fluid = IFNTestSupport.liquidFluid();
        IntegratedFluidNetwork input = IFNTestSupport.newNetwork(fluid, 10_000, 0, 100.0f);
        IntegratedFluidNetwork firstOutput = IFNTestSupport.newNetwork(fluid, 10_000, 0, 100.0f);
        IntegratedFluidNetwork secondOutput = IFNTestSupport.newNetwork(fluid, 10_000, 0, 100.0f);
        long amountQ = 5L * IntegratedFluidNetwork.AMOUNT_SCALE;
        input.addState(fluid, amountQ, IntegratedFluidNetwork.toEnthalpyQFromSpecific(300.0d, amountQ));
        AtomicBoolean providerCalled = new AtomicBoolean(false);

        input.freeze("test freeze");

        IFNSplitOutputProcess.Result result = IFNSplitOutputProcess.execute(IFNSplitOutputProcess.Request.of(
            input,
            firstOutput,
            fluid,
            secondOutput,
            fluid,
            2L * IntegratedFluidNetwork.AMOUNT_SCALE,
            0.5d,
            ignored -> {
                providerCalled.set(true);
                return 350.0d;
            },
            ignored -> 250.0d,
            1.0f));

        assertEquals(IFNMachineProcessStatus.INPUT_BLOCKED, result.getStatus());
        assertFalse(providerCalled.get());
        assertEquals(amountQ, input.getAmountQ());
        assertEquals(0L, firstOutput.getAmountQ());
        assertEquals(0L, secondOutput.getAmountQ());
    }

    @Test
    void outputMutationFailureRestoresExtractedInputStateAndOutputs() {
        Fluid fluid = IFNTestSupport.liquidFluid();
        IntegratedFluidNetwork input = IFNTestSupport.newNetwork(fluid, 10_000, 0, 100.0f);
        IntegratedFluidNetwork firstOutput = IFNTestSupport.newNetwork(fluid, 10_000, 0, 100.0f);
        IntegratedFluidNetwork secondOutput = rejectingOutputNetwork(fluid);
        long amountQ = 5L * IntegratedFluidNetwork.AMOUNT_SCALE;

        input.addState(fluid, amountQ, IntegratedFluidNetwork.toEnthalpyQFromSpecific(300.0d, amountQ));

        IFNSplitOutputProcess.Result result = IFNSplitOutputProcess.execute(IFNSplitOutputProcess.Request.of(
            input,
            firstOutput,
            fluid,
            secondOutput,
            fluid,
            2L * IntegratedFluidNetwork.AMOUNT_SCALE,
            0.5d,
            ignored -> 350.0d,
            ignored -> 250.0d,
            1.0f));

        assertEquals(IFNMachineProcessStatus.OUTPUT_BLOCKED, result.getStatus());
        assertEquals(amountQ, input.getAmountQ());
        assertEquals(0L, firstOutput.getAmountQ());
        assertEquals(0L, secondOutput.getAmountQ());
    }

    private static IntegratedFluidNetwork rejectingOutputNetwork(Fluid fluid) {
        IntegratedFluidNetwork network = new IntegratedFluidNetwork() {

            @Override
            public Fluid getFluid() {
                return getAmountQ() > 0L ? fluid : null;
            }

            @Override
            public boolean addState(Fluid addFluid, long addAmountQ, long addEnthalpyQ) {
                return false;
            }
        };
        network.addMember(new TestMember());
        return network;
    }

    private static final class TestMember implements IIntegratedFluidMember {

        private IntegratedFluidNetwork network;
        private UUID networkId;

        @Override
        public IntegratedFluidNetwork getNetwork() {
            return network;
        }

        @Override
        public void setNetwork(IntegratedFluidNetwork network) {
            this.network = network;
        }

        @Override
        public UUID getNetworkId() {
            return networkId;
        }

        @Override
        public void setNetworkId(UUID id) {
            this.networkId = id;
        }

        @Override
        public void onNetworkUpdate() {}

        @Override
        public int getCapacityContribution() {
            return 10_000;
        }
    }
}
