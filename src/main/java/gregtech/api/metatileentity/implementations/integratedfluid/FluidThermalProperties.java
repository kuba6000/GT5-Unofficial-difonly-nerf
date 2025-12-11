package gregtech.api.metatileentity.implementations.integratedfluid;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

/**
 * Manages thermal properties of fluids in the integrated fluid network system.
 * This class provides centralized access to heat capacity and energy calculations.
 *
 * Currently uses a hardcoded default value, but is designed to be extended
 * for fluid-specific properties in the future.
 */
public class FluidThermalProperties {

    /**
     * Default specific heat capacity in EU/(mB·K) (EU per millibucket per Kelvin).
     *
     * This is currently hardcoded but designed to be easily replaceable with
     * fluid-specific values in the future.
     *
     * Default value: 420.0 EU/(mB·K)
     * High value to make fluids retain temperature longer and require more energy to heat/cool.
     * This creates a more realistic thermal system where fluids act as thermal buffers.
     */
    public static final float DEFAULT_SPECIFIC_HEAT_CAPACITY = 420.0f;

    /**
     * Gets the specific heat capacity for a given fluid.
     *
     * @param fluid The fluid to query
     * @return Specific heat capacity in EU/(mB·K)
     */
    public static float getSpecificHeatCapacity(Fluid fluid) {
        if (fluid == null) {
            return DEFAULT_SPECIFIC_HEAT_CAPACITY;
        }

        // TODO: Implement fluid-specific heat capacities
        // Future enhancement: Check fluid against a registry/map of specific values
        // Example:
        // if (fluidHeatCapacityMap.containsKey(fluid)) {
        //     return fluidHeatCapacityMap.get(fluid);
        // }

        return DEFAULT_SPECIFIC_HEAT_CAPACITY;
    }

    /**
     * Gets the specific heat capacity for a given fluid stack.
     *
     * @param fluidStack The fluid stack to query
     * @return Specific heat capacity in EU/(mB·K)
     */
    public static float getSpecificHeatCapacity(FluidStack fluidStack) {
        if (fluidStack == null || fluidStack.getFluid() == null) {
            return DEFAULT_SPECIFIC_HEAT_CAPACITY;
        }
        return getSpecificHeatCapacity(fluidStack.getFluid());
    }

    /**
     * Calculates the total heat capacity for a given amount of fluid.
     *
     * Heat Capacity = Specific Heat Capacity × Amount
     *
     * @param fluidStack The fluid stack
     * @return Total heat capacity in EU/K (EU per Kelvin)
     */
    public static float getTotalHeatCapacity(FluidStack fluidStack) {
        if (fluidStack == null || fluidStack.amount <= 0) {
            return 0.0f;
        }
        return getSpecificHeatCapacity(fluidStack) * fluidStack.amount;
    }

    /**
     * Calculates the ideal energy required to change the temperature of a fluid.
     * This is the theoretical minimum energy without efficiency losses.
     *
     * Energy = Heat Capacity × Temperature Change
     * Q = m × c × ΔT
     *
     * @param fluidStack       The fluid stack
     * @param temperatureDelta The change in temperature (K)
     * @return Energy required in EU
     */
    public static long calculateIdealEnergyForTemperatureChange(FluidStack fluidStack, float temperatureDelta) {
        if (fluidStack == null || fluidStack.amount <= 0) {
            return 0;
        }
        float energy = getTotalHeatCapacity(fluidStack) * temperatureDelta;
        return (long) Math.ceil(energy);
    }

    /**
     * Calculates the Coefficient of Performance (COP) for a heat pump.
     * COP represents the efficiency: how much heat is moved per unit of energy consumed.
     *
     * Based on ideal Carnot efficiency:
     * COP_heating = T_hot / (T_hot - T_cold)
     *
     * This is the theoretical maximum efficiency. Real efficiency penalties can be
     * applied separately based on temperature differences or other factors.
     *
     * @param coldTemperature Temperature of cold reservoir (K) - typically ambient or input
     * @param hotTemperature  Temperature of hot reservoir (K) - typically output
     * @return COP (dimensionless, theoretical Carnot efficiency)
     */
    public static float calculateHeatPumpCOP(float coldTemperature, float hotTemperature) {
        if (coldTemperature <= 0 || hotTemperature <= coldTemperature) {
            return 1.0f; // Minimum COP
        }

        // Carnot COP for heating: T_hot / (T_hot - T_cold)
        float carnotCOP = hotTemperature / (hotTemperature - coldTemperature);

        // Clamp COP to reasonable range (1.0 to 50.0)
        // COP = 1.0 means 100% of input energy goes to heating (like a resistive heater)
        // COP > 1.0 means the heat pump is moving more heat than energy consumed
        // Higher upper limit since we're using ideal Carnot efficiency
        return Math.max(1.0f, Math.min(carnotCOP, 50.0f));
    }

    /**
     * Calculates the actual energy required for a heat pump operation.
     * This accounts for efficiency losses via COP.
     *
     * Actual Energy = Ideal Energy / COP
     *
     * @param fluidStack       The fluid stack being heated
     * @param temperatureDelta The temperature increase (K)
     * @param coldTemperature  Temperature of cold reservoir (K)
     * @param hotTemperature   Temperature of hot reservoir (K)
     * @return Actual energy required in EU
     */
    public static long calculateHeatPumpEnergy(FluidStack fluidStack, float temperatureDelta,
                                                float coldTemperature, float hotTemperature) {
        long idealEnergy = calculateIdealEnergyForTemperatureChange(fluidStack, temperatureDelta);
        float cop = calculateHeatPumpCOP(coldTemperature, hotTemperature);
        return (long) Math.ceil(idealEnergy / cop);
    }

    /**
     * Calculates the temperature change resulting from adding energy to a fluid.
     *
     * Temperature Change = Energy / Heat Capacity
     * ΔT = Q / (m × c)
     *
     * @param fluidStack The fluid stack
     * @param energy     The energy added in EU (can be negative for cooling)
     * @return Temperature change in Kelvin
     */
    public static float calculateTemperatureChangeFromEnergy(FluidStack fluidStack, long energy) {
        if (fluidStack == null || fluidStack.amount <= 0) {
            return 0.0f;
        }
        float heatCapacity = getTotalHeatCapacity(fluidStack);
        if (heatCapacity <= 0) {
            return 0.0f;
        }
        return energy / heatCapacity;
    }

    /**
     * Calculates the weighted average temperature when mixing two fluid volumes.
     *
     * This is used when fluids at different temperatures are combined.
     *
     * T_final = (T1 × m1 × c1 + T2 × m2 × c2) / (m1 × c1 + m2 × c2)
     *
     * For fluids with the same specific heat:
     * T_final = (T1 × m1 + T2 × m2) / (m1 + m2)
     *
     * @param fluid1       First fluid stack
     * @param temperature1 Temperature of first fluid (K)
     * @param fluid2       Second fluid stack
     * @param temperature2 Temperature of second fluid (K)
     * @return Weighted average temperature in Kelvin
     */
    public static float calculateMixedTemperature(FluidStack fluid1, float temperature1,
                                                   FluidStack fluid2, float temperature2) {
        if (fluid1 == null || fluid1.amount <= 0) {
            return temperature2;
        }
        if (fluid2 == null || fluid2.amount <= 0) {
            return temperature1;
        }

        float heatCapacity1 = getTotalHeatCapacity(fluid1);
        float heatCapacity2 = getTotalHeatCapacity(fluid2);

        if (heatCapacity1 + heatCapacity2 <= 0) {
            // Fallback to simple average if no heat capacity
            return (temperature1 + temperature2) / 2.0f;
        }

        return (temperature1 * heatCapacity1 + temperature2 * heatCapacity2)
            / (heatCapacity1 + heatCapacity2);
    }
}

