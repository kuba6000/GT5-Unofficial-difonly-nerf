package gregtech.api.metatileentity.implementations.integratedfluid;

import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_PIPE_OUT;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import gregtech.api.enums.Dyes;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTUtility;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;

/**
 * Integrated Fluid Output Hatch - Outputs fluid from the integrated fluid network.
 * This hatch can be connected to integrated fluid pipes to access shared fluid data
 * across a tree-structured network.
 */
public class MTEIntegratedFluidOutputHatch extends MTEHatch implements IIntegratedFluidMember {

    private IntegratedFluidNetwork network;

    public MTEIntegratedFluidOutputHatch(int aID, String aName, String aNameRegional, int aTier) {
        super(
            aID,
            aName,
            aNameRegional,
            aTier,
            0, // No inventory slots
            new String[] { "Integrated Fluid Output Hatch", "Outputs fluid from the Integrated Fluid Network",
                "Connect with Integrated Fluid Pipes",
                EnumChatFormatting.AQUA + "Network Capacity: "
                    + EnumChatFormatting.WHITE
                    + GTUtility.formatNumbers(IntegratedFluidNetwork.MAX_CAPACITY)
                    + "L" });
    }

    public MTEIntegratedFluidOutputHatch(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, 0, aDescription, aTextures);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEIntegratedFluidOutputHatch(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public ITexture[] getTexturesActive(ITexture aBaseTexture) {
        return new ITexture[] { aBaseTexture,
            TextureFactory.of(OVERLAY_PIPE_OUT, Dyes.getModulation(-1, new short[] { 255, 128, 64, 255 })) };
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture aBaseTexture) {
        return new ITexture[] { aBaseTexture,
            TextureFactory.of(OVERLAY_PIPE_OUT, Dyes.getModulation(-1, new short[] { 192, 96, 64, 255 })) };
    }

    @Override
    public boolean isFacingValid(ForgeDirection facing) {
        return true;
    }

    @Override
    public boolean isAccessAllowed(EntityPlayer aPlayer) {
        return true;
    }

    @Override
    public boolean isValidSlot(int aIndex) {
        return false;
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        // Save network fluid data if we are the "primary" holder
        if (network != null && network.getStoredFluid() != null) {
            aNBT.setTag(
                "networkFluid",
                network.getStoredFluid()
                    .writeToNBT(new NBTTagCompound()));
        }
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        if (aNBT.hasKey("networkFluid")) {
            FluidStack fluid = FluidStack.loadFluidStackFromNBT(aNBT.getCompoundTag("networkFluid"));
            if (fluid != null && network == null) {
                network = new IntegratedFluidNetwork();
                network.addFluid(fluid, false);
                network.addMember(this);
            }
        }
    }

    @Override
    public void onFirstTick(IGregTechTileEntity aBaseMetaTileEntity) {
        super.onFirstTick(aBaseMetaTileEntity);
        if (aBaseMetaTileEntity.isServerSide()) {
            findAndJoinNetwork();
        }
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (aBaseMetaTileEntity.isServerSide() && aTick % 20 == 0) {
            // Periodically check network connectivity
            if (network == null) {
                findAndJoinNetwork();
            }
        }
    }

    /**
     * Finds adjacent integrated fluid pipes and joins their network.
     */
    private void findAndJoinNetwork() {
        IGregTechTileEntity baseTile = getBaseMetaTileEntity();
        if (baseTile == null) return;

        for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
            TileEntity neighbor = baseTile.getTileEntityAtSide(side);
            if (neighbor instanceof IGregTechTileEntity gtNeighbor) {
                IMetaTileEntity mte = gtNeighbor.getMetaTileEntity();
                if (mte instanceof MTEIntegratedFluidPipe pipe) {
                    // Check if the pipe is connected to us
                    if (pipe.isConnectedAtSide(side.getOpposite())) {
                        // Join the pipe's network
                        IntegratedFluidNetwork pipeNetwork = pipe.getNetwork();
                        if (pipeNetwork != null) {
                            // If we had our own network with fluid, merge it
                            if (network != null && network != pipeNetwork) {
                                pipeNetwork.merge(network);
                            }
                            if (network != pipeNetwork) {
                                if (network != null) {
                                    network.removeMember(this);
                                }
                                pipeNetwork.addMember(this);
                            }
                            return;
                        }
                    }
                }
            }
        }

        // No connected pipe network found, create our own
        if (network == null) {
            network = new IntegratedFluidNetwork();
            network.addMember(this);
        }
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

    @Override
    public void getWailaNBTData(EntityPlayerMP player, TileEntity tile, NBTTagCompound tag, World world, int x, int y,
        int z) {
        super.getWailaNBTData(player, tile, tag, world, x, y, z);
        // Send network data to client for WAILA display
        if (network != null) {
            tag.setBoolean("hasNetwork", true);
            tag.setInteger("memberCount", network.getMemberCount());
            FluidStack fluid = network.getStoredFluid();
            if (fluid != null) {
                tag.setTag("networkFluid", fluid.writeToNBT(new NBTTagCompound()));
            }
        } else {
            tag.setBoolean("hasNetwork", false);
        }
    }

    @Override
    public void getWailaBody(ItemStack itemStack, List<String> currenttip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        super.getWailaBody(itemStack, currenttip, accessor, config);
        NBTTagCompound tag = accessor.getNBTData();
        if (tag.getBoolean("hasNetwork")) {
            if (tag.hasKey("networkFluid")) {
                FluidStack fluid = FluidStack.loadFluidStackFromNBT(tag.getCompoundTag("networkFluid"));
                if (fluid != null) {
                    currenttip.add(
                        "Network Fluid: " + EnumChatFormatting.AQUA
                            + fluid.getLocalizedName()
                            + EnumChatFormatting.RESET);
                    currenttip.add(
                        "Amount: " + EnumChatFormatting.GREEN
                            + GTUtility.formatNumbers(fluid.amount)
                            + "/"
                            + GTUtility.formatNumbers(IntegratedFluidNetwork.MAX_CAPACITY)
                            + " L"
                            + EnumChatFormatting.RESET);
                } else {
                    currenttip.add("Network: Empty");
                }
            } else {
                currenttip.add("Network: Empty");
            }
            currenttip.add("Network Members: " + tag.getInteger("memberCount"));
        } else {
            currenttip.add(EnumChatFormatting.RED + "No network connected" + EnumChatFormatting.RESET);
        }
    }

    // IIntegratedFluidMember implementation

    @Override
    public IntegratedFluidNetwork getNetwork() {
        return network;
    }

    @Override
    public void setNetwork(IntegratedFluidNetwork network) {
        this.network = network;
    }

    @Override
    public void onNetworkUpdate() {
        // Could trigger visual updates if needed
        if (getBaseMetaTileEntity() != null) {
            getBaseMetaTileEntity().issueTextureUpdate();
        }
    }

    /**
     * Drains fluid from the network (for machines to call).
     * 
     * @param maxDrain The maximum amount to drain
     * @param simulate If true, only simulates the operation
     * @return The fluid that was drained
     */
    public FluidStack drainFluidFromNetwork(int maxDrain, boolean simulate) {
        if (network == null) {
            findAndJoinNetwork();
        }
        if (network != null) {
            return network.drainFluid(maxDrain, simulate);
        }
        return null;
    }

    /**
     * Drains a specific fluid from the network.
     * 
     * @param fluid    The fluid to drain (must match network fluid)
     * @param simulate If true, only simulates the operation
     * @return The fluid that was drained
     */
    public FluidStack drainFluidFromNetwork(FluidStack fluid, boolean simulate) {
        if (network == null) {
            findAndJoinNetwork();
        }
        if (network != null) {
            return network.drainFluid(fluid, simulate);
        }
        return null;
    }

    /**
     * Gets the current fluid in the network.
     */
    public FluidStack getNetworkFluid() {
        if (network != null) {
            return network.getStoredFluid();
        }
        return null;
    }
}
