package gregtech.api.metatileentity.implementations.integratedfluid.amount;

public final class VolumeAmount implements Comparable<VolumeAmount> {

    public static final long SCALE = 1_000_000L;
    public static final VolumeAmount ZERO = new VolumeAmount(0L);

    private final long rawUnits;

    private VolumeAmount(long rawUnits) {
        if (rawUnits < 0L) {
            throw new IllegalArgumentException("volume cannot be negative");
        }
        this.rawUnits = rawUnits;
    }

    public static VolumeAmount fromRawUnits(long rawUnits) {
        return rawUnits == 0L ? ZERO : new VolumeAmount(rawUnits);
    }

    public static VolumeAmount fromLiters(long liters) {
        if (liters < 0L) {
            throw new IllegalArgumentException("liters cannot be negative");
        }
        return fromRawUnits(Math.multiplyExact(liters, SCALE));
    }

    public long rawUnits() {
        return rawUnits;
    }

    public long toWholeLiters() {
        return rawUnits / SCALE;
    }

    @Override
    public int compareTo(VolumeAmount other) {
        return Long.compare(rawUnits, other.rawUnits);
    }
}
