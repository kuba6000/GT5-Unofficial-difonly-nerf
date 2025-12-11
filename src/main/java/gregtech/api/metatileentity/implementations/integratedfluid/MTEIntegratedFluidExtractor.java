package gregtech.api.metatileentity.implementations.integratedfluid;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.render.TextureFactory;

/**
 * Integrated Fluid Extractor - Extracts fluid from Integrated Fluid Network
 * and outputs it as standard fluid (without temperature/pressure properties).
 *
 * Acts as a bridge between the integrated network and standard fluid systems.
 */
public class MTEIntegratedFluidExtractor extends MTEHatch implements IIntegratedFluidMember {

    private IntegratedFluidNetwork network;
    private int textureIndex = 0;
    private static final int EXTRACTION_RATE = 1000; // Extract up to 1000 mB per operation

    public MTEIntegratedFluidExtractor(int aID, String aName, String aNameRegional, int aTier) {
        super(aID, aName, aNameRegional, aTier, 0, new String[] {
            "Extracts fluid from Integrated Fluid Network",
            "Converts network fluid to standard fluid",
            "Removes temperature and pressure properties",
            "Can output to adjacent machines or tanks",
            "Extraction rate: 1000 mB/operation"
        });
    }

    public MTEIntegratedFluidExtractor(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, 0, aDescription, aTextures);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEIntegratedFluidExtractor(this.mName, this.mTier, this.mDescriptionArray, this.mTextures);
    }

    @Override
    public ITexture[] getTexturesActive(ITexture aBaseTexture) {
        return new ITexture[] {
            aBaseTexture,
            TextureFactory.of(Textures.BlockIcons.OVERLAY_PIPE_OUT)
        };
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture aBaseTexture) {
        return new ITexture[] {
            aBaseTexture,
            TextureFactory.of(Textures.BlockIcons.OVERLAY_PIPE_OUT)
        };
    }

    @Override
    public boolean isFacingValid(ForgeDirection facing) {
        return true;
    }

    @Override
    public boolean isValidSlot(int aIndex) {
        return false;
    }

    @Override
    public boolean allowPullStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection side,
        ItemStack aStack) {
        return false;
    }

    @Override
    public boolean allowPutStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection side,
        ItemStack aStack) {
        return false;
    }

    // ===== IIntegratedFluidMember Implementation =====

    @Override
    public IntegratedFluidNetwork getNetwork() {
        return network;
    }

    @Override
    public void setNetwork(IntegratedFluidNetwork network) {
        this.network = network;
    }

    @Override
    public int getCapacityContribution() {
        return 0; // Extractor doesn't add capacity to network
    }

    public void onNetworkChanged() {
        // Network changed - update state if needed
    }

    @Override
    public void onNetworkUpdate() {
        // Called when network data updates
    }

    /**
     * Updates the texture index to match multiblock casing.
     */
    public void setTextureIndex(int aTextureIndex) {
        this.textureIndex = aTextureIndex;
    }

    // ===== Fluid Handling - Override MTEHatch methods =====

    @Override
    public FluidStack getFluid() {
        // Extractor doesn't store fluid locally - it pulls from network on demand
        // Return null so Input Hatch doesn't think we're full
        return null;
    }

    @Override
    public int getFluidAmount() {
        // Extractor has no local storage
        return 0;
    }

    @Override
    public int getCapacity() {
        // Extractor has no local storage - it's a passthrough to network
        return 0;
    }

    @Override
    public FluidStack drain(int maxDrain, boolean doDrain) {
        if (network == null) {
            return null;
        }

        int amountToDrain = Math.min(maxDrain, EXTRACTION_RATE);
        return network.drainFluid(amountToDrain, doDrain);
    }

    @Override
    public int fill(FluidStack resource, boolean doFill) {
        // Extractor is output-only, cannot fill
        return 0;
    }

    // ===== Tick Update =====

    @Override
    public void onFirstTick(IGregTechTileEntity aBaseMetaTileEntity) {
        super.onFirstTick(aBaseMetaTileEntity);
        if (aBaseMetaTileEntity.isServerSide()) {
            NetworkManager manager = NetworkManager.getInstance(aBaseMetaTileEntity.getWorld());
            manager.onMemberAdded(this);
        }
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);

        if (aBaseMetaTileEntity.isServerSide() && aTick % 20 == 0) {
            // Periodically check network connectivity
            NetworkManager manager = NetworkManager.getInstance(aBaseMetaTileEntity.getWorld());

            // AGGRESSIVE INITIALIZATION: Always try to join/create network
            if (network == null) {
                manager.onMemberAdded(this);
            }
        }
    }

    // ===== NBT =====

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setInteger("mTextureIndex", textureIndex);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        if (aNBT.hasKey("mTextureIndex")) {
            textureIndex = aNBT.getInteger("mTextureIndex");
        }
    }

    // ===== Network Management =====

    @Override
    public void onRemoval() {
        if (network != null) {
            network.removeMember(this);
        }
        super.onRemoval();
    }

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer) {
        if (aBaseMetaTileEntity.isClientSide()) {
            return true;
        }

        // Display network info
        if (network != null) {
            FluidStack fluid = network.getStoredFluid();
            if (fluid != null) {
                aPlayer.addChatMessage(
                    new net.minecraft.util.ChatComponentText(
                        String.format(
                            "Network: %s - %d/%d mB (%.1fK, %.1f bar)",
                            fluid.getLocalizedName(),
                            fluid.amount,
                            network.getMaxCapacity(),
                            network.getTemperature(),
                            network.getPressure()
                        )
                    )
                );
            } else {
                aPlayer.addChatMessage(
                    new net.minecraft.util.ChatComponentText(
                        String.format("Network: Empty (%d mB capacity)", network.getMaxCapacity())
                    )
                );
            }
        } else {
            aPlayer.addChatMessage(
                new net.minecraft.util.ChatComponentText("Not connected to network")
            );
        }

        return true;
    }
}

