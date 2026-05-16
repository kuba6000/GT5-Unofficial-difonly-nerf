package gregtech.api.metatileentity.implementations.integratedfluid.safety;

import java.util.Collection;

import gregtech.api.metatileentity.implementations.integratedfluid.IFNPressurePolicy;
import gregtech.api.metatileentity.implementations.integratedfluid.IIntegratedFluidMember;

public final class IFNOperationalLimits {

    private final float accumulatorMaxPressureBar;
    private final float maxTemperatureKelvin;

    private IFNOperationalLimits(float accumulatorMaxPressureBar, float maxTemperatureKelvin) {
        this.accumulatorMaxPressureBar = accumulatorMaxPressureBar;
        this.maxTemperatureKelvin = maxTemperatureKelvin;
    }

    public static IFNOperationalLimits ofAccumulatorMaxPressureBar(float accumulatorMaxPressureBar) {
        return new IFNOperationalLimits(accumulatorMaxPressureBar, Float.POSITIVE_INFINITY);
    }

    public static IFNOperationalLimits fromMembers(Collection<? extends IIntegratedFluidMember> members) {
        float accumulatorMaxPressureBar = IFNPressurePolicy.DEFAULT_MAX_PRESSURE_BAR;
        float maxTemperatureKelvin = Float.POSITIVE_INFINITY;
        boolean hasAccumulator = false;
        for (IIntegratedFluidMember member : members) {
            maxTemperatureKelvin = Math.min(maxTemperatureKelvin, member.getMaxTemperatureKelvin());
            if (member.getAccumulatorContribution() > 0) {
                hasAccumulator = true;
                accumulatorMaxPressureBar = Math.min(accumulatorMaxPressureBar, member.getAccumulatorMaxPressureBar());
            }
        }
        return new IFNOperationalLimits(hasAccumulator
                ? accumulatorMaxPressureBar
                : IFNPressurePolicy.DEFAULT_MAX_PRESSURE_BAR,
                maxTemperatureKelvin);
    }

    public float accumulatorMaxPressureBar() {
        return accumulatorMaxPressureBar;
    }

    public float maxTemperatureKelvin() {
        return maxTemperatureKelvin;
    }
}
