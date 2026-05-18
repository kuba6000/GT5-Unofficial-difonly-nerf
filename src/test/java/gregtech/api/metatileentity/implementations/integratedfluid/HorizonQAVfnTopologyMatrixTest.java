package gregtech.api.metatileentity.implementations.integratedfluid;

import java.util.List;

import org.junit.jupiter.api.Test;

import gregtech.api.metatileentity.implementations.integratedfluid.HorizonQAVfnScenario.ScenarioMember;
import gregtech.api.metatileentity.implementations.integratedfluid.HorizonQAVfnScenario.SeededFluid;

class HorizonQAVfnTopologyMatrixTest {

    @Test
    void lineTopologyFormsOneNetwork() {
        HorizonQAVfnScenario scenario = HorizonQAVfnScenario.create();
        ScenarioMember pipeA = scenario.pipe("line-a");
        ScenarioMember pipeB = scenario.pipe("line-b");
        ScenarioMember pipeC = scenario.pipe("line-c");
        ScenarioMember pipeD = scenario.pipe("line-d");
        List<ScenarioMember> members = scenario.members(pipeA, pipeB, pipeC, pipeD);

        scenario.connectChain(pipeA, pipeB, pipeC, pipeD);
        scenario.addNetworkFrom(pipeA);

        scenario.assertSingleNetwork(members, 4);
    }

    @Test
    void branchAndCrossTopologiesFormOneNetwork() {
        HorizonQAVfnScenario scenario = HorizonQAVfnScenario.create();
        ScenarioMember center = scenario.pipe("cross-center");
        ScenarioMember north = scenario.pipe("cross-north");
        ScenarioMember south = scenario.pipe("cross-south");
        ScenarioMember east = scenario.pipe("cross-east");
        ScenarioMember west = scenario.pipe("cross-west");
        List<ScenarioMember> tJunction = scenario.members(center, north, south, east);
        List<ScenarioMember> cross = scenario.members(center, north, south, east, west);

        scenario.connect(center, north);
        scenario.connect(center, south);
        scenario.connect(center, east);
        scenario.addNetworkFrom(center);
        scenario.assertSingleNetwork(tJunction, 4);

        scenario.connect(center, west);
        scenario.changedAt(center);
        scenario.assertSingleNetwork(cross, 5);
    }

    @Test
    void isolatedPipeGroupsStaySeparate() {
        HorizonQAVfnScenario scenario = HorizonQAVfnScenario.create();
        ScenarioMember leftA = scenario.pipe("left-a");
        ScenarioMember leftB = scenario.pipe("left-b");
        ScenarioMember rightA = scenario.pipe("right-a");
        ScenarioMember rightB = scenario.pipe("right-b");
        List<ScenarioMember> allMembers = scenario.members(leftA, leftB, rightA, rightB);

        scenario.connect(leftA, leftB);
        scenario.connect(rightA, rightB);
        scenario.addNetworkFrom(leftA);
        scenario.addNetworkFrom(rightA);

        scenario.assertNetworkGroups(allMembers, 2);
    }

    @Test
    void mixedCapacityPipeSplitAndMergePreservesStoredFluid() {
        HorizonQAVfnScenario scenario = HorizonQAVfnScenario.create();
        ScenarioMember narrow = scenario.pipe("narrow-pipe", 1000);
        ScenarioMember medium = scenario.pipe("medium-pipe", 2000);
        ScenarioMember wide = scenario.pipe("wide-pipe", 3000);
        List<ScenarioMember> members = scenario.members(narrow, medium, wide);

        scenario.connectChain(narrow, medium, wide);
        scenario.addNetworkFrom(narrow);
        scenario.assertSingleNetwork(members, 3);
        SeededFluid fluid = scenario.seedFluid(narrow, 6L, 300.0d);

        scenario.disconnect(medium, wide);
        scenario.changedAt(medium);
        scenario.assertNetworkGroups(members, 2, fluid);

        scenario.connect(medium, wide);
        scenario.changedAt(medium);
        scenario.assertSingleNetwork(members, 3);
        scenario.assertTotals(members, fluid);
    }
}
