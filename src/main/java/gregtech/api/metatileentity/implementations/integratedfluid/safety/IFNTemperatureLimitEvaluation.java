package gregtech.api.metatileentity.implementations.integratedfluid.safety;

import gregtech.api.metatileentity.implementations.integratedfluid.IFNPressurePolicy;

public final class IFNTemperatureLimitEvaluation {

    private static final double LIMIT_EPSILON = 1.0e-9d;

    private final IFNTemperatureLimitStatus status;
    private final double temperatureKelvin;
    private final float maxTemperatureKelvin;

    private IFNTemperatureLimitEvaluation(IFNTemperatureLimitStatus status, double temperatureKelvin,
        float maxTemperatureKelvin) {
        this.status = status;
        this.temperatureKelvin = temperatureKelvin;
        this.maxTemperatureKelvin = maxTemperatureKelvin;
    }

    public static IFNTemperatureLimitEvaluation evaluate(double temperatureKelvin, float maxTemperatureKelvin,
        boolean incompleteNetwork) {
        if (Float.isInfinite(maxTemperatureKelvin) || temperatureKelvin <= maxTemperatureKelvin) {
            return new IFNTemperatureLimitEvaluation(
                IFNTemperatureLimitStatus.NORMAL,
                temperatureKelvin,
                maxTemperatureKelvin);
        }
        double ruptureTemperatureKelvin = maxTemperatureKelvin * IFNPressurePolicy.RUPTURE_PRESSURE_FACTOR;
        if (!incompleteNetwork && temperatureKelvin + LIMIT_EPSILON >= ruptureTemperatureKelvin) {
            return new IFNTemperatureLimitEvaluation(
                IFNTemperatureLimitStatus.RUPTURE,
                temperatureKelvin,
                maxTemperatureKelvin);
        }
        return new IFNTemperatureLimitEvaluation(
            IFNTemperatureLimitStatus.WARNING,
            temperatureKelvin,
            maxTemperatureKelvin);
    }

    public IFNTemperatureLimitStatus status() {
        return status;
    }

    public boolean isOverLimit() {
        return status != IFNTemperatureLimitStatus.NORMAL;
    }

    public boolean isRuptureRequired() {
        return status == IFNTemperatureLimitStatus.RUPTURE;
    }

    public double temperatureKelvin() {
        return temperatureKelvin;
    }

    public float maxTemperatureKelvin() {
        return maxTemperatureKelvin;
    }
}
