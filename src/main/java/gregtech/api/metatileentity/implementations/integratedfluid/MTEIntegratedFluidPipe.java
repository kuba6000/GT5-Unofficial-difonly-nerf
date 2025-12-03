package gregtech.api.metatileentity.implementations.integratedfluid;

import static gregtech.api.enums.Textures.BlockIcons.MACHINE_CASINGS;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
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
import gregtech.api.util.GTModHandler;
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
        // Network fluid data is saved at the network level, but we store a reference
        if (network != null && network.getStoredFluid() != null) {
            aNBT.setTag(
                "networkFluid",
                network.getStoredFluid()
                    .writeToNBT(new NBTTagCompound()));
            aNBT.setInteger("networkAmount", network.getStoredAmount());
        }
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        if (GTMod.proxy.gt6Pipe) {
            mConnections = aNBT.getByte("mConnections");
        }
        // Network will be rebuilt on first tick, but we can restore fluid data
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
        if (GTMod.proxy.gt6Pipe
            && GTModHandler.damageOrDechargeItem(aPlayer.inventory.getCurrentItem(), 1, 500, aPlayer)) {
            if (isConnectedAtSide(wrenchingSide)) {
                disconnect(wrenchingSide);
                GTUtility.sendChatToPlayer(aPlayer, GTUtility.trans("215", "Disconnected"));
            } else if (!GTMod.proxy.costlyCableConnection) {
                if (connect(wrenchingSide) > 0) {
                    GTUtility.sendChatToPlayer(aPlayer, GTUtility.trans("214", "Connected"));
                }
            }
            rebuildNetwork();
            return true;
        }
        return false;
    }

    @Override
    public boolean onSolderingToolRightClick(ForgeDirection side, ForgeDirection wrenchingSide, EntityPlayer aPlayer,
        float aX, float aY, float aZ, ItemStack aTool) {
        if (GTMod.proxy.gt6Pipe
            && GTModHandler.damageOrDechargeItem(aPlayer.inventory.getCurrentItem(), 1, 500, aPlayer)) {
            if (isConnectedAtSide(wrenchingSide)) {
                disconnect(wrenchingSide);
                GTUtility.sendChatToPlayer(aPlayer, GTUtility.trans("215", "Disconnected"));
            } else if (!GTMod.proxy.costlyCableConnection || GTModHandler.consumeSolderingMaterial(aPlayer)) {
                if (connect(wrenchingSide) > 0) {
                    GTUtility.sendChatToPlayer(aPlayer, GTUtility.trans("214", "Connected"));
                }
            }
            rebuildNetwork();
            return true;
        }
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
            EnumChatFormatting.AQUA + "Network Capacity: "
                + EnumChatFormatting.WHITE
                + GTUtility.formatNumbers(IntegratedFluidNetwork.MAX_CAPACITY)
                + "L"
                + EnumChatFormatting.GRAY };
    }

    @Override
    public void getWailaBody(ItemStack itemStack, List<String> currenttip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        if (network != null) {
            FluidStack fluid = network.getStoredFluid();
            if (fluid != null) {
                currenttip
                    .add("Fluid: " + EnumChatFormatting.AQUA + fluid.getLocalizedName() + EnumChatFormatting.RESET);
                currenttip.add(
                    "Amount: " + EnumChatFormatting.GREEN
                        + GTUtility.formatNumbers(fluid.amount)
                        + "/"
                        + GTUtility.formatNumbers(network.getMaxCapacity())
                        + " L"
                        + EnumChatFormatting.RESET);
            } else {
                currenttip.add("Empty");
            }
            currenttip.add("Network Members: " + network.getMemberCount());
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

    /**
     * Rebuilds the network by traversing connected pipes and hatches.
     */
    public void rebuildNetwork() {
        if (getBaseMetaTileEntity() == null || !getBaseMetaTileEntity().isServerSide()) {
            return;
        }

        // Create new network and traverse to find all connected members
        Set<IIntegratedFluidMember> visited = new HashSet<>();
        List<IIntegratedFluidMember> toVisit = new ArrayList<>();
        toVisit.add(this);

        IntegratedFluidNetwork newNetwork = new IntegratedFluidNetwork();

        // If we had a network with fluid, preserve it
        if (network != null && network.getStoredFluid() != null) {
            newNetwork.addFluid(network.getStoredFluid(), false);
        }

        while (!toVisit.isEmpty()) {
            IIntegratedFluidMember current = toVisit.remove(0);
            if (visited.contains(current)) {
                continue;
            }
            visited.add(current);

            // If this member had a network with fluid and our new network is empty, take its fluid
            if (current.getNetwork() != null && current.getNetwork() != newNetwork
                && current.getNetwork()
                    .getStoredFluid() != null
                && newNetwork.getStoredFluid() == null) {
                newNetwork.addFluid(
                    current.getNetwork()
                        .getStoredFluid(),
                    false);
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

        // Notify all members of the update
        for (IIntegratedFluidMember member : visited) {
            member.onNetworkUpdate();
        }
    }

    @Override
    public void disconnect(ForgeDirection side) {
        super.disconnect(side);
        // Trigger network rebuild on both sides
        rebuildNetwork();
    }
}
