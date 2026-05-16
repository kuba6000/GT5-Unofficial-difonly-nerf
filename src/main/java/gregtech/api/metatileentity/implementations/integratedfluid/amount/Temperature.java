package gregtech.api.metatileentity.implementations.integratedfluid.amount;

public final class Temperature implements Comparable<Temperature> {

    public static final long SCALE = 1_000_000L;
    public static final Temperature ZERO = new Temperature(0L);

    private final long rawKelvinUnits;

    private Temperature(long rawKelvinUnits) {
        if (rawKelvinUnits < 0L) {
            throw new IllegalArgumentException("temperature cannot be negative");
        }
        this.rawKelvinUnits = rawKelvinUnits;
    }

    public static Temperature fromRawUnits(long rawKelvinUnits) {
        return rawKelvinUnits == 0L ? ZERO : new Temperature(rawKelvinUnits);
    }

    public static Temperature fromKelvin(double kelvin) {
        if (kelvin < 0.0d) {
            throw new IllegalArgumentException("kelvin cannot be negative");
        }
        return fromRawUnits(Math.round(kelvin * SCALE));
    }

    public long rawUnits() {
        return rawKelvinUnits;
    }

    public double toKelvin() {
        return rawKelvinUnits / (double) SCALE;
    }

    @Override
    public int compareTo(Temperature other) {
        return Long.compare(rawKelvinUnits, other.rawKelvinUnits);
    }
}
