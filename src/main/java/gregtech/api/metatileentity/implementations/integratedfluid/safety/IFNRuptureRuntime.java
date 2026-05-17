package gregtech.api.metatileentity.implementations.integratedfluid.safety;

import java.util.Random;

public final class IFNRuptureRuntime {

    private final Random random;

    public IFNRuptureRuntime(Random random) {
        this.random = random == null ? new Random() : random;
    }

    public boolean shouldRupture(IFNOperationalSafetyEvaluation evaluation) {
        if (evaluation == null) {
            return false;
        }
        return IFNOperationalFailurePolicy.shouldFailNow(evaluation, random);
    }
}
