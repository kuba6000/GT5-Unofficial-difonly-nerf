package gregtech.api.metatileentity.implementations.integratedfluid.fluid;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import net.minecraftforge.fluids.Fluid;

public final class IFNFluidRegistry {

    private static final Map<String, IFNFluidDefinition> DEFINITIONS = new LinkedHashMap<>();
    private static boolean initialized;

    private IFNFluidRegistry() {}

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        register(waterLike("water", "Water"));
        register(waterLike("steam", "Steam"));
        register(simpleCryogen("hydrogen", "Hydrogen", 12.964d, 33.19d, 13.99d, 20.27d, 9.7d, 14.3d, 445.0d));
        register(simpleCryogen("helium", "Helium", 2.276d, 5.195d, 0.95d, 4.22d, 5.2d, 20.8d, 85.0d));
        register(simpleCryogen("oxygen", "Oxygen", 50.43d, 154.58d, 54.36d, 90.19d, 1.7d, 1.0d, 213.0d));
        register(simpleCryogen("nitrogen", "Nitrogen", 33.98d, 126.19d, 63.15d, 77.36d, 2.0d, 1.1d, 199.0d));
    }

    public static boolean isSupported(Fluid fluid) {
        return fluid != null && get(fluid.getName()) != null;
    }

    public static IFNFluidDefinition get(String fluidId) {
        init();
        return DEFINITIONS.get(normalize(fluidId));
    }

    public static IFNFluidDefinition require(String fluidId) {
        IFNFluidDefinition definition = get(fluidId);
        if (definition == null) {
            throw new IllegalStateException("IFN fluid not registered: " + fluidId);
        }
        return definition;
    }

    public static Collection<IFNFluidDefinition> definitions() {
        init();
        return Collections.unmodifiableCollection(DEFINITIONS.values());
    }

    private static void register(IFNFluidDefinition definition) {
        DEFINITIONS.put(normalize(definition.fluidId()), definition);
        definition.registerThermalProperties();
    }

    private static IFNFluidDefinition waterLike(String fluidId, String displayName) {
        return IFNFluidDefinition.builder(fluidId, "water")
            .displayName(displayName)
            .criticalPoint(220.64d, 647.096d)
            .freezeTemperature(273.15d)
            .normalBoilingTemperature(373.15d)
            .saturationSlope(27.0d)
            .specificHeat(4.18d, 2.08d)
            .latentHeat(2257.0d)
            .specificVolumes(1.0d, 1700.0d)
            .build();
    }

    private static IFNFluidDefinition simpleCryogen(
        String fluidId,
        String displayName,
        double criticalPressureBar,
        double criticalTemperatureKelvin,
        double freezeTemperatureKelvin,
        double normalBoilingTemperatureKelvin,
        double liquidSpecificHeat,
        double vaporSpecificHeat,
        double latentHeat) {
        return IFNFluidDefinition.builder(fluidId, fluidId)
            .displayName(displayName)
            .criticalPoint(criticalPressureBar, criticalTemperatureKelvin)
            .freezeTemperature(freezeTemperatureKelvin)
            .normalBoilingTemperature(normalBoilingTemperatureKelvin)
            .saturationSlope(Math.max(0.1d, (criticalTemperatureKelvin - normalBoilingTemperatureKelvin)
                / Math.log(criticalPressureBar)))
            .specificHeat(liquidSpecificHeat, vaporSpecificHeat)
            .latentHeat(latentHeat)
            .specificVolumes(1.0d, 24.0d)
            .build();
    }

    private static String normalize(String fluidId) {
        return fluidId == null ? "" : fluidId.trim().toLowerCase(Locale.ROOT);
    }
}
