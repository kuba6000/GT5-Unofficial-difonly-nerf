package gregtech.api.metatileentity.implementations.integratedfluid.covers;

public final class IFNDetectorCoverLogic {

    private IFNDetectorCoverLogic() {}

    public static int evaluate(double value, double minValue, double maxValue, Mode mode) {
        if (!Double.isFinite(value) || !Double.isFinite(minValue) || !Double.isFinite(maxValue) || minValue > maxValue
            || mode == null) {
            return 0;
        }
        switch (mode) {
            case BINARY:
                return value >= minValue && value <= maxValue ? 15 : 0;
            case LINEAR:
                if (minValue == maxValue) {
                    return value >= minValue ? 15 : 0;
                }
                double normalized = (value - minValue) / (maxValue - minValue);
                return (int) Math.round(Math.max(0.0d, Math.min(1.0d, normalized)) * 15.0d);
            default:
                return 0;
        }
    }

    public enum Mode {
        BINARY,
        LINEAR
    }
}
