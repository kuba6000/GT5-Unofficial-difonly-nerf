package gregtech.api.metatileentity.implementations.integratedfluid.safety;

import java.util.Random;

public final class IFNOperationalFailurePolicy {

    private IFNOperationalFailurePolicy() {}

    public static boolean shouldFailNow(IFNOperationalSafetyEvaluation evaluation, Random random) {
        if (evaluation.isImmediateRuptureRequired()) {
            return true;
        }
        return evaluation.shouldRollFailureThisTick()
            && random.nextDouble() < IFNLimitWarningTracker.FAILURE_ROLL_CHANCE;
    }
}
