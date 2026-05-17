package gregtech.api.metatileentity.implementations.integratedfluid.thermal;

import net.minecraft.world.World;

import gregtech.api.metatileentity.implementations.integratedfluid.IFNAmbientTemperature;

public final class IFNAmbientContext {

    private final float ambientTemperatureKelvin;

    private IFNAmbientContext(float ambientTemperatureKelvin) {
        this.ambientTemperatureKelvin = ambientTemperatureKelvin;
    }

    public static IFNAmbientContext fixed(float ambientTemperatureKelvin) {
        return new IFNAmbientContext(ambientTemperatureKelvin);
    }

    public static IFNAmbientContext forWorld(World world) {
        return fixed(IFNAmbientTemperature.getAmbientTemperature(world));
    }

    public float ambientTemperatureKelvin() {
        return ambientTemperatureKelvin;
    }
}
