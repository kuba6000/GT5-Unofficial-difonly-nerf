package gregtech.api.metatileentity.implementations.integratedfluid;

import net.minecraftforge.fluids.Fluid;

/**
 * Immutable physical state snapshot for an Integrated Fluid Network.
 *
 * This is the handoff object between network storage, transfer planning, and machine process code.
 */
public final class IFNFluidState {

    private final String fluidName;
    private final long amountQ;
    private final long enthalpyQ;
    private final float pressureBar;

    public IFNFluidState(String fluidName, long amountQ, long enthalpyQ, float pressureBar) {
        this.fluidName = amountQ > 0L ? fluidName : null;
        this.amountQ = Math.max(0L, amountQ);
        this.enthalpyQ = this.amountQ > 0L ? Math.max(0L, enthalpyQ) : 0L;
        this.pressureBar = IFNPressurePolicy.clampMinimum(pressureBar);
    }

    public String getFluidName() {
        return fluidName;
    }

    public long getAmountQ() {
        return amountQ;
    }

    public long getEnthalpyQ() {
        return enthalpyQ;
    }

    public float getPressureBar() {
        return pressureBar;
    }

    public boolean isEmpty() {
        return amountQ <= 0L || fluidName == null;
    }

    public double getSpecificEnthalpy() {
        if (amountQ <= 0L) {
            return 0.0d;
        }
        return (double) enthalpyQ / (double) amountQ
            * (double) IntegratedFluidNetwork.AMOUNT_SCALE
            / (double) IntegratedFluidNetwork.ENTHALPY_SCALE;
    }

    public IFNFluidState withAddedState(Fluid fluid, long addAmountQ, long addEnthalpyQ, float pressureBar) {
        if (fluid == null || addAmountQ <= 0L) {
            return withPressure(pressureBar);
        }
        String nextFluidName = isEmpty() ? fluid.getName() : fluidName;
        return new IFNFluidState(nextFluidName, amountQ + addAmountQ, enthalpyQ + addEnthalpyQ, pressureBar);
    }

    public IFNFluidState withExtractedAmount(long extractAmountQ, float pressureBar) {
        if (extractAmountQ <= 0L || isEmpty()) {
            return withPressure(pressureBar);
        }
        long removedAmountQ = Math.min(extractAmountQ, amountQ);
        long remainingAmountQ = amountQ - removedAmountQ;
        if (remainingAmountQ < IntegratedFluidNetwork.AMOUNT_SCALE) {
            return new IFNFluidState(null, 0L, 0L, pressureBar);
        }
        long removedEnthalpyQ = removedAmountQ == amountQ
            ? enthalpyQ
            : (long) (enthalpyQ * ((double) removedAmountQ / (double) amountQ));
        return new IFNFluidState(fluidName, remainingAmountQ, enthalpyQ - removedEnthalpyQ, pressureBar);
    }

    public IFNFluidState withEnthalpy(long nextEnthalpyQ, float pressureBar) {
        return new IFNFluidState(fluidName, amountQ, nextEnthalpyQ, pressureBar);
    }

    public IFNFluidState withPressure(float pressureBar) {
        return new IFNFluidState(fluidName, amountQ, enthalpyQ, pressureBar);
    }
}
