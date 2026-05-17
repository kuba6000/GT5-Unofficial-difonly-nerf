package gregtech.api.metatileentity.implementations.integratedfluid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import net.minecraftforge.fluids.Fluid;

class IFNDualOutputProcessTest {

    @Test
    void frozenInputNetworkStopsProcessBeforeThermoWork() {
        Fluid fluid = IFNTestSupport.liquidFluid();
        IntegratedFluidNetwork firstInput = IFNTestSupport.newNetwork(fluid, 10_000, 0, 100.0f);
        IntegratedFluidNetwork secondInput = IFNTestSupport.newNetwork(fluid, 10_000, 0, 100.0f);
        IntegratedFluidNetwork firstOutput = IFNTestSupport.newNetwork(fluid, 10_000, 0, 100.0f);
        IntegratedFluidNetwork secondOutput = IFNTestSupport.newNetwork(fluid, 10_000, 0, 100.0f);
        long amountQ = 5L * IntegratedFluidNetwork.AMOUNT_SCALE;
        firstInput.addState(fluid, amountQ, IntegratedFluidNetwork.toEnthalpyQFromSpecific(300.0d, amountQ));
        secondInput.addState(fluid, amountQ, IntegratedFluidNetwork.toEnthalpyQFromSpecific(300.0d, amountQ));
        AtomicBoolean providerCalled = new AtomicBoolean(false);

        firstInput.freeze("test freeze");

        IFNDualOutputProcess.Result result = IFNDualOutputProcess.execute(IFNDualOutputProcess.Request.of(
            firstInput,
            firstOutput,
            fluid,
            amountQ,
            secondInput,
            secondOutput,
            fluid,
            amountQ,
            ignored -> {
                providerCalled.set(true);
                return 350.0d;
            },
            ignored -> 250.0d,
            1.0f));

        assertEquals(IFNDualOutputProcess.Status.INPUT_BLOCKED, result.getStatus());
        assertFalse(providerCalled.get());
        assertEquals(amountQ, firstInput.getAmountQ());
        assertEquals(amountQ, secondInput.getAmountQ());
        assertEquals(0L, firstOutput.getAmountQ());
        assertEquals(0L, secondOutput.getAmountQ());
    }

    @Test
    void outputMutationFailureRestoresBothInputsAndOutputs() {
        Fluid fluid = IFNTestSupport.liquidFluid();
        IntegratedFluidNetwork firstInput = IFNTestSupport.newNetwork(fluid, 10_000, 0, 100.0f);
        IntegratedFluidNetwork secondInput = IFNTestSupport.newNetwork(fluid, 10_000, 0, 100.0f);
        IntegratedFluidNetwork firstOutput = IFNTestSupport.newNetwork(fluid, 10_000, 0, 100.0f);
        IntegratedFluidNetwork secondOutput = rejectingOutputNetwork(fluid);
        long amountQ = 5L * IntegratedFluidNetwork.AMOUNT_SCALE;
        long storedAmountQ = 2L * amountQ;

        firstInput.addState(fluid, storedAmountQ, IntegratedFluidNetwork.toEnthalpyQFromSpecific(300.0d, storedAmountQ));
        secondInput.addState(fluid, storedAmountQ, IntegratedFluidNetwork.toEnthalpyQFromSpecific(300.0d, storedAmountQ));

        IFNDualOutputProcess.Result result = IFNDualOutputProcess.execute(IFNDualOutputProcess.Request.of(
            firstInput,
            firstOutput,
            fluid,
            amountQ,
            secondInput,
            secondOutput,
            fluid,
            amountQ,
            ignored -> 350.0d,
            ignored -> 250.0d,
            100.0f));

        assertEquals(IFNDualOutputProcess.Status.OUTPUT_BLOCKED, result.getStatus());
        assertEquals(storedAmountQ, firstInput.getAmountQ());
        assertEquals(storedAmountQ, secondInput.getAmountQ());
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
