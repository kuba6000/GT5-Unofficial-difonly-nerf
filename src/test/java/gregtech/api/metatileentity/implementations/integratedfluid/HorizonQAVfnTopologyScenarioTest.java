package gregtech.api.metatileentity.implementations.integratedfluid;

import java.util.List;

import org.junit.jupiter.api.Test;

import gregtech.api.metatileentity.implementations.integratedfluid.HorizonQAVfnScenario.ScenarioMember;
import gregtech.api.metatileentity.implementations.integratedfluid.HorizonQAVfnScenario.SeededFluid;

class HorizonQAVfnTopologyScenarioTest {

    @Test
    void repeatedPipeSplitsAndMergesKeepOneCopyOfStoredFluid() {
        HorizonQAVfnScenario scenario = HorizonQAVfnScenario.create();
        ScenarioMember pipeA = scenario.pipe("pipe-a");
        ScenarioMember pipeB = scenario.pipe("pipe-b");
        ScenarioMember pipeC = scenario.pipe("pipe-c");
        ScenarioMember pipeD = scenario.pipe("pipe-d");
        List<ScenarioMember> members = scenario.members(pipeA, pipeB, pipeC, pipeD);

        scenario.connectChain(pipeA, pipeB, pipeC, pipeD);
        scenario.addNetworkFrom(pipeA);
        scenario.assertSingleNetwork(members, 4);
        SeededFluid fluid = scenario.seedFluid(pipeA, 12L, 300.0d);
        scenario.assertTotals(members, fluid);

        scenario.disconnect(pipeB, pipeC);
        scenario.changedAt(pipeB);
        scenario.assertNetworkGroups(members, 2, fluid);

        scenario.connect(pipeB, pipeC);
        scenario.changedAt(pipeB);
        scenario.assertSingleNetwork(members, 4);
        scenario.assertTotals(members, fluid);

        scenario.disconnect(pipeA, pipeB);
        scenario.changedAt(pipeA);
        scenario.assertNetworkGroups(members, 2, fluid);

        scenario.connect(pipeA, pipeB);
        scenario.changedAt(pipeA);
        scenario.assertSingleNetwork(members, 4);
        scenario.assertTotals(members, fluid);
    }
}
