package gregtech.api.metatileentity.implementations.integratedfluid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import gregtech.api.metatileentity.implementations.integratedfluid.HorizonQAVfnScenario.ScenarioMember;
import gregtech.api.metatileentity.implementations.integratedfluid.HorizonQAVfnScenario.SeededFluid;

class HorizonQAVfnDeterministicStressTest {

    @Test
    void deterministicSplitMergeStressPreservesGraphAndFluidInvariants() {
        HorizonQAVfnScenario scenario = HorizonQAVfnScenario.create();
        ScenarioMember input = scenario.inputHatch("stress-input");
        ScenarioMember pipeA = scenario.pipe("stress-pipe-a");
        ScenarioMember heatPump = scenario.machineHatch("stress-heat-pump");
        ScenarioMember pipeB = scenario.pipe("stress-pipe-b");
        ScenarioMember radiator = scenario.machineHatch("stress-radiator");
        ScenarioMember output = scenario.outputHatch("stress-output");
        ScenarioMember injector = scenario.injectorHatch("stress-injector");
        ScenarioMember extractor = scenario.extractorHatch("stress-extractor");
        List<ScenarioMember> members = scenario.members(
            input,
            pipeA,
            heatPump,
            pipeB,
            radiator,
            output,
            injector,
            extractor);

        Set<Edge> modelEdges = new HashSet<>();
        Edge[] initialLine = new Edge[] {
            edge(input, pipeA),
            edge(pipeA, heatPump),
            edge(heatPump, pipeB),
            edge(pipeB, radiator),
            edge(radiator, output),
            edge(output, injector),
            edge(injector, extractor)
        };
        for (Edge edge : initialLine) {
            scenario.connect(edge.first, edge.second);
            assertTrue(modelEdges.add(edge), edge.label());
        }
        scenario.addNetworkFrom(input);
        SeededFluid seededFluid = scenario.seedFluid(input, 120L, 300.0d);
        assertRuntimeMatchesModel(scenario, members, modelEdges, seededFluid, "initial line");

        Operation[] operations = new Operation[] {
            disconnect(heatPump, pipeB),
            connect(pipeA, radiator),
            disconnect(pipeA, radiator),
            disconnect(radiator, output),
            connect(heatPump, pipeB),
            connect(radiator, output),
            disconnect(input, pipeA),
            connect(input, output),
            disconnect(output, injector),
            connect(pipeA, injector),
            disconnect(pipeB, radiator),
            connect(extractor, radiator),
            disconnect(pipeA, heatPump),
            connect(heatPump, output),
            disconnect(input, output),
            connect(input, pipeA),
            connect(pipeB, radiator),
            disconnect(extractor, radiator),
            connect(output, injector),
            disconnect(pipeA, injector),
            connect(pipeA, heatPump)
        };

        for (int step = 0; step < operations.length; step++) {
            Operation operation = operations[step];
            operation.apply(scenario, modelEdges);
            assertRuntimeMatchesModel(
                scenario,
                members,
                modelEdges,
                seededFluid,
                "step " + (step + 1) + " " + operation.label());
        }
    }

    private static void assertRuntimeMatchesModel(HorizonQAVfnScenario scenario, List<ScenarioMember> members,
        Set<Edge> modelEdges, SeededFluid seededFluid, String checkpoint) {

        List<Set<ScenarioMember>> expectedComponents = expectedComponents(members, modelEdges);
        Set<IntegratedFluidNetwork> runtimeNetworks = scenario.networksOf(members);
        assertEquals(expectedComponents.size(), runtimeNetworks.size(), checkpoint + " network group count");

        for (Set<ScenarioMember> component : expectedComponents) {
            ScenarioMember first = component.iterator().next();
            IntegratedFluidNetwork network = first.getNetwork();
            assertNotNull(network, checkpoint + " " + first.name);
            for (ScenarioMember member : component) {
                assertSame(network, member.getNetwork(), checkpoint + " " + member.name);
            }
            assertEquals(component.size(), network.getMemberCount(), checkpoint + " member count");
            assertEquals(component.size(), network.getExpectedMemberCount(), checkpoint + " expected member count");
        }

        long actualAmountQ = 0L;
        long actualEnthalpyQ = 0L;
        for (IntegratedFluidNetwork network : runtimeNetworks) {
            assertEquals(network.getMemberCount(), network.getExpectedMemberCount(), checkpoint + " network consistency");
            actualAmountQ += network.getAmountQ();
            actualEnthalpyQ += network.getEnthalpyQ();
        }
        assertEquals(seededFluid.amountQ, actualAmountQ, checkpoint + " fluid amount");
        assertEquals(seededFluid.enthalpyQ, actualEnthalpyQ, checkpoint + " fluid enthalpy");
    }

    private static List<Set<ScenarioMember>> expectedComponents(List<ScenarioMember> members, Set<Edge> edges) {
        Map<ScenarioMember, Set<ScenarioMember>> neighbors = new HashMap<>();
        for (ScenarioMember member : members) {
            neighbors.put(member, new HashSet<ScenarioMember>());
        }
        for (Edge edge : edges) {
            neighbors.get(edge.first).add(edge.second);
            neighbors.get(edge.second).add(edge.first);
        }

        List<Set<ScenarioMember>> components = new ArrayList<>();
        Set<ScenarioMember> visited = new HashSet<>();
        for (ScenarioMember member : members) {
            if (visited.contains(member)) {
                continue;
            }
            Set<ScenarioMember> component = new HashSet<>();
            ArrayDeque<ScenarioMember> queue = new ArrayDeque<>(Arrays.asList(member));
            visited.add(member);
            while (!queue.isEmpty()) {
                ScenarioMember current = queue.removeFirst();
                component.add(current);
                for (ScenarioMember neighbor : neighbors.get(current)) {
                    if (visited.add(neighbor)) {
                        queue.addLast(neighbor);
                    }
                }
            }
            components.add(component);
        }
        return components;
    }

    private static Edge edge(ScenarioMember first, ScenarioMember second) {
        return new Edge(first, second);
    }

    private static Operation connect(ScenarioMember first, ScenarioMember second) {
        return new Operation(true, edge(first, second));
    }

    private static Operation disconnect(ScenarioMember first, ScenarioMember second) {
        return new Operation(false, edge(first, second));
    }

    private static final class Operation {

        private final boolean connect;
        private final Edge edge;

        private Operation(boolean connect, Edge edge) {
            this.connect = connect;
            this.edge = edge;
        }

        private void apply(HorizonQAVfnScenario scenario, Set<Edge> modelEdges) {
            if (connect) {
                scenario.connect(edge.first, edge.second);
                assertTrue(modelEdges.add(edge), label());
            } else {
                scenario.disconnect(edge.first, edge.second);
                assertTrue(modelEdges.remove(edge), label());
            }
            scenario.changedAt(edge.first);
        }

        private String label() {
            return (connect ? "connect " : "disconnect ") + edge.label();
        }
    }

    private static final class Edge {

        private final ScenarioMember first;
        private final ScenarioMember second;

        private Edge(ScenarioMember first, ScenarioMember second) {
            this.first = first;
            this.second = second;
        }

        private String label() {
            return first.name + "-" + second.name;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Edge)) {
                return false;
            }
            Edge edge = (Edge) other;
            return first == edge.first && second == edge.second || first == edge.second && second == edge.first;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(first) ^ System.identityHashCode(second);
        }
    }
}
