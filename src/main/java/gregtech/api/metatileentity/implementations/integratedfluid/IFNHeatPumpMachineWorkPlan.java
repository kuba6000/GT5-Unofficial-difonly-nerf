package gregtech.api.metatileentity.implementations.integratedfluid;

public final class IFNHeatPumpMachineWorkPlan {

    private static final int PASSTHROUGH_PROGRESS_TIME = 5;
    private static final int ACTIVE_PROGRESS_TIME = 20;
    private static final int ACTIVE_EFFICIENCY = 10000;

    private final int maxProgressTime;
    private final int euT;
    private final boolean setEfficiency;
    private final int efficiency;
    private final int energyCostPerTick;

    private IFNHeatPumpMachineWorkPlan(int maxProgressTime, int euT, boolean setEfficiency, int efficiency,
        int energyCostPerTick) {
        this.maxProgressTime = maxProgressTime;
        this.euT = euT;
        this.setEfficiency = setEfficiency;
        this.efficiency = efficiency;
        this.energyCostPerTick = energyCostPerTick;
    }

    public static IFNHeatPumpMachineWorkPlan of(boolean passthrough, boolean targetEnergyMode, long energyCostEu,
        int targetEnergyPerTick) {
        int energyCostPerTick = toEnergyCostPerTick(energyCostEu);
        if (passthrough) {
            return new IFNHeatPumpMachineWorkPlan(
                PASSTHROUGH_PROGRESS_TIME,
                0,
                false,
                0,
                energyCostPerTick);
        }
        int euT = targetEnergyMode ? -targetEnergyPerTick : -energyCostPerTick;
        return new IFNHeatPumpMachineWorkPlan(
            ACTIVE_PROGRESS_TIME,
            euT,
            true,
            ACTIVE_EFFICIENCY,
            energyCostPerTick);
    }

    private static int toEnergyCostPerTick(long energyCostEu) {
        return (int) ((energyCostEu + 19L) / 20L);
    }

    public int getMaxProgressTime() {
        return maxProgressTime;
    }

    public int getEuT() {
        return euT;
    }

    public boolean shouldSetEfficiency() {
        return setEfficiency;
    }

    public int getEfficiency() {
        return efficiency;
    }

    public int getEnergyCostPerTick() {
        return energyCostPerTick;
    }
}
