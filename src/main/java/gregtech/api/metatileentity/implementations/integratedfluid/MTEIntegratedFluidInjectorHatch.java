package gregtech.api.metatileentity.implementations.integratedfluid;

import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_PIPE_IN;
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
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

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
 * Integrated Fluid Injector Hatch - Bridges normal GT fluid pipes with the integrated fluid network.
 * This hatch can receive fluid from normal GT fluid pipes and inject it into the integrated fluid network.
 * It acts as an IFluidHandler to accept fluids from connected GT pipes.
 */
public class MTEIntegratedFluidInjectorHatch extends MTEHatch implements IIntegratedFluidMember, IFluidHandler {

    private IntegratedFluidNetwork network;

    public MTEIntegratedFluidInjectorHatch(int aID, String aName, String aNameRegional, int aTier) {
        super(
            aID,
            aName,
            aNameRegional,
            aTier,
            0, // No inventory slots
            new String[] { "Integrated Fluid Injector Hatch", "Bridges GT Fluid Pipes with Integrated Fluid Network",
                "Accepts fluid from GT pipes and injects into network", "Injector hatch does not add network capacity",
                "Only works at 1.00 bar pressure" });
    }

    public MTEIntegratedFluidInjectorHatch(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, 0, aDescription, aTextures);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEIntegratedFluidInjectorHatch(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public ITexture[] getTexturesActive(ITexture aBaseTexture) {
        return new ITexture[] { aBaseTexture,
            TextureFactory.of(OVERLAY_PIPE_IN, Dyes.getModulation(-1, new short[] { 255, 255, 64, 255 })),
            TextureFactory.of(OVERLAY_PIPE_OUT, Dyes.getModulation(-1, new short[] { 64, 255, 255, 255 })) };
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture aBaseTexture) {
        return new ITexture[] { aBaseTexture,
            TextureFactory.of(OVERLAY_PIPE_IN, Dyes.getModulation(-1, new short[] { 192, 192, 64, 255 })),
            TextureFactory.of(OVERLAY_PIPE_OUT, Dyes.getModulation(-1, new short[] { 64, 192, 192, 255 })) };
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
            float pressure = tag.getFloat("pressure");
            currenttip.add(
                "Pressure: " + EnumChatFormatting.YELLOW
                    + String.format("%.2f", pressure)
                    + " bar"
                    + EnumChatFormatting.RESET);
            // Add warning if pressure is not 1 bar (injector won't work)
            if (Math.abs(pressure - 1.0f) >= 0.01f) {
                currenttip.add(
                    EnumChatFormatting.RED + "WARNING: Injector only works at 1.00 bar!" + EnumChatFormatting.RESET);
            }
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
        // Injector hatch adds 0L (0 mB) of capacity
        return 0;
    }

    // IFluidHandler implementation - allows GT fluid pipes to push fluid into us

    @Override
    public int fill(ForgeDirection from, FluidStack resource, boolean doFill) {
        if (network == null) {
            findAndJoinNetwork();
        }
        if (network != null && resource != null) {
            // Only allow filling if pressure is at 1 bar
            if (Math.abs(network.getPressure() - 1.0f) < 0.01f) {
                return network.addFluid(resource, !doFill);
            }
        }
        return 0;
    }

    @Override
    public FluidStack drain(ForgeDirection from, FluidStack resource, boolean doDrain) {
        // We don't allow draining from the network via GT pipes
        return null;
    }

    @Override
    public FluidStack drain(ForgeDirection from, int maxDrain, boolean doDrain) {
        // We don't allow draining from the network via GT pipes
        return null;
    }

    @Override
    public boolean canFill(ForgeDirection from, net.minecraftforge.fluids.Fluid fluid) {
        if (network == null) return true; // Allow fill if we can potentially join a network
        FluidStack stored = network.getStoredFluid();
        if (stored == null) return true; // Empty network accepts any fluid
        return stored.getFluid() == fluid; // Only accept matching fluid
    }

    @Override
    public boolean canDrain(ForgeDirection from, net.minecraftforge.fluids.Fluid fluid) {
        return false; // Don't allow draining via GT pipes
    }

    @Override
    public FluidTankInfo[] getTankInfo(ForgeDirection from) {
        if (network != null) {
            FluidStack stored = network.getStoredFluid();
            int capacity = network.getMaxCapacity();
            return new FluidTankInfo[] { new FluidTankInfo(stored, capacity) };
        }
        // Return a default capacity if network not available yet
        return new FluidTankInfo[] { new FluidTankInfo(null, 0) };
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
}
