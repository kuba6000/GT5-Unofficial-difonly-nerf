package gregtech.api.metatileentity.implementations.integratedfluid.topology;

import gregtech.api.metatileentity.implementations.integratedfluid.state.IFNCanonicalState;

public final class IFNMergePolicy {

    private IFNMergePolicy() {}

    public static boolean canMerge(IFNCanonicalState first, IFNCanonicalState second) {
        IFNCanonicalState a = normalize(first);
        IFNCanonicalState b = normalize(second);

        if (a.isEmpty() || b.isEmpty()) {
            return true;
        }
        return a.fluidId().get().equals(b.fluidId().get());
    }

    public static IFNCanonicalState merge(IFNCanonicalState first, IFNCanonicalState second) {
        IFNCanonicalState a = normalize(first);
        IFNCanonicalState b = normalize(second);

        if (!canMerge(a, b)) {
            throw new IllegalArgumentException("cannot merge different non-empty IFN fluids");
        }
        if (a.isEmpty()) {
            return b;
        }
        if (b.isEmpty()) {
            return a;
        }

        return IFNCanonicalState.of(
            a.fluidId().get(),
            a.substanceAmount().plus(b.substanceAmount()),
            a.internalEnergy().plus(b.internalEnergy())
        );
    }

    private static IFNCanonicalState normalize(IFNCanonicalState state) {
        return state == null ? IFNCanonicalState.empty() : state;
    }
}
