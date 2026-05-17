package gregtech.api.metatileentity.implementations.integratedfluid.covers;

public final class IFNDetectorCoverLogic {

    private IFNDetectorCoverLogic() {}

    public static int evaluate(double value, double minValue, double maxValue, Mode mode) {
        return evaluate(value, 0.0d, minValue, maxValue, mode, SourceMode.ABSOLUTE);
    }

    public static int evaluate(double value, double referenceValue, double minValue, double maxValue, Mode mode,
        SourceMode sourceMode) {
        if (!Double.isFinite(value) || !Double.isFinite(minValue) || !Double.isFinite(maxValue) || minValue > maxValue
            || mode == null || sourceMode == null) {
            return 0;
        }
        if (sourceMode == SourceMode.DELTA) {
            if (!Double.isFinite(referenceValue)) {
                return 0;
            }
            value -= referenceValue;
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

    public enum SourceMode {
        ABSOLUTE,
        DELTA
    }
}
