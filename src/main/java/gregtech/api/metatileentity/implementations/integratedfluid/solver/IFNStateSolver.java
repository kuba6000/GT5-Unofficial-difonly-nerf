package gregtech.api.metatileentity.implementations.integratedfluid.solver;

import net.minecraftforge.fluids.Fluid;

import gregtech.api.metatileentity.implementations.integratedfluid.FluidThermalProperties;
import gregtech.api.metatileentity.implementations.integratedfluid.IFNPressurePolicy;
import gregtech.api.metatileentity.implementations.integratedfluid.IntegratedFluidNetwork;

public final class IFNStateSolver {

    private IFNStateSolver() {}

    public static IFNSolvedState solveAtPressure(Fluid fluid, long amountQ, long enthalpyQ, double pressureBar) {
        double clampedPressure = IFNPressurePolicy.clampMinimum(pressureBar);
        if (fluid == null || amountQ <= 0L) {
            return new IFNSolvedState(
                fluid,
                0L,
                0L,
                clampedPressure,
                IntegratedFluidNetwork.DEFAULT_TEMPERATURE,
                0.0d,
                0.0d,
                0.0d,
                IFNPhaseComposition.empty());
        }

        double amountRefLiters = amountQ / (double) IntegratedFluidNetwork.AMOUNT_SCALE;
        double specificEnthalpy = (enthalpyQ / (double) IntegratedFluidNetwork.ENTHALPY_SCALE) / amountRefLiters;
        double temperature = FluidThermalProperties.getTemperatureFromPH(fluid, clampedPressure, specificEnthalpy);
        FluidThermalProperties.PhaseResult phaseResult =
            FluidThermalProperties.getPhaseFromPH(fluid, clampedPressure, specificEnthalpy);
        double specificVolume = FluidThermalProperties.getSpecificVolumeFromPH(fluid, clampedPressure, specificEnthalpy);

        return new IFNSolvedState(
            fluid,
            amountQ,
            enthalpyQ,
            clampedPressure,
            temperature,
            specificEnthalpy,
            specificVolume,
            amountRefLiters * specificVolume,
            IFNPhaseComposition.from(phaseResult));
    }
}
