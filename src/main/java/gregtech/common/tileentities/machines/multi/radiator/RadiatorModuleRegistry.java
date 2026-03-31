package gregtech.common.tileentities.machines.multi.radiator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.common.blocks.BlockRadiatorModule;

public final class RadiatorModuleRegistry {

    public enum ModuleType {
        CONDUCTION("conduction", " Radiator Conduction Module", OrePrefixes.frameGt),
        HEAT_EXCHANGE("heat_exchange", " Radiator Heat Exchange Module", OrePrefixes.sheetmetal);

        private final String blockNamePart;
        private final String displaySuffix;
        private final OrePrefixes texturePrefix;

        ModuleType(String blockNamePart, String displaySuffix, OrePrefixes texturePrefix) {
            this.blockNamePart = blockNamePart;
            this.displaySuffix = displaySuffix;
            this.texturePrefix = texturePrefix;
        }

        public String getDisplaySuffix() {
            return displaySuffix;
        }

        public String getBlockNamePart() {
            return blockNamePart;
        }

        public OrePrefixes getTexturePrefix() {
            return texturePrefix;
        }
    }

    public static final class ModuleData {
        public final ModuleType type;
        public final Materials material;
        public final double conductiveCoefficient;
        public final double radiativeCoefficient;

        private ModuleData(ModuleType type, Materials material, double conductiveCoefficient, double radiativeCoefficient) {
            this.type = type;
            this.material = material;
            this.conductiveCoefficient = conductiveCoefficient;
            this.radiativeCoefficient = radiativeCoefficient;
        }
    }

    private static final class ModuleEntry {
        private final Block block;
        private final int meta;

        private ModuleEntry(Block block, int meta) {
            this.block = block;
            this.meta = meta;
        }
    }

    private static final int MATERIALS_PER_BLOCK = 16;
    private static final EnumMap<ModuleType, List<BlockRadiatorModule>> REGISTERED_BLOCKS = new EnumMap<>(ModuleType.class);
    private static final EnumMap<ModuleType, Map<Materials, ModuleEntry>> MATERIAL_INDEX = new EnumMap<>(ModuleType.class);
    private static final Map<Block, BlockRadiatorModule> BLOCK_INDEX = new IdentityHashMap<>();
    private static boolean initialized;

    static {
        for (ModuleType type : ModuleType.values()) {
            REGISTERED_BLOCKS.put(type, new ArrayList<>());
            MATERIAL_INDEX.put(type, new HashMap<>());
        }
    }

    private RadiatorModuleRegistry() {}

    public static synchronized void register() {
        if (initialized) {
            return;
        }

        List<Materials> materials = Arrays.stream(GregTechAPI.sGeneratedMaterials)
            .filter(Objects::nonNull)
            .filter(Materials::hasMetalItems)
            .filter(material -> material.generatesPrefix(OrePrefixes.plate))
            .collect(Collectors.toList());

        for (ModuleType type : ModuleType.values()) {
            int blockIndex = 0;
            for (int start = 0; start < materials.size(); start += MATERIALS_PER_BLOCK) {
                int end = Math.min(start + MATERIALS_PER_BLOCK, materials.size());
                Materials[] page = materials.subList(start, end)
                    .toArray(new Materials[0]);

                BlockRadiatorModule block = new BlockRadiatorModule(
                    "gt.blockradiator." + type.getBlockNamePart() + "." + blockIndex,
                    type,
                    page
                );
                REGISTERED_BLOCKS.get(type)
                    .add(block);
                BLOCK_INDEX.put(block, block);

                for (int meta = 0; meta < page.length; meta++) {
                    MATERIAL_INDEX.get(type)
                        .put(page[meta], new ModuleEntry(block, meta));
                }
                blockIndex++;
            }
        }

        Materials defaultMaterial = MATERIAL_INDEX.get(ModuleType.CONDUCTION)
            .containsKey(Materials.Steel) ? Materials.Steel : materials.get(0);
        ItemList.Radiator_Conduction_Module.set(getItemStack(ModuleType.CONDUCTION, defaultMaterial, 1));
        ItemList.Radiator_Heat_Exchange_Module.set(getItemStack(ModuleType.HEAT_EXCHANGE, defaultMaterial, 1));
        initialized = true;
    }

    public static ModuleData getModuleData(Block block, int meta) {
        if (block instanceof BlockRadiatorModule moduleBlock) {
            return getModuleData(moduleBlock, meta);
        }
        if (block == GregTechAPI.sBlockCasings11) {
            if (meta == RadiatorLoopAnalyzer.CONDUCTION_META) {
                return legacyModuleData(ModuleType.CONDUCTION);
            }
            if (meta == RadiatorLoopAnalyzer.HEAT_EXCHANGE_META) {
                return legacyModuleData(ModuleType.HEAT_EXCHANGE);
            }
        }
        return null;
    }

    public static ModuleData getModuleData(BlockRadiatorModule block, int meta) {
        Materials material = block.getMaterial(meta);
        if (material == null) {
            return null;
        }
        return createModuleData(block.getModuleType(), material);
    }

    public static boolean isModule(Block block, int meta, ModuleType type) {
        ModuleData data = getModuleData(block, meta);
        return data != null && data.type == type;
    }

    public static ItemStack getItemStack(ModuleType type, Materials material, int amount) {
        ModuleEntry entry = MATERIAL_INDEX.get(type)
            .get(material);
        if (entry == null) {
            return null;
        }
        return new ItemStack(entry.block, amount, entry.meta);
    }

    public static Block getRepresentativeBlock(ModuleType type, Materials material) {
        ModuleEntry entry = MATERIAL_INDEX.get(type)
            .get(material);
        return entry == null ? null : entry.block;
    }

    public static int getRepresentativeMeta(ModuleType type, Materials material) {
        ModuleEntry entry = MATERIAL_INDEX.get(type)
            .get(material);
        return entry == null ? 0 : entry.meta;
    }

    private static ModuleData legacyModuleData(ModuleType type) {
        return switch (type) {
            case CONDUCTION -> new ModuleData(type, Materials.Steel, 28.0d, 0.0d);
            case HEAT_EXCHANGE -> new ModuleData(type, Materials.Steel, 12.0d, 6.0e-7d);
        };
    }

    private static ModuleData createModuleData(ModuleType type, Materials material) {
        double thermalTier = computeThermalTier(material);
        return switch (type) {
            case CONDUCTION -> new ModuleData(type, material, 6.0d + thermalTier * 2.5d, 0.0d);
            case HEAT_EXCHANGE -> new ModuleData(
                type,
                material,
                4.0d + thermalTier * 1.35d,
                1.0e-7d * (1.0d + thermalTier * 0.25d + effectiveTemperature(material) / 1800.0d)
            );
        };
    }

    private static double computeThermalTier(Materials material) {
        double temperatureTier = Math.sqrt(effectiveTemperature(material)) / 20.0d;
        double toolTier = Math.max(0.0d, material.mToolQuality) * 1.5d;
        double heatTier = Math.max(0.0d, material.mHeatDamage) * 2.0d;
        return clamp(1.0d + temperatureTier + toolTier + heatTier, 1.0d, 24.0d);
    }

    private static double effectiveTemperature(Materials material) {
        return Math.max(300.0d, Math.max(material.mBlastFurnaceTemp, material.mMeltingPoint));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
