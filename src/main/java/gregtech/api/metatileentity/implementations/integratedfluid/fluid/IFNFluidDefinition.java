package gregtech.api.metatileentity.implementations.integratedfluid.fluid;

import gregtech.api.metatileentity.implementations.integratedfluid.FluidThermalProperties;
import gregtech.api.metatileentity.implementations.integratedfluid.IFNFluidThermalRegistry;

public final class IFNFluidDefinition {

    private static final double REFERENCE_TEMPERATURE_K = 273.15d;
    private static final double MIN_PRESSURE_BAR = 0.000001d;

    private final String fluidId;
    private final String substanceId;
    private final String displayName;
    private final double criticalPressureBar;
    private final double criticalTemperatureKelvin;
    private final double freezeTemperatureKelvin;
    private final double normalBoilingTemperatureKelvin;
    private final double saturationSlopeKelvinPerLnBar;
    private final double liquidSpecificHeat;
    private final double vaporSpecificHeat;
    private final double latentHeat;
    private final double liquidSpecificVolume;
    private final double vaporSpecificVolumeAtStp;

    private IFNFluidDefinition(Builder builder) {
        this.fluidId = requireId(builder.fluidId, "fluidId");
        this.substanceId = requireId(builder.substanceId, "substanceId");
        this.displayName = builder.displayName == null ? builder.fluidId : builder.displayName;
        this.criticalPressureBar = positive(builder.criticalPressureBar, "criticalPressureBar");
        this.criticalTemperatureKelvin = positive(builder.criticalTemperatureKelvin, "criticalTemperatureKelvin");
        this.freezeTemperatureKelvin = positive(builder.freezeTemperatureKelvin, "freezeTemperatureKelvin");
        this.normalBoilingTemperatureKelvin =
            positive(builder.normalBoilingTemperatureKelvin, "normalBoilingTemperatureKelvin");
        this.saturationSlopeKelvinPerLnBar =
            positive(builder.saturationSlopeKelvinPerLnBar, "saturationSlopeKelvinPerLnBar");
        this.liquidSpecificHeat = positive(builder.liquidSpecificHeat, "liquidSpecificHeat");
        this.vaporSpecificHeat = positive(builder.vaporSpecificHeat, "vaporSpecificHeat");
        this.latentHeat = positive(builder.latentHeat, "latentHeat");
        this.liquidSpecificVolume = positive(builder.liquidSpecificVolume, "liquidSpecificVolume");
        this.vaporSpecificVolumeAtStp = positive(builder.vaporSpecificVolumeAtStp, "vaporSpecificVolumeAtStp");
    }

    public String fluidId() {
        return fluidId;
    }

    public String substanceId() {
        return substanceId;
    }

    public String displayName() {
        return displayName;
    }

    public void registerThermalProperties() {
        IFNFluidThermalRegistry.register(fluidId, builder -> builder
            .setCriticalPressure(criticalPressureBar)
            .setCriticalTemperature(criticalTemperatureKelvin)
            .setFreezeTemperature(freezeTemperatureKelvin)
            .setSpecificHeatCapacity(liquidSpecificHeat)
            .setTemperatureFromPH(this::temperatureFromPH)
            .setPhaseFromPH(this::phaseFromPH)
            .setSpecificVolumeFromPH(this::specificVolumeFromPH)
            .setSaturationTemperatureFromP(this::saturationTemperature)
            .setHfFromP(this::saturatedLiquidEnthalpy)
            .setHgFromP(this::saturatedVaporEnthalpy)
            .setSpecificEnthalpyFromPT(this::specificEnthalpyFromPT));
    }

    public static Builder builder(String fluidId, String substanceId) {
        return new Builder(fluidId, substanceId);
    }

    private double temperatureFromPH(double pressureBar, double specificEnthalpy) {
        double hf = saturatedLiquidEnthalpy(pressureBar);
        double hg = saturatedVaporEnthalpy(pressureBar);
        if (specificEnthalpy < hf) {
            return REFERENCE_TEMPERATURE_K + specificEnthalpy / liquidSpecificHeat;
        }
        if (specificEnthalpy <= hg) {
            return saturationTemperature(pressureBar);
        }
        return saturationTemperature(pressureBar) + (specificEnthalpy - hg) / vaporSpecificHeat;
    }

    private FluidThermalProperties.PhaseResult phaseFromPH(double pressureBar, double specificEnthalpy) {
        double temperature = temperatureFromPH(pressureBar, specificEnthalpy);
        if (pressureBar >= criticalPressureBar && temperature >= criticalTemperatureKelvin) {
            return new FluidThermalProperties.PhaseResult(FluidThermalProperties.Phase.SUPERCRITICAL, 0.0d);
        }

        double hf = saturatedLiquidEnthalpy(pressureBar);
        double hg = saturatedVaporEnthalpy(pressureBar);
        if (specificEnthalpy < hf) {
            return new FluidThermalProperties.PhaseResult(FluidThermalProperties.Phase.LIQUID, 0.0d);
        }
        if (specificEnthalpy <= hg) {
            double quality = (specificEnthalpy - hf) / (hg - hf);
            return new FluidThermalProperties.PhaseResult(FluidThermalProperties.Phase.TWO_PHASE, clamp(quality, 0.0d, 1.0d));
        }
        return new FluidThermalProperties.PhaseResult(FluidThermalProperties.Phase.VAPOR, 1.0d);
    }

    private double specificVolumeFromPH(double pressureBar, double specificEnthalpy) {
        FluidThermalProperties.PhaseResult phase = phaseFromPH(pressureBar, specificEnthalpy);
        if (phase.phase == FluidThermalProperties.Phase.LIQUID) {
            return liquidSpecificVolume;
        }
        double gasVolume = vaporSpecificVolume(pressureBar, temperatureFromPH(pressureBar, specificEnthalpy));
        if (phase.phase == FluidThermalProperties.Phase.TWO_PHASE) {
            return liquidSpecificVolume * (1.0d - phase.quality) + gasVolume * phase.quality;
        }
        return gasVolume;
    }

    private double saturationTemperature(double pressureBar) {
        double normalizedPressure = clamp(pressureBar, MIN_PRESSURE_BAR, criticalPressureBar);
        double temperature =
            normalBoilingTemperatureKelvin + saturationSlopeKelvinPerLnBar * Math.log(normalizedPressure);
        return clamp(temperature, freezeTemperatureKelvin + 0.001d, criticalTemperatureKelvin - 0.001d);
    }

    private double saturatedLiquidEnthalpy(double pressureBar) {
        return liquidSpecificHeat * (saturationTemperature(pressureBar) - REFERENCE_TEMPERATURE_K);
    }

    private double saturatedVaporEnthalpy(double pressureBar) {
        return saturatedLiquidEnthalpy(pressureBar) + latentHeat;
    }

    private double specificEnthalpyFromPT(double pressureBar, double temperatureKelvin) {
        double saturationTemperature = saturationTemperature(pressureBar);
        if (temperatureKelvin <= saturationTemperature) {
            return liquidSpecificHeat * (temperatureKelvin - REFERENCE_TEMPERATURE_K);
        }
        return saturatedVaporEnthalpy(pressureBar) + vaporSpecificHeat * (temperatureKelvin - saturationTemperature);
    }

    private double vaporSpecificVolume(double pressureBar, double temperatureKelvin) {
        double pressure = Math.max(MIN_PRESSURE_BAR, pressureBar);
        return vaporSpecificVolumeAtStp * temperatureKelvin / REFERENCE_TEMPERATURE_K / pressure;
    }

    private static String requireId(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim().toLowerCase();
    }

    private static double positive(double value, String fieldName) {
        if (!(value > 0.0d) || Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return value;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static final class Builder {

        private final String fluidId;
        private final String substanceId;
        private String displayName;
        private double criticalPressureBar;
        private double criticalTemperatureKelvin;
        private double freezeTemperatureKelvin;
        private double normalBoilingTemperatureKelvin;
        private double saturationSlopeKelvinPerLnBar;
        private double liquidSpecificHeat;
        private double vaporSpecificHeat;
        private double latentHeat;
        private double liquidSpecificVolume = 1.0d;
        private double vaporSpecificVolumeAtStp = 24.0d;

        private Builder(String fluidId, String substanceId) {
            this.fluidId = fluidId;
            this.substanceId = substanceId;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder criticalPoint(double pressureBar, double temperatureKelvin) {
            this.criticalPressureBar = pressureBar;
            this.criticalTemperatureKelvin = temperatureKelvin;
            return this;
        }

        public Builder freezeTemperature(double temperatureKelvin) {
            this.freezeTemperatureKelvin = temperatureKelvin;
            return this;
        }

        public Builder normalBoilingTemperature(double temperatureKelvin) {
            this.normalBoilingTemperatureKelvin = temperatureKelvin;
            return this;
        }

        public Builder saturationSlope(double kelvinPerLnBar) {
            this.saturationSlopeKelvinPerLnBar = kelvinPerLnBar;
            return this;
        }

        public Builder specificHeat(double liquid, double vapor) {
            this.liquidSpecificHeat = liquid;
            this.vaporSpecificHeat = vapor;
            return this;
        }

        public Builder latentHeat(double latentHeat) {
            this.latentHeat = latentHeat;
            return this;
        }

        public Builder specificVolumes(double liquid, double vaporAtStp) {
            this.liquidSpecificVolume = liquid;
            this.vaporSpecificVolumeAtStp = vaporAtStp;
            return this;
        }

        public IFNFluidDefinition build() {
            return new IFNFluidDefinition(this);
        }
    }
}
