package gregtech.common.tileentities.machines.multi.radiator;

public final class RadiatorPowerPolicy {

    private RadiatorPowerPolicy() {}

    public static boolean requiresEnergyInput() {
        return false;
    }

    public static int computeEUt(long idealEnergyMovedEu, int processTicks) {
        return 0;
    }
}
