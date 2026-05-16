package gregtech.api.metatileentity.implementations.integratedfluid.state;

import java.util.Optional;

import gregtech.api.metatileentity.implementations.integratedfluid.amount.EnergyAmount;
import gregtech.api.metatileentity.implementations.integratedfluid.amount.SubstanceAmount;

public final class IFNCanonicalState {

    private static final IFNCanonicalState EMPTY = new IFNCanonicalState(null, SubstanceAmount.ZERO, EnergyAmount.ZERO);

    private final String fluidId;
    private final SubstanceAmount substanceAmount;
    private final EnergyAmount internalEnergy;

    private IFNCanonicalState(String fluidId, SubstanceAmount substanceAmount, EnergyAmount internalEnergy) {
        this.fluidId = fluidId;
        this.substanceAmount = substanceAmount;
        this.internalEnergy = internalEnergy;
    }

    public static IFNCanonicalState empty() {
        return EMPTY;
    }

    public static IFNCanonicalState of(String fluidId, SubstanceAmount substanceAmount, EnergyAmount internalEnergy) {
        SubstanceAmount normalizedSubstance = substanceAmount == null ? SubstanceAmount.ZERO : substanceAmount;
        if (normalizedSubstance.isZero()) {
            return EMPTY;
        }
        if (fluidId == null || fluidId.trim().isEmpty()) {
            throw new IllegalArgumentException("non-empty IFN state requires a fluid id");
        }
        EnergyAmount normalizedEnergy = internalEnergy == null ? EnergyAmount.ZERO : internalEnergy;
        return new IFNCanonicalState(fluidId, normalizedSubstance, normalizedEnergy);
    }

    public boolean isEmpty() {
        return substanceAmount.isZero();
    }

    public Optional<String> fluidId() {
        return Optional.ofNullable(fluidId);
    }

    public SubstanceAmount substanceAmount() {
        return substanceAmount;
    }

    public EnergyAmount internalEnergy() {
        return internalEnergy;
    }
}
