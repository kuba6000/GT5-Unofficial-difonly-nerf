package gregtech.api.metatileentity.implementations.integratedfluid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.minecraftforge.fluids.Fluid;

import org.junit.jupiter.api.Test;

import gregtech.api.metatileentity.implementations.integratedfluid.HorizonQAVfnScenario.ScenarioMember;
import gregtech.api.metatileentity.implementations.integratedfluid.state.IFNNetworkStatus;

class HorizonQAVfnPersistenceStressTest {

    private static final float PRESSURE_RATIO_LIMIT = 10.0f;

    @Test
    void splitTransferReloadAndContinuePreservesNetworkState() {
        Fluid fluid = IFNTestSupport.pressureSensitiveLiquidFluid();
        HorizonQAVfnScenario scenario = HorizonQAVfnScenario.create(fluid);
        ScenarioMember input = scenario.inputHatch("persist-input");
        ScenarioMember pipeA = scenario.pipe("persist-pipe-a");
        ScenarioMember heatPump = scenario.machineHatch("persist-heat-pump");
        ScenarioMember pipeB = scenario.pipe("persist-pipe-b");
        ScenarioMember radiator = scenario.machineHatch("persist-radiator");
        ScenarioMember output = scenario.outputHatch("persist-output");
        List<ScenarioMember> members = scenario.members(input, pipeA, heatPump, pipeB, radiator, output);
        IntegratedFluidNetwork source = IFNTestSupport.newNetwork(fluid, 4_000, 1_000, 10.0f);
        IntegratedFluidNetwork sink = IFNTestSupport.newNetwork(fluid, 4_000, 1_000, 10.0f);
        long initialSourceAmountQ = amountQ(500L);
        source.addState(
            fluid,
            initialSourceAmountQ,
            IntegratedFluidNetwork.toEnthalpyQFromSpecific(300.0d, initialSourceAmountQ));

        scenario.connectChain(input, pipeA, heatPump, pipeB, radiator, output);
        scenario.addNetworkFrom(input);
        transfer(source, input.getNetwork(), fluid, 100L, 318.0d);
        assertGlobalAmount(scenario, members, source, sink, initialSourceAmountQ, "after initial transfer");

        scenario.disconnect(heatPump, pipeB);
        scenario.changedAt(heatPump);
        scenario.assertNetworkGroups(members, 2);
        IntegratedFluidNetwork inputSide = input.getNetwork();
        IntegratedFluidNetwork radiatorSide = radiator.getNetwork();
        transfer(source, inputSide, fluid, 35L, 322.0d);
        transfer(radiatorSide, sink, fluid, 20L, radiatorSide.getSpecificEnthalpy());
        long splitVfnAmountQ = totalVfnAmountQ(scenario, members);
        long splitVfnEnthalpyQ = totalVfnEnthalpyQ(scenario, members);
        assertGlobalAmount(scenario, members, source, sink, initialSourceAmountQ, "before split reload");

        scenario.roundTripSavedDataAndReloadFrom(input, members);
        scenario.addNetworkFrom(radiator);

        scenario.assertNetworkGroups(members, 2);
        assertEquals(splitVfnAmountQ, totalVfnAmountQ(scenario, members), "split reload VFN amount");
        assertEquals(splitVfnEnthalpyQ, totalVfnEnthalpyQ(scenario, members), "split reload VFN enthalpy");
        assertGlobalAmount(scenario, members, source, sink, initialSourceAmountQ, "after split reload");

        transfer(source, input.getNetwork(), fluid, 15L, 324.0d);
        transfer(radiator.getNetwork(), sink, fluid, 10L, radiator.getNetwork().getSpecificEnthalpy());
        assertGlobalAmount(scenario, members, source, sink, initialSourceAmountQ, "after post-reload split transfers");

        scenario.connect(heatPump, pipeB);
        scenario.changedAt(heatPump);
        scenario.assertSingleNetwork(members, 6);
        long mergedVfnAmountQ = input.getNetwork().getAmountQ();
        long mergedVfnEnthalpyQ = input.getNetwork().getEnthalpyQ();

        scenario.roundTripSavedDataAndReloadFrom(input, members);

        scenario.assertSingleNetwork(members, 6);
        assertEquals(mergedVfnAmountQ, input.getNetwork().getAmountQ(), "merged reload VFN amount");
        assertEquals(mergedVfnEnthalpyQ, input.getNetwork().getEnthalpyQ(), "merged reload VFN enthalpy");
        transfer(source, input.getNetwork(), fluid, 20L, 326.0d);
        transfer(input.getNetwork(), sink, fluid, 25L, input.getNetwork().getSpecificEnthalpy());
        assertGlobalAmount(scenario, members, source, sink, initialSourceAmountQ, "after merged reload transfers");
    }

    @Test
    void frozenSplitReloadKeepsReasonAndRejectsTransfers() {
        Fluid fluid = IFNTestSupport.pressureSensitiveLiquidFluid();
        HorizonQAVfnScenario scenario = HorizonQAVfnScenario.create(fluid);
        ScenarioMember pipeA = scenario.pipe("persist-freeze-a");
        ScenarioMember pipeB = scenario.pipe("persist-freeze-b");
        ScenarioMember pipeC = scenario.pipe("persist-freeze-c");
        ScenarioMember pipeD = scenario.pipe("persist-freeze-d");
        List<ScenarioMember> members = scenario.members(pipeA, pipeB, pipeC, pipeD);
        IntegratedFluidNetwork source = IFNTestSupport.newNetwork(fluid, 4_000, 1_000, 10.0f);
        long initialSourceAmountQ = amountQ(160L);
        source.addState(
            fluid,
            initialSourceAmountQ,
            IntegratedFluidNetwork.toEnthalpyQFromSpecific(300.0d, initialSourceAmountQ));

        scenario.connectChain(pipeA, pipeB, pipeC, pipeD);
        scenario.addNetworkFrom(pipeA);
        transfer(source, pipeA.getNetwork(), fluid, 70L, 316.0d);
        pipeA.getNetwork().freeze("Horizon-QA persistence freeze");
        long frozenVfnAmountQ = pipeA.getNetwork().getAmountQ();
        long frozenVfnEnthalpyQ = pipeA.getNetwork().getEnthalpyQ();

        scenario.roundTripSavedDataAndReloadFrom(pipeA, members);

        scenario.assertSingleNetwork(members, 4);
        assertEquals(IFNNetworkStatus.FROZEN, pipeA.getNetwork().getNetworkStatus());
        assertEquals("Horizon-QA persistence freeze", pipeA.getNetwork().getFrozenReason());
        assertEquals(frozenVfnAmountQ, pipeA.getNetwork().getAmountQ(), "frozen reload amount");
        assertEquals(frozenVfnEnthalpyQ, pipeA.getNetwork().getEnthalpyQ(), "frozen reload enthalpy");
        assertRejectedTransfer(source, pipeA.getNetwork(), fluid, IFNStateTransferPlanner.Status.OUTPUT_BLOCKED);

        scenario.disconnect(pipeB, pipeC);
        scenario.changedAt(pipeB);
        scenario.assertNetworkGroups(members, 2);
        long splitFrozenAmountQ = totalVfnAmountQ(scenario, members);
        long splitFrozenEnthalpyQ = totalVfnEnthalpyQ(scenario, members);

        scenario.roundTripSavedDataAndReloadFrom(pipeA, members);
        scenario.addNetworkFrom(pipeC);

        scenario.assertNetworkGroups(members, 2);
        assertEquals(splitFrozenAmountQ, totalVfnAmountQ(scenario, members), "split frozen reload amount");
        assertEquals(splitFrozenEnthalpyQ, totalVfnEnthalpyQ(scenario, members), "split frozen reload enthalpy");
        for (IntegratedFluidNetwork network : scenario.networksOf(members)) {
            assertEquals(IFNNetworkStatus.FROZEN, network.getNetworkStatus());
            assertEquals("Horizon-QA persistence freeze", network.getFrozenReason());
            assertRejectedTransfer(source, network, fluid, IFNStateTransferPlanner.Status.OUTPUT_BLOCKED);
        }
    }

    private static void transfer(IntegratedFluidNetwork inputNetwork, IntegratedFluidNetwork outputNetwork,
        Fluid fluid, long refLiters, double outputSpecificEnthalpy) {
        long requestedAmountQ = amountQ(refLiters);
        IFNStateTransferPlanner.PlannedStateTransfer plan = IFNStateTransferPlanner.planSingleOutputStateAdd(
            inputNetwork,
            outputNetwork,
            fluid,
            outputSpecificEnthalpy,
            requestedAmountQ,
            PRESSURE_RATIO_LIMIT,
            0.0f);
        assertEquals(IFNStateTransferPlanner.Status.ACCEPTED, plan.status);
        assertTrue(plan.acceptedAmountQ >= IntegratedFluidNetwork.AMOUNT_SCALE);
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
        Fluid fluid, IFNStateTransferPlanner.Status expectedStatus) {
        long inputBeforeQ = inputNetwork.getAmountQ();
        long outputBeforeQ = outputNetwork.getAmountQ();
        IFNStateTransferPlanner.PlannedStateTransfer plan = IFNStateTransferPlanner.planSingleOutputStateAdd(
            inputNetwork,
            outputNetwork,
            fluid,
            315.0d,
            amountQ(20L),
            PRESSURE_RATIO_LIMIT,
            0.0f);
        assertEquals(expectedStatus, plan.status);
        assertEquals(0L, plan.acceptedAmountQ);
        assertEquals(inputBeforeQ, inputNetwork.getAmountQ());
        assertEquals(outputBeforeQ, outputNetwork.getAmountQ());
    }

    private static void assertGlobalAmount(HorizonQAVfnScenario scenario, List<ScenarioMember> members,
        IntegratedFluidNetwork source, IntegratedFluidNetwork sink, long expectedAmountQ, String checkpoint) {
        long actualAmountQ = source.getAmountQ() + sink.getAmountQ() + totalVfnAmountQ(scenario, members);
        assertEquals(expectedAmountQ, actualAmountQ, checkpoint + " global amount");
        for (IntegratedFluidNetwork network : scenario.networksOf(members)) {
            assertEquals(network.getMemberCount(), network.getExpectedMemberCount(), checkpoint + " member count");
        }
    }

    private static long totalVfnAmountQ(HorizonQAVfnScenario scenario, List<ScenarioMember> members) {
        long amountQ = 0L;
        for (IntegratedFluidNetwork network : scenario.networksOf(members)) {
            amountQ += network.getAmountQ();
        }
        return amountQ;
    }

    private static long totalVfnEnthalpyQ(HorizonQAVfnScenario scenario, List<ScenarioMember> members) {
        long enthalpyQ = 0L;
        for (IntegratedFluidNetwork network : scenario.networksOf(members)) {
            enthalpyQ += network.getEnthalpyQ();
        }
        return enthalpyQ;
    }

    private static long amountQ(long refLiters) {
        return refLiters * IntegratedFluidNetwork.AMOUNT_SCALE;
    }
}
