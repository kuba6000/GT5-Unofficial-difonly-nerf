package gregtech.api.metatileentity.implementations.integratedfluid.safety;

import java.util.Collection;

import gregtech.api.metatileentity.implementations.integratedfluid.IFNPressurePolicy;
import gregtech.api.metatileentity.implementations.integratedfluid.IIntegratedFluidMember;

public final class IFNOperationalLimits {

    private final float accumulatorMaxPressureBar;

    private IFNOperationalLimits(float accumulatorMaxPressureBar) {
        this.accumulatorMaxPressureBar = accumulatorMaxPressureBar;
    }

    public static IFNOperationalLimits ofAccumulatorMaxPressureBar(float accumulatorMaxPressureBar) {
        return new IFNOperationalLimits(accumulatorMaxPressureBar);
    }

    public static IFNOperationalLimits fromMembers(Collection<? extends IIntegratedFluidMember> members) {
        float accumulatorMaxPressureBar = IFNPressurePolicy.DEFAULT_MAX_PRESSURE_BAR;
        boolean hasAccumulator = false;
        for (IIntegratedFluidMember member : members) {
            if (member.getAccumulatorContribution() <= 0) {
                continue;
            }
            hasAccumulator = true;
            accumulatorMaxPressureBar = Math.min(accumulatorMaxPressureBar, member.getAccumulatorMaxPressureBar());
        }
        return new IFNOperationalLimits(hasAccumulator
                ? accumulatorMaxPressureBar
                : IFNPressurePolicy.DEFAULT_MAX_PRESSURE_BAR);
    }

    public float accumulatorMaxPressureBar() {
        return accumulatorMaxPressureBar;
    }
}
