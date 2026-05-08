package gregtech.api.metatileentity.implementations.integratedfluid;

import static com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil.formatNumber;
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
import gregtech.api.metatileentity.implementations.integratedfluid.FluidThermalProperties;
import gregtech.api.metatileentity.implementations.integratedfluid.IFNAmbientTemperature;
import gregtech.api.metatileentity.implementations.integratedfluid.IntegratedFluidThermoModel;
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

    static final float MAX_INJECTOR_NETWORK_PRESSURE_BAR = IFNPressurePolicy.INJECTOR_TARGET_PRESSURE_BAR;
    public enum InjectMode {
        INJECT_CONSERVED_AMOUNT,
        INJECT_REAL_PIPE_VOLUME
    }

    private IntegratedFluidNetwork network;
    private java.util.UUID networkId;
    private InjectMode injectMode = InjectMode.INJECT_REAL_PIPE_VOLUME;
    private double amountRemainderMb = 0.0d;

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
        if (networkId != null) {
            aNBT.setLong("ifnNetworkIdMost", networkId.getMostSignificantBits());
            aNBT.setLong("ifnNetworkIdLeast", networkId.getLeastSignificantBits());
        }
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
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
            tag.setInteger("accumulatorCapacity", network.getAccumulatorCapacity());
            tag.setInteger("totalCapacity", network.getTotalCapacity());
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
            int maxCapacity = tag.getInteger("maxCapacity");
            int accumulatorCapacity = tag.getInteger("accumulatorCapacity");
            int totalCapacity = tag.hasKey("totalCapacity")
                ? tag.getInteger("totalCapacity")
                : maxCapacity + accumulatorCapacity;
            if (tag.hasKey("networkFluid")) {
                FluidStack fluid = FluidStack.loadFluidStackFromNBT(tag.getCompoundTag("networkFluid"));
                if (fluid != null) {
                    currenttip.add(
                        "Network Fluid: " + EnumChatFormatting.AQUA
                            + fluid.getLocalizedName()
                            + EnumChatFormatting.RESET);
                    currenttip.add(
                        "Amount: " + EnumChatFormatting.GREEN
                            + formatNumber(fluid.amount)
                            + "/"
                            + formatNumber(totalCapacity)
                            + " L"
                            + EnumChatFormatting.RESET);
                    if (accumulatorCapacity > 0) {
                        currenttip.add(
                            EnumChatFormatting.GRAY + "(+"
                                + formatNumber(accumulatorCapacity)
                                + " Hydrophore capacity)" + EnumChatFormatting.RESET);
                    }
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
            // Add warning if pressure is above 1 bar (injector won't inject above 1 bar)
            if (pressure > 1.01f) {
                currenttip.add(
                    EnumChatFormatting.RED + "WARNING: Injector only works up to 1.00 bar!" + EnumChatFormatting.RESET);
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
        // Trigger visual updates
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
            NetworkManager manager = NetworkManager.getInstance(getBaseMetaTileEntity().getWorld());
            manager.onMemberAdded(this);
        }
        if (network != null && resource != null) {
            if (network.getPressure() > IFNPressurePolicy.injectorCutoffPressureBar()) {
                return 0;
            }
            if (resource.amount <= 0 || resource.getFluid() == null) {
                return 0;
            }

            double pInBar = 1.0d;
            float ambientK = IFNAmbientTemperature.getAmbientTemperature(getBaseMetaTileEntity().getWorld());
            double hSpecIn = FluidThermalProperties.getSpecificEnthalpyFromPT(
                resource.getFluid(),
                pInBar,
                ambientK
            );
            double vFactor = IntegratedFluidThermoModel
                .specificVolumeFromPressureAndSpecificEnthalpy(resource.getFluid(), (float) pInBar, hSpecIn);
            if (vFactor <= 0.0d) {
                return 0;
            }

            double vRealMb = resource.amount;
            double baseExact = injectMode == InjectMode.INJECT_REAL_PIPE_VOLUME
                ? (vRealMb / vFactor)
                : vRealMb;
            double exact = baseExact + amountRemainderMb;
            long addAmountMb = (long) Math.floor(exact);
            double nextRemainder = exact - addAmountMb;
            if (addAmountMb <= 0L) {
                if (doFill) {
                    amountRemainderMb = nextRemainder;
                }
                return 0;
            }

            long maxCandidateAmountQ = toAmountQ(addAmountMb);
            long acceptedAmountQ = network.getMaxAddableAmountQ(resource.getFluid(), hSpecIn, maxCandidateAmountQ);
            acceptedAmountQ = clampAcceptedPhysicalAddToInjectorPressure(network, resource.getFluid(), hSpecIn, acceptedAmountQ);
            long acceptedAmountMb = acceptedAmountQ / IntegratedFluidNetwork.AMOUNT_SCALE;
            if (acceptedAmountMb <= 0L) {
                if (doFill) {
                    amountRemainderMb = nextRemainder;
                }
                return 0;
            }

            if (doFill) {
                long acceptedWholeAmountQ = toAmountQ(acceptedAmountMb);
                long acceptedEnthalpyQ = IntegratedFluidNetwork.toEnthalpyQFromSpecific(hSpecIn, acceptedWholeAmountQ);
                network.add(resource.getFluid(), acceptedWholeAmountQ, acceptedEnthalpyQ);
                if (acceptedAmountMb < addAmountMb) {
                    amountRemainderMb = nextRemainder + (addAmountMb - acceptedAmountMb);
                } else {
                    amountRemainderMb = nextRemainder;
                }
            }

            if (injectMode == InjectMode.INJECT_REAL_PIPE_VOLUME) {
                long realAccepted = (long) Math.floor(acceptedAmountMb * vFactor);
                if (realAccepted <= 0L) {
                    return 0;
                }
                return (int) Math.min(resource.amount, realAccepted);
            }

            return (int) Math.min(resource.amount, acceptedAmountMb);
        }
        return 0;
    }

    static long clampAcceptedPhysicalAddToInjectorPressure(IntegratedFluidNetwork network, net.minecraftforge.fluids.Fluid fluid,
        double incomingSpecificEnthalpy, long maxAcceptedAmountQ) {
        if (network == null || fluid == null || maxAcceptedAmountQ < IntegratedFluidNetwork.AMOUNT_SCALE) {
            return 0L;
        }

        long low = 0L;
        long high = maxAcceptedAmountQ;
        long best = 0L;

        for (int i = 0; i < IFNPressurePolicy.TRANSFER_SEARCH_ITERATIONS; i++) {
            if (low > high) {
                break;
            }

            long mid = (low + high) / 2L;
            if (mid < IntegratedFluidNetwork.AMOUNT_SCALE) {
                low = mid + 1L;
                continue;
            }

            long midEnthalpyQ = IntegratedFluidNetwork.toEnthalpyQFromSpecific(incomingSpecificEnthalpy, mid);
            float predictedPressure = network.predictPressureAfterAdd(fluid, mid, midEnthalpyQ);
            if (predictedPressure <= IFNPressurePolicy.injectorCutoffPressureBar()) {
                best = mid;
                low = mid + 1L;
            } else {
                high = mid - 1L;
            }
        }

        return best;
    }

    public void setInjectMode(InjectMode mode) {
        if (mode != null) {
            this.injectMode = mode;
        }
    }

    private static long toAmountQ(long amountMb) {
        return amountMb * IntegratedFluidNetwork.AMOUNT_SCALE;
    }

    // ...existing drain methods...

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

}
