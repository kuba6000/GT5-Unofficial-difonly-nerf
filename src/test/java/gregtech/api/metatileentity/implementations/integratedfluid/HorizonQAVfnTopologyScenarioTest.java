package gregtech.api.metatileentity.implementations.integratedfluid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraftforge.fluids.Fluid;

import org.junit.jupiter.api.Test;

class HorizonQAVfnTopologyScenarioTest {

    @Test
    void repeatedPipeSplitsAndMergesKeepOneCopyOfStoredFluid() {
        Fluid fluid = IFNTestSupport.liquidFluid();
        ScenarioNetworkManager manager = new ScenarioNetworkManager(fluid);
        ScenarioMember pipeA = new ScenarioMember("pipe-a", 1000);
        ScenarioMember pipeB = new ScenarioMember("pipe-b", 1000);
        ScenarioMember pipeC = new ScenarioMember("pipe-c", 1000);
        ScenarioMember pipeD = new ScenarioMember("pipe-d", 1000);
        List<ScenarioMember> members = Arrays.asList(pipeA, pipeB, pipeC, pipeD);
        long expectedAmountQ = 12L * IntegratedFluidNetwork.AMOUNT_SCALE;
        long expectedEnthalpyQ = IntegratedFluidNetwork.toEnthalpyQFromSpecific(300.0d, expectedAmountQ);

        manager.connect(pipeA, pipeB);
        manager.connect(pipeB, pipeC);
        manager.connect(pipeC, pipeD);
        manager.onMemberAdded(pipeA);
        assertSingleNetwork(members, 4);

        IntegratedFluidNetwork initialNetwork = pipeA.getNetwork();
        assertTrue(initialNetwork.addState(fluid, expectedAmountQ, expectedEnthalpyQ));
        assertTotals(members, expectedAmountQ, expectedEnthalpyQ);

        manager.disconnect(pipeB, pipeC);
        manager.onConnectionChanged(pipeB);
        assertNetworkGroups(members, 2, expectedAmountQ, expectedEnthalpyQ);

        manager.connect(pipeB, pipeC);
        manager.onConnectionChanged(pipeB);
        assertSingleNetwork(members, 4);
        assertTotals(members, expectedAmountQ, expectedEnthalpyQ);

        manager.disconnect(pipeA, pipeB);
        manager.onConnectionChanged(pipeA);
        assertNetworkGroups(members, 2, expectedAmountQ, expectedEnthalpyQ);

        manager.connect(pipeA, pipeB);
        manager.onConnectionChanged(pipeA);
        assertSingleNetwork(members, 4);
        assertTotals(members, expectedAmountQ, expectedEnthalpyQ);
    }

    private static void assertSingleNetwork(List<ScenarioMember> members, int expectedMembers) {
        IntegratedFluidNetwork network = members.get(0).getNetwork();
        assertNotNull(network);
        for (ScenarioMember member : members) {
            assertSame(network, member.getNetwork(), member.name);
        }
        assertEquals(expectedMembers, network.getMemberCount());
        assertEquals(expectedMembers, network.getExpectedMemberCount());
    }

    private static void assertNetworkGroups(List<ScenarioMember> members, int expectedGroups, long expectedAmountQ,
        long expectedEnthalpyQ) {
        assertEquals(expectedGroups, networksOf(members).size());
        assertTotals(members, expectedAmountQ, expectedEnthalpyQ);
        for (IntegratedFluidNetwork network : networksOf(members)) {
            assertEquals(network.getMemberCount(), network.getExpectedMemberCount());
        }
    }

    private static void assertTotals(List<ScenarioMember> members, long expectedAmountQ, long expectedEnthalpyQ) {
        long actualAmountQ = 0L;
        long actualEnthalpyQ = 0L;
        for (IntegratedFluidNetwork network : networksOf(members)) {
            actualAmountQ += network.getAmountQ();
            actualEnthalpyQ += network.getEnthalpyQ();
        }
        assertEquals(expectedAmountQ, actualAmountQ);
        assertEquals(expectedEnthalpyQ, actualEnthalpyQ);
    }

    private static Set<IntegratedFluidNetwork> networksOf(List<ScenarioMember> members) {
        Set<IntegratedFluidNetwork> networks = new HashSet<>();
        for (ScenarioMember member : members) {
            assertNotNull(member.getNetwork(), member.name);
            networks.add(member.getNetwork());
        }
        return networks;
    }

    private static final class ScenarioNetworkManager extends NetworkManager {

        private final Fluid fluid;
        private final Map<IIntegratedFluidMember, Set<IIntegratedFluidMember>> links = new HashMap<>();

        private ScenarioNetworkManager(Fluid fluid) {
            super(null, new IntegratedFluidNetworkSavedData());
            this.fluid = fluid;
        }

        private void connect(IIntegratedFluidMember first, IIntegratedFluidMember second) {
            links.computeIfAbsent(first, ignored -> new HashSet<>()).add(second);
            links.computeIfAbsent(second, ignored -> new HashSet<>()).add(first);
        }

        private void disconnect(IIntegratedFluidMember first, IIntegratedFluidMember second) {
            Set<IIntegratedFluidMember> firstLinks = links.get(first);
            if (firstLinks != null) {
                firstLinks.remove(second);
            }
            Set<IIntegratedFluidMember> secondLinks = links.get(second);
            if (secondLinks != null) {
                secondLinks.remove(first);
            }
        }

        @Override
        public List<IIntegratedFluidMember> findConnectedNeighbors(IIntegratedFluidMember member) {
            Set<IIntegratedFluidMember> neighbors = links.get(member);
            if (neighbors == null) {
                return Collections.emptyList();
            }
            return new ArrayList<>(neighbors);
        }

        @Override
        protected IntegratedFluidNetwork newNetwork(UUID networkId) {
            return new ScenarioNetwork(networkId, fluid);
        }
    }

    private static final class ScenarioNetwork extends IntegratedFluidNetwork {

        private final Fluid fluid;

        private ScenarioNetwork(UUID networkId, Fluid fluid) {
            super(networkId);
            this.fluid = fluid;
        }

        @Override
        public Fluid getFluid() {
            return getAmountQ() > 0L ? fluid : null;
        }
    }

    private static final class ScenarioMember implements IIntegratedFluidMember {

        private final String name;
        private final int capacityContribution;
        private IntegratedFluidNetwork network;
        private UUID networkId;

        private ScenarioMember(String name, int capacityContribution) {
            this.name = name;
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
