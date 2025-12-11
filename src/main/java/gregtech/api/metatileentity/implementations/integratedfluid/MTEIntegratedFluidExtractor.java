package gregtech.api.metatileentity.implementations.integratedfluid;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

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
public class MTEIntegratedFluidExtractor extends MTEHatch implements IIntegratedFluidMember, IFluidHandler {

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

    // ===== IFluidHandler Implementation - Output side =====

    @Override
    public int fill(ForgeDirection from, FluidStack resource, boolean doFill) {
        // Extractor doesn't accept input from outside
        return 0;
    }

    @Override
    public FluidStack drain(ForgeDirection from, FluidStack resource, boolean doDrain) {
        if (resource == null || network == null) {
            return null;
        }

        FluidStack networkFluid = network.getStoredFluid();
        if (networkFluid == null || !networkFluid.isFluidEqual(resource)) {
            return null;
        }

        return drainFromNetwork(resource.amount, doDrain);
    }

    @Override
    public FluidStack drain(ForgeDirection from, int maxDrain, boolean doDrain) {
        if (network == null) {
            return null;
        }

        return drainFromNetwork(maxDrain, doDrain);
    }

    /**
     * Drains fluid from the network and returns it as standard fluid.
     * Temperature and pressure information is stripped.
     */
    private FluidStack drainFromNetwork(int maxDrain, boolean doDrain) {
        if (network == null) {
            return null;
        }

        int amountToDrain = Math.min(maxDrain, EXTRACTION_RATE);
        // Return as standard fluid (no temperature/pressure data)
        return network.drainFluid(amountToDrain, doDrain);
    }

    @Override
    public boolean canFill(ForgeDirection from, Fluid fluid) {
        return false; // Extractor is output-only
    }

    @Override
    public boolean canDrain(ForgeDirection from, Fluid fluid) {
        if (network == null) {
            return false;
        }

        FluidStack networkFluid = network.getStoredFluid();
        return networkFluid != null && networkFluid.getFluid() == fluid;
    }

    @Override
    public FluidTankInfo[] getTankInfo(ForgeDirection from) {
        if (network == null) {
            return new FluidTankInfo[0];
        }

        FluidStack networkFluid = network.getStoredFluid();
        int capacity = network.getMaxCapacity();

        return new FluidTankInfo[] {
            new FluidTankInfo(networkFluid, capacity)
        };
    }

    // ===== Tick Update =====

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

            // NOTE: Automatic pushing is DISABLED - it prevents fluid from being added to network!
            // Extractor works on-demand via drain() calls from adjacent machines/pipes
            // tryPushFluidToAdjacent();
        }
    }

    /**
     * Attempts to push fluid from network to adjacent fluid handlers.
     */
    private void tryPushFluidToAdjacent() {
        if (network == null) {
            return;
        }

        FluidStack networkFluid = network.getStoredFluid();
        if (networkFluid == null || networkFluid.amount <= 0) {
            return;
        }

        IGregTechTileEntity baseMetaTileEntity = getBaseMetaTileEntity();
        if (baseMetaTileEntity == null) {
            return;
        }

        // Try to push to adjacent tile in output direction
        ForgeDirection outputDirection = baseMetaTileEntity.getFrontFacing();
        var adjacentTile = baseMetaTileEntity.getTileEntityAtSide(outputDirection);

        if (adjacentTile instanceof IFluidHandler fluidHandler) {
            // Try to push up to EXTRACTION_RATE mB
            FluidStack toPush = networkFluid.copy();
            toPush.amount = Math.min(toPush.amount, EXTRACTION_RATE);

            // IMPORTANT: Check if adjacent can accept fluid BEFORE draining from network!
            int canFill = fluidHandler.fill(outputDirection.getOpposite(), toPush, false);
            if (canFill > 0) {
                // Only drain from network if adjacent can actually accept it
                FluidStack drained = network.drainFluid(canFill, true);
                if (drained != null) {
                    // Now actually fill the adjacent handler
                    fluidHandler.fill(outputDirection.getOpposite(), drained, true);
                }
            }
            // If canFill == 0, we don't drain anything - no voiding!
        }
        // If no IFluidHandler adjacent, we don't drain anything - no voiding!
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

