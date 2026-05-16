package gregtech.api.metatileentity.implementations.integratedfluid.safety;

import gregtech.api.metatileentity.implementations.integratedfluid.IFNPressurePolicy;

public final class IFNPressureLimitEvaluation {

    private final IFNPressureLimitStatus status;
    private final double pressureBar;
    private final float maxPressureBar;

    private IFNPressureLimitEvaluation(IFNPressureLimitStatus status, double pressureBar, float maxPressureBar) {
        this.status = status;
        this.pressureBar = pressureBar;
        this.maxPressureBar = maxPressureBar;
    }

    public static IFNPressureLimitEvaluation evaluate(double pressureBar, IFNOperationalLimits limits,
        boolean incompleteNetwork) {
        float maxPressureBar = limits.accumulatorMaxPressureBar();
        if (pressureBar <= maxPressureBar) {
            return new IFNPressureLimitEvaluation(IFNPressureLimitStatus.NORMAL, pressureBar, maxPressureBar);
        }
        if (!incompleteNetwork && IFNPressurePolicy.exceedsRuptureLimit(pressureBar, maxPressureBar)) {
            return new IFNPressureLimitEvaluation(IFNPressureLimitStatus.RUPTURE, pressureBar, maxPressureBar);
        }
        return new IFNPressureLimitEvaluation(IFNPressureLimitStatus.WARNING, pressureBar, maxPressureBar);
    }

    public IFNPressureLimitStatus status() {
        return status;
    }

    public boolean isOverLimit() {
        return status != IFNPressureLimitStatus.NORMAL;
    }

    public boolean isRuptureRequired() {
        return status == IFNPressureLimitStatus.RUPTURE;
    }

    public double pressureBar() {
        return pressureBar;
    }

    public float maxPressureBar() {
        return maxPressureBar;
    }
}
