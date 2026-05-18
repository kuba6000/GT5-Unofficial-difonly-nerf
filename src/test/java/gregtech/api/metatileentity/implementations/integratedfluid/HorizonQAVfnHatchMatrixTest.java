package gregtech.api.metatileentity.implementations.integratedfluid;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import gregtech.api.metatileentity.implementations.integratedfluid.HorizonQAVfnScenario.ScenarioMember;
import gregtech.api.metatileentity.implementations.integratedfluid.HorizonQAVfnScenario.SeededFluid;

class HorizonQAVfnHatchMatrixTest {

    @Test
    void machineFacingHatchesContributeExpectedCapacityAndStayOperational() {
        HorizonQAVfnScenario scenario = HorizonQAVfnScenario.create();
        ScenarioMember pipe = scenario.pipe("pipe");
        ScenarioMember inputHatch = scenario.inputHatch("input-hatch");
        ScenarioMember outputHatch = scenario.outputHatch("output-hatch");
        ScenarioMember injector = scenario.injectorHatch("injector-hatch");
        ScenarioMember extractor = scenario.extractorHatch("extractor-hatch");
        ScenarioMember machine = scenario.machineHatch("machine-hatch");
        List<ScenarioMember> members = scenario.members(pipe, inputHatch, outputHatch, injector, extractor, machine);

        scenario.connect(pipe, inputHatch);
        scenario.connect(pipe, outputHatch);
        scenario.connect(inputHatch, injector);
        scenario.connect(outputHatch, extractor);
        scenario.connect(inputHatch, machine);
        scenario.connect(outputHatch, machine);
        scenario.addNetworkFrom(pipe);

        scenario.assertSingleNetwork(members, 6);
        scenario.assertSingleNetworkCapacity(members, 21_000, 20_000);
        assertTrue(IFNNetworkTransferGate.canReceiveFromMachine(pipe.getNetwork()));
        assertTrue(IFNNetworkTransferGate.canProvideToMachine(pipe.getNetwork()));
        assertTrue(IFNNetworkTransferGate.canInjectFromGtPipe(pipe.getNetwork()));
        assertTrue(IFNNetworkTransferGate.canExtractToGtPipe(pipe.getNetwork()));
    }

    @Test
    void hatchAndMachineSplitMergeDoesNotDuplicateFluid() {
        HorizonQAVfnScenario scenario = HorizonQAVfnScenario.create();
        ScenarioMember inputPipe = scenario.pipe("input-pipe");
        ScenarioMember inputHatch = scenario.inputHatch("input-hatch");
        ScenarioMember machine = scenario.machineHatch("machine-hatch");
        ScenarioMember outputHatch = scenario.outputHatch("output-hatch");
        ScenarioMember outputPipe = scenario.pipe("output-pipe");
        List<ScenarioMember> members = scenario.members(inputPipe, inputHatch, machine, outputHatch, outputPipe);

        scenario.connectChain(inputPipe, inputHatch, machine, outputHatch, outputPipe);
        scenario.addNetworkFrom(inputPipe);
        scenario.assertSingleNetwork(members, 5);
        SeededFluid fluid = scenario.seedFluid(inputPipe, 18L, 300.0d);

        scenario.disconnect(machine, outputHatch);
        scenario.changedAt(machine);
        scenario.assertNetworkGroups(members, 2, fluid);

        scenario.connect(machine, outputHatch);
        scenario.changedAt(machine);
        scenario.assertSingleNetwork(members, 5);
        scenario.assertTotals(members, fluid);
    }

    @Test
    void pendingAndFrozenHatchNetworkBlocksEveryTransferDirection() {
        HorizonQAVfnScenario scenario = HorizonQAVfnScenario.create();
        ScenarioMember pipe = scenario.pipe("pipe");
        ScenarioMember inputHatch = scenario.inputHatch("input-hatch");
        ScenarioMember outputHatch = scenario.outputHatch("output-hatch");
        List<ScenarioMember> members = scenario.members(pipe, inputHatch, outputHatch);

        scenario.connectChain(pipe, inputHatch, outputHatch);
        scenario.addNetworkFrom(pipe);
        scenario.assertSingleNetwork(members, 3);

        IntegratedFluidNetwork network = pipe.getNetwork();
        assertTrue(IFNNetworkTransferGate.canReceiveFromMachine(network));
        assertTrue(IFNNetworkTransferGate.canProvideToMachine(network));
        assertTrue(IFNNetworkTransferGate.canInjectFromGtPipe(network));
        assertTrue(IFNNetworkTransferGate.canExtractToGtPipe(network));

        network.setPending(true);

        assertAllTransferDirectionsBlocked(network);

        network.setPending(false);
        network.freeze("Horizon-QA hatch freeze");

        assertAllTransferDirectionsBlocked(network);
    }

    @Test
    void injectorHatchBlocksGtPipeInputAtCutoffPressureOnly() {
        HorizonQAVfnScenario scenario = HorizonQAVfnScenario.create();
        ScenarioMember pipe = scenario.pipe("pipe");
        ScenarioMember injector = scenario.injectorHatch("injector-hatch");
        ScenarioMember extractor = scenario.extractorHatch("extractor-hatch");
        List<ScenarioMember> members = scenario.members(pipe, injector, extractor);

        scenario.connect(pipe, injector);
        scenario.connect(pipe, extractor);
        scenario.addNetworkFrom(pipe);
        scenario.assertSingleNetwork(members, 3);

        IntegratedFluidNetwork network = pipe.getNetwork();
        assertTrue(IFNNetworkTransferGate.canInjectFromGtPipe(network));
        assertTrue(IFNNetworkTransferGate.canExtractToGtPipe(network));

        network.setPressure(IFNPressurePolicy.injectorCutoffPressureBar() + 0.01f);

        assertFalse(IFNNetworkTransferGate.canInjectFromGtPipe(network));
        assertTrue(IFNNetworkTransferGate.canExtractToGtPipe(network));
        assertTrue(IFNNetworkTransferGate.canReceiveFromMachine(network));
        assertTrue(IFNNetworkTransferGate.canProvideToMachine(network));
    }

    private static void assertAllTransferDirectionsBlocked(IntegratedFluidNetwork network) {
        assertFalse(IFNNetworkTransferGate.canReceiveFromMachine(network));
        assertFalse(IFNNetworkTransferGate.canProvideToMachine(network));
        assertFalse(IFNNetworkTransferGate.canInjectFromGtPipe(network));
        assertFalse(IFNNetworkTransferGate.canExtractToGtPipe(network));
    }
}
