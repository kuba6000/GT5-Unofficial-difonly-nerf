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
    private java.util.UUID networkId;

    public MTEIntegratedFluidExtractor(int aID, String aName, String aNameRegional, int aTier) {
        super(aID, aName, aNameRegional, aTier, 0, new String[] {
            "Extracts fluid from Integrated Fluid Network",
            "Converts network fluid to standard fluid",
            "Removes temperature and pressure properties",
            "Passively responds to drain requests",
            "No extraction rate limit"
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
    public java.util.UUID getNetworkId() {
        return networkId;
    }

    @Override
    public void setNetworkId(java.util.UUID id) {
        this.networkId = id;
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
        if (!canExtractFromNetwork(network)) {
            return null;
        }

        // Get stored fluid info BEFORE draining
        FluidStack networkFluid = network.getStoredFluid();
        if (networkFluid == null || networkFluid.amount <= 0) {
            return null;
        }

        // Calculate how much we can actually drain
        int actualAmount = Math.min(maxDrain, networkFluid.amount);

        if (actualAmount <= 0) {
            return null;
        }

        // IMPORTANT: IFluidHandler.drain() parameter meanings:
        // - doDrain = true  -> actually drain (simulate = false)
        // - doDrain = false -> simulate only (simulate = true)
        boolean simulate = !doDrain;  // Invert the parameter!

        // Drain from network
        return network.drainFluid(actualAmount, simulate);
    }

    static boolean canExtractFromNetwork(IntegratedFluidNetwork network) {
        return IFNNetworkTransferGate.canExtractToGtPipe(network);
    }

    @Override
    public int fill(FluidStack resource, boolean doFill) {
        // Extractor is OUTPUT-ONLY - it only extracts from network, never inputs
        // If something tries to fill the extractor, reject it (return 0)
        // Fluid should go to network through Input Hatches or pipes instead
        return 0;
    }

    // ===== Tick Update =====

    @Override
    public void onFirstTick(IGregTechTileEntity aBaseMetaTileEntity) {
        super.onFirstTick(aBaseMetaTileEntity);
        // Add extractor to network - it IS a member, but with 0 capacity
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

        // NO AUTO-OUTPUT!
        // Extractor is PASSIVE - it only outputs when something tries to drain() from it
        // This prevents race conditions with Heat Pump adding fluid
    }

    // ===== NBT =====

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setInteger("mTextureIndex", textureIndex);
        if (networkId != null) {
            aNBT.setLong("ifnNetworkIdMost", networkId.getMostSignificantBits());
            aNBT.setLong("ifnNetworkIdLeast", networkId.getLeastSignificantBits());
        }
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        if (aNBT.hasKey("mTextureIndex")) {
            textureIndex = aNBT.getInteger("mTextureIndex");
        }
        if (aNBT.hasKey("ifnNetworkIdMost") && aNBT.hasKey("ifnNetworkIdLeast")) {
            long most = aNBT.getLong("ifnNetworkIdMost");
            long least = aNBT.getLong("ifnNetworkIdLeast");
            networkId = new java.util.UUID(most, least);
        } else {
            networkId = null;
        }
    }

    // ===== Network Management =====

    @Override
    public void onRemoval() {
        IGregTechTileEntity baseTile = getBaseMetaTileEntity();
        if (baseTile != null && baseTile.isServerSide()) {
            NetworkManager manager = NetworkManager.getInstance(baseTile.getWorld());
            manager.onMemberRemoved(this);
        }
        super.onRemoval();
    }

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer) {
        if (aBaseMetaTileEntity.isClientSide()) {
            return true;
        }

        // Display own network info (extractor is a member)
        if (network != null) {
            FluidStack fluid = network.getStoredFluid();
            if (fluid != null) {
                int accumulatorCapacity = network.getAccumulatorCapacity();
                int totalCapacity = network.getTotalCapacity();
                aPlayer.addChatMessage(
                    new net.minecraft.util.ChatComponentText(
                        String.format(
                            "Network: %s - %d/%d mB (%.1fK, %.1f bar)",
                            fluid.getLocalizedName(),
                            fluid.amount,
                            totalCapacity,
                            network.getTemperature(),
                            network.getPressure()
                        )
                    )
                );
                if (accumulatorCapacity > 0) {
                    aPlayer.addChatMessage(
                        new net.minecraft.util.ChatComponentText(
                            String.format("(+%d Hydrophore capacity)", accumulatorCapacity)
                        )
                    );
                }
            } else {
                aPlayer.addChatMessage(
                    new net.minecraft.util.ChatComponentText(
                        String.format("Network: Empty (%d mB capacity)", network.getTotalCapacity())
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
