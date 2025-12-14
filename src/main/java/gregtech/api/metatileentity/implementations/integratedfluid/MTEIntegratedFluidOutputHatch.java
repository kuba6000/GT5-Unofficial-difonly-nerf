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

    // Snapshot data loaded from NBT (used to seed network on first tick)
    private FluidStack snapshotFluid = null;
    private float snapshotTemperature = IntegratedFluidNetwork.DEFAULT_TEMPERATURE;
    private float snapshotPressure = IntegratedFluidNetwork.DEFAULT_PRESSURE;

    public MTEIntegratedFluidOutputHatch(int aID, String aName, String aNameRegional, int aTier) {
        super(
            aID,
            aName,
            aNameRegional,
            aTier,
            0, // No inventory slots
            new String[] { "Integrated Fluid Output Hatch", "Outputs fluid from the Integrated Fluid Network",
                "Connect with Integrated Fluid Pipes", "Each hatch adds 10,000L of network capacity" });
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
        byte color = getBaseMetaTileEntity().getColorization();
        if (color >= 0) {
            // Colored - show the color overlay
            return new ITexture[] { aBaseTexture,
                TextureFactory.of(gregtech.api.enums.Textures.BlockIcons.OVERLAY_PIPE_OUT),
                TextureFactory.of(gregtech.api.enums.Textures.BlockIcons.OVERLAY_PIPE_COLORS[color + 1]) };
        }
        // Default orange color when not colored
        return new ITexture[] { aBaseTexture,
            TextureFactory.of(OVERLAY_PIPE_OUT, Dyes.getModulation(-1, new short[] { 255, 128, 64, 255 })) };
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture aBaseTexture) {
        byte color = getBaseMetaTileEntity().getColorization();
        if (color >= 0) {
            // Colored - show the color overlay
            return new ITexture[] { aBaseTexture,
                TextureFactory.of(gregtech.api.enums.Textures.BlockIcons.OVERLAY_PIPE_OUT),
                TextureFactory.of(gregtech.api.enums.Textures.BlockIcons.OVERLAY_PIPE_COLORS[color + 1]) };
        }
        // Default orange color when not colored
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
        // Load snapshot data WITHOUT creating a network
        // NetworkManager will build networks based on connectivity and seed from one snapshot
        if (aNBT.hasKey("networkPressure") || aNBT.hasKey("networkFluid")) {
            // Load pressure
            if (aNBT.hasKey("networkPressure")) {
                snapshotPressure = aNBT.getFloat("networkPressure");
            }
            // Load temperature
            if (aNBT.hasKey("networkTemperature")) {
                snapshotTemperature = aNBT.getFloat("networkTemperature");
            }
            // Load fluid
            if (aNBT.hasKey("networkFluid")) {
                snapshotFluid = FluidStack.loadFluidStackFromNBT(aNBT.getCompoundTag("networkFluid"));
            }
        }
        // Keep network = null, will be built by NetworkManager
    }

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
            } else {
                // Check if any neighbors have different networks
                List<IIntegratedFluidMember> neighbors = manager.findConnectedNeighbors(this);
                boolean shouldMerge = false;

                for (IIntegratedFluidMember neighbor : neighbors) {
                    if (neighbor.getNetwork() != null && neighbor.getNetwork() != network) {
                        shouldMerge = true;
                        break;
                    }
                }

                if (shouldMerge) {
                    manager.onMemberAdded(this);
                }
            }
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
        // IMPORTANT: Cap fluid to capacity whenever network is updated
        if (network != null) {
            network.capFluidToCapacity();
        }
        // Trigger visual updates
        if (getBaseMetaTileEntity() != null) {
            getBaseMetaTileEntity().issueTextureUpdate();
        }
    }

    @Override
    public int getCapacityContribution() {
        // Each output hatch adds 10,000L (10,000 mB) of capacity
        return 10000;
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
            NetworkManager manager = NetworkManager.getInstance(getBaseMetaTileEntity().getWorld());
            manager.onMemberAdded(this);
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
            NetworkManager manager = NetworkManager.getInstance(getBaseMetaTileEntity().getWorld());
            manager.onMemberAdded(this);
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

    @Override
    public void onRemoval() {
        super.onRemoval();
        // Use NetworkManager to properly handle removal
        IGregTechTileEntity baseTile = getBaseMetaTileEntity();
        if (baseTile != null && baseTile.isServerSide()) {
            NetworkManager manager = NetworkManager.getInstance(baseTile.getWorld());
            manager.onMemberRemoved(this);
        }
    }

    @Override
    public void onMachineBlockUpdate() {
        // DON'T rebuild network here - causes fluid scaling issues
        // Network is properly managed via onFirstTick/onMemberAdded
    }

    // Snapshot access methods for NetworkManager

    public FluidStack getSnapshotFluid() {
        return snapshotFluid;
    }

    public float getSnapshotTemperature() {
        return snapshotTemperature;
    }

    public float getSnapshotPressure() {
        return snapshotPressure;
    }

    public void clearSnapshot() {
        snapshotFluid = null;
        snapshotTemperature = IntegratedFluidNetwork.DEFAULT_TEMPERATURE;
        snapshotPressure = IntegratedFluidNetwork.DEFAULT_PRESSURE;
    }
}
