package gregtech.api.metatileentity.implementations.integratedfluid.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraftforge.fluids.Fluid;

import gregtech.api.metatileentity.implementations.integratedfluid.IFNTestSupport;
import gregtech.api.metatileentity.implementations.integratedfluid.IIntegratedFluidMember;
import gregtech.api.metatileentity.implementations.integratedfluid.IntegratedFluidNetwork;
import gregtech.api.metatileentity.implementations.integratedfluid.solver.IFNSolvedState;

class IntegratedFluidNetworkCanonicalStateTest {

    @Test
    void clearFluidExposesEmptyCanonicalState() {
        IntegratedFluidNetwork network = new IntegratedFluidNetwork();

        network.clearFluid();

        assertTrue(network.getCanonicalState().isEmpty());
    }

    @Test
    void loadedTraceSubstanceRemainsCanonicalState() {
        Fluid fluid = IFNTestSupport.liquidFluid();
        IntegratedFluidNetwork network = IFNTestSupport.newNetwork(fluid, 1000, 0, 100.0f);

        network.loadState(fluid.getName(), 1L, 2L, IntegratedFluidNetwork.DEFAULT_PRESSURE, 0);

        IFNCanonicalState state = network.getCanonicalState();
        assertFalse(state.isEmpty());
        assertEquals(fluid.getName(), state.fluidId().get());
        assertEquals(1L, state.substanceAmount().rawUnits());
        assertEquals(2L, state.internalEnergy().rawUnits());
    }

    @Test
    void replaceCanonicalStateCommitsNonEmptyState() {
        Fluid fluid = IFNTestSupport.liquidFluid();
        IntegratedFluidNetwork network = IFNTestSupport.newNetwork(fluid, 1000, 0, 100.0f);

        network.replaceCanonicalState(IFNCanonicalState.of(
            fluid.getName(),
            gregtech.api.metatileentity.implementations.integratedfluid.amount.SubstanceAmount.fromRawUnits(12L),
            gregtech.api.metatileentity.implementations.integratedfluid.amount.EnergyAmount.fromRawUnits(34L)
        ));

        IFNCanonicalState state = network.getCanonicalState();
        assertFalse(state.isEmpty());
        assertEquals(fluid.getName(), state.fluidId().get());
        assertEquals(12L, state.substanceAmount().rawUnits());
        assertEquals(34L, state.internalEnergy().rawUnits());
    }

    @Test
    void replaceCanonicalStateWithEmptyClearsNetwork() {
        Fluid fluid = IFNTestSupport.liquidFluid();
        IntegratedFluidNetwork network = IFNTestSupport.newNetwork(fluid, 1000, 0, 100.0f);
        network.loadState(fluid.getName(), 10L, 20L, IntegratedFluidNetwork.DEFAULT_PRESSURE, 0);

        network.replaceCanonicalState(IFNCanonicalState.empty());

        assertTrue(network.getCanonicalState().isEmpty());
        assertEquals(0L, network.getAmountQ());
        assertEquals(0L, network.getEnthalpyQ());
    }

    @Test
    void solvedStateCacheIsReusedUntilCanonicalStateChanges() {
        Fluid fluid = IFNTestSupport.liquidFluid();
        IntegratedFluidNetwork network = IFNTestSupport.newNetwork(fluid, 1000, 0, 100.0f);
        network.loadState(fluid.getName(), 10L * IntegratedFluidNetwork.AMOUNT_SCALE,
            3000L * IntegratedFluidNetwork.ENTHALPY_SCALE, IntegratedFluidNetwork.DEFAULT_PRESSURE, 0);

        IFNSolvedState first = network.getSolvedState();

        assertSame(first, network.getSolvedState());

        network.replaceCanonicalState(IFNCanonicalState.of(
            fluid.getName(),
            gregtech.api.metatileentity.implementations.integratedfluid.amount.SubstanceAmount.fromRawUnits(
                12L * IntegratedFluidNetwork.AMOUNT_SCALE),
            gregtech.api.metatileentity.implementations.integratedfluid.amount.EnergyAmount.fromRawUnits(
                3600L * IntegratedFluidNetwork.ENTHALPY_SCALE)
        ));

        IFNSolvedState afterMutation = network.getSolvedState();
        assertNotSame(first, afterMutation);
        assertEquals(12L * IntegratedFluidNetwork.AMOUNT_SCALE, afterMutation.amountQ());
    }

    @Test
    void solvedStateCacheInvalidatesWhenTopologyChanges() {
        Fluid fluid = IFNTestSupport.liquidFluid();
        IntegratedFluidNetwork network = IFNTestSupport.newNetwork(fluid, 1000, 0, 100.0f);
        network.loadState(fluid.getName(), 10L * IntegratedFluidNetwork.AMOUNT_SCALE,
            3000L * IntegratedFluidNetwork.ENTHALPY_SCALE, IntegratedFluidNetwork.DEFAULT_PRESSURE, 0);
        IFNSolvedState first = network.getSolvedState();

        network.addMember(new FixedCapacityMember(2000));

        IFNSolvedState afterTopologyChange = network.getSolvedState();
        assertNotSame(first, afterTopologyChange);
    }

    private static final class FixedCapacityMember implements IIntegratedFluidMember {

        private final int capacityContribution;
        private IntegratedFluidNetwork network;
        private UUID networkId;

        private FixedCapacityMember(int capacityContribution) {
            this.capacityContribution = capacityContribution;
        }

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
            return capacityContribution;
        }
    }
}
