package gregtech.common.covers;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import org.jetbrains.annotations.NotNull;

import com.google.common.io.ByteArrayDataInput;

import gregtech.api.covers.CoverContext;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.ICoverable;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.implementations.integratedfluid.IIntegratedFluidMember;
import gregtech.api.metatileentity.implementations.integratedfluid.IntegratedFluidNetwork;
import gregtech.api.metatileentity.implementations.integratedfluid.covers.IFNDetectorCoverLogic;
import gregtech.common.gui.modularui.cover.CoverIFNDetectorGui;
import gregtech.common.gui.modularui.cover.base.CoverBaseGui;
import io.netty.buffer.ByteBuf;

public abstract class CoverIFNDetectorBase extends Cover {

    private double minValue;
    private double maxValue;
    private IFNDetectorCoverLogic.Mode mode = IFNDetectorCoverLogic.Mode.BINARY;

    protected CoverIFNDetectorBase(CoverContext context, ITexture coverTexture, double minValue, double maxValue) {
        super(context, coverTexture);
        this.minValue = minValue;
        this.maxValue = maxValue;
    }

    public CoverIFNDetectorBase setRange(double minValue, double maxValue) {
        this.minValue = minValue;
        this.maxValue = maxValue;
        return this;
    }

    public double getMinValue() {
        return minValue;
    }

    public CoverIFNDetectorBase setMinValue(double minValue) {
        this.minValue = minValue;
        return this;
    }

    public double getMaxValue() {
        return maxValue;
    }

    public CoverIFNDetectorBase setMaxValue(double maxValue) {
        this.maxValue = maxValue;
        return this;
    }

    public IFNDetectorCoverLogic.Mode getMode() {
        return mode;
    }

    public CoverIFNDetectorBase setMode(IFNDetectorCoverLogic.Mode mode) {
        this.mode = mode == null ? IFNDetectorCoverLogic.Mode.BINARY : mode;
        return this;
    }

    public boolean isLinearMode() {
        return mode == IFNDetectorCoverLogic.Mode.LINEAR;
    }

    public CoverIFNDetectorBase setLinearMode(boolean linearMode) {
        return setMode(linearMode ? IFNDetectorCoverLogic.Mode.LINEAR : IFNDetectorCoverLogic.Mode.BINARY);
    }

    @Override
    public void doCoverThings(byte aInputRedstone, long aTimer) {
        ICoverable coverable = coveredTile.get();
        if (coverable != null) {
            coverable.setOutputRedstoneSignal(coverSide, computeSignal(coverable));
        }
    }

    protected byte computeSignal(ICoverable coverable) {
        IntegratedFluidNetwork network = resolveNetwork(coverable);
        if (network == null) {
            return 0;
        }
        return (byte) IFNDetectorCoverLogic.evaluate(readValue(network), minValue, maxValue, mode);
    }

    private static IntegratedFluidNetwork resolveNetwork(ICoverable coverable) {
        if (!(coverable instanceof BaseMetaTileEntity)) {
            return null;
        }
        IMetaTileEntity metaTileEntity = ((BaseMetaTileEntity) coverable).getMetaTileEntity();
        if (metaTileEntity instanceof IIntegratedFluidMember) {
            return ((IIntegratedFluidMember) metaTileEntity).getNetwork();
        }
        return null;
    }

    protected abstract double readValue(IntegratedFluidNetwork network);

    @Override
    protected void readDataFromNbt(NBTBase nbt) {
        NBTTagCompound tag = (NBTTagCompound) nbt;
        minValue = tag.getDouble("minValue");
        maxValue = tag.getDouble("maxValue");
        int modeOrdinal = tag.getInteger("mode");
        IFNDetectorCoverLogic.Mode[] modes = IFNDetectorCoverLogic.Mode.values();
        mode = modeOrdinal >= 0 && modeOrdinal < modes.length ? modes[modeOrdinal] : IFNDetectorCoverLogic.Mode.BINARY;
    }

    @Override
    public void readDataFromPacket(ByteArrayDataInput byteData) {
        minValue = byteData.readDouble();
        maxValue = byteData.readDouble();
        int modeOrdinal = byteData.readInt();
        IFNDetectorCoverLogic.Mode[] modes = IFNDetectorCoverLogic.Mode.values();
        mode = modeOrdinal >= 0 && modeOrdinal < modes.length ? modes[modeOrdinal] : IFNDetectorCoverLogic.Mode.BINARY;
    }

    @Override
    protected @NotNull NBTBase saveDataToNbt() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setDouble("minValue", minValue);
        tag.setDouble("maxValue", maxValue);
        tag.setInteger("mode", mode.ordinal());
        return tag;
    }

    @Override
    protected void writeDataToByteBuf(ByteBuf byteBuf) {
        byteBuf.writeDouble(minValue);
        byteBuf.writeDouble(maxValue);
        byteBuf.writeInt(mode.ordinal());
    }

    @Override
    public boolean manipulatesSidedRedstoneOutput() {
        return true;
    }

    @Override
    public boolean hasCoverGUI() {
        return true;
    }

    @Override
    protected CoverBaseGui<? extends CoverIFNDetectorBase> getCoverGui() {
        return new CoverIFNDetectorGui(this);
    }

    @Override
    public int getMinimumTickRate() {
        return 1;
    }

    @Override
    public int getDefaultTickRate() {
        return 5;
    }
}
