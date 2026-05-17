package gregtech.api.metatileentity.implementations.integratedfluid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import net.minecraftforge.fluids.Fluid;

class IFNSingleOutputProcessTest {

    @Test
    void frozenInputNetworkStopsProcessBeforeThermoWork() {
        Fluid fluid = IFNTestSupport.liquidFluid();
        IntegratedFluidNetwork input = IFNTestSupport.newNetwork(fluid, 10_000, 0, 100.0f);
        IntegratedFluidNetwork output = IFNTestSupport.newNetwork(fluid, 10_000, 0, 100.0f);
        long amountQ = 5L * IntegratedFluidNetwork.AMOUNT_SCALE;
        input.addState(fluid, amountQ, IntegratedFluidNetwork.toEnthalpyQFromSpecific(300.0d, amountQ));
        AtomicBoolean providerCalled = new AtomicBoolean(false);

        input.freeze("test freeze");

        IFNSingleOutputProcess.Result result = IFNSingleOutputProcess.execute(IFNSingleOutputProcess.Request.of(
            input,
            output,
            fluid,
            IntegratedFluidNetwork.AMOUNT_SCALE,
            ignored -> {
                providerCalled.set(true);
                return 350.0d;
            },
            1.0f));

        assertEquals(IFNSingleOutputProcess.Status.INPUT_BLOCKED, result.getStatus());
        assertFalse(providerCalled.get());
        assertEquals(amountQ, input.getAmountQ());
        assertEquals(0L, output.getAmountQ());
    }

    @Test
    void frozenOutputNetworkStopsProcessBeforeInputExtraction() {
        Fluid fluid = IFNTestSupport.liquidFluid();
        IntegratedFluidNetwork input = IFNTestSupport.newNetwork(fluid, 10_000, 0, 100.0f);
        IntegratedFluidNetwork output = IFNTestSupport.newNetwork(fluid, 10_000, 0, 100.0f);
        long amountQ = 5L * IntegratedFluidNetwork.AMOUNT_SCALE;
        input.addState(fluid, amountQ, IntegratedFluidNetwork.toEnthalpyQFromSpecific(300.0d, amountQ));

        output.freeze("test freeze");

        IFNSingleOutputProcess.Result result = IFNSingleOutputProcess.execute(IFNSingleOutputProcess.Request.of(
            input,
            output,
            fluid,
            IntegratedFluidNetwork.AMOUNT_SCALE,
            ignored -> 350.0d,
            1.0f));

        assertEquals(IFNSingleOutputProcess.Status.OUTPUT_BLOCKED, result.getStatus());
        assertEquals(amountQ, input.getAmountQ());
        assertEquals(0L, output.getAmountQ());
    }

    @Test
    void outputMutationFailureRestoresExtractedInputState() {
        Fluid fluid = IFNTestSupport.liquidFluid();
        IntegratedFluidNetwork input = IFNTestSupport.newNetwork(fluid, 10_000, 0, 100.0f);
        IntegratedFluidNetwork output = rejectingOutputNetwork(fluid);
        long amountQ = 5L * IntegratedFluidNetwork.AMOUNT_SCALE;

        input.addState(fluid, amountQ, IntegratedFluidNetwork.toEnthalpyQFromSpecific(300.0d, amountQ));

        IFNSingleOutputProcess.Result result = IFNSingleOutputProcess.execute(IFNSingleOutputProcess.Request.of(
            input,
            output,
            fluid,
            IntegratedFluidNetwork.AMOUNT_SCALE,
            ignored -> 350.0d,
            1.0f));

        assertEquals(IFNSingleOutputProcess.Status.OUTPUT_BLOCKED, result.getStatus());
        assertEquals(amountQ, input.getAmountQ());
        assertEquals(0L, output.getAmountQ());
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
