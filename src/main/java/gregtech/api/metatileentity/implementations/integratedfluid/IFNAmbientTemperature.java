package gregtech.api.metatileentity.implementations.integratedfluid;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.world.World;

public final class IFNAmbientTemperature {

    private static final Map<Integer, Float> DIMENSION_TEMPERATURES = new HashMap<>();

    private IFNAmbientTemperature() {
    }

    static {
        registerDimensionTemperature(0, 300.0f);
        registerDimensionTemperature(-1, 400.0f);
        registerDimensionTemperature(1, 100.0f);
    }

    public static void registerDimensionTemperature(int dimensionId, float temperatureK) {
        DIMENSION_TEMPERATURES.put(dimensionId, temperatureK);
    }

    public static float getAmbientTemperature(World world) {
        if (world == null || world.provider == null) {
            return IntegratedFluidNetwork.AMBIENT_TEMPERATURE;
        }
        Integer dimId = world.provider.dimensionId;
        Float temperature = DIMENSION_TEMPERATURES.get(dimId);
        return temperature != null ? temperature : IntegratedFluidNetwork.AMBIENT_TEMPERATURE;
    }
}
