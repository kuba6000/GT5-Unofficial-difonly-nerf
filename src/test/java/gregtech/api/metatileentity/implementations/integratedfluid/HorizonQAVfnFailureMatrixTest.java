package gregtech.api.metatileentity.implementations.integratedfluid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;

import net.minecraftforge.fluids.Fluid;

import org.junit.jupiter.api.Test;

import gregtech.api.metatileentity.implementations.integratedfluid.HorizonQAVfnScenario.ScenarioMember;
import gregtech.api.metatileentity.implementations.integratedfluid.HorizonQAVfnScenario.SeededFluid;
import gregtech.api.metatileentity.implementations.integratedfluid.state.IFNNetworkStatus;

class HorizonQAVfnFailureMatrixTest {

    @Test
    void incompatibleFluidMergeFreezesBothSidesWithoutMovingMembers() {
        HorizonQAVfnScenario scenario = HorizonQAVfnScenario.create();
        ScenarioMember liquidPipe = scenario.pipe("liquid-pipe");
        ScenarioMember liquidHatch = scenario.inputHatch("liquid-hatch");
        ScenarioMember vaporPipe = scenario.pipe("vapor-pipe");
        ScenarioMember vaporHatch = scenario.outputHatch("vapor-hatch");
        List<ScenarioMember> members = scenario.members(liquidPipe, liquidHatch, vaporPipe, vaporHatch);
        Fluid vapor = IFNTestSupport.vaporFluid();

        scenario.connect(liquidPipe, liquidHatch);
        scenario.connect(vaporPipe, vaporHatch);
        scenario.addNetworkFrom(liquidPipe);
        scenario.addNetworkFrom(vaporPipe);
        SeededFluid liquidState = scenario.seedFluid(liquidPipe, 4L, 300.0d);
        IntegratedFluidNetwork liquidNetwork = liquidPipe.getNetwork();
        IntegratedFluidNetwork vaporNetwork = vaporPipe.getNetwork();
        long vaporAmountQ = 4L * IntegratedFluidNetwork.AMOUNT_SCALE;
        vaporNetwork.loadState(
            vapor.getName(),
            vaporAmountQ,
            IntegratedFluidNetwork.toEnthalpyQFromSpecific(350.0d, vaporAmountQ),
            IntegratedFluidNetwork.DEFAULT_PRESSURE,
            2);

        scenario.connect(liquidHatch, vaporPipe);
        scenario.changedAt(liquidHatch);

        assertSame(liquidNetwork, liquidPipe.getNetwork());
        assertSame(vaporNetwork, vaporPipe.getNetwork());
        scenario.assertNetworkGroups(members, 2);
        assertEquals(IFNNetworkStatus.FROZEN, liquidNetwork.getNetworkStatus());
        assertEquals(IFNNetworkStatus.FROZEN, vaporNetwork.getNetworkStatus());
        assertEquals("fluid conflict", liquidNetwork.getFrozenReason());
        assertEquals("fluid conflict", vaporNetwork.getFrozenReason());
        assertEquals(liquidState.amountQ, liquidNetwork.getAmountQ());
        assertEquals(vaporAmountQ, vaporNetwork.getAmountQ());
        assertAllTransferDirectionsBlocked(liquidNetwork);
        assertAllTransferDirectionsBlocked(vaporNetwork);
    }

    @Test
    void frozenNetworkStaysFrozenAcrossSplitAndMerge() {
        HorizonQAVfnScenario scenario = HorizonQAVfnScenario.create();
        ScenarioMember pipeA = scenario.pipe("pipe-a");
        ScenarioMember pipeB = scenario.pipe("pipe-b");
        ScenarioMember pipeC = scenario.pipe("pipe-c");
        List<ScenarioMember> members = scenario.members(pipeA, pipeB, pipeC);

        scenario.connectChain(pipeA, pipeB, pipeC);
        scenario.addNetworkFrom(pipeA);
        SeededFluid fluid = scenario.seedFluid(pipeA, 9L, 300.0d);
        pipeA.getNetwork().freeze("Horizon-QA forced freeze");

        scenario.disconnect(pipeB, pipeC);
        scenario.changedAt(pipeB);

        scenario.assertNetworkGroups(members, 2, fluid);
        for (IntegratedFluidNetwork network : scenario.networksOf(members)) {
            assertEquals(IFNNetworkStatus.FROZEN, network.getNetworkStatus());
            assertEquals("Horizon-QA forced freeze", network.getFrozenReason());
            assertAllTransferDirectionsBlocked(network);
        }

        scenario.connect(pipeB, pipeC);
        scenario.changedAt(pipeB);

        scenario.assertSingleNetwork(members, 3);
        scenario.assertTotals(members, fluid);
        assertEquals(IFNNetworkStatus.FROZEN, pipeA.getNetwork().getNetworkStatus());
        assertEquals("Horizon-QA forced freeze", pipeA.getNetwork().getFrozenReason());
        assertAllTransferDirectionsBlocked(pipeA.getNetwork());
    }

    @Test
    void zeroCapacityNetworkRejectsMachineOutputTransfer() {
        Fluid fluid = IFNTestSupport.liquidFluid();
        HorizonQAVfnScenario scenario = HorizonQAVfnScenario.create(fluid);
        ScenarioMember injector = scenario.injectorHatch("zero-injector");
        ScenarioMember machine = scenario.machineHatch("zero-machine");
        ScenarioMember extractor = scenario.extractorHatch("zero-extractor");
        List<ScenarioMember> members = scenario.members(injector, machine, extractor);
        IntegratedFluidNetwork sourceNetwork = IFNTestSupport.newNetwork(fluid, 1_000, 100, 10.0f);
        long sourceAmountQ = 10L * IntegratedFluidNetwork.AMOUNT_SCALE;
        sourceNetwork.addState(
            fluid,
            sourceAmountQ,
            IntegratedFluidNetwork.toEnthalpyQFromSpecific(300.0d, sourceAmountQ));

        scenario.connectChain(injector, machine, extractor);
        scenario.addNetworkFrom(injector);
        scenario.assertSingleNetwork(members, 3);
        scenario.assertSingleNetworkCapacity(members, 0, 0);

        IFNStateTransferPlanner.PlannedStateTransfer plan = IFNStateTransferPlanner.planSingleOutputStateAdd(
            sourceNetwork,
            injector.getNetwork(),
            fluid,
            300.0d,
            IntegratedFluidNetwork.AMOUNT_SCALE,
            10.0f);

        assertEquals(IFNStateTransferPlanner.Status.OUTPUT_BLOCKED, plan.status);
        assertEquals(0L, plan.acceptedAmountQ);
        assertEquals(sourceAmountQ, sourceNetwork.getAmountQ());
        assertEquals(0L, injector.getNetwork().getAmountQ());
    }

    @Test
    void highPressureInjectorBranchStaysBlockedAfterSplitAndMerge() {
        Fluid vapor = IFNTestSupport.vaporFluid();
        HorizonQAVfnScenario scenario = HorizonQAVfnScenario.create(vapor);
        ScenarioMember pipe = scenario.pipe("pressure-pipe");
        ScenarioMember injector = scenario.injectorHatch("pressure-injector");
        ScenarioMember extractor = scenario.extractorHatch("pressure-extractor");
        List<ScenarioMember> members = scenario.members(pipe, injector, extractor);

        scenario.connectChain(pipe, injector, extractor);
        scenario.addNetworkFrom(pipe);
        pipe.getNetwork().setPressure(IFNPressurePolicy.injectorCutoffPressureBar() + 0.01f);

        assertFalse(IFNNetworkTransferGate.canInjectFromGtPipe(pipe.getNetwork()));

        scenario.disconnect(injector, extractor);
        scenario.changedAt(injector);
        injector.getNetwork().setPressure(IFNPressurePolicy.injectorCutoffPressureBar() + 0.01f);

        scenario.assertNetworkGroups(members, 2);
        assertFalse(IFNNetworkTransferGate.canInjectFromGtPipe(injector.getNetwork()));

        scenario.connect(injector, extractor);
        scenario.changedAt(injector);
        scenario.assertSingleNetwork(members, 3);
        pipe.getNetwork().setPressure(IFNPressurePolicy.injectorCutoffPressureBar() + 0.01f);

        assertFalse(IFNNetworkTransferGate.canInjectFromGtPipe(pipe.getNetwork()));
    }

    private static void assertAllTransferDirectionsBlocked(IntegratedFluidNetwork network) {
        assertFalse(IFNNetworkTransferGate.canReceiveFromMachine(network));
        assertFalse(IFNNetworkTransferGate.canProvideToMachine(network));
        assertFalse(IFNNetworkTransferGate.canInjectFromGtPipe(network));
        assertFalse(IFNNetworkTransferGate.canExtractToGtPipe(network));
    }
}
