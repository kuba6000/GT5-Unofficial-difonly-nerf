package gregtech.api.metatileentity.implementations.integratedfluid;

/**
 * Shared IFN heat pump planner mode.
 *
 * This mirrors the machine-facing HeatPumpMode without making API planners depend on the multiblock package.
 */
public enum IFNHeatPumpMode {
    TARGET_TEMPERATURE,
    TARGET_COP,
    TARGET_ENERGY
}
