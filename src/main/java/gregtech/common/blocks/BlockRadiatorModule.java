package gregtech.common.blocks;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;
import gregtech.api.interfaces.IBlockWithTextures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.render.TextureFactory;
import gregtech.common.render.GTRendererBlock;
import gregtech.common.tileentities.machines.multi.radiator.RadiatorModuleRegistry;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;

public class BlockRadiatorModule extends BlockStorage implements IBlockWithTextures {

    private final RadiatorModuleRegistry.ModuleType moduleType;
    private final Materials[] materials;
    private final Int2ObjectLinkedOpenHashMap<ITexture[][]> textureCache = new Int2ObjectLinkedOpenHashMap<>();

    public BlockRadiatorModule(String unlocalizedName, RadiatorModuleRegistry.ModuleType moduleType,
        Materials[] materials) {
        super(ItemRadiatorModule.class, unlocalizedName, Material.iron);
        this.moduleType = moduleType;
        this.materials = materials;
        GregTechAPI.registerMachineBlock(this, -1);
    }

    public RadiatorModuleRegistry.ModuleType getModuleType() {
        return moduleType;
    }

    public @Nullable Materials getMaterial(int meta) {
        if (meta < 0 || meta >= materials.length) {
            return null;
        }
        return materials[meta];
    }

    @Override
    public String getLocalizedName(int meta) {
        Materials material = getMaterial(meta);
        if (material == null) {
            return super.getLocalizedName(meta);
        }
        return material.getLocalizedName() + moduleType.getDisplaySuffix();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void getSubBlocks(Item item, CreativeTabs tab, List<ItemStack> stacks) {
        for (int meta = 0; meta < materials.length; meta++) {
            if (materials[meta] != null) {
                stacks.add(new ItemStack(item, 1, meta));
            }
        }
    }

    @Override
    public int getRenderType() {
        return GTRendererBlock.RENDER_ID;
    }

    @Override
    public boolean canRenderInPass(int pass) {
        return pass == 0 || pass == 1;
    }

    @Override
    public int getRenderBlockPass() {
        return 1;
    }

    @Override
    public synchronized @Nullable ITexture[][] getTextures(int meta) {
        ITexture[][] cached = textureCache.getAndMoveToFirst(meta);
        if (cached != null) {
            return cached;
        }

        Materials material = getMaterial(meta);
        if (material == null) {
            return null;
        }

        ITexture texture = TextureFactory.builder()
            .addIcon(material.mIconSet.mTextures[moduleType.getTexturePrefix().getTextureIndex()])
            .setRGBA(material.getRGBA())
            .build();

        cached = new ITexture[][] { { texture }, { texture }, { texture }, { texture }, { texture }, { texture } };
        textureCache.putAndMoveToFirst(meta, cached);
        while (textureCache.size() > 64) {
            textureCache.removeLast();
        }
        return cached;
    }

    @Override
    public void onBlockAdded(World world, int x, int y, int z) {
        if (GregTechAPI.isMachineBlock(this, world.getBlockMetadata(x, y, z))) {
            GregTechAPI.causeMachineUpdate(world, x, y, z);
        }
        super.onBlockAdded(world, x, y, z);
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        if (GregTechAPI.isMachineBlock(this, meta)) {
            GregTechAPI.causeMachineUpdate(world, x, y, z);
        }
        super.breakBlock(world, x, y, z, block, meta);
    }
}
