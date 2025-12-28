package gregtech.api.metatileentity.implementations.integratedfluid;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;
import java.util.function.UnaryOperator;

import net.minecraftforge.fluids.Fluid;

public final class IFNFluidThermalRegistry {

    @FunctionalInterface
    public interface PhaseFromPH {
        FluidThermalProperties.PhaseResult apply(double pBar, double specificEnthalpy);
    }

    @FunctionalInterface
    public interface SpecificEnthalpyFromPT {
        double apply(double pBar, double temperatureK);
    }

    public static final class FluidProperties {
        private final String fluidId;
        private final double criticalPressure;
        private final double criticalTemperature;
        private final double freezeTemperature;
        private final double specificHeatCapacity;
        private final DoubleBinaryOperator temperatureFromPH;
        private final PhaseFromPH phaseFromPH;
        private final DoubleBinaryOperator specificVolumeFromPH;
        private final DoubleUnaryOperator saturationTemperatureFromP;
        private final DoubleUnaryOperator hfFromP;
        private final DoubleUnaryOperator hgFromP;
        private final SpecificEnthalpyFromPT specificEnthalpyFromPT;

        private FluidProperties(Builder builder) {
            this.fluidId = builder.fluidId;
            this.criticalPressure = builder.criticalPressure;
            this.criticalTemperature = builder.criticalTemperature;
            this.freezeTemperature = builder.freezeTemperature;
            this.specificHeatCapacity = builder.specificHeatCapacity;
            this.temperatureFromPH = builder.temperatureFromPH;
            this.phaseFromPH = builder.phaseFromPH;
            this.specificVolumeFromPH = builder.specificVolumeFromPH;
            this.saturationTemperatureFromP = builder.saturationTemperatureFromP;
            this.hfFromP = builder.hfFromP;
            this.hgFromP = builder.hgFromP;
            this.specificEnthalpyFromPT = builder.specificEnthalpyFromPT;
        }

        public String getFluidId() {
            return fluidId;
        }

        public double getCriticalPressure() {
            return criticalPressure;
        }

        public double getCriticalTemperature() {
            return criticalTemperature;
        }

        public double getFreezeTemperature() {
            return freezeTemperature;
        }

        public double getSpecificHeatCapacity() {
            return specificHeatCapacity;
        }

        public double temperatureFromPH(double pBar, double specificEnthalpy) {
            return temperatureFromPH.applyAsDouble(pBar, specificEnthalpy);
        }

        public FluidThermalProperties.PhaseResult phaseFromPH(double pBar, double specificEnthalpy) {
            return phaseFromPH.apply(pBar, specificEnthalpy);
        }

        public double specificVolumeFromPH(double pBar, double specificEnthalpy) {
            return specificVolumeFromPH.applyAsDouble(pBar, specificEnthalpy);
        }

        public double saturationTemperatureFromP(double pBar) {
            return saturationTemperatureFromP.applyAsDouble(pBar);
        }

        public double hfFromP(double pBar) {
            return hfFromP.applyAsDouble(pBar);
        }

        public double hgFromP(double pBar) {
            return hgFromP.applyAsDouble(pBar);
        }

        public double specificEnthalpyFromPT(double pBar, double temperatureK) {
            return specificEnthalpyFromPT.apply(pBar, temperatureK);
        }
    }

    public static final class Builder {
        private final String fluidId;
        private boolean criticalPressureSet;
        private boolean criticalTemperatureSet;
        private boolean freezeTemperatureSet;
        private boolean specificHeatCapacitySet;
        private boolean temperatureFromPHSet;
        private boolean phaseFromPHSet;
        private boolean specificVolumeFromPHSet;
        private boolean saturationTemperatureFromPSet;
        private boolean hfFromPSet;
        private boolean hgFromPSet;
        private boolean specificEnthalpyFromPTSet;

        private double criticalPressure;
        private double criticalTemperature;
        private double freezeTemperature;
        private double specificHeatCapacity;
        private DoubleBinaryOperator temperatureFromPH;
        private PhaseFromPH phaseFromPH;
        private DoubleBinaryOperator specificVolumeFromPH;
        private DoubleUnaryOperator saturationTemperatureFromP;
        private DoubleUnaryOperator hfFromP;
        private DoubleUnaryOperator hgFromP;
        private SpecificEnthalpyFromPT specificEnthalpyFromPT;

        private Builder(String fluidId) {
            this.fluidId = fluidId;
        }

        public Builder setCriticalPressure(double criticalPressure) {
            this.criticalPressure = criticalPressure;
            this.criticalPressureSet = true;
            return this;
        }

        public Builder setCriticalTemperature(double criticalTemperature) {
            this.criticalTemperature = criticalTemperature;
            this.criticalTemperatureSet = true;
            return this;
        }

        public Builder setFreezeTemperature(double freezeTemperature) {
            this.freezeTemperature = freezeTemperature;
            this.freezeTemperatureSet = true;
            return this;
        }

        public Builder setSpecificHeatCapacity(double specificHeatCapacity) {
            this.specificHeatCapacity = specificHeatCapacity;
            this.specificHeatCapacitySet = true;
            return this;
        }

        public Builder setTemperatureFromPH(DoubleBinaryOperator temperatureFromPH) {
            this.temperatureFromPH = Objects.requireNonNull(temperatureFromPH, "temperatureFromPH");
            this.temperatureFromPHSet = true;
            return this;
        }

        public Builder setPhaseFromPH(PhaseFromPH phaseFromPH) {
            this.phaseFromPH = Objects.requireNonNull(phaseFromPH, "phaseFromPH");
            this.phaseFromPHSet = true;
            return this;
        }

        public Builder setSpecificVolumeFromPH(DoubleBinaryOperator specificVolumeFromPH) {
            this.specificVolumeFromPH = Objects.requireNonNull(specificVolumeFromPH, "specificVolumeFromPH");
            this.specificVolumeFromPHSet = true;
            return this;
        }

        public Builder setSaturationTemperatureFromP(DoubleUnaryOperator saturationTemperatureFromP) {
            this.saturationTemperatureFromP = Objects.requireNonNull(saturationTemperatureFromP, "saturationTemperatureFromP");
            this.saturationTemperatureFromPSet = true;
            return this;
        }

        public Builder setHfFromP(DoubleUnaryOperator hfFromP) {
            this.hfFromP = Objects.requireNonNull(hfFromP, "hfFromP");
            this.hfFromPSet = true;
            return this;
        }

        public Builder setHgFromP(DoubleUnaryOperator hgFromP) {
            this.hgFromP = Objects.requireNonNull(hgFromP, "hgFromP");
            this.hgFromPSet = true;
            return this;
        }

        public Builder setSpecificEnthalpyFromPT(SpecificEnthalpyFromPT specificEnthalpyFromPT) {
            this.specificEnthalpyFromPT = Objects.requireNonNull(specificEnthalpyFromPT, "specificEnthalpyFromPT");
            this.specificEnthalpyFromPTSet = true;
            return this;
        }

        private FluidProperties build() {
            if (!criticalPressureSet) {
                throw new IllegalStateException("Missing criticalPressure for " + fluidId);
            }
            if (!criticalTemperatureSet) {
                throw new IllegalStateException("Missing criticalTemperature for " + fluidId);
            }
            if (!freezeTemperatureSet) {
                throw new IllegalStateException("Missing freezeTemperature for " + fluidId);
            }
            if (!specificHeatCapacitySet) {
                throw new IllegalStateException("Missing specificHeatCapacity for " + fluidId);
            }
            if (!temperatureFromPHSet) {
                throw new IllegalStateException("Missing temperatureFromPH for " + fluidId);
            }
            if (!phaseFromPHSet) {
                throw new IllegalStateException("Missing phaseFromPH for " + fluidId);
            }
            if (!specificVolumeFromPHSet) {
                throw new IllegalStateException("Missing specificVolumeFromPH for " + fluidId);
            }
            if (!saturationTemperatureFromPSet) {
                throw new IllegalStateException("Missing saturationTemperatureFromP for " + fluidId);
            }
            if (!hfFromPSet) {
                throw new IllegalStateException("Missing hfFromP for " + fluidId);
            }
            if (!hgFromPSet) {
                throw new IllegalStateException("Missing hgFromP for " + fluidId);
            }
            if (!specificEnthalpyFromPTSet) {
                throw new IllegalStateException("Missing specificEnthalpyFromPT for " + fluidId);
            }
            return new FluidProperties(this);
        }
    }

    private static final Map<String, FluidProperties> REGISTRY = new HashMap<>();

    private IFNFluidThermalRegistry() {
    }

    public static void register(String fluidId, UnaryOperator<Builder> builder) {
        if (fluidId == null || fluidId.trim().isEmpty()) {
            throw new IllegalArgumentException("fluidId is required");
        }
        Builder b = new Builder(fluidId);
        Builder configured = builder.apply(b);
        FluidProperties properties = configured.build();
        REGISTRY.put(normalize(fluidId), properties);
    }

    public static void register(Fluid fluid, UnaryOperator<Builder> builder) {
        if (fluid == null) {
            throw new IllegalArgumentException("fluid is required");
        }
        register(fluid.getName(), builder);
    }

    public static boolean isRegistered(Fluid fluid) {
        if (fluid == null) {
            return false;
        }
        return REGISTRY.containsKey(normalize(fluid.getName()));
    }

    public static FluidProperties get(Fluid fluid) {
        if (fluid == null) {
            return null;
        }
        return REGISTRY.get(normalize(fluid.getName()));
    }

    public static FluidProperties require(Fluid fluid) {
        if (fluid == null) {
            return null;
        }
        FluidProperties properties = get(fluid);
        if (properties == null) {
            throw new IllegalStateException("IFN fluid not registered: " + fluid.getName());
        }
        return properties;
    }

    private static String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase();
    }

}
