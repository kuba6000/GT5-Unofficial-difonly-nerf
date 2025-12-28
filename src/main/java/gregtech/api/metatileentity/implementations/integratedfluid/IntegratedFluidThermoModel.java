package gregtech.api.metatileentity.implementations.integratedfluid;

import net.minecraftforge.fluids.Fluid;

/**
 * Minimal thermodynamic model for Integrated Fluid Network.
 * Uses a simple single-phase model with constant heat capacity.
 * Gas specific volume scales with ideal gas relationship.
 */
public final class IntegratedFluidThermoModel {

    public enum Phase {
        LIQUID,
        GAS,
        TWO_PHASE,
        SUPERCRITICAL
    }

    public static final float BASE_TEMPERATURE = 300.0f;
    public static final float BASE_PRESSURE = 1.0f;

    private IntegratedFluidThermoModel() {
    }

    public static double specificEnthalpyFromTemperature(Fluid fluid, float temperature) {
        return FluidThermalProperties.getSpecificEnthalpyFromPT(fluid, BASE_PRESSURE, temperature);
    }

    public static float temperatureFromPressureAndSpecificEnthalpy(Fluid fluid, float pressure, double specificEnthalpy) {
        return (float) FluidThermalProperties.getTemperatureFromPH(fluid, pressure, specificEnthalpy);
    }

    public static Phase phaseFromPressureAndSpecificEnthalpy(Fluid fluid, float pressure, double specificEnthalpy) {
        FluidThermalProperties.PhaseResult result = FluidThermalProperties.getPhaseFromPH(fluid, pressure, specificEnthalpy);
        switch (result.phase) {
            case VAPOR:
                return Phase.GAS;
            case TWO_PHASE:
                return Phase.TWO_PHASE;
            case SUPERCRITICAL:
                return Phase.SUPERCRITICAL;
            case LIQUID:
            default:
                return Phase.LIQUID;
        }
    }

    public static double specificVolumeFromPressureAndSpecificEnthalpy(Fluid fluid, float pressure, double specificEnthalpy) {
        return FluidThermalProperties.getSpecificVolumeFromPH(fluid, pressure, specificEnthalpy);
    }

    public static double saturatedLiquidEnthalpy(Fluid fluid, float pressure) {
        if (FluidThermalProperties.getCriticalPressure(fluid) > 0.0) {
            return FluidThermalProperties.getHfFromP(fluid, pressure);
        }
        return specificEnthalpyFromTemperature(fluid, BASE_TEMPERATURE);
    }

    public static double saturatedVaporEnthalpy(Fluid fluid, float pressure) {
        if (FluidThermalProperties.getCriticalPressure(fluid) > 0.0) {
            return FluidThermalProperties.getHgFromP(fluid, pressure);
        }
        return specificEnthalpyFromTemperature(fluid, BASE_TEMPERATURE);
    }
}
