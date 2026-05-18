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

final class HorizonQAVfnScenario {

    private static final int PIPE_CAPACITY = 1000;
    private static final int MACHINE_HATCH_CAPACITY = 0;
    private static final int INPUT_OUTPUT_HATCH_CAPACITY = 10_000;
    private static final int INJECTOR_EXTRACTOR_HATCH_CAPACITY = 0;

    final Fluid fluid;
    final ScenarioNetworkManager manager;

    private HorizonQAVfnScenario(Fluid fluid) {
        this.fluid = fluid;
        this.manager = new ScenarioNetworkManager(fluid);
    }

    static HorizonQAVfnScenario create() {
        return new HorizonQAVfnScenario(IFNTestSupport.liquidFluid());
    }

    static HorizonQAVfnScenario create(Fluid fluid) {
        return new HorizonQAVfnScenario(fluid);
    }

    ScenarioMember pipe(String name) {
        return pipe(name, PIPE_CAPACITY);
    }

    ScenarioMember pipe(String name, int capacityContribution) {
        return new ScenarioMember(name, capacityContribution);
    }

    ScenarioMember inputHatch(String name) {
        return new ScenarioMember(name, INPUT_OUTPUT_HATCH_CAPACITY, INPUT_OUTPUT_HATCH_CAPACITY);
    }

    ScenarioMember outputHatch(String name) {
        return new ScenarioMember(name, INPUT_OUTPUT_HATCH_CAPACITY, INPUT_OUTPUT_HATCH_CAPACITY);
    }

    ScenarioMember injectorHatch(String name) {
        return new ScenarioMember(name, INJECTOR_EXTRACTOR_HATCH_CAPACITY);
    }

    ScenarioMember extractorHatch(String name) {
        return new ScenarioMember(name, INJECTOR_EXTRACTOR_HATCH_CAPACITY);
    }

    ScenarioMember machineHatch(String name) {
        return new ScenarioMember(name, MACHINE_HATCH_CAPACITY);
    }

    List<ScenarioMember> members(ScenarioMember... members) {
        return Arrays.asList(members);
    }

    void connect(ScenarioMember first, ScenarioMember second) {
        manager.connect(first, second);
    }

    void disconnect(ScenarioMember first, ScenarioMember second) {
        manager.disconnect(first, second);
    }

    void connectChain(ScenarioMember... members) {
        for (int i = 1; i < members.length; i++) {
            connect(members[i - 1], members[i]);
        }
    }

    void addNetworkFrom(ScenarioMember member) {
        manager.onMemberAdded(member);
    }

    void changedAt(ScenarioMember member) {
        manager.onConnectionChanged(member);
    }

    SeededFluid seedFluid(ScenarioMember member, long refLiters, double specificEnthalpy) {
        IntegratedFluidNetwork network = member.getNetwork();
        assertNotNull(network, member.name);
        long amountQ = refLiters * IntegratedFluidNetwork.AMOUNT_SCALE;
        long enthalpyQ = IntegratedFluidNetwork.toEnthalpyQFromSpecific(specificEnthalpy, amountQ);
        assertTrue(network.addState(fluid, amountQ, enthalpyQ));
        return new SeededFluid(amountQ, enthalpyQ);
    }

    void assertSingleNetwork(List<ScenarioMember> members, int expectedMembers) {
        IntegratedFluidNetwork network = members.get(0).getNetwork();
        assertNotNull(network);
        for (ScenarioMember member : members) {
            assertSame(network, member.getNetwork(), member.name);
        }
        assertEquals(expectedMembers, network.getMemberCount());
        assertEquals(expectedMembers, network.getExpectedMemberCount());
    }

    void assertSingleNetworkCapacity(List<ScenarioMember> members, int expectedBaseCapacity,
        int expectedAccumulatorCapacity) {
        IntegratedFluidNetwork network = members.get(0).getNetwork();
        assertNotNull(network);
        assertEquals(expectedBaseCapacity, network.getMaxCapacity());
        assertEquals(expectedAccumulatorCapacity, network.getAccumulatorCapacity());
    }

    void assertNetworkGroups(List<ScenarioMember> members, int expectedGroups) {
        assertEquals(expectedGroups, networksOf(members).size());
        for (IntegratedFluidNetwork network : networksOf(members)) {
            assertEquals(network.getMemberCount(), network.getExpectedMemberCount());
        }
    }

    void assertNetworkGroups(List<ScenarioMember> members, int expectedGroups, SeededFluid expectedFluid) {
        assertNetworkGroups(members, expectedGroups);
        assertTotals(members, expectedFluid);
    }

    void assertTotals(List<ScenarioMember> members, SeededFluid expectedFluid) {
        long actualAmountQ = 0L;
        long actualEnthalpyQ = 0L;
        for (IntegratedFluidNetwork network : networksOf(members)) {
            actualAmountQ += network.getAmountQ();
            actualEnthalpyQ += network.getEnthalpyQ();
        }
        assertEquals(expectedFluid.amountQ, actualAmountQ);
        assertEquals(expectedFluid.enthalpyQ, actualEnthalpyQ);
    }

    Set<IntegratedFluidNetwork> networksOf(List<ScenarioMember> members) {
        Set<IntegratedFluidNetwork> networks = new HashSet<>();
        for (ScenarioMember member : members) {
            assertNotNull(member.getNetwork(), member.name);
            networks.add(member.getNetwork());
        }
        return networks;
    }

    static final class SeededFluid {

        final long amountQ;
        final long enthalpyQ;

        private SeededFluid(long amountQ, long enthalpyQ) {
            this.amountQ = amountQ;
            this.enthalpyQ = enthalpyQ;
        }
    }

    static final class ScenarioMember implements IIntegratedFluidMember {

        final String name;
        private final int capacityContribution;
        private final int accumulatorContribution;
        private IntegratedFluidNetwork network;
        private UUID networkId;

        private ScenarioMember(String name, int capacityContribution) {
            this(name, capacityContribution, 0);
        }

        private ScenarioMember(String name, int capacityContribution, int accumulatorContribution) {
            this.name = name;
            this.capacityContribution = capacityContribution;
            this.accumulatorContribution = accumulatorContribution;
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

        @Override
        public int getAccumulatorContribution() {
            return accumulatorContribution;
        }
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
}
