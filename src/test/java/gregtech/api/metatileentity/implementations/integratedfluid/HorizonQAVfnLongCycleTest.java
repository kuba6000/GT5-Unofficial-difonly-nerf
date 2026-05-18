package gregtech.api.metatileentity.implementations.integratedfluid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;

import net.minecraftforge.fluids.Fluid;

import org.junit.jupiter.api.Test;

import gregtech.api.metatileentity.implementations.integratedfluid.HorizonQAVfnScenario.ScenarioMember;
import gregtech.common.tileentities.machines.multi.radiator.RadiatorLoopSnapshot;
import gregtech.common.tileentities.machines.multi.radiator.RadiatorThermo;

class HorizonQAVfnLongCycleTest {

    @Test
    void repeatedHeatRadiatorSplitMergeAndReloadPreservesNetworkState() {
        Fluid fluid = IFNTestSupport.pressureSensitiveLiquidFluid();
        HorizonQAVfnScenario scenario = HorizonQAVfnScenario.create(fluid);
        ScenarioMember inputPipe = scenario.pipe("cycle-input-pipe");
        ScenarioMember inputHatch = scenario.inputHatch("cycle-input-hatch");
        ScenarioMember heatPump = scenario.machineHatch("cycle-heat-pump");
        ScenarioMember outputHatch = scenario.outputHatch("cycle-output-hatch");
        ScenarioMember radiator = scenario.machineHatch("cycle-radiator");
        ScenarioMember radiatorPipe = scenario.pipe("cycle-radiator-pipe");
        List<ScenarioMember> members = scenario.members(inputPipe, inputHatch, heatPump, outputHatch, radiator, radiatorPipe);
        IntegratedFluidNetwork sourceNetwork = IFNTestSupport.newNetwork(fluid, 4_000, 1_000, 10.0f);
        long sourceInitialAmountQ = 900L * IntegratedFluidNetwork.AMOUNT_SCALE;
        sourceNetwork.addState(
            fluid,
            sourceInitialAmountQ,
            IntegratedFluidNetwork.toEnthalpyQFromSpecific(301.0d, sourceInitialAmountQ));

        scenario.connectChain(inputPipe, inputHatch, heatPump, outputHatch, radiator, radiatorPipe);
        scenario.addNetworkFrom(inputPipe);
        scenario.assertSingleNetwork(members, 6);

        for (int cycle = 0; cycle < 3; cycle++) {
            IntegratedFluidNetwork vfnNetwork = inputPipe.getNetwork();
            long requestedAmountQ = 70L * IntegratedFluidNetwork.AMOUNT_SCALE;
            double targetTemperature = 322.0d + cycle * 3.0d;
            long amountBeforeHeatQ = vfnNetwork.getAmountQ();
            double temperatureBeforeHeat = vfnNetwork.getTemperature();
            applyHeatPumpTransfer(sourceNetwork, vfnNetwork, fluid, requestedAmountQ, targetTemperature);
            assertEquals(sourceInitialAmountQ, sourceNetwork.getAmountQ() + totalAmountQ(scenario, members));
            assertTrue(vfnNetwork.getTemperature() <= targetTemperature + 0.15d);
            assertTrue(amountBeforeHeatQ == 0L || vfnNetwork.getTemperature() > temperatureBeforeHeat);

            long vfnAmountBeforeRadiatorQ = vfnNetwork.getAmountQ();
            double heatedTemperature = vfnNetwork.getTemperature();
            double cooledTemperature = computeRadiatorTemperature(fluid, vfnAmountBeforeRadiatorQ, heatedTemperature);
            assertTrue(cooledTemperature >= 300.0d);
            replaceNetworkAtTemperature(vfnNetwork, fluid, cooledTemperature);
            assertEquals(vfnAmountBeforeRadiatorQ, vfnNetwork.getAmountQ());
            assertTrue(vfnNetwork.getTemperature() < heatedTemperature);
            assertTrue(vfnNetwork.getTemperature() > 0.0d);

            scenario.disconnect(heatPump, outputHatch);
            scenario.changedAt(heatPump);
            scenario.assertNetworkGroups(members, 2);
            assertEquals(sourceInitialAmountQ, sourceNetwork.getAmountQ() + totalAmountQ(scenario, members));

            scenario.connect(heatPump, outputHatch);
            scenario.changedAt(heatPump);
            scenario.assertSingleNetwork(members, 6);
            assertEquals(sourceInitialAmountQ, sourceNetwork.getAmountQ() + totalAmountQ(scenario, members));
        }

        IntegratedFluidNetwork networkBeforeReload = inputPipe.getNetwork();
        long amountBeforeReloadQ = networkBeforeReload.getAmountQ();
        long enthalpyBeforeReloadQ = networkBeforeReload.getEnthalpyQ();
        double temperatureBeforeReload = networkBeforeReload.getTemperature();

        scenario.roundTripSavedDataAndReloadFrom(inputPipe, members);

        IntegratedFluidNetwork reloadedNetwork = inputPipe.getNetwork();
        scenario.assertSingleNetwork(members, 6);
        assertEquals(amountBeforeReloadQ, reloadedNetwork.getAmountQ());
        assertEquals(enthalpyBeforeReloadQ, reloadedNetwork.getEnthalpyQ());
        assertEquals(temperatureBeforeReload, reloadedNetwork.getTemperature(), 0.001d);
        assertEquals(sourceInitialAmountQ, sourceNetwork.getAmountQ() + totalAmountQ(scenario, members));
        assertTrue(IFNNetworkTransferGate.canReceiveFromMachine(reloadedNetwork));
        assertTrue(IFNNetworkTransferGate.canProvideToMachine(reloadedNetwork));
    }

    private static void applyHeatPumpTransfer(IntegratedFluidNetwork inputNetwork, IntegratedFluidNetwork outputNetwork,
        Fluid fluid, long requestedAmountQ, double targetTemperature) {
        double outputSpecificEnthalpy = IFNMachineThermo.computeTargetSpecificEnthalpyForStateAdd(
            outputNetwork,
            fluid,
            targetTemperature,
            requestedAmountQ);
        IFNStateTransferPlanner.PlannedStateTransfer plan = IFNStateTransferPlanner.planSingleOutputStateAdd(
            inputNetwork,
            outputNetwork,
            fluid,
            outputSpecificEnthalpy,
            requestedAmountQ,
            10.0f);

        assertTrue(plan.acceptedAmountQ >= IntegratedFluidNetwork.AMOUNT_SCALE);

        IntegratedFluidNetwork.ExtractedPayload extracted = inputNetwork.extractProportional(plan.acceptedAmountQ, false);
        long outputEnthalpyQ = IntegratedFluidNetwork.toEnthalpyQFromSpecific(outputSpecificEnthalpy, extracted.amountQ);
        outputNetwork.addState(fluid, extracted.amountQ, outputEnthalpyQ);
    }

    private static double computeRadiatorTemperature(Fluid fluid, long amountQ, double inputTemperature) {
        RadiatorLoopSnapshot snapshot = RadiatorLoopSnapshot.valid(
            1,
            0,
            1,
            0.01f,
            Collections.singletonList(RadiatorLoopSnapshot.ThermalBranch.exchanger(20.0d, 0.0d))
        );
        int amount = Math.max(1, (int) (amountQ / IntegratedFluidNetwork.AMOUNT_SCALE));
        return RadiatorThermo.computeOutputTemperatureForFixedTime(snapshot, fluid, amount, inputTemperature, 300.0d, 20);
    }

    private static void replaceNetworkAtTemperature(IntegratedFluidNetwork network, Fluid fluid, double temperature) {
        long amountQ = network.getAmountQ();
        network.clearFluid();
        network.addState(
            fluid,
            amountQ,
            IntegratedFluidNetwork.toEnthalpyQFromSpecific(temperature, amountQ));
    }

    private static long totalAmountQ(HorizonQAVfnScenario scenario, List<ScenarioMember> members) {
        long totalAmountQ = 0L;
        for (IntegratedFluidNetwork network : scenario.networksOf(members)) {
            totalAmountQ += network.getAmountQ();
        }
        return totalAmountQ;
    }
}
