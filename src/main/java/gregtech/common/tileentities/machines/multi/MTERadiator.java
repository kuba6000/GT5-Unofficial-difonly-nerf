package gregtech.common.tileentities.machines.multi;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.*;
import static gregtech.api.enums.HatchElement.*;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;
import static gregtech.api.util.GTStructureUtility.ofHatchAdder;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.NotNull;

import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;

import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.metatileentity.implementations.integratedfluid.FluidThermalProperties;
import gregtech.api.metatileentity.implementations.integratedfluid.IFNMachineResultMapper;
import gregtech.api.metatileentity.implementations.integratedfluid.IFNStateMutationApplier;
import gregtech.api.metatileentity.implementations.integratedfluid.IFNStateTransferPlanner;
import gregtech.api.metatileentity.implementations.integratedfluid.IntegratedFluidNetwork;
import gregtech.api.metatileentity.implementations.integratedfluid.MTEIntegratedFluidInputHatch;
import gregtech.api.metatileentity.implementations.integratedfluid.MTEIntegratedFluidOutputHatch;
import gregtech.api.modularui2.GTGuiTextures;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.recipe.check.SimpleCheckRecipeResult;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.blocks.BlockCasings2;
import gregtech.common.gui.modularui.multiblock.MTERadiatorGui;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;
import gregtech.common.tileentities.machines.multi.radiator.RadiatorLoopAnalyzer;
import gregtech.common.tileentities.machines.multi.radiator.RadiatorLoopSnapshot;
import gregtech.common.tileentities.machines.multi.radiator.RadiatorPowerPolicy;
import gregtech.common.tileentities.machines.multi.radiator.RadiatorThermo;

public class MTERadiator extends MTEEnhancedMultiBlockBase<MTERadiator> implements ISurvivalConstructable {

    private static final String STRUCTURE_PIECE_MAIN = "main";
    private static final int HEAT_CAPACITY_PER_OPERATION = 1000;
    private static final int MODE_CONSTANT_TIME = 0;
    private static final int MODE_TARGET_TEMPERATURE = 1;
    private static final int DEFAULT_CONSTANT_OPERATION_TICKS = 20;
    private static final int MIN_CONSTANT_OPERATION_TICKS = 20;
    private static final int MAX_CONSTANT_OPERATION_TICKS = 20 * 60;
    private static final float DEFAULT_TARGET_TEMPERATURE = 310.0f;
    private static final float TARGET_TEMPERATURE_STEP = 5.0f;
    private static final float MIN_TARGET_TEMPERATURE = 1.0f;
    private static final float MAX_TARGET_TEMPERATURE = 5000.0f;

    private final List<MTEIntegratedFluidInputHatch> mIntegratedInputHatches = new ArrayList<>();
    private final List<MTEIntegratedFluidOutputHatch> mIntegratedOutputHatches = new ArrayList<>();

    private static final IStructureDefinition<MTERadiator> STRUCTURE_DEFINITION = StructureDefinition
        .<MTERadiator>builder()
        .addShape(
            STRUCTURE_PIECE_MAIN,
            transpose(
                new String[][] {
                    { "CCC", "CCC", "CCC" },
                    { "C~C", "CCC", "CCC" },
                    { "CCC", "CCC", "CCC" }
                }))
        .addElement(
            'C',
            ofChain(
                buildHatchAdder(MTERadiator.class)
                    .atLeast(Energy, Maintenance)
                    .casingIndex(((BlockCasings2) GregTechAPI.sBlockCasings2).getTextureIndex(0))
                    .hint(1)
                    .buildAndChain(onElementPass(x -> ++((MTERadiator) x).mCasingAmount, ofBlock(GregTechAPI.sBlockCasings2, 0))),
                ofHatchAdder(
                    MTERadiator::addIntegratedInputHatch,
                    ((BlockCasings2) GregTechAPI.sBlockCasings2).getTextureIndex(0),
                    1
                ),
                ofHatchAdder(
                    MTERadiator::addIntegratedOutputHatch,
                    ((BlockCasings2) GregTechAPI.sBlockCasings2).getTextureIndex(0),
                    1
                ),
                onElementPass(
                    x -> {
                        ++((MTERadiator) x).mCasingAmount;
                        ++((MTERadiator) x).radiatorPortCount;
                    },
                    ofBlock(GregTechAPI.sBlockCasings11, RadiatorLoopAnalyzer.PORT_META))))
        .build();

    private int mCasingAmount;
    private int radiatorPortCount;
    private int constantOperationTicks = DEFAULT_CONSTANT_OPERATION_TICKS;
    private float targetTemperature = DEFAULT_TARGET_TEMPERATURE;
    private RadiatorLoopSnapshot lastLoopSnapshot = RadiatorLoopSnapshot.invalid("radiator_loop_missing");
    private float lastPredictedOutputTemperature = RadiatorThermo.AMBIENT_TEMPERATURE;
    private float lastLoopOutletPressure = IntegratedFluidNetwork.DEFAULT_PRESSURE;

    public MTERadiator(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTERadiator(String aName) {
        super(aName);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTERadiator(this.mName);
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType("Radiator")
            .addInfo("Moves fluid temperature toward ambient using an external loop")
            .addInfo("Requires 2 Radiator Loop Ports inside the multiblock shell")
            .addInfo("Connect the ports with Radiator Loop Pipe blocks")
            .addInfo("Loop Pipe adds heat transfer, but also loses 0.01 bar per segment")
            .addInfo("Heat Exchange Modules improve transfer directly")
            .addInfo("Conduction Modules extend Heat Exchange Modules without lengthening the loop")
            .addInfo("Constant Time mode: fixed duration, variable output temperature")
            .addInfo("Target Temperature mode: fixed output temperature, variable duration")
            .addSeparator()
            .beginStructureBlock(3, 3, 3, true)
            .addController("Front center")
            .addCasingInfoMin("Solid Steel Machine Casing / Radiator Loop Port", 18, false)
            .addInputHatch("Any casing (Integrated Fluid type)", 1)
            .addOutputHatch("Any casing (Integrated Fluid type)", 1)
            .addEnergyHatch("Any casing", 1)
            .addMaintenanceHatch("Any casing", 1)
            .toolTipFinisher("GregTech");
        return tt;
    }

    @Override
    public IStructureDefinition<MTERadiator> getStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity baseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int colorIndex, boolean active, boolean redstoneLevel) {
        if (side == facing) {
            return new ITexture[] {
                TextureFactory.of(GregTechAPI.sBlockCasings2, 0),
                TextureFactory.of(active
                    ? gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_VACUUM_FREEZER_ACTIVE
                    : gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_VACUUM_FREEZER)
            };
        }
        return new ITexture[] { TextureFactory.of(GregTechAPI.sBlockCasings2, 0) };
    }

    @Override
    public boolean checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack) {
        mCasingAmount = 0;
        radiatorPortCount = 0;
        mIntegratedInputHatches.clear();
        mIntegratedOutputHatches.clear();

        boolean valid = checkPiece(STRUCTURE_PIECE_MAIN, 1, 1, 0)
            && mCasingAmount >= 16
            && radiatorPortCount == 2
            && !mIntegratedInputHatches.isEmpty()
            && !mIntegratedOutputHatches.isEmpty()
            && !mEnergyHatches.isEmpty()
            && !mMaintenanceHatches.isEmpty();

        if (valid) {
            refreshLoopSnapshot();
        } else {
            lastLoopSnapshot = RadiatorLoopSnapshot.invalid("radiator_loop_missing");
        }
        return valid;
    }

    @Override
    public @NotNull CheckRecipeResult checkProcessing() {
        if (mIntegratedInputHatches.isEmpty() || mIntegratedOutputHatches.isEmpty()) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        MTEIntegratedFluidInputHatch inputHatch = mIntegratedInputHatches.get(0);
        MTEIntegratedFluidOutputHatch outputHatch = mIntegratedOutputHatches.get(0);
        IntegratedFluidNetwork inputNetwork = inputHatch.getNetwork();
        IntegratedFluidNetwork outputNetwork = outputHatch.getNetwork();
        CheckRecipeResult networkStatus = IFNMachineResultMapper.requireOperationalNetworks(
            new IntegratedFluidNetwork[] { inputNetwork },
            new IntegratedFluidNetwork[] { outputNetwork });
        if (networkStatus != CheckRecipeResultRegistry.SUCCESSFUL) {
            return networkStatus;
        }

        FluidStack inputFluid = inputNetwork.getStoredFluid();
        if (inputFluid == null || inputFluid.amount <= 0) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        refreshLoopSnapshot();
        if (!lastLoopSnapshot.valid) {
            return SimpleCheckRecipeResult.ofFailure("awaiting_configuration");
        }

        float inputTemperature = inputNetwork.getTemperature();
        if (inputTemperature <= 0.0f) {
            inputTemperature = RadiatorThermo.AMBIENT_TEMPERATURE;
        }

        int fluidToProcess = Math.min(inputFluid.amount, HEAT_CAPACITY_PER_OPERATION);
        if (fluidToProcess <= 0) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        int originalFluidToProcess = fluidToProcess;
        Fluid fluid = inputFluid.getFluid();
        if (fluid == null) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        FluidStack fluidForCalculation = inputFluid.copy();
        fluidForCalculation.amount = fluidToProcess;

        float loopOutletPressure = RadiatorThermo.computeLoopOutletPressure(inputNetwork.getPressure(), lastLoopSnapshot);
        double outputTemperature = inputTemperature;
        int processTicks;

        if (machineMode == MODE_TARGET_TEMPERATURE) {
            if (!RadiatorThermo.movesTowardAmbient(inputTemperature, RadiatorThermo.AMBIENT_TEMPERATURE, targetTemperature)) {
                return SimpleCheckRecipeResult.ofFailure("awaiting_configuration");
            }

            processTicks = RadiatorThermo.computeRequiredProcessTicks(
                lastLoopSnapshot,
                fluidForCalculation,
                inputTemperature,
                RadiatorThermo.AMBIENT_TEMPERATURE,
                targetTemperature
            );
            if (processTicks <= 0) {
                return CheckRecipeResultRegistry.NO_RECIPE;
            }
            outputTemperature = targetTemperature;
        } else {
            processTicks = constantOperationTicks;
            outputTemperature = RadiatorThermo.computeOutputTemperatureForFixedTime(
                lastLoopSnapshot,
                fluidForCalculation,
                inputTemperature,
                RadiatorThermo.AMBIENT_TEMPERATURE,
                processTicks
            );
        }

        double outputSpecificEnthalpy = FluidThermalProperties.getSpecificEnthalpyFromPT(
            fluid,
            loopOutletPressure,
            outputTemperature
        );

        if (outputNetwork != inputNetwork) {
            long requestedAmountQ = fluidToProcess * IntegratedFluidNetwork.AMOUNT_SCALE;
            var plan = IFNStateTransferPlanner.planSingleOutputStateAdd(
                inputNetwork,
                outputNetwork,
                fluid,
                outputSpecificEnthalpy,
                requestedAmountQ,
                1.0f,
                lastLoopSnapshot.pressureDropBar
            );
            if (plan.acceptedAmountQ < IntegratedFluidNetwork.AMOUNT_SCALE) {
                return IFNMachineResultMapper.toRecipeResult(plan.status);
            }
            fluidToProcess = toAmountMb(plan.acceptedAmountQ);
        }

        if (fluidToProcess <= 0) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        if (fluidToProcess != originalFluidToProcess) {
            fluidForCalculation.amount = fluidToProcess;
            if (machineMode == MODE_TARGET_TEMPERATURE) {
                processTicks = RadiatorThermo.computeRequiredProcessTicks(
                    lastLoopSnapshot,
                    fluidForCalculation,
                    inputTemperature,
                    RadiatorThermo.AMBIENT_TEMPERATURE,
                    targetTemperature
                );
                outputTemperature = targetTemperature;
            } else {
                outputTemperature = RadiatorThermo.computeOutputTemperatureForFixedTime(
                    lastLoopSnapshot,
                    fluidForCalculation,
                    inputTemperature,
                    RadiatorThermo.AMBIENT_TEMPERATURE,
                    processTicks
                );
            }

            outputSpecificEnthalpy = FluidThermalProperties.getSpecificEnthalpyFromPT(
                fluid,
                loopOutletPressure,
                outputTemperature
            );
        }

        double rollbackSpecificEnthalpy = inputNetwork.getSpecificEnthalpy();
        FluidStack drainedFluid = inputNetwork.drainFluid(fluidToProcess, false);
        if (drainedFluid == null || drainedFluid.amount <= 0) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        long drainedAmountQ = drainedFluid.amount * IntegratedFluidNetwork.AMOUNT_SCALE;
        long outputEnthalpyQ = IntegratedFluidNetwork.toEnthalpyQFromSpecific(outputSpecificEnthalpy, drainedAmountQ);
        long rollbackEnthalpyQ = IntegratedFluidNetwork.toEnthalpyQFromSpecific(rollbackSpecificEnthalpy, drainedAmountQ);
        if (!IFNStateMutationApplier.addOutputOrRestoreInput(
            inputNetwork,
            outputNetwork,
            fluid,
            IntegratedFluidNetwork.ExtractedPayload.of(drainedAmountQ, rollbackEnthalpyQ),
            outputEnthalpyQ)) {
            return CheckRecipeResultRegistry.ITEM_OUTPUT_FULL;
        }

        lastPredictedOutputTemperature = (float) outputTemperature;
        lastLoopOutletPressure = loopOutletPressure;
        this.mMaxProgresstime = Math.max(1, processTicks);
        this.mEfficiency = 10000;
        this.mEUt = RadiatorPowerPolicy.computeEUt(0L, this.mMaxProgresstime);

        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setInteger("radiatorMode", machineMode);
        aNBT.setInteger("radiatorConstantTicks", constantOperationTicks);
        aNBT.setFloat("radiatorTargetTemperature", targetTemperature);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        if (aNBT.hasKey("radiatorMode")) {
            machineMode = aNBT.getInteger("radiatorMode");
        }
        if (aNBT.hasKey("radiatorConstantTicks")) {
            constantOperationTicks = clampConstantTicks(aNBT.getInteger("radiatorConstantTicks"));
        }
        if (aNBT.hasKey("radiatorTargetTemperature")) {
            targetTemperature = clampTargetTemperature(aNBT.getFloat("radiatorTargetTemperature"));
        }
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        buildPiece(STRUCTURE_PIECE_MAIN, stackSize, hintsOnly, 1, 1, 0);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        return survivialBuildPiece(STRUCTURE_PIECE_MAIN, stackSize, 1, 1, 0, elementBudget, env, false, true);
    }

    @Override
    protected boolean useMui2() {
        return true;
    }

    @Override
    protected @NotNull MTEMultiBlockBaseGui<?> getGui() {
        return new MTERadiatorGui(this).withMachineModeIcons(
            GTGuiTextures.OVERLAY_BUTTON_MACHINEMODE_DEFAULT,
            GTGuiTextures.OVERLAY_BUTTON_MACHINEMODE_SIMPLEWASHER
        );
    }

    @Override
    public boolean supportsMachineModeSwitch() {
        return true;
    }

    @Override
    public String getMachineModeKey() {
        return "GT5U.RADIATOR.mode." + machineMode;
    }

    @Override
    public void onScrewdriverRightClick(ForgeDirection side, EntityPlayer aPlayer, float aX, float aY, float aZ,
        ItemStack aTool) {
        setMachineMode(nextMachineMode());
        GTUtility.sendChatTrans(aPlayer, "GT5U.MULTI_MACHINE_CHANGE", new ChatComponentTranslation(getMachineModeKey()));
    }

    @Override
    public boolean onWireCutterRightClick(ForgeDirection side, ForgeDirection wrenchingSide, EntityPlayer aPlayer,
        float aX, float aY, float aZ, ItemStack aTool) {
        if (machineMode == MODE_TARGET_TEMPERATURE) {
            targetTemperature = clampTargetTemperature(
                targetTemperature + (aPlayer.isSneaking() ? -TARGET_TEMPERATURE_STEP : TARGET_TEMPERATURE_STEP)
            );
            GTUtility.sendChatToPlayer(aPlayer, "Radiator target temperature: " + String.format("%.1f K", targetTemperature));
            return true;
        }

        constantOperationTicks = clampConstantTicks(
            constantOperationTicks + (aPlayer.isSneaking() ? -MIN_CONSTANT_OPERATION_TICKS : MIN_CONSTANT_OPERATION_TICKS)
        );
        GTUtility.sendChatToPlayer(aPlayer, "Radiator constant time: " + constantOperationTicks + " ticks");
        return true;
    }

    public float getInputTemperature() {
        if (mIntegratedInputHatches.isEmpty()) return 0.0f;
        var network = mIntegratedInputHatches.get(0).getNetwork();
        return network != null ? network.getTemperature() : 0.0f;
    }

    public float getOutputTemperature() {
        return lastPredictedOutputTemperature;
    }

    public int getInputNetworkCapacity() {
        if (mIntegratedInputHatches.isEmpty()) return 0;
        var network = mIntegratedInputHatches.get(0).getNetwork();
        return network != null ? network.getMaxCapacity() : 0;
    }

    public int getInputNetworkStored() {
        if (mIntegratedInputHatches.isEmpty()) return 0;
        var network = mIntegratedInputHatches.get(0).getNetwork();
        if (network == null) return 0;
        var fluid = network.getStoredFluid();
        return fluid != null ? fluid.amount : 0;
    }

    public int getOutputNetworkCapacity() {
        if (mIntegratedOutputHatches.isEmpty()) return 0;
        var network = mIntegratedOutputHatches.get(0).getNetwork();
        return network != null ? network.getMaxCapacity() : 0;
    }

    public int getOutputNetworkStored() {
        if (mIntegratedOutputHatches.isEmpty()) return 0;
        var network = mIntegratedOutputHatches.get(0).getNetwork();
        if (network == null) return 0;
        var fluid = network.getStoredFluid();
        return fluid != null ? fluid.amount : 0;
    }

    public String getFluidName() {
        if (mIntegratedInputHatches.isEmpty()) return "";
        var network = mIntegratedInputHatches.get(0).getNetwork();
        if (network == null) return "";
        var fluid = network.getStoredFluid();
        return fluid != null ? fluid.getLocalizedName() : "";
    }

    public int getLoopSegmentCount() {
        refreshLoopSnapshot();
        return lastLoopSnapshot != null ? lastLoopSnapshot.segmentCount : 0;
    }

    public int getLoopConductionModuleCount() {
        refreshLoopSnapshot();
        return lastLoopSnapshot != null ? lastLoopSnapshot.conductionModuleCount : 0;
    }

    public int getLoopHeatExchangeModuleCount() {
        refreshLoopSnapshot();
        return lastLoopSnapshot != null ? lastLoopSnapshot.heatExchangeModuleCount : 0;
    }

    public float getLoopPressureDropBar() {
        refreshLoopSnapshot();
        return lastLoopSnapshot != null ? lastLoopSnapshot.pressureDropBar : 0.0f;
    }

    public float getLoopOutletPressure() {
        return lastLoopOutletPressure;
    }

    public int getConstantOperationTicks() {
        return constantOperationTicks;
    }

    public void setConstantOperationTicks(int ticks) {
        constantOperationTicks = clampConstantTicks(ticks);
    }

    public float getTargetTemperatureSetting() {
        return targetTemperature;
    }

    public void setTargetTemperatureSetting(float temperature) {
        targetTemperature = clampTargetTemperature(temperature);
    }

    public String getMachineModeName() {
        return machineMode == MODE_TARGET_TEMPERATURE ? "Target Temperature" : "Constant Time";
    }

    public String getLoopStatus() {
        refreshLoopSnapshot();
        String statusKey = lastLoopSnapshot != null ? lastLoopSnapshot.statusKey : "radiator_loop_missing";
        return switch (statusKey) {
            case "ok" -> "Complete";
            case "radiator_loop_ports" -> "Port layout invalid";
            case "radiator_loop_missing" -> "Loop pipe missing";
            case "radiator_loop_invalid" -> "Loop path invalid";
            default -> "Not ready";
        };
    }

    public boolean addIntegratedInputHatch(IGregTechTileEntity baseMetaTileEntity, Short color) {
        IMetaTileEntity mte = baseMetaTileEntity.getMetaTileEntity();
        if (mte instanceof MTEIntegratedFluidInputHatch hatch) {
            hatch.updateTexture(((BlockCasings2) GregTechAPI.sBlockCasings2).getTextureIndex(0));
            return mIntegratedInputHatches.add(hatch);
        }
        return false;
    }

    public boolean addIntegratedOutputHatch(IGregTechTileEntity baseMetaTileEntity, Short color) {
        IMetaTileEntity mte = baseMetaTileEntity.getMetaTileEntity();
        if (mte instanceof MTEIntegratedFluidOutputHatch hatch) {
            hatch.updateTexture(((BlockCasings2) GregTechAPI.sBlockCasings2).getTextureIndex(0));
            return mIntegratedOutputHatches.add(hatch);
        }
        return false;
    }

    private static int clampConstantTicks(int ticks) {
        return Math.max(MIN_CONSTANT_OPERATION_TICKS, Math.min(MAX_CONSTANT_OPERATION_TICKS, ticks));
    }

    private static float clampTargetTemperature(float temperature) {
        return Math.max(MIN_TARGET_TEMPERATURE, Math.min(MAX_TARGET_TEMPERATURE, temperature));
    }

    private static int toAmountMb(long amountQ) {
        return (int) Math.min(Integer.MAX_VALUE, amountQ / IntegratedFluidNetwork.AMOUNT_SCALE);
    }

    private void refreshLoopSnapshot() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null || base.getWorld() == null) {
            lastLoopSnapshot = RadiatorLoopSnapshot.invalid("radiator_loop_missing");
            return;
        }
        lastLoopSnapshot = RadiatorLoopAnalyzer.analyze(
            base.getWorld(),
            base.getXCoord(),
            base.getYCoord(),
            base.getZCoord(),
            base.getFrontFacing()
        );
    }
}
