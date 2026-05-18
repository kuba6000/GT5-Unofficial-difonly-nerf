package gregtech.api.metatileentity.implementations.integratedfluid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.minecraftforge.fluids.Fluid;

import org.junit.jupiter.api.Test;

import gregtech.api.metatileentity.implementations.integratedfluid.HorizonQAVfnScenario.ScenarioMember;
import gregtech.api.metatileentity.implementations.integratedfluid.state.IFNNetworkStatus;

class HorizonQAVfnBlockedTransferStressTest {

    private static final float PRESSURE_RATIO_LIMIT = 10.0f;

    @Test
    void frozenSplitMergeBlocksTransferAndRestoresFailedOutputMutation() {
        Fluid fluid = IFNTestSupport.pressureSensitiveLiquidFluid();
        HorizonQAVfnScenario scenario = HorizonQAVfnScenario.create(fluid);
        ScenarioMember input = scenario.inputHatch("blocked-input");
        ScenarioMember pipe = scenario.pipe("blocked-pipe");
        ScenarioMember machine = scenario.machineHatch("blocked-machine");
        ScenarioMember output = scenario.outputHatch("blocked-output");
        List<ScenarioMember> members = scenario.members(input, pipe, machine, output);
        IntegratedFluidNetwork source = IFNTestSupport.newNetwork(fluid, 4_000, 1_000, 10.0f);
        IntegratedFluidNetwork sink = IFNTestSupport.newNetwork(fluid, 4_000, 1_000, 10.0f);
        long initialSourceAmountQ = amountQ(320L);
        source.addState(
            fluid,
            initialSourceAmountQ,
            IntegratedFluidNetwork.toEnthalpyQFromSpecific(300.0d, initialSourceAmountQ));

        scenario.connectChain(input, pipe, machine, output);
        scenario.addNetworkFrom(input);
        scenario.assertSingleNetwork(members, 4);
        transfer(source, input.getNetwork(), fluid, 80L, 314.0d);
        assertGlobalAmount(scenario, members, source, sink, initialSourceAmountQ, "after warmup transfer");

        IntegratedFluidNetwork liveNetwork = input.getNetwork();
        liveNetwork.freeze("Horizon-QA blocked transfer stress");
        assertRejectedTransfer(
            source,
            liveNetwork,
            fluid,
            IFNStateTransferPlanner.Status.OUTPUT_BLOCKED,
            "source into frozen VFN");
        assertRejectedTransfer(
            liveNetwork,
            sink,
            fluid,
            IFNStateTransferPlanner.Status.INPUT_BLOCKED,
            "frozen VFN into sink");
        assertFailedOutputMutationRestoresInput(source, liveNetwork, fluid);
        assertGlobalAmount(scenario, members, source, sink, initialSourceAmountQ, "after frozen transfer attempts");

        scenario.disconnect(pipe, machine);
        scenario.changedAt(pipe);
        scenario.assertNetworkGroups(members, 2);
        for (IntegratedFluidNetwork network : scenario.networksOf(members)) {
            assertEquals(IFNNetworkStatus.FROZEN, network.getNetworkStatus());
            assertEquals("Horizon-QA blocked transfer stress", network.getFrozenReason());
            assertRejectedTransfer(
                source,
                network,
                fluid,
                IFNStateTransferPlanner.Status.OUTPUT_BLOCKED,
                "source into split frozen VFN");
            assertRejectedTransfer(
                network,
                sink,
                fluid,
                IFNStateTransferPlanner.Status.INPUT_BLOCKED,
                "split frozen VFN into sink");
        }
        assertGlobalAmount(scenario, members, source, sink, initialSourceAmountQ, "after frozen split attempts");

        scenario.connect(pipe, machine);
        scenario.changedAt(pipe);
        scenario.assertSingleNetwork(members, 4);
        assertEquals(IFNNetworkStatus.FROZEN, input.getNetwork().getNetworkStatus());
        assertEquals("Horizon-QA blocked transfer stress", input.getNetwork().getFrozenReason());
        assertRejectedTransfer(
            source,
            input.getNetwork(),
            fluid,
            IFNStateTransferPlanner.Status.OUTPUT_BLOCKED,
            "source into remerged frozen VFN");
        assertGlobalAmount(scenario, members, source, sink, initialSourceAmountQ, "after frozen merge attempts");
    }

    @Test
    void highPressureInjectorBranchStaysBlockedThroughTransferSequenceAndTopologyRebuild() {
        HorizonQAVfnScenario scenario = HorizonQAVfnScenario.create();
        ScenarioMember injector = scenario.injectorHatch("pressure-injector");
        ScenarioMember pipe = scenario.pipe("pressure-pipe");
        ScenarioMember extractor = scenario.extractorHatch("pressure-extractor");
        List<ScenarioMember> members = scenario.members(injector, pipe, extractor);

        scenario.connectChain(injector, pipe, extractor);
        scenario.addNetworkFrom(injector);
        scenario.assertSingleNetwork(members, 3);
        IntegratedFluidNetwork network = injector.getNetwork();
        network.setPressure(IFNPressurePolicy.injectorCutoffPressureBar() + 1.0f);
        assertFalse(IFNNetworkTransferGate.canInjectFromGtPipe(network));
        assertTrue(IFNNetworkTransferGate.canExtractToGtPipe(network));

        scenario.disconnect(pipe, extractor);
        scenario.changedAt(pipe);
        scenario.assertNetworkGroups(members, 2);
        IntegratedFluidNetwork injectorSide = injector.getNetwork();
        IntegratedFluidNetwork extractorSide = extractor.getNetwork();
        assertSame(injectorSide, pipe.getNetwork());
        injectorSide.setPressure(IFNPressurePolicy.injectorCutoffPressureBar() + 1.0f);
        extractorSide.setPressure(IFNPressurePolicy.injectorCutoffPressureBar() + 1.0f);
        assertFalse(IFNNetworkTransferGate.canInjectFromGtPipe(injectorSide));
        assertFalse(IFNNetworkTransferGate.canInjectFromGtPipe(extractorSide));

        scenario.connect(pipe, extractor);
        scenario.changedAt(pipe);
        scenario.assertSingleNetwork(members, 3);
        injector.getNetwork().setPressure(IFNPressurePolicy.injectorCutoffPressureBar() + 1.0f);
        assertFalse(IFNNetworkTransferGate.canInjectFromGtPipe(injector.getNetwork()));
    }

    private static void transfer(IntegratedFluidNetwork inputNetwork, IntegratedFluidNetwork outputNetwork,
        Fluid fluid, long refLiters, double outputSpecificEnthalpy) {
        IFNStateTransferPlanner.PlannedStateTransfer plan = planTransfer(
            inputNetwork,
            outputNetwork,
            fluid,
            amountQ(refLiters),
            outputSpecificEnthalpy);
        assertEquals(IFNStateTransferPlanner.Status.ACCEPTED, plan.status);
        IntegratedFluidNetwork.ExtractedPayload extracted = inputNetwork.extractProportional(plan.acceptedAmountQ, false);
        long outputEnthalpyQ = IntegratedFluidNetwork.toEnthalpyQFromSpecific(outputSpecificEnthalpy, extracted.amountQ);
        assertTrue(IFNStateMutationApplier.addOutputOrRestoreInput(
            inputNetwork,
            outputNetwork,
            fluid,
            extracted,
            outputEnthalpyQ));
    }

    private static void assertRejectedTransfer(IntegratedFluidNetwork inputNetwork, IntegratedFluidNetwork outputNetwork,
        Fluid fluid, IFNStateTransferPlanner.Status expectedStatus, String checkpoint) {
        long inputBeforeQ = inputNetwork.getAmountQ();
        long outputBeforeQ = outputNetwork.getAmountQ();
        IFNStateTransferPlanner.PlannedStateTransfer plan = planTransfer(
            inputNetwork,
            outputNetwork,
            fluid,
            amountQ(20L),
            315.0d);
        assertEquals(expectedStatus, plan.status, checkpoint);
        assertEquals(0L, plan.acceptedAmountQ, checkpoint + " amount");
        assertEquals(inputBeforeQ, inputNetwork.getAmountQ(), checkpoint + " input amount");
        assertEquals(outputBeforeQ, outputNetwork.getAmountQ(), checkpoint + " output amount");
    }

    private static void assertFailedOutputMutationRestoresInput(IntegratedFluidNetwork inputNetwork,
        IntegratedFluidNetwork frozenOutputNetwork, Fluid fluid) {
        long inputBeforeQ = inputNetwork.getAmountQ();
        long outputBeforeQ = frozenOutputNetwork.getAmountQ();
        IntegratedFluidNetwork.ExtractedPayload extracted = inputNetwork.extractProportional(amountQ(20L), false);
        assertFalse(IFNStateMutationApplier.addOutputOrRestoreInput(
            inputNetwork,
            frozenOutputNetwork,
            fluid,
            extracted,
            IntegratedFluidNetwork.toEnthalpyQFromSpecific(315.0d, extracted.amountQ)));
        assertEquals(inputBeforeQ, inputNetwork.getAmountQ(), "failed output mutation restores source amount");
        assertEquals(outputBeforeQ, frozenOutputNetwork.getAmountQ(), "failed output mutation leaves frozen output amount");
    }

    private static IFNStateTransferPlanner.PlannedStateTransfer planTransfer(
        IntegratedFluidNetwork inputNetwork, IntegratedFluidNetwork outputNetwork, Fluid fluid, long requestedAmountQ,
        double outputSpecificEnthalpy) {
        return IFNStateTransferPlanner.planSingleOutputStateAdd(
            inputNetwork,
            outputNetwork,
            fluid,
            outputSpecificEnthalpy,
            requestedAmountQ,
            PRESSURE_RATIO_LIMIT,
            0.0f);
    }

    private static void assertGlobalAmount(HorizonQAVfnScenario scenario, List<ScenarioMember> members,
        IntegratedFluidNetwork source, IntegratedFluidNetwork sink, long expectedAmountQ, String checkpoint) {
        long actualAmountQ = source.getAmountQ() + sink.getAmountQ();
        for (IntegratedFluidNetwork network : scenario.networksOf(members)) {
            actualAmountQ += network.getAmountQ();
            assertEquals(network.getMemberCount(), network.getExpectedMemberCount(), checkpoint + " member count");
        }
        assertEquals(expectedAmountQ, actualAmountQ, checkpoint + " global amount");
    }

    private static long amountQ(long refLiters) {
        return refLiters * IntegratedFluidNetwork.AMOUNT_SCALE;
    }
}
