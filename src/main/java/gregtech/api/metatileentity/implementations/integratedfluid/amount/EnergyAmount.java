package gregtech.api.metatileentity.implementations.integratedfluid.amount;

import gregtech.api.metatileentity.implementations.integratedfluid.IntegratedFluidNetwork;

public final class EnergyAmount implements Comparable<EnergyAmount> {

    public static final long SCALE = IntegratedFluidNetwork.ENTHALPY_SCALE;
    public static final EnergyAmount ZERO = new EnergyAmount(0L);

    private final long rawUnits;

    private EnergyAmount(long rawUnits) {
        if (rawUnits < 0L) {
            throw new IllegalArgumentException("energy amount cannot be negative");
        }
        this.rawUnits = rawUnits;
    }

    public static EnergyAmount fromRawUnits(long rawUnits) {
        return rawUnits == 0L ? ZERO : new EnergyAmount(rawUnits);
    }

    public static EnergyAmount fromEu(long eu) {
        if (eu < 0L) {
            throw new IllegalArgumentException("EU cannot be negative");
        }
        return fromRawUnits(Math.multiplyExact(eu, SCALE));
    }

    public long rawUnits() {
        return rawUnits;
    }

    public long toWholeEu() {
        return rawUnits / SCALE;
    }

    public EnergyAmount plus(EnergyAmount other) {
        if (other == null || other.rawUnits == 0L) {
            return this;
        }
        return fromRawUnits(Math.addExact(rawUnits, other.rawUnits));
    }

    @Override
    public int compareTo(EnergyAmount other) {
        return Long.compare(rawUnits, other.rawUnits);
    }
}
