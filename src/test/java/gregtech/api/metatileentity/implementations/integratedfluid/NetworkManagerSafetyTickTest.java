package gregtech.api.metatileentity.implementations.integratedfluid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Field;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class NetworkManagerSafetyTickTest {

    @Test
    void worldTickAppliesOperationalFailureBeforeHeatLossInterval() {
        NetworkManager manager = new NetworkManager(null, new IntegratedFluidNetworkSavedData());
        TestPipe pipe = new TestPipe();

        manager.onMemberAdded(pipe);
        IntegratedFluidNetwork network = pipe.network;
        network.setPressure(12.0f);

        manager.onWorldTick(1L);

        assertNull(pipe.network);
    }

    @Test
    void frozenNetworkStillAppliesHeatLossOnWorldTick() {
        NetworkManager manager = new NetworkManager(null, new IntegratedFluidNetworkSavedData());
        HeatLossProbeNetwork network = new HeatLossProbeNetwork();

        network.freeze("test freeze");
        addNetwork(manager, network);
        manager.onWorldTick(20L);

        assertEquals(1, network.heatLossCalls);
    }

    @Test
    void pendingNetworkSkipsHeatLossOnWorldTick() {
        NetworkManager manager = new NetworkManager(null, new IntegratedFluidNetworkSavedData());
        HeatLossProbeNetwork network = new HeatLossProbeNetwork();

        network.setPending(true);
        addNetwork(manager, network);
        manager.onWorldTick(20L);

        assertEquals(0, network.heatLossCalls);
    }

    @SuppressWarnings("unchecked")
    private static void addNetwork(NetworkManager manager, IntegratedFluidNetwork network) {
        try {
            Field field = NetworkManager.class.getDeclaredField("allNetworks");
            field.setAccessible(true);
            ((Set<IntegratedFluidNetwork>) field.get(manager)).add(network);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static final class TestPipe implements IIntegratedFluidMember {

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
            return 100;
        }

        @Override
        public int getAccumulatorContribution() {
            return 1000;
        }

        @Override
        public float getAccumulatorMaxPressureBar() {
            return 10.0f;
        }

        @Override
        public boolean isOperationalFailureCandidate() {
            return true;
        }

        @Override
        public float getMaxPressureDifferentialBar() {
            return 6.0f;
        }
    }

    private static final class HeatLossProbeNetwork extends IntegratedFluidNetwork {

        private int heatLossCalls;

        @Override
        public void applyHeatLoss(float ambientTemperature) {
            heatLossCalls++;
        }
    }
}
