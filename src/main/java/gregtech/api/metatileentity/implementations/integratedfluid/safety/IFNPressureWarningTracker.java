package gregtech.api.metatileentity.implementations.integratedfluid.safety;

public final class IFNPressureWarningTracker {

    public static final int WARNING_TICKS_BEFORE_FAILURE_ROLL = 200;
    public static final int FAILURE_ROLL_INTERVAL_TICKS = 20;
    public static final double FAILURE_ROLL_CHANCE = 0.01d;

    private int warningTicks;

    public void update(IFNPressureLimitEvaluation evaluation) {
        if (evaluation.status() == IFNPressureLimitStatus.WARNING) {
            warningTicks++;
            return;
        }
        warningTicks = 0;
    }

    public int warningTicks() {
        return warningTicks;
    }

    public boolean shouldRollFailureThisTick() {
        return warningTicks >= WARNING_TICKS_BEFORE_FAILURE_ROLL
            && warningTicks % FAILURE_ROLL_INTERVAL_TICKS == 0;
    }
}
