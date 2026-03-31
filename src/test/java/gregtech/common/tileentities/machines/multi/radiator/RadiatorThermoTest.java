package gregtech.common.tileentities.machines.multi.radiator;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import gregtech.api.metatileentity.implementations.integratedfluid.IFNTestSupport;

class RadiatorThermoTest {

    @Test
    void conductionBranchNeverReducesEffectiveConductance() {
        RadiatorLoopSnapshot plainSnapshot = RadiatorLoopSnapshot.valid(
            1,
            0,
            1,
            0.01f,
            Collections.singletonList(RadiatorLoopSnapshot.ThermalBranch.exchanger(10.0d, 0.0d))
        );

        RadiatorLoopSnapshot conductionSnapshot = RadiatorLoopSnapshot.valid(
            1,
            1,
            1,
            0.01f,
            Collections.singletonList(
                RadiatorLoopSnapshot.ThermalBranch.conduction(
                    5.0d,
                    Collections.singletonList(RadiatorLoopSnapshot.ThermalBranch.exchanger(10.0d, 0.0d))
                ))
        );

        double plainConductance = RadiatorThermo.computeEffectiveConductance(plainSnapshot, 300.0d);
        double conductionConductance = RadiatorThermo.computeEffectiveConductance(conductionSnapshot, 300.0d);

        assertTrue(conductionConductance >= plainConductance);
    }

    @Test
    void fixedTimeCoolingMovesTemperatureTowardAmbientFromBothSides() {
        RadiatorLoopSnapshot snapshot = RadiatorLoopSnapshot.valid(
            1,
            0,
            1,
            0.01f,
            Collections.singletonList(RadiatorLoopSnapshot.ThermalBranch.exchanger(20.0d, 0.0d))
        );

        double cooled = RadiatorThermo.computeOutputTemperatureForFixedTime(
            snapshot,
            IFNTestSupport.liquidFluid(),
            1_000,
            500.0d,
            300.0d,
            20
        );
        double warmed = RadiatorThermo.computeOutputTemperatureForFixedTime(
            snapshot,
            IFNTestSupport.liquidFluid(),
            1_000,
            250.0d,
            300.0d,
            20
        );

        assertTrue(cooled < 500.0d && cooled > 300.0d);
        assertTrue(warmed > 250.0d && warmed < 300.0d);
    }
}
