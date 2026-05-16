package gregtech.api.metatileentity.implementations.integratedfluid.amount;

public final class Pressure implements Comparable<Pressure> {

    public static final long SCALE = 1_000_000L;
    public static final Pressure ZERO = new Pressure(0L);

    private final long rawBarUnits;

    private Pressure(long rawBarUnits) {
        if (rawBarUnits < 0L) {
            throw new IllegalArgumentException("pressure cannot be negative");
        }
        this.rawBarUnits = rawBarUnits;
    }

    public static Pressure fromRawUnits(long rawBarUnits) {
        return rawBarUnits == 0L ? ZERO : new Pressure(rawBarUnits);
    }

    public static Pressure fromBar(double bar) {
        if (bar < 0.0d) {
            throw new IllegalArgumentException("bar cannot be negative");
        }
        return fromRawUnits(Math.round(bar * SCALE));
    }

    public long rawUnits() {
        return rawBarUnits;
    }

    public double toBar() {
        return rawBarUnits / (double) SCALE;
    }

    @Override
    public int compareTo(Pressure other) {
        return Long.compare(rawBarUnits, other.rawBarUnits);
    }
}
