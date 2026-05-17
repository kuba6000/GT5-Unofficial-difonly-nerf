package gregtech.api.metatileentity.implementations.integratedfluid.thermal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import gregtech.api.metatileentity.implementations.integratedfluid.IIntegratedFluidMember;
import gregtech.api.metatileentity.implementations.integratedfluid.IntegratedFluidNetwork;

class IFNHeatExchangeRuntimeTest {

    @Test
    void energyDeltaMovesTowardAmbientWithoutOvershoot() {
        assertEquals(-200.0d, IFNHeatExchangeRuntime.computeEnergyDelta(500.0d, 300.0d, 1000.0d, 1.0d));
        assertEquals(200.0d, IFNHeatExchangeRuntime.computeEnergyDelta(100.0d, 300.0d, 1000.0d, 1.0d));
        assertEquals(-20.0d, IFNHeatExchangeRuntime.computeEnergyDelta(320.0d, 300.0d, 1.0d, 100.0d));
    }

    @Test
    void passiveConductanceAggregatesMemberContributions() {
        assertEquals(
            3.5d,
            IFNHeatExchangeRuntime.computePassiveConductance(
                Arrays.asList(new TestMember(2.0d), new TestMember(1.5d), new TestMember(-5.0d))));
    }

    @Test
    void pendingNetworkSkipsPassiveHeatExchange() {
        ProbeNetwork network = new ProbeNetwork();
        network.setPending(true);

        IFNHeatExchangeRuntime.apply(network, IFNAmbientContext.fixed(300.0f));

        assertEquals(0, network.heatExchangeApplications);
    }

    @Test
    void frozenNetworkStillAppliesPassiveHeatExchange() {
        ProbeNetwork network = new ProbeNetwork();
        network.freeze("test");

        IFNHeatExchangeRuntime.apply(network, IFNAmbientContext.fixed(300.0f));

        assertEquals(1, network.heatExchangeApplications);
    }

    private static final class ProbeNetwork extends IntegratedFluidNetwork {

        private int heatExchangeApplications;

        @Override
        public void applyHeatLoss(float ambientTemperature) {
            heatExchangeApplications++;
        }
    }

    private static final class TestMember implements IIntegratedFluidMember {

        private final double passiveConductance;

        private TestMember(double passiveConductance) {
            this.passiveConductance = passiveConductance;
        }

        @Override
        public IntegratedFluidNetwork getNetwork() {
            return null;
        }

        @Override
        public void setNetwork(IntegratedFluidNetwork network) {}

        @Override
        public UUID getNetworkId() {
            return null;
        }

        @Override
        public void setNetworkId(UUID id) {}

        @Override
        public void onNetworkUpdate() {}

        @Override
        public int getCapacityContribution() {
            return 0;
        }

        @Override
        public double getPassiveHeatConductanceEuPerKelvinPerSecond() {
            return passiveConductance;
        }
    }
}
