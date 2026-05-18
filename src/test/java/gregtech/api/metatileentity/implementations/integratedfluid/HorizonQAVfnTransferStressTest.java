package gregtech.api.metatileentity.implementations.integratedfluid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.minecraftforge.fluids.Fluid;

import org.junit.jupiter.api.Test;

import gregtech.api.metatileentity.implementations.integratedfluid.HorizonQAVfnScenario.ScenarioMember;

class HorizonQAVfnTransferStressTest {

    private static final float PRESSURE_RATIO_LIMIT = 10.0f;

    @Test
    void splitMergeTransfersPreserveGlobalFluidAmountAndStayInActiveSegment() {
        Fluid fluid = IFNTestSupport.pressureSensitiveLiquidFluid();
        HorizonQAVfnScenario scenario = HorizonQAVfnScenario.create(fluid);
        ScenarioMember input = scenario.inputHatch("transfer-input");
        ScenarioMember pipeA = scenario.pipe("transfer-pipe-a");
        ScenarioMember heatPump = scenario.machineHatch("transfer-heat-pump");
        ScenarioMember pipeB = scenario.pipe("transfer-pipe-b");
        ScenarioMember radiator = scenario.machineHatch("transfer-radiator");
        ScenarioMember output = scenario.outputHatch("transfer-output");
        List<ScenarioMember> members = scenario.members(input, pipeA, heatPump, pipeB, radiator, output);
        IntegratedFluidNetwork source = IFNTestSupport.newNetwork(fluid, 4_000, 1_000, 10.0f);
        IntegratedFluidNetwork sink = IFNTestSupport.newNetwork(fluid, 4_000, 1_000, 10.0f);
        long initialSourceAmountQ = amountQ(420L);
        source.addState(
            fluid,
            initialSourceAmountQ,
            IntegratedFluidNetwork.toEnthalpyQFromSpecific(300.0d, initialSourceAmountQ));

        scenario.connectChain(input, pipeA, heatPump, pipeB, radiator, output);
        scenario.addNetworkFrom(input);
        scenario.assertSingleNetwork(members, 6);
        assertGlobalAmount(scenario, members, source, sink, initialSourceAmountQ, "initial");

        transfer(source, input.getNetwork(), fluid, 80L, 314.0d);
        assertGlobalAmount(scenario, members, source, sink, initialSourceAmountQ, "after first fill");

        scenario.disconnect(heatPump, pipeB);
        scenario.changedAt(heatPump);
        scenario.assertNetworkGroups(members, 2);
        IntegratedFluidNetwork inputSide = input.getNetwork();
        IntegratedFluidNetwork radiatorSide = radiator.getNetwork();
        assertSame(inputSide, heatPump.getNetwork());
        assertSame(radiatorSide, output.getNetwork());
        assertGlobalAmount(scenario, members, source, sink, initialSourceAmountQ, "after split");

        long radiatorSideAfterSplitQ = radiatorSide.getAmountQ();
        transfer(source, inputSide, fluid, 45L, 320.0d);
        assertEquals(
            radiatorSideAfterSplitQ,
            radiatorSide.getAmountQ(),
            "split radiator side must not receive input-side transfer");
        assertGlobalAmount(scenario, members, source, sink, initialSourceAmountQ, "after split fill");

        transfer(inputSide, sink, fluid, 30L, inputSide.getSpecificEnthalpy());
        assertEquals(
            radiatorSideAfterSplitQ,
            radiatorSide.getAmountQ(),
            "split radiator side must stay isolated while draining input side");
        assertGlobalAmount(scenario, members, source, sink, initialSourceAmountQ, "after split drain");

        scenario.connect(heatPump, pipeB);
        scenario.changedAt(heatPump);
        scenario.assertSingleNetwork(members, 6);
        IntegratedFluidNetwork merged = input.getNetwork();
        assertGlobalAmount(scenario, members, source, sink, initialSourceAmountQ, "after merge");

        transfer(source, merged, fluid, 35L, 326.0d);
        assertGlobalAmount(scenario, members, source, sink, initialSourceAmountQ, "after merged fill");

        scenario.disconnect(pipeA, heatPump);
        scenario.changedAt(pipeA);
        scenario.assertNetworkGroups(members, 2);
        IntegratedFluidNetwork upstream = input.getNetwork();
        IntegratedFluidNetwork machineSide = heatPump.getNetwork();
        long machineSideBeforeQ = machineSide.getAmountQ();

        transfer(source, upstream, fluid, 25L, 318.0d);
        assertEquals(machineSideBeforeQ, machineSide.getAmountQ(), "machine side must not receive upstream transfer");
        assertGlobalAmount(scenario, members, source, sink, initialSourceAmountQ, "after second split fill");

        transfer(machineSide, sink, fluid, 20L, machineSide.getSpecificEnthalpy());
        assertGlobalAmount(scenario, members, source, sink, initialSourceAmountQ, "after machine-side drain");

        scenario.connect(pipeA, heatPump);
        scenario.changedAt(pipeA);
        scenario.assertSingleNetwork(members, 6);
        assertGlobalAmount(scenario, members, source, sink, initialSourceAmountQ, "final merge");
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
