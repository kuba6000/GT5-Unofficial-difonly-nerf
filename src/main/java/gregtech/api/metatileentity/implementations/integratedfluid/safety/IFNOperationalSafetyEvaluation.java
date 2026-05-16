package gregtech.api.metatileentity.implementations.integratedfluid.safety;

public final class IFNOperationalSafetyEvaluation {

    private final IFNPressureLimitEvaluation pressure;
    private final IFNTemperatureLimitEvaluation temperature;
    private final boolean pressureFailureRoll;
    private final boolean temperatureFailureRoll;

    public IFNOperationalSafetyEvaluation(IFNPressureLimitEvaluation pressure, IFNTemperatureLimitEvaluation temperature,
        boolean pressureFailureRoll, boolean temperatureFailureRoll) {
        this.pressure = pressure;
        this.temperature = temperature;
        this.pressureFailureRoll = pressureFailureRoll;
        this.temperatureFailureRoll = temperatureFailureRoll;
    }

    public IFNPressureLimitEvaluation pressure() {
        return pressure;
    }

    public IFNTemperatureLimitEvaluation temperature() {
        return temperature;
    }

    public boolean isImmediateRuptureRequired() {
        return pressure.isRuptureRequired() || temperature.isRuptureRequired();
    }

    public boolean shouldRollFailureThisTick() {
        return !isImmediateRuptureRequired() && (pressureFailureRoll || temperatureFailureRoll);
    }
}
