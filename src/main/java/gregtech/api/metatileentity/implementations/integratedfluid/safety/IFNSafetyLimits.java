package gregtech.api.metatileentity.implementations.integratedfluid.safety;

import java.util.Collection;

import gregtech.api.metatileentity.implementations.integratedfluid.IIntegratedFluidMember;

public final class IFNSafetyLimits {

    private final IFNOperationalLimits operationalLimits;

    private IFNSafetyLimits(IFNOperationalLimits operationalLimits) {
        this.operationalLimits = operationalLimits;
    }

    public static IFNSafetyLimits fromMembers(Collection<? extends IIntegratedFluidMember> members) {
        return new IFNSafetyLimits(IFNOperationalLimits.fromMembers(members));
    }

    public static IFNSafetyLimits fromOperationalLimits(IFNOperationalLimits operationalLimits) {
        if (operationalLimits == null) {
            throw new IllegalArgumentException("operationalLimits is required");
        }
        return new IFNSafetyLimits(operationalLimits);
    }

    public float maxPressureDifferentialBar() {
        return operationalLimits.accumulatorMaxPressureBar();
    }

    public float maxTemperatureKelvin() {
        return operationalLimits.maxTemperatureKelvin();
    }

    public IFNOperationalLimits operationalLimits() {
        return operationalLimits;
    }
}
