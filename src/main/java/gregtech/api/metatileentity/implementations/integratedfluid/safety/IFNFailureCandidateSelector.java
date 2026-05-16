package gregtech.api.metatileentity.implementations.integratedfluid.safety;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

import gregtech.api.metatileentity.implementations.integratedfluid.IIntegratedFluidMember;

public final class IFNFailureCandidateSelector {

    private IFNFailureCandidateSelector() {}

    public static Optional<IIntegratedFluidMember> selectWeakestPressureCandidate(Collection<IIntegratedFluidMember> members) {
        return members.stream()
            .filter(IIntegratedFluidMember::isOperationalFailureCandidate)
            .min(Comparator.comparingDouble(IIntegratedFluidMember::getMaxPressureDifferentialBar));
    }

    public static Optional<IIntegratedFluidMember> selectWeakestTemperatureCandidate(Collection<IIntegratedFluidMember> members) {
        return members.stream()
            .filter(IIntegratedFluidMember::isOperationalFailureCandidate)
            .min(Comparator.comparingDouble(IIntegratedFluidMember::getMaxTemperatureKelvin));
    }
}
