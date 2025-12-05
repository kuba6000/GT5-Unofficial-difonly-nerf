package gregtech.api.metatileentity.implementations.integratedfluid;

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

    public MTEIntegratedFluidPipe(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional, 0, false);
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
        if (GTMod.proxy.gt6Pipe) {
            mConnections = aNBT.getByte("mConnections");
        }
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
            rebuildNetwork();
        }
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (aBaseMetaTileEntity.isServerSide()) {
            if (aTick % 20 == 0 && (!GTMod.proxy.gt6Pipe || mCheckConnections)) {
                checkConnections();
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
            rebuildNetwork();
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

        // Can connect to other integrated fluid pipes
        if (tileEntity instanceof IGregTechTileEntity gtTile) {
            IMetaTileEntity mte = gtTile.getMetaTileEntity();
            if (mte instanceof IIntegratedFluidMember) {
                return true;
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
        NBTTagCompound tag = accessor.getNBTData();
        if (tag.getBoolean("hasNetwork")) {
            int maxCapacity = tag.getInteger("maxCapacity");
            if (tag.hasKey("networkFluid")) {
                FluidStack fluid = FluidStack.loadFluidStackFromNBT(tag.getCompoundTag("networkFluid"));
                if (fluid != null) {
                    currenttip
                        .add("Fluid: " + EnumChatFormatting.AQUA + fluid.getLocalizedName() + EnumChatFormatting.RESET);
                    currenttip.add(
                        "Amount: " + EnumChatFormatting.GREEN
                            + GTUtility.formatNumbers(fluid.amount)
                            + "/"
                            + GTUtility.formatNumbers(maxCapacity)
                            + " L"
                            + EnumChatFormatting.RESET);
                } else {
                    currenttip.add("Empty (Capacity: " + GTUtility.formatNumbers(maxCapacity) + " L)");
                }
            } else {
                currenttip.add("Empty (Capacity: " + GTUtility.formatNumbers(maxCapacity) + " L)");
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
    public void onNetworkUpdate() {
        // Could trigger visual updates if needed
    }

    @Override
    public int getCapacityContribution() {
        // Each pipe adds 100L (100 mB) of capacity
        return 100;
    }

    /**
     * Rebuilds the network by traversing connected pipes and hatches.
     */
    public void rebuildNetwork() {
        if (getBaseMetaTileEntity() == null || !getBaseMetaTileEntity().isServerSide()) {
            return;
        }

        // Store the old network information before rebuilding
        IntegratedFluidNetwork oldNetwork = network;
        FluidStack oldFluid = oldNetwork != null ? oldNetwork.getStoredFluid() : null;
        int oldMemberCount = oldNetwork != null ? oldNetwork.getMemberCount() : 0;
        float oldPressure = oldNetwork != null ? oldNetwork.getPressure() : IntegratedFluidNetwork.DEFAULT_PRESSURE;
        float oldTemperature = oldNetwork != null ? oldNetwork.getTemperature()
            : IntegratedFluidNetwork.DEFAULT_TEMPERATURE;

        // Create new network and traverse to find all connected members
        Set<IIntegratedFluidMember> visited = new HashSet<>();
        List<IIntegratedFluidMember> toVisit = new ArrayList<>();
        Set<IntegratedFluidNetwork> existingNetworks = new HashSet<>();
        toVisit.add(this);

        IntegratedFluidNetwork newNetwork = new IntegratedFluidNetwork();

        // Preserve pressure (use old network's pressure for now)
        newNetwork.setPressure(oldPressure);

        while (!toVisit.isEmpty()) {
            IIntegratedFluidMember current = toVisit.remove(0);
            if (visited.contains(current)) {
                continue;
            }
            visited.add(current);

            // Track all existing networks that will be merged
            // Use identity-based set to ensure we only count each network instance once
            if (current.getNetwork() != null) {
                existingNetworks.add(current.getNetwork());
            }

            newNetwork.addMember(current);

            // Find connected members
            if (current instanceof MetaPipeEntity pipe) {
                IGregTechTileEntity baseTile = pipe.getBaseMetaTileEntity();
                if (baseTile != null) {
                    for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
                        if (pipe.isConnectedAtSide(side)) {
                            TileEntity neighbor = baseTile.getTileEntityAtSide(side);
                            if (neighbor instanceof IGregTechTileEntity gtNeighbor) {
                                IMetaTileEntity mte = gtNeighbor.getMetaTileEntity();
                                if (mte instanceof IIntegratedFluidMember member && !visited.contains(member)) {
                                    toVisit.add(member);
                                }
                            }
                        }
                    }
                }
            }
        }

        // Check if all visited members are already in the same single network
        // If so, we don't need to rebuild - this prevents fluid duplication when
        // connecting two pipes that are already part of the same network
        if (existingNetworks.size() == 1) {
            IntegratedFluidNetwork singleNetwork = existingNetworks.iterator()
                .next();
            // Check if all visited members are already in this single network
            // AND the network size matches (no new members being added)
            if (singleNetwork.getMemberCount() == visited.size()) {
                // All members are already in the same network with no new members - no rebuild needed
                // Just ensure all members have the correct network reference and notify them
                for (IIntegratedFluidMember member : visited) {
                    if (member.getNetwork() != singleNetwork) {
                        // Should not happen, but be safe
                        singleNetwork.addMember(member);
                    }
                    // Notify each member so they update their display/state
                    member.onNetworkUpdate();
                }
                return;
            }
        }

        // Merge all fluids from existing networks with weighted temperature averaging
        int totalFluid = 0;
        double weightedTemperature = 0.0;
        FluidStack combinedFluid = null;

        // Track total capacity across all networks being merged
        int totalOldCapacity = 0;

        for (IntegratedFluidNetwork existingNet : existingNetworks) {
            totalOldCapacity += existingNet.getMaxCapacity();
            FluidStack fluid = existingNet.getStoredFluid();
            if (fluid != null) {
                if (combinedFluid == null) {
                    combinedFluid = fluid.copy();
                    totalFluid = fluid.amount;
                    weightedTemperature = fluid.amount * existingNet.getTemperature();
                } else if (combinedFluid.isFluidEqual(fluid)) {
                    // Same fluid type - combine amounts and temperatures
                    totalFluid += fluid.amount;
                    weightedTemperature += fluid.amount * existingNet.getTemperature();
                    combinedFluid.amount += fluid.amount;
                } else {
                    // Different fluid types - can't merge, keep the larger one
                    // This shouldn't normally happen but handle it gracefully
                    if (fluid.amount > combinedFluid.amount) {
                        combinedFluid = fluid.copy();
                        totalFluid = fluid.amount;
                        weightedTemperature = fluid.amount * existingNet.getTemperature();
                    }
                }
            }
        }

        // Calculate average temperature BEFORE adjusting for splits
        float avgTemperature = IntegratedFluidNetwork.DEFAULT_TEMPERATURE;
        if (totalFluid > 0) {
            avgTemperature = (float) (weightedTemperature / totalFluid);
        }

        // If this is a split (new network has fewer members than old), distribute proportionally by CAPACITY
        if (combinedFluid != null && oldMemberCount > 0 && newNetwork.getMemberCount() < oldMemberCount) {
            // Calculate capacity for this new network
            int newNetworkCapacity = newNetwork.getMaxCapacity();

            // Distribute fluid proportionally by capacity, not member count
            // This prevents voiding when a smaller-capacity segment splits off
            if (totalOldCapacity > 0) {
                int proportionalAmount = (combinedFluid.amount * newNetworkCapacity) / totalOldCapacity;
                // Cap at the new network's capacity to avoid overflow
                proportionalAmount = Math.min(proportionalAmount, newNetworkCapacity);

                // Take this amount from the old network(s)
                // This leaves the remainder for other segments that haven't rebuilt yet
                for (IntegratedFluidNetwork existingNet : existingNetworks) {
                    FluidStack existingFluid = existingNet.getStoredFluid();
                    if (existingFluid != null && existingFluid.amount > 0) {
                        int toTake = Math.min(proportionalAmount, existingFluid.amount);
                        existingFluid.amount -= toTake;
                        proportionalAmount -= toTake;

                        // Update the network's fluid
                        if (existingFluid.amount == 0) {
                            existingNet.clearFluid();
                        }

                        if (proportionalAmount == 0) break;
                    }
                }

                combinedFluid.amount = (combinedFluid.amount * newNetworkCapacity) / totalOldCapacity;
                combinedFluid.amount = Math.min(combinedFluid.amount, newNetworkCapacity);
            }
            // Temperature stays the same - it's the average of all the fluid that was present
        } else {
            // Not a split - this is a merge or initial connection
            // Clear fluid from all old networks since we're combining them into the new network
            for (IntegratedFluidNetwork existingNet : existingNetworks) {
                existingNet.clearFluid();
            }
        }

        // Add the combined fluid to the new network with averaged temperature
        if (combinedFluid != null && combinedFluid.amount > 0) {
            newNetwork.addFluid(combinedFluid, false, avgTemperature);
        } else {
            // No fluid, just set default temperature
            newNetwork.setTemperature(IntegratedFluidNetwork.DEFAULT_TEMPERATURE);
        }

        // Notify all members of the update
        for (IIntegratedFluidMember member : visited) {
            member.onNetworkUpdate();
        }
    }

    @Override
    public void onRemoval() {
        super.onRemoval();
        // Remove this pipe from the network and trigger rebuild for all connected neighbors
        if (network != null) {
            network.removeMember(this);
        }

        // Notify all connected neighbors to rebuild their networks
        IGregTechTileEntity baseTile = getBaseMetaTileEntity();
        if (baseTile != null) {
            for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
                if (isConnectedAtSide(side)) {
                    TileEntity neighbor = baseTile.getTileEntityAtSide(side);
                    if (neighbor instanceof IGregTechTileEntity gtNeighbor) {
                        IMetaTileEntity mte = gtNeighbor.getMetaTileEntity();
                        if (mte instanceof MTEIntegratedFluidPipe pipe) {
                            pipe.rebuildNetwork();
                        } else if (mte instanceof IIntegratedFluidMember member) {
                            // For hatches, remove from network and mark for rebuild
                            // They will rejoin on next update
                            if (member.getNetwork() != null) {
                                member.getNetwork()
                                    .removeMember(member);
                                member.setNetwork(null);
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public void disconnect(ForgeDirection side) {
        super.disconnect(side);
        // Trigger network rebuild on both sides
        rebuildNetwork();
    }

    @Override
    public void onMachineBlockUpdate() {
        // This is called when a neighbor block changes (including when blocks are destroyed)
        // Trigger a network rebuild to update connections
        rebuildNetwork();
    }
}
