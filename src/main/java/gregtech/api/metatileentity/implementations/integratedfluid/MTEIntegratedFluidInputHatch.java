package gregtech.api.metatileentity.implementations.integratedfluid;

import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_PIPE_IN;

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
 * Integrated Fluid Input Hatch - Adds fluid to the integrated fluid network.
 * This hatch can be connected to integrated fluid pipes to share fluid data
 * across a tree-structured network.
 * 
 * For now, this hatch adds no fluid automatically but provides the infrastructure
 * for the network system.
 */
public class MTEIntegratedFluidInputHatch extends MTEHatch implements IIntegratedFluidMember {

    private IntegratedFluidNetwork network;

    public MTEIntegratedFluidInputHatch(int aID, String aName, String aNameRegional, int aTier) {
        super(
            aID,
            aName,
            aNameRegional,
            aTier,
            0, // No inventory slots
            new String[] { "Integrated Fluid Input Hatch", "Adds fluid to the Integrated Fluid Network",
                "Connect with Integrated Fluid Pipes", "Each hatch adds 10,000L of network capacity" });
    }

    public MTEIntegratedFluidInputHatch(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, 0, aDescription, aTextures);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEIntegratedFluidInputHatch(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public ITexture[] getTexturesActive(ITexture aBaseTexture) {
        return new ITexture[] { aBaseTexture,
            TextureFactory.of(OVERLAY_PIPE_IN, Dyes.getModulation(-1, new short[] { 64, 255, 64, 255 })) };
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture aBaseTexture) {
        return new ITexture[] { aBaseTexture,
            TextureFactory.of(OVERLAY_PIPE_IN, Dyes.getModulation(-1, new short[] { 64, 192, 64, 255 })) };
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
        // Save complete network data including temperature and pressure
        if (network != null) {
            FluidStack fluid = network.getStoredFluid();
            if (fluid != null) {
                aNBT.setTag("networkFluid", fluid.writeToNBT(new NBTTagCompound()));
            }
            aNBT.setFloat("networkPressure", network.getPressure());
            aNBT.setFloat("networkTemperature", network.getTemperature());
        }
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        // Restore network data - will be merged during rebuild on first tick
        if (aNBT.hasKey("networkPressure") || aNBT.hasKey("networkFluid")) {
            network = new IntegratedFluidNetwork();

            // Restore pressure and temperature
            if (aNBT.hasKey("networkPressure")) {
                network.setPressure(aNBT.getFloat("networkPressure"));
            }
            if (aNBT.hasKey("networkTemperature")) {
                network.setTemperature(aNBT.getFloat("networkTemperature"));
            } else {
                // If no temperature saved, use default
                network.setTemperature(IntegratedFluidNetwork.DEFAULT_TEMPERATURE);
            }

            // Restore fluid
            if (aNBT.hasKey("networkFluid")) {
                FluidStack fluid = FluidStack.loadFluidStackFromNBT(aNBT.getCompoundTag("networkFluid"));
                if (fluid != null) {
                    // Add fluid with the restored temperature
                    network.addFluid(fluid, false, network.getTemperature());
                }
            }

            network.addMember(this);
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
            tag.setInteger("maxCapacity", network.getMaxCapacity());
            tag.setFloat("pressure", network.getPressure());
            tag.setFloat("temperature", network.getTemperature());
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
                            + GTUtility.formatNumbers(tag.getInteger("maxCapacity"))
                            + " L"
                            + EnumChatFormatting.RESET);
                } else {
                    currenttip.add("Network: Empty");
                }
            } else {
                currenttip.add("Network: Empty");
            }
            currenttip.add("Network Members: " + tag.getInteger("memberCount"));
            currenttip.add(
                "Pressure: " + EnumChatFormatting.YELLOW
                    + String.format("%.2f", tag.getFloat("pressure"))
                    + " bar"
                    + EnumChatFormatting.RESET);
            currenttip.add(
                "Temperature: " + EnumChatFormatting.RED
                    + String.format("%.1f", tag.getFloat("temperature"))
                    + " K"
                    + EnumChatFormatting.RESET);
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

    @Override
    public int getCapacityContribution() {
        // Each input hatch adds 10,000L (10,000 mB) of capacity
        return 10000;
    }

    /**
     * Adds fluid to the network (for machines to call).
     * 
     * @param fluid    The fluid to add
     * @param simulate If true, only simulates the operation
     * @return The amount of fluid actually added
     */
    public int addFluidToNetwork(FluidStack fluid, boolean simulate) {
        if (network == null) {
            findAndJoinNetwork();
        }
        if (network != null) {
            return network.addFluid(fluid, simulate);
        }
        return 0;
    }

    @Override
    public void onRemoval() {
        super.onRemoval();
        // Remove this hatch from the network and notify connected pipes
        if (network != null) {
            network.removeMember(this);
        }

        // Notify connected pipes to rebuild their networks
        IGregTechTileEntity baseTile = getBaseMetaTileEntity();
        if (baseTile != null) {
            for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
                TileEntity neighbor = baseTile.getTileEntityAtSide(side);
                if (neighbor instanceof IGregTechTileEntity gtNeighbor) {
                    IMetaTileEntity mte = gtNeighbor.getMetaTileEntity();
                    if (mte instanceof MTEIntegratedFluidPipe pipe) {
                        pipe.rebuildNetwork();
                    }
                }
            }
        }
    }

    @Override
    public void onMachineBlockUpdate() {
        // This is called when a neighbor block changes (including when blocks are destroyed)
        // Try to rejoin the network if we lost connection
        findAndJoinNetwork();
    }
}
