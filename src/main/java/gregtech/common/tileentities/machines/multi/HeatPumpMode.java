package gregtech.common.tileentities.machines.multi;

import gregtech.api.metatileentity.implementations.integratedfluid.IFNHeatPumpMode;

/**
 * Operating modes for Heat Pump multiblock.
 * Each mode determines which parameter is fixed and which are calculated.
 */
public enum HeatPumpMode {
    /**
     * Target Temperature Mode - User sets desired output temperature.
     * COP and energy usage are calculated based on this temperature.
     */
    TARGET_TEMPERATURE(0, "Target Temperature"),

    /**
     * Target COP Mode - User sets desired Coefficient of Performance.
     * Output temperature and energy usage are calculated based on this COP.
     */
    TARGET_COP(1, "Target COP"),

    /**
     * Target Energy Mode - User sets desired energy consumption.
     * Output temperature and COP are calculated based on this energy limit.
     */
    TARGET_ENERGY(2, "Target Energy Usage");

    private final int id;
    private final String displayName;

    HeatPumpMode(int id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public int getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static HeatPumpMode fromId(int id) {
        for (HeatPumpMode mode : values()) {
            if (mode.id == id) {
                return mode;
            }
        }
        return TARGET_TEMPERATURE; // Default
    }

    public HeatPumpMode next() {
        int nextId = (this.id + 1) % values().length;
        return fromId(nextId);
    }

    public IFNHeatPumpMode toIFNMode() {
        switch (this) {
            case TARGET_TEMPERATURE:
                return IFNHeatPumpMode.TARGET_TEMPERATURE;
            case TARGET_COP:
                return IFNHeatPumpMode.TARGET_COP;
            case TARGET_ENERGY:
                return IFNHeatPumpMode.TARGET_ENERGY;
            default:
                return IFNHeatPumpMode.TARGET_TEMPERATURE;
        }
    }

    public boolean isConfigurationValid(float targetCOP, int targetEnergyPerTick) {
        switch (this) {
            case TARGET_TEMPERATURE:
                return true;
            case TARGET_COP:
                return !(targetCOP <= 0 || targetCOP < 1.1f);
            case TARGET_ENERGY:
                return targetEnergyPerTick > 0;
            default:
                return false;
        }
    }
}
