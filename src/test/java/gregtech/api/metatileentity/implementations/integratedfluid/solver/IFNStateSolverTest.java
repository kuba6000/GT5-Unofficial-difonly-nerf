package gregtech.api.metatileentity.implementations.integratedfluid.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraftforge.fluids.Fluid;

import org.junit.jupiter.api.Test;

import gregtech.api.metatileentity.implementations.integratedfluid.FluidThermalProperties;
import gregtech.api.metatileentity.implementations.integratedfluid.IFNFluidThermalRegistry;
import gregtech.api.metatileentity.implementations.integratedfluid.IntegratedFluidNetwork;

class IFNStateSolverTest {

    private static final Fluid FLUID = registerFluid();

    @Test
    void solvesLiquidStateAtKnownPressure() {
        IFNSolvedState solved = solve(300.0d, 1.0d);

        assertEquals(FluidThermalProperties.Phase.LIQUID, solved.phaseComposition().primaryPhase());
        assertEquals(300.0d, solved.temperatureKelvin(), 0.000001d);
        assertEquals(1.0d, solved.phaseComposition().liquidFraction(), 0.000001d);
        assertEquals(0.0d, solved.phaseComposition().vaporFraction(), 0.000001d);
        assertEquals(1.0d, solved.phaseComposition().flowableFraction(), 0.000001d);
        assertEquals(10.0d, solved.occupiedVolumeLiters(), 0.000001d);
    }

    @Test
    void exposesTwoPhaseCompositionFromQuality() {
        IFNSolvedState solved = solve(400.0d, 1.0d);

        assertEquals(FluidThermalProperties.Phase.TWO_PHASE, solved.phaseComposition().primaryPhase());
        assertEquals(0.5d, solved.phaseComposition().liquidFraction(), 0.000001d);
        assertEquals(0.5d, solved.phaseComposition().vaporFraction(), 0.000001d);
        assertEquals(1.0d, solved.phaseComposition().flowableFraction(), 0.000001d);
    }

    @Test
    void exposesSupercriticalAsFlowableSinglePhase() {
        IFNSolvedState solved = solve(600.0d, 20.0d);

        assertEquals(FluidThermalProperties.Phase.SUPERCRITICAL, solved.phaseComposition().primaryPhase());
        assertEquals(1.0d, solved.phaseComposition().flowableFraction(), 0.000001d);
        assertEquals(0.0d, solved.phaseComposition().solidFraction(), 0.000001d);
    }

    @Test
    void emptyStateHasNoFlowablePhase() {
        IFNSolvedState solved = IFNStateSolver.solveAtPressure(FLUID, 0L, 0L, 1.0d);

        assertTrue(solved.isEmpty());
        assertEquals(0.0d, solved.phaseComposition().flowableFraction(), 0.000001d);
    }

    private static IFNSolvedState solve(double specificEnthalpy, double pressureBar) {
        long amountQ = 10L * IntegratedFluidNetwork.AMOUNT_SCALE;
        long enthalpyQ = IntegratedFluidNetwork.toEnthalpyQFromSpecific(specificEnthalpy, amountQ);
        return IFNStateSolver.solveAtPressure(FLUID, amountQ, enthalpyQ, pressureBar);
    }

    private static Fluid registerFluid() {
        Fluid fluid = new Fluid("ifn_solver_test_fluid");
        IFNFluidThermalRegistry.register(fluid, builder -> builder
            .setCriticalPressure(10.0d)
            .setCriticalTemperature(500.0d)
            .setFreezeTemperature(250.0d)
            .setSpecificHeatCapacity(1.0d)
            .setTemperatureFromPH((pBar, h) -> h)
            .setPhaseFromPH(IFNStateSolverTest::phaseFromPH)
            .setSpecificVolumeFromPH((pBar, h) -> h < 350.0d ? 1.0d : h <= 450.0d ? 5.0d : 10.0d)
            .setSaturationTemperatureFromP(pBar -> 400.0d)
            .setHfFromP(pBar -> 350.0d)
            .setHgFromP(pBar -> 450.0d)
            .setSpecificEnthalpyFromPT((pBar, temperatureK) -> temperatureK));
        return fluid;
    }

    private static FluidThermalProperties.PhaseResult phaseFromPH(double pBar, double h) {
        if (pBar >= 10.0d && h >= 500.0d) {
            return new FluidThermalProperties.PhaseResult(FluidThermalProperties.Phase.SUPERCRITICAL, 0.0d);
        }
        if (h < 350.0d) {
            return new FluidThermalProperties.PhaseResult(FluidThermalProperties.Phase.LIQUID, 0.0d);
        }
        if (h <= 450.0d) {
            return new FluidThermalProperties.PhaseResult(
                FluidThermalProperties.Phase.TWO_PHASE,
                (h - 350.0d) / 100.0d);
        }
        return new FluidThermalProperties.PhaseResult(FluidThermalProperties.Phase.VAPOR, 1.0d);
    }
}
