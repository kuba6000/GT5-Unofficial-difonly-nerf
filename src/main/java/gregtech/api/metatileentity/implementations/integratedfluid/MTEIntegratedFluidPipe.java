package gregtech.api.metatileentity.implementations.integratedfluid;

import static com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil.formatNumber;
import static gregtech.api.enums.Textures.BlockIcons.MACHINE_CASINGS;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import gregtech.GTMod;
import gregtech.api.enums.Dyes;
import gregtech.api.enums.HarvestTool;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaPipeEntity;
import gregtech.api.metatileentity.implementations.integratedfluid.IFNAmbientTemperature;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTUtility;
import gregtech.common.covers.Cover;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;

/**
 * Integrated Fluid Pipe - A pipe that connects integrated fluid hatches in a tree structure.
 * These pipes transfer virtual fluid data (not actual fluid) between connected hatches.
 *
 * Similar to GT cables in terms of connection mechanics and textures, but for
 * integrated fluid system data transfer.
 */
public class MTEIntegratedFluidPipe extends MetaPipeEntity implements IIntegratedFluidMember {

    public static final float THICKNESS = 0.375F;

    private IntegratedFluidNetwork network;
    private java.util.UUID networkId;
    private long lastConnectionUpdateTick = -1L;

    public MTEIntegratedFluidPipe(int aID, String aName, String aNameRegional) {
        super(aID, aName, 0, false);
    }

    public MTEIntegratedFluidPipe(String aName) {
        super(aName, 0);
    }

    @Override
    public byte getTileEntityBaseType() {
        return HarvestTool.WrenchPipeLevel1.toTileEntityBaseType();
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEIntegratedFluidPipe(mName);
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity baseMetaTileEntity, ForgeDirection sideDirection, int connections,
        int colorIndex, boolean active, boolean redstoneLevel) {
        // Use a simple machine casing texture with a colored overlay to distinguish from regular pipes
        if (active) {
            // Connected side - show the "active" texture
            return new ITexture[] { TextureFactory.of(Textures.BlockIcons.MACHINE_CASINGS[1][colorIndex + 1]),
                TextureFactory.of(
                    Textures.BlockIcons.OVERLAY_PIPE_IN,
                    Dyes.getModulation(colorIndex, new short[] { 64, 192, 255, 255 })) };
        } else {
            // End cap or unconnected
            return new ITexture[] { TextureFactory.of(MACHINE_CASINGS[1][colorIndex + 1]) };
        }
    }

    @Override
    public float getCollisionThickness() {
        return THICKNESS;
    }

    @Override
    public boolean renderInside(ForgeDirection side) {
        return false;
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        if (GTMod.proxy.gt6Pipe) {
            aNBT.setByte("mConnections", mConnections);
        }
        if (networkId != null) {
            aNBT.setLong("ifnNetworkIdMost", networkId.getMostSignificantBits());
            aNBT.setLong("ifnNetworkIdLeast", networkId.getLeastSignificantBits());
        }
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        if (GTMod.proxy.gt6Pipe) {
            mConnections = aNBT.getByte("mConnections");
        }
        if (aNBT.hasKey("ifnNetworkIdMost") && aNBT.hasKey("ifnNetworkIdLeast")) {
            long most = aNBT.getLong("ifnNetworkIdMost");
            long least = aNBT.getLong("ifnNetworkIdLeast");
            networkId = new java.util.UUID(most, least);
        } else {
            networkId = null;
        }
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
        if (aBaseMetaTileEntity.isServerSide()) {
            if (aTick % 20 == 0) {
                if (!GTMod.proxy.gt6Pipe || mCheckConnections) {
                    checkConnections();
                }

                NetworkManager manager = NetworkManager.getInstance(aBaseMetaTileEntity.getWorld());

                // AGGRESSIVE INITIALIZATION: If no network, force join/create
                if (network == null) {
                    manager.onMemberAdded(this);
                } else {
                    // Check if we should merge with neighbors
                    List<IIntegratedFluidMember> neighbors = manager.findConnectedNeighbors(this);
                    boolean shouldMerge = false;

                    for (IIntegratedFluidMember neighbor : neighbors) {
                        if (neighbor.getNetwork() != null && neighbor.getNetwork() != network) {
                            shouldMerge = true;
                            break;
                        }
                    }

                    if (shouldMerge) {
                        // We have neighbors with different networks - merge!
                        manager.onMemberAdded(this);
                    }
                }
            }
        }
    }

    @Override
    public boolean onWireCutterRightClick(ForgeDirection side, ForgeDirection wrenchingSide, EntityPlayer aPlayer,
        float aX, float aY, float aZ, ItemStack aTool) {
        // Only wrenches should be able to connect/disconnect integrated fluid pipes
        return false;
    }

    @Override
    public boolean onSolderingToolRightClick(ForgeDirection side, ForgeDirection wrenchingSide, EntityPlayer aPlayer,
        float aX, float aY, float aZ, ItemStack aTool) {
        // Only wrenches should be able to connect/disconnect integrated fluid pipes
        return false;
    }

    @Override
    public boolean onWrenchRightClick(ForgeDirection side, ForgeDirection wrenchingSide, EntityPlayer aPlayer, float aX,
        float aY, float aZ, ItemStack aTool) {
        if (GTMod.proxy.gt6Pipe) {
            final ForgeDirection tSide = GTUtility.determineWrenchingSide(side, aX, aY, aZ);
            if (isConnectedAtSide(tSide)) {
                disconnect(tSide);
                GTUtility.sendChatToPlayer(aPlayer, GTUtility.trans("215", "Disconnected"));
            } else {
                if (connect(tSide) > 0) {
                    GTUtility.sendChatToPlayer(aPlayer, GTUtility.trans("214", "Connected"));
                }
            }
            // Connection changed - use NetworkManager
            NetworkManager manager = NetworkManager.getInstance(getBaseMetaTileEntity().getWorld());
            manager.onConnectionChanged(this);
            return true;
        }
        return false;
    }

    @Override
    public boolean onRightclick(IGregTechTileEntity baseMetaTileEntity, EntityPlayer player, ForgeDirection side,
        float x, float y, float z) {
        // Only allow wrench interactions - all other tools should not interact
        // The wrench handling is done by BaseMetaPipeEntity.onRightclick() which checks
        // for wrenches and calls onWrenchRightClick()
        return false;
    }

    @Override
    public boolean letsIn(Cover cover) {
        return cover.letsFluidIn(null);
    }

    @Override
    public boolean letsOut(Cover cover) {
        return cover.letsFluidOut(null);
    }

    @Override
    public boolean canConnect(ForgeDirection side, TileEntity tileEntity) {
        if (tileEntity == null) return false;

        // Can connect to other integrated fluid pipes and hatches
        if (tileEntity instanceof IGregTechTileEntity gtTile) {
            IMetaTileEntity mte = gtTile.getMetaTileEntity();
            if (mte instanceof IIntegratedFluidMember) {
                // If it's a pipe, always allow connection
                if (mte instanceof MetaPipeEntity) {
                    return true;
                }
                // If it's a hatch, only allow connection through its front facing (dot side)
                else {
                    // Check if the hatch's front facing points towards us
                    ForgeDirection hatchFrontFacing = gtTile.getFrontFacing();
                    // Only connect if hatch is facing us
                    return hatchFrontFacing == side.getOpposite();
                }
            }
        }
        return false;
    }

    @Override
    public boolean getGT6StyleConnection() {
        return GTMod.proxy.gt6Pipe;
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
    public String[] getDescription() {
        return new String[] { "Integrated Fluid Pipe", "Connects Integrated Fluid Hatches in a network",
            "Each pipe adds 100L of capacity", "Each hatch adds 10,000L of capacity" + EnumChatFormatting.GRAY };
    }

    @Override
    public void getWailaNBTData(EntityPlayerMP player, TileEntity tile, NBTTagCompound tag, World world, int x, int y,
        int z) {
        super.getWailaNBTData(player, tile, tag, world, x, y, z);
        // Send network data to client for WAILA display
        if (network != null) {
            tag.setBoolean("hasNetwork", true);
            tag.setInteger("memberCount", network.getMemberCount());
            tag.setInteger("pipeCount", network.getPipeCount());
            tag.setInteger("maxCapacity", network.getMaxCapacity());
            tag.setInteger("accumulatorCapacity", network.getAccumulatorCapacity());
            tag.setInteger("totalCapacity", network.getTotalCapacity());
            tag.setFloat("pressure", network.getPressure());
            tag.setFloat("temperature", network.getTemperature());
            float ambientTemperature = IFNAmbientTemperature.getAmbientTemperature(world);
            tag.setFloat("ambientTemperature", ambientTemperature);
            double ambientSpecificEnthalpy = 0.0d;
            FluidStack fluid = network.getStoredFluid();
            if (fluid != null) {
                ambientSpecificEnthalpy = FluidThermalProperties.getSpecificEnthalpyFromPT(
                    fluid.getFluid(),
                    network.getPressure(),
                    ambientTemperature
                );
            }
            tag.setDouble("specificEnthalpyRelative", network.getSpecificEnthalpy() - ambientSpecificEnthalpy);
            tag.setString("phase", network.getPhase().name());
            tag.setDouble("quality", network.getQuality());
            tag.setDouble("occupiedVolume", network.getOccupiedVolume());
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
        NBTTagCompound tag = accessor.getNBTData();
        if (tag.getBoolean("hasNetwork")) {
            int maxCapacity = tag.getInteger("maxCapacity");
            int accumulatorCapacity = tag.getInteger("accumulatorCapacity");
            int totalCapacity = tag.hasKey("totalCapacity")
                ? tag.getInteger("totalCapacity")
                : maxCapacity + accumulatorCapacity;
            if (tag.hasKey("networkFluid")) {
                FluidStack fluid = FluidStack.loadFluidStackFromNBT(tag.getCompoundTag("networkFluid"));
                if (fluid != null) {
                    currenttip
                        .add("Fluid: " + EnumChatFormatting.AQUA + fluid.getLocalizedName() + EnumChatFormatting.RESET);
                    double occupiedVolume = tag.getDouble("occupiedVolume");
                    int occupiedRounded = (int) Math.round(occupiedVolume);
                    currenttip.add(
                        "Occupied: " + EnumChatFormatting.GREEN
                            + formatNumber(occupiedRounded)
                            + "/"
                            + formatNumber(totalCapacity)
                            + " L"
                            + EnumChatFormatting.RESET);
                    if (accumulatorCapacity > 0) {
                        currenttip.add(
                            EnumChatFormatting.GRAY + "Capacity: "
                                + formatNumber(maxCapacity)
                                + " + "
                                + formatNumber(accumulatorCapacity)
                                + " = "
                                + formatNumber(totalCapacity)
                                + " L" + EnumChatFormatting.RESET);
                    }
                    currenttip.add(
                        "Std Amount: " + EnumChatFormatting.GRAY
                            + formatNumber(fluid.amount)
                            + " L"
                            + EnumChatFormatting.RESET);
                } else {
                    currenttip.add("Empty (Capacity: " + formatNumber(totalCapacity) + " L)");
                    if (accumulatorCapacity > 0) {
                        currenttip.add(
                            EnumChatFormatting.GRAY + "Capacity: "
                                + formatNumber(maxCapacity)
                                + " + "
                                + formatNumber(accumulatorCapacity)
                                + " = "
                                + formatNumber(totalCapacity)
                                + " L" + EnumChatFormatting.RESET);
                    }
                }
            } else {
                currenttip.add("Empty (Capacity: " + formatNumber(totalCapacity) + " L)");
                if (accumulatorCapacity > 0) {
                    currenttip.add(
                        EnumChatFormatting.GRAY + "Capacity: "
                            + formatNumber(maxCapacity)
                            + " + "
                            + formatNumber(accumulatorCapacity)
                            + " = "
                            + formatNumber(totalCapacity)
                            + " L" + EnumChatFormatting.RESET);
                }
            }
            currenttip.add("Network Members: " + tag.getInteger("memberCount"));

            // Display current heat loss based on actual temperature difference
            int pipeCount = tag.getInteger("pipeCount");
            float temperature = tag.getFloat("temperature");
            float ambientTemperature = tag.getFloat("ambientTemperature");
            if (ambientTemperature <= 0.0f) {
                ambientTemperature = 300.0f;
            }
            float temperatureDelta = Math.abs(temperature - ambientTemperature);
            float currentHeatLoss = pipeCount * temperatureDelta;

            currenttip.add(
                "Pipes in Network: " + EnumChatFormatting.GRAY
                    + pipeCount
                    + EnumChatFormatting.RESET);

            if (temperatureDelta > 0.1f) {
                currenttip.add(
                    "Current Heat Loss: " + EnumChatFormatting.DARK_RED
                        + String.format("%.1f", currentHeatLoss)
                        + " EU/s"
                        + EnumChatFormatting.RESET
                        + " (dT=" + String.format("%.1f", temperatureDelta) + "K)");
            } else {
                currenttip.add(
                    "Current Heat Loss: " + EnumChatFormatting.GREEN
                        + "0 EU/s"
                        + EnumChatFormatting.RESET
                        + " (at ambient temp)");
            }

            currenttip.add(
                "Pressure: " + EnumChatFormatting.YELLOW
                    + String.format("%.2f", tag.getFloat("pressure"))
                    + " bar"
                    + EnumChatFormatting.RESET);
            currenttip.add(
                "Temperature: " + EnumChatFormatting.RED
                    + String.format("%.2f", tag.getFloat("temperature"))
                    + " K"
                    + EnumChatFormatting.RESET);
            currenttip.add(
                "Specific Enthalpy: " + EnumChatFormatting.AQUA
                    + String.format("%.2f", tag.getDouble("specificEnthalpyRelative"))
                    + " EU/u"
                    + EnumChatFormatting.RESET);
            String phase = tag.getString("phase");
            double quality = tag.getDouble("quality");
            if ("TWO_PHASE".equals(phase)) {
                currenttip.add(
                    "Phase: " + EnumChatFormatting.LIGHT_PURPLE
                        + "Two-Phase"
                        + EnumChatFormatting.RESET
                        + " (x="
                        + String.format("%.2f", quality)
                        + ")");
            } else if (phase != null && !phase.isEmpty()) {
                currenttip.add("Phase: " + EnumChatFormatting.LIGHT_PURPLE + phase + EnumChatFormatting.RESET);
            }
        } else {
            currenttip.add(EnumChatFormatting.RED + "No network" + EnumChatFormatting.RESET);
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
    public java.util.UUID getNetworkId() {
        return networkId;
    }

    @Override
    public void setNetworkId(java.util.UUID id) {
        this.networkId = id;
    }

    @Override
    public void onNetworkUpdate() {
        // IMPORTANT: Cap fluid to capacity whenever network is updated
        if (network != null) {
            network.capFluidToCapacity();
        }
    }

    @Override
    public int getCapacityContribution() {
        // Each pipe adds 100L (100 mB) of capacity
        return 100;
    }


    @Override
    public void onRemoval() {
        super.onRemoval();
        // Use NetworkManager to properly handle removal and split networks
        IGregTechTileEntity baseTile = getBaseMetaTileEntity();
        if (baseTile != null && baseTile.isServerSide()) {
            NetworkManager manager = NetworkManager.getInstance(baseTile.getWorld());
            manager.onMemberRemoved(this);
        }
    }

    @Override
    public void disconnect(ForgeDirection side) {
        super.disconnect(side);
        // Trigger network rebuild through NetworkManager
        IGregTechTileEntity baseTile = getBaseMetaTileEntity();
        if (baseTile != null && baseTile.isServerSide()) {
            NetworkManager manager = NetworkManager.getInstance(baseTile.getWorld());
            manager.onConnectionChanged(this);
        }
    }

    @Override
    public void onMachineBlockUpdate() {
        // This is called when a neighbor block changes
        IGregTechTileEntity baseTile = getBaseMetaTileEntity();
        if (baseTile != null && baseTile.isServerSide()) {
            long tick = baseTile.getWorld().getTotalWorldTime();
            if (tick != lastConnectionUpdateTick) {
                lastConnectionUpdateTick = tick;
                NetworkManager manager = NetworkManager.getInstance(baseTile.getWorld());
                manager.onConnectionChanged(this);
            }
        }
    }
}
