package gregtech.api.metatileentity.implementations.integratedfluid.safety;

public final class IFNPressureWarningTracker {

    public static final int WARNING_TICKS_BEFORE_FAILURE_ROLL = 200;
    public static final int FAILURE_ROLL_INTERVAL_TICKS = 20;
    public static final double FAILURE_ROLL_CHANCE = 0.01d;

    private final IFNLimitWarningTracker delegate = new IFNLimitWarningTracker();

    public void update(IFNPressureLimitEvaluation evaluation) {
        delegate.update(evaluation.status() == IFNPressureLimitStatus.WARNING);
    }

    public int warningTicks() {
        return delegate.warningTicks();
    }

    public boolean shouldRollFailureThisTick() {
        return delegate.shouldRollFailureThisTick();
    }
}
