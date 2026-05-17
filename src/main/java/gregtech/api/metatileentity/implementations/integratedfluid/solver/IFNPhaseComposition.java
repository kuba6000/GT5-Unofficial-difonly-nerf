package gregtech.api.metatileentity.implementations.integratedfluid.solver;

import gregtech.api.metatileentity.implementations.integratedfluid.FluidThermalProperties;

public final class IFNPhaseComposition {

    private static final IFNPhaseComposition EMPTY = new IFNPhaseComposition(
        FluidThermalProperties.Phase.LIQUID,
        0.0d,
        0.0d,
        0.0d,
        0.0d);

    private final FluidThermalProperties.Phase primaryPhase;
    private final double liquidFraction;
    private final double vaporFraction;
    private final double solidFraction;
    private final double flowableFraction;

    private IFNPhaseComposition(
        FluidThermalProperties.Phase primaryPhase,
        double liquidFraction,
        double vaporFraction,
        double solidFraction,
        double flowableFraction) {
        this.primaryPhase = primaryPhase;
        this.liquidFraction = liquidFraction;
        this.vaporFraction = vaporFraction;
        this.solidFraction = solidFraction;
        this.flowableFraction = flowableFraction;
    }

    public static IFNPhaseComposition empty() {
        return EMPTY;
    }

    public static IFNPhaseComposition from(FluidThermalProperties.PhaseResult phaseResult) {
        if (phaseResult == null || phaseResult.phase == null) {
            return EMPTY;
        }
        switch (phaseResult.phase) {
            case LIQUID:
                return new IFNPhaseComposition(phaseResult.phase, 1.0d, 0.0d, 0.0d, 1.0d);
            case VAPOR:
                return new IFNPhaseComposition(phaseResult.phase, 0.0d, 1.0d, 0.0d, 1.0d);
            case TWO_PHASE:
                double vapor = clamp01(phaseResult.quality);
                return new IFNPhaseComposition(phaseResult.phase, 1.0d - vapor, vapor, 0.0d, 1.0d);
            case SUPERCRITICAL:
                return new IFNPhaseComposition(phaseResult.phase, 0.0d, 0.0d, 0.0d, 1.0d);
            default:
                return EMPTY;
        }
    }

    public FluidThermalProperties.Phase primaryPhase() {
        return primaryPhase;
    }

    public double liquidFraction() {
        return liquidFraction;
    }

    public double vaporFraction() {
        return vaporFraction;
    }

    public double solidFraction() {
        return solidFraction;
    }

    public double flowableFraction() {
        return flowableFraction;
    }

    private static double clamp01(double value) {
        if (value <= 0.0d) {
            return 0.0d;
        }
        if (value >= 1.0d) {
            return 1.0d;
        }
        return value;
    }
}
