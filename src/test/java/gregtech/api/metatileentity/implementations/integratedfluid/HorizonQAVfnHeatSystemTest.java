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

class HorizonQAVfnHeatSystemTest {

    @Test
    void heatPumpFeedsVfnHatchesAndRadiatorCoolsWithoutDuplicatingFluid() {
        Fluid fluid = IFNTestSupport.pressureSensitiveLiquidFluid();
        HorizonQAVfnScenario scenario = HorizonQAVfnScenario.create(fluid);
        ScenarioMember inputPipe = scenario.pipe("input-pipe");
        ScenarioMember inputHatch = scenario.inputHatch("input-hatch");
        ScenarioMember heatPump = scenario.machineHatch("heat-pump");
        ScenarioMember outputHatch = scenario.outputHatch("output-hatch");
        ScenarioMember radiator = scenario.machineHatch("radiator");
        ScenarioMember radiatorPipe = scenario.pipe("radiator-pipe");
        List<ScenarioMember> members = scenario.members(inputPipe, inputHatch, heatPump, outputHatch, radiator, radiatorPipe);
        IntegratedFluidNetwork sourceNetwork = IFNTestSupport.newNetwork(fluid, 4_000, 1_000, 10.0f);
        long sourceInitialAmountQ = 800L * IntegratedFluidNetwork.AMOUNT_SCALE;
        sourceNetwork.addState(
            fluid,
            sourceInitialAmountQ,
            IntegratedFluidNetwork.toEnthalpyQFromSpecific(301.0d, sourceInitialAmountQ));

        scenario.connectChain(inputPipe, inputHatch, heatPump, outputHatch, radiator, radiatorPipe);
        scenario.addNetworkFrom(inputPipe);
        scenario.assertSingleNetwork(members, 6);

        IntegratedFluidNetwork vfnNetwork = inputPipe.getNetwork();
        long requestedAmountQ = 120L * IntegratedFluidNetwork.AMOUNT_SCALE;
        double heatPumpTargetTemperature = 330.0d;
        double outputSpecificEnthalpy = IFNMachineThermo.computeTargetSpecificEnthalpyForStateAdd(
            vfnNetwork,
            fluid,
            heatPumpTargetTemperature,
            requestedAmountQ);
        IFNStateTransferPlanner.PlannedStateTransfer plan = IFNStateTransferPlanner.planSingleOutputStateAdd(
            sourceNetwork,
            vfnNetwork,
            fluid,
            outputSpecificEnthalpy,
            requestedAmountQ,
            10.0f);

        assertTrue(plan.acceptedAmountQ >= IntegratedFluidNetwork.AMOUNT_SCALE);

        applyHeatPumpTransfer(sourceNetwork, vfnNetwork, fluid, outputSpecificEnthalpy, plan.acceptedAmountQ);
        long totalAfterHeatPumpQ = sourceNetwork.getAmountQ() + vfnNetwork.getAmountQ();
        double heatedTemperature = vfnNetwork.getTemperature();

        assertEquals(sourceInitialAmountQ, totalAfterHeatPumpQ);
        assertEquals(heatPumpTargetTemperature, heatedTemperature, 0.05d);
        assertTrue(IFNNetworkTransferGate.canReceiveFromMachine(vfnNetwork));
        assertTrue(IFNNetworkTransferGate.canProvideToMachine(vfnNetwork));

        long vfnAmountBeforeRadiatorQ = vfnNetwork.getAmountQ();
        double cooledTemperature = computeRadiatorTemperature(fluid, vfnAmountBeforeRadiatorQ, heatedTemperature);
        replaceNetworkAtTemperature(vfnNetwork, fluid, cooledTemperature);

        assertEquals(vfnAmountBeforeRadiatorQ, vfnNetwork.getAmountQ());
        assertEquals(sourceInitialAmountQ, sourceNetwork.getAmountQ() + vfnNetwork.getAmountQ());
        assertTrue(vfnNetwork.getTemperature() < heatedTemperature);
        assertTrue(vfnNetwork.getTemperature() > 300.0d);
        assertTrue(IFNNetworkTransferGate.canReceiveFromMachine(vfnNetwork));
        assertTrue(IFNNetworkTransferGate.canProvideToMachine(vfnNetwork));
    }

    private static void applyHeatPumpTransfer(IntegratedFluidNetwork inputNetwork, IntegratedFluidNetwork outputNetwork,
        Fluid fluid, double outputSpecificEnthalpy, long amountQ) {
        IntegratedFluidNetwork.ExtractedPayload extracted = inputNetwork.extractProportional(amountQ, false);
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
}
