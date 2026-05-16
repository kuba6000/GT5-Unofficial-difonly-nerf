package gregtech.common.tileentities.machines.multi;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.*;
import static gregtech.api.enums.HatchElement.*;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;
import static gregtech.api.util.GTStructureUtility.ofHatchAdder;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;

import org.jetbrains.annotations.NotNull;

import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.metatileentity.implementations.integratedfluid.FluidThermalProperties;
import gregtech.api.metatileentity.implementations.integratedfluid.IFNMachineResultMapper;
import gregtech.api.metatileentity.implementations.integratedfluid.IFNMachineThermo;
import gregtech.api.metatileentity.implementations.integratedfluid.IFNPressurePolicy;
import gregtech.api.metatileentity.implementations.integratedfluid.IFNSingleOutputProcess;
import gregtech.api.metatileentity.implementations.integratedfluid.IntegratedFluidNetwork;
import gregtech.api.metatileentity.implementations.integratedfluid.IntegratedFluidThermoModel;
import gregtech.api.metatileentity.implementations.integratedfluid.IFNStateTransferPlanner;
import gregtech.api.metatileentity.implementations.integratedfluid.MTEIntegratedFluidInputHatch;
import gregtech.api.metatileentity.implementations.integratedfluid.MTEIntegratedFluidOutputHatch;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.recipe.check.SimpleCheckRecipeResult;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.blocks.BlockCasings2;
import gregtech.common.gui.modularui.multiblock.MTEHeatPumpGui;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

public class MTEHeatPump extends MTEEnhancedMultiBlockBase<MTEHeatPump> implements ISurvivalConstructable {

    private static final String STRUCTURE_PIECE_MAIN = "main";
    private static final int MAX_FLUID_PER_OPERATION = 1000; // Maximum fluid amount per operation (in mB)
    private static final float COLD_RESERVOIR_TEMPERATURE = 300.0f; // Ambient temperature for COP calculation
    private static final float DEFAULT_TARGET_TEMPERATURE = 310.0f; // Default target output temperature (310K)
    private static final float DEFAULT_TARGET_COP = 5.0f; // Default COP target
    private static final int DEFAULT_TARGET_ENERGY_PER_TICK = 100; // Default 100 EU/t

    // Custom hatch lists for Integrated Fluid Hatches
    private final List<MTEIntegratedFluidInputHatch> mIntegratedInputHatches = new ArrayList<>();
    private final List<MTEIntegratedFluidOutputHatch> mIntegratedOutputHatches = new ArrayList<>();

    // Operating mode and targets
    private HeatPumpMode operatingMode = HeatPumpMode.TARGET_TEMPERATURE;
    private boolean heatExchangerMode = false; // Heat Exchanger Mode enabled/disabled
    private boolean configuringHotStream = true; // true = configuring hot stream, false = configuring cold stream
    private boolean splitFlowMode = false; // Split Flow Mode - divides input into 2 streams with temp differential
    private float splitRatio = 0.5f; // Ratio for split (0.1 to 0.9) - fraction that goes to hot/primary stream
    private boolean targetHeating = true; // Direction for COP/Energy modes (true = heating, false = cooling)

    // Cache for hatch validation (updated when structure changes or every 20 ticks)
    private boolean cachedHatchValidation = false;
    private long lastValidationCheck = 0L;

    private float targetTemperature = DEFAULT_TARGET_TEMPERATURE; // For TARGET_TEMPERATURE mode (absolute temperature in K)
    private float targetCOP = DEFAULT_TARGET_COP; // For TARGET_COP mode
    private int targetEnergyPerTick = DEFAULT_TARGET_ENERGY_PER_TICK; // For TARGET_ENERGY mode (EU per tick)
    private int fluidAmountPerOperation = MAX_FLUID_PER_OPERATION; // Amount of fluid to process per operation (in mB)
    private float lowerTemperatureTolerance = 0.5f; // Lower temperature tolerance for passthrough (heating threshold in K)
    private float upperTemperatureTolerance = 0.5f; // Upper temperature tolerance for passthrough (cooling threshold in K)

    // Current calculated values for GUI display
    private float currentCOP = 0.0f;
    private float currentOutputTemperature = 0.0f;
    private long currentEnergyUsage = 0L;
    private float currentTemperatureDelta = 0.0f;  // NEW: for efficiency warnings
    private float currentEfficiencyPenalty = 1.0f;  // NEW: shows if penalty is applied
    private float effectiveCOP = 0.0f; // Real COP including penalty (currentCOP / penalty)
    private int totalEnergyCost = 0; // Total energy cost per tick including penalty for GUI


    private static final IStructureDefinition<MTEHeatPump> STRUCTURE_DEFINITION = StructureDefinition
        .<MTEHeatPump>builder()
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
                // FIRST: Let buildHatchAdder capture Energy and Maintenance hatches
                buildHatchAdder(MTEHeatPump.class)
                    .atLeast(Energy, Maintenance)
                    .casingIndex(((BlockCasings2) GregTechAPI.sBlockCasings2).getTextureIndex(0))
                    .hint(1)
                    .buildAndChain(
                        onElementPass(
                            x -> ++((MTEHeatPump) x).mCasingAmount,
                            ofBlock(GregTechAPI.sBlockCasings2, 0)
                        )
                    ),
                // THEN: Accept any remaining GregTech machines (like Integrated Fluid Hatches)
                ofHatchAdder(
                    MTEHeatPump::addIntegratedInputHatch, ((BlockCasings2) GregTechAPI.sBlockCasings2).getTextureIndex(0), 1
                ),
                ofHatchAdder(
                    MTEHeatPump::addIntegratedOutputHatch, ((BlockCasings2) GregTechAPI.sBlockCasings2).getTextureIndex(0), 1
                )
            )
        )
        .build();

    private int mCasingAmount;

    public MTEHeatPump(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTEHeatPump(String aName) {
        super(aName);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEHeatPump(this.mName);
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType("Heat Pump")
            .addInfo("Heats fluid from Input Hatch to Output Hatch")
            .addInfo("3 Operating Modes (configurable via Settings button):")
            .addInfo("1) Target Temperature - Set output temperature")
            .addInfo("   COP and energy are calculated")
            .addInfo("2) Target COP - Set efficiency (Coefficient of Performance)")
            .addInfo("   Temperature and energy are calculated")
            .addInfo("3) Target Energy - Set energy consumption limit")
            .addInfo("   Temperature and COP are calculated")
            .addInfo("Processes up to 1000L per tick")
            .addInfo("Uses ideal Carnot COP formula")
            .addInfo("Requires Integrated Fluid Input and Output Hatches")
            .addSeparator()
            .beginStructureBlock(3, 3, 3, true)
            .addController("Front center")
            .addCasingInfoMin("Solid Steel Machine Casing", 18, false)
            .addInputHatch("Any casing (Integrated Fluid type)", 1)
            .addOutputHatch("Any casing (Integrated Fluid type)", 1)
            .addEnergyHatch("Any casing", 1)
            .addMaintenanceHatch("Any casing", 1)
            .toolTipFinisher("GregTech");
        return tt;
    }

    @Override
    public IStructureDefinition<MTEHeatPump> getStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity baseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int colorIndex, boolean active, boolean redstoneLevel) {
        if (side == facing) {
            if (active) {
                return new ITexture[] {
                    TextureFactory.of(GregTechAPI.sBlockCasings2, 0),
                    TextureFactory.of(Textures.BlockIcons.OVERLAY_FRONT_MULTI_SMELTER_ACTIVE)
                };
            }
            return new ITexture[] {
                TextureFactory.of(GregTechAPI.sBlockCasings2, 0),
                TextureFactory.of(Textures.BlockIcons.OVERLAY_FRONT_MULTI_SMELTER)
            };
        }
        return new ITexture[] { TextureFactory.of(GregTechAPI.sBlockCasings2, 0) };
    }


    @Override
    public boolean checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack) {
        mCasingAmount = 0;
        mIntegratedInputHatches.clear();
        mIntegratedOutputHatches.clear();

        return checkPiece(STRUCTURE_PIECE_MAIN, 1, 1, 0)
            && mCasingAmount >= 18
            && !mIntegratedInputHatches.isEmpty()
            && !mIntegratedOutputHatches.isEmpty()
            && !mEnergyHatches.isEmpty()
            && !mMaintenanceHatches.isEmpty();
    }

    @Override
    public @NotNull CheckRecipeResult checkProcessing() {
        if (splitFlowMode) {
            return processSplitFlow();
        } else if (heatExchangerMode) {
            return processHeatExchanger();
        } else {
            return processNormalMode();
        }
    }

    private @NotNull CheckRecipeResult processSplitFlow() {
        if (!hasValidSplitFlowHatches()) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        // Validate configuration
        CheckRecipeResult configResult = validateConfiguration();
        if (configResult != CheckRecipeResultRegistry.SUCCESSFUL) {
            return configResult;
        }

        MTEIntegratedFluidInputHatch inputHatch = mIntegratedInputHatches.get(0);
        MTEIntegratedFluidOutputHatch redOutput = getOutputHatchByColor(1); // Red
        MTEIntegratedFluidOutputHatch blueOutput = getOutputHatchByColor(4); // Blue

        if (redOutput == null || blueOutput == null) return CheckRecipeResultRegistry.NO_RECIPE;

        var inputNetwork = inputHatch.getNetwork();
        var redNetwork = redOutput.getNetwork();
        var blueNetwork = blueOutput.getNetwork();

        if (inputNetwork == null || redNetwork == null || blueNetwork == null) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        Fluid inputFluid = inputNetwork.getFluid();
        if (inputFluid == null) return CheckRecipeResultRegistry.NO_RECIPE;

        long availableAmountQ = inputNetwork.getAmountQ();
        if (availableAmountQ <= 0) return CheckRecipeResultRegistry.NO_RECIPE;

        double inputSpecificEnthalpy = inputNetwork.getSpecificEnthalpy();
        double vFactor = IntegratedFluidThermoModel
            .specificVolumeFromPressureAndSpecificEnthalpy(inputFluid, inputNetwork.getPressure(), inputSpecificEnthalpy);
        if (vFactor <= 0.0d) return CheckRecipeResultRegistry.NO_RECIPE;

        double desiredVocc = fluidAmountPerOperation;
        long desiredAmountMb = (long) Math.floor(desiredVocc / vFactor);
        long amountToProcessQ = Math.min(availableAmountQ, desiredAmountMb * IntegratedFluidNetwork.AMOUNT_SCALE);
        if (amountToProcessQ <= 0) return CheckRecipeResultRegistry.NO_RECIPE;

        double inputTemperature = FluidThermalProperties.getTemperatureFromPH(
            inputFluid, inputNetwork.getPressure(), inputSpecificEnthalpy
        );
        if (inputTemperature <= 0.0d) inputTemperature = COLD_RESERVOIR_TEMPERATURE;

        // Split amounts based on ratio
        long hotAmountQ = (long) (amountToProcessQ * splitRatio);
        long coldAmountQ = amountToProcessQ - hotAmountQ;

        if (hotAmountQ <= 0 || coldAmountQ <= 0) return CheckRecipeResultRegistry.NO_RECIPE;

        double hotAmount = toAmount(hotAmountQ);
        double coldAmount = toAmount(coldAmountQ);

        double hotSpecificEnthalpy = inputSpecificEnthalpy;
        double hotTemperature = inputTemperature;
        double temperatureDelta = 0.0d;
        long energyCost = 0L;
        boolean passthroughMode = false;

        // --- Calculate Hot Stream (Primary) ---
        // Treat inputTemperature as the cold reservoir T_cold for Carnot efficiency.
        switch (operatingMode) {
            case TARGET_TEMPERATURE:
                hotTemperature = targetTemperature;
                temperatureDelta = hotTemperature - inputTemperature;

                if (inputTemperature >= targetTemperature - lowerTemperatureTolerance
                    && inputTemperature <= targetTemperature + upperTemperatureTolerance) {
                    passthroughMode = true;
                    currentCOP = 0.0f;
                    energyCost = 0;
                    hotTemperature = inputTemperature;
                    temperatureDelta = 0.0d;
                    currentTemperatureDelta = 0.0f;
                    currentEfficiencyPenalty = 1.0f;
                    effectiveCOP = 0.0f;
                } else {
                    float tCold = (float) Math.min(inputTemperature, hotTemperature);
                    float tHot = (float) Math.max(inputTemperature, hotTemperature);
                    currentCOP = FluidThermalProperties.calculateHeatPumpCOP(tCold, tHot);

                    double absDelta = Math.abs(temperatureDelta);
                    double penalty = FluidThermalProperties.calculateTemperaturePenalty((float) absDelta);
                    currentTemperatureDelta = (float) absDelta;
                    currentEfficiencyPenalty = (float) penalty;
                    effectiveCOP = currentCOP / currentEfficiencyPenalty;

                    double hTarget = FluidThermalProperties.getSpecificEnthalpyFromPT(inputFluid, redNetwork.getPressure(), hotTemperature);
                    energyCost = IFNMachineThermo.computeHeatPumpEnergyCost(
                        inputSpecificEnthalpy,
                        hTarget,
                        hotAmountQ,
                        currentCOP,
                        (float) penalty
                    );
                    hotSpecificEnthalpy = hTarget;
                }
                break;

            case TARGET_COP:
                if (targetCOP <= 1.0f) targetCOP = 1.1f;
                if (targetHeating) {
                    hotTemperature = (targetCOP * inputTemperature) / (targetCOP - 1.0f);
                } else {
                    hotTemperature = inputTemperature * (targetCOP - 1.0f) / targetCOP;
                }
                temperatureDelta = hotTemperature - inputTemperature;
                double absDelta = Math.abs(temperatureDelta);
                if (absDelta < 0.1d) {
                    absDelta = 0.1d;
                    hotTemperature = inputTemperature + (targetHeating ? absDelta : -absDelta);
                    temperatureDelta = hotTemperature - inputTemperature;
                }
                currentCOP = targetCOP;
                double penalty = FluidThermalProperties.calculateTemperaturePenalty((float) Math.abs(temperatureDelta));
                currentTemperatureDelta = (float) Math.abs(temperatureDelta);
                currentEfficiencyPenalty = (float) penalty;
                effectiveCOP = currentCOP / currentEfficiencyPenalty;

                double hTarget = FluidThermalProperties.getSpecificEnthalpyFromPT(inputFluid, redNetwork.getPressure(), hotTemperature);
                energyCost = IFNMachineThermo.computeHeatPumpEnergyCost(
                    inputSpecificEnthalpy,
                    hTarget,
                    hotAmountQ,
                    currentCOP,
                    (float) penalty
                );
                hotSpecificEnthalpy = hTarget;
                break;

            case TARGET_ENERGY:
                long targetTotalEnergy = (long) targetEnergyPerTick * 20L;
                energyCost = targetTotalEnergy;

                double tempEstimate = inputTemperature;
                double lastTempEstimate;
                for (int i = 0; i < 10; i++) {
                    lastTempEstimate = tempEstimate;
                    float tCold = (float) Math.min(tempEstimate, inputTemperature);
                    float tHot = (float) Math.max(tempEstimate, inputTemperature);
                    double copLocal = FluidThermalProperties.calculateHeatPumpCOP(tCold, tHot);
                    double delta = Math.abs(tempEstimate - inputTemperature);
                    double penaltyLocal = FluidThermalProperties.calculateTemperaturePenalty((float) delta);
                    double effectiveCopLocal = copLocal / penaltyLocal;
                    double qHot = effectiveCopLocal * targetTotalEnergy;

                    hotSpecificEnthalpy = inputSpecificEnthalpy + (targetHeating ? qHot : -qHot) / hotAmount;
                    tempEstimate = FluidThermalProperties.getTemperatureFromPH(
                        inputFluid, redNetwork.getPressure(), hotSpecificEnthalpy
                    );

                    if (Double.isNaN(tempEstimate) || Double.isInfinite(tempEstimate)) {
                        tempEstimate = lastTempEstimate;
                        break;
                    }
                    if (Math.abs(tempEstimate - lastTempEstimate) < 1e-3) break;
                }

                hotTemperature = tempEstimate;
                temperatureDelta = hotTemperature - inputTemperature;
                currentCOP = FluidThermalProperties.calculateHeatPumpCOP((float) Math.min(hotTemperature, inputTemperature), (float) Math.max(hotTemperature, inputTemperature));
                currentEfficiencyPenalty = FluidThermalProperties.calculateTemperaturePenalty((float) Math.abs(temperatureDelta));
                currentTemperatureDelta = (float) Math.abs(temperatureDelta);
                effectiveCOP = currentCOP / currentEfficiencyPenalty;
                break;
        }

        // Validate hot stream direction
        if (!passthroughMode) {
            if (targetHeating && hotSpecificEnthalpy < inputSpecificEnthalpy - 1e-6d) return SimpleCheckRecipeResult.ofFailure("awaiting_configuration");
            if (!targetHeating && hotSpecificEnthalpy > inputSpecificEnthalpy + 1e-6d) return SimpleCheckRecipeResult.ofFailure("awaiting_configuration");
        }

        // --- Calculate Cold Stream (Secondary) ---
        // Energy conservation: Energy extracted from cold stream = Heat delivered to hot stream - Input Work
        double qHotTotal = Math.abs(hotSpecificEnthalpy - inputSpecificEnthalpy) * hotAmount;
        double wTotal = energyCost;
        double qColdTotal = qHotTotal - wTotal;

        // If qColdTotal is negative, it means the work input was greater than the heat delivered,
        // which physically shouldn't happen with Carnot efficiency >= 1, but we clamp it to 0 just in case.
        if (qColdTotal < 0) qColdTotal = 0;

        double coldSpecificEnthalpy;
        if (targetHeating) {
            // Heating hot stream means we cooled the cold stream
            coldSpecificEnthalpy = inputSpecificEnthalpy - (qColdTotal / coldAmount);
        } else {
            // Cooling hot stream means we heated the cold stream
            coldSpecificEnthalpy = inputSpecificEnthalpy + (qColdTotal / coldAmount);
        }

        long originalAmountToProcessQ = amountToProcessQ;
        long originalEnergyCost = energyCost;
        if (redNetwork != inputNetwork || blueNetwork != inputNetwork) {
            var plan = IFNStateTransferPlanner.planSplitStateAdd(
                inputNetwork,
                redNetwork,
                inputFluid,
                hotSpecificEnthalpy,
                blueNetwork,
                inputFluid,
                coldSpecificEnthalpy,
                amountToProcessQ,
                splitRatio,
                IFNPressurePolicy.MACHINE_OUTPUT_TO_INPUT_PRESSURE_RATIO
            );
            if (plan.acceptedTotalAmountQ < IntegratedFluidNetwork.AMOUNT_SCALE) {
                return IFNMachineResultMapper.toRecipeResult(plan.status);
            }
            amountToProcessQ = plan.acceptedTotalAmountQ;
        }

        // Extract from input
        IntegratedFluidNetwork.ExtractedPayload extracted = inputNetwork.extractProportional(amountToProcessQ, false);
        if (extracted.amountQ <= 0L) return CheckRecipeResultRegistry.NO_RECIPE;

        // Adjust if extraction was partial OR if we scaled down due to pressure
        if (extracted.amountQ != originalAmountToProcessQ) {
            double ratio = extracted.amountQ / (double) originalAmountToProcessQ;
            if (energyCost > 0L) {
                energyCost = IFNMachineThermo.scaleEnergyCost(originalEnergyCost, originalAmountToProcessQ, extracted.amountQ);
            }
        }

        hotAmountQ = (long) (extracted.amountQ * splitRatio);
        coldAmountQ = extracted.amountQ - hotAmountQ;

        currentEnergyUsage = energyCost;
        this.totalEnergyCost = (int) ((energyCost + 19) / 20);

        long outHotEnthalpyQ = toEnthalpyQ(hotSpecificEnthalpy, hotAmountQ);
        long outColdEnthalpyQ = toEnthalpyQ(coldSpecificEnthalpy, coldAmountQ);

        // Add to outputs
        redNetwork.addState(inputFluid, hotAmountQ, outHotEnthalpyQ);
        blueNetwork.addState(inputFluid, coldAmountQ, outColdEnthalpyQ);

        currentOutputTemperature = (float) hotTemperature;

        if (passthroughMode) {
            this.mMaxProgresstime = 5;
            this.mEUt = 0;
        } else {
            this.mMaxProgresstime = 20;
            this.mEfficiency = 10000;
            this.mEUt = (operatingMode == HeatPumpMode.TARGET_ENERGY) ? -targetEnergyPerTick : -this.totalEnergyCost;
        }

        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    private @NotNull CheckRecipeResult processHeatExchanger() {
        if (!hasValidHeatExchangerHatches()) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        CheckRecipeResult configResult = validateConfiguration();
        if (configResult != CheckRecipeResultRegistry.SUCCESSFUL) {
            return configResult;
        }

        MTEIntegratedFluidInputHatch redInput = getInputHatchByColor(1);
        MTEIntegratedFluidInputHatch blueInput = getInputHatchByColor(4);
        MTEIntegratedFluidOutputHatch redOutput = getOutputHatchByColor(1);
        MTEIntegratedFluidOutputHatch blueOutput = getOutputHatchByColor(4);

        if (redInput == null || blueInput == null || redOutput == null || blueOutput == null) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        var redInNet = redInput.getNetwork();
        var blueInNet = blueInput.getNetwork();
        var redOutNet = redOutput.getNetwork();
        var blueOutNet = blueOutput.getNetwork();

        if (redInNet == null || blueInNet == null || redOutNet == null || blueOutNet == null) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        Fluid redFluid = redInNet.getFluid();
        Fluid blueFluid = blueInNet.getFluid();
        if (redFluid == null || blueFluid == null) return CheckRecipeResultRegistry.NO_RECIPE;

        long redAvailQ = redInNet.getAmountQ();
        long blueAvailQ = blueInNet.getAmountQ();
        if (redAvailQ <= 0 || blueAvailQ <= 0) return CheckRecipeResultRegistry.NO_RECIPE;

        double redInH = redInNet.getSpecificEnthalpy();
        double blueInH = blueInNet.getSpecificEnthalpy();

        double redInTemp = FluidThermalProperties.getTemperatureFromPH(redFluid, redInNet.getPressure(), redInH);
        double blueInTemp = FluidThermalProperties.getTemperatureFromPH(blueFluid, blueInNet.getPressure(), blueInH);

        // HEAT FLOW DIRECTION: ALWAYS BLUE (Source) -> RED (Target)
        // This means we extract heat from Blue (cooling it down) and pump it into Red (heating it up).
        // GUI settings (targetTemp, targetCOP) apply to the Red (Target) stream.
        // If configuringHotStream is true, it means we are actively configuring the Target (Red) to reach a specific heat.
        // If configuringHotStream is false, we are theoretically configuring the Source (Blue) to reach a specific coldness,
        // BUT to keep logic simple and consistent with the physical heat flow, we will evaluate the GUI settings
        // on the Target (Red) by default, and just flip the target direction if configuringHotStream is false.

        // Actually, let's make it intuitive:
        // configuringHotStream = true  -> We want to hit a specific property on the RED stream.
        // configuringHotStream = false -> We want to hit a specific property on the BLUE stream.
        // Heat STILL flows from Blue to Red.

        boolean configureRed = configuringHotStream;

        var targetInNet = configureRed ? redInNet : blueInNet;
        var targetOutNet = configureRed ? redOutNet : blueOutNet;
        Fluid targetFluid = configureRed ? redFluid : blueFluid;
        long targetAvailQ = configureRed ? redAvailQ : blueAvailQ;
        double targetInH = configureRed ? redInH : blueInH;
        double targetInTemp = configureRed ? redInTemp : blueInTemp;

        var sourceInNet = !configureRed ? redInNet : blueInNet;
        var sourceOutNet = !configureRed ? redOutNet : blueOutNet;
        Fluid sourceFluid = !configureRed ? redFluid : blueFluid;
        long sourceAvailQ = !configureRed ? redAvailQ : blueAvailQ;
        double sourceInH = !configureRed ? redInH : blueInH;
        double sourceInTemp = !configureRed ? redInTemp : blueInTemp;


        // Calculate max amount to process
        double tVFactor = IntegratedFluidThermoModel.specificVolumeFromPressureAndSpecificEnthalpy(targetFluid, targetInNet.getPressure(), targetInH);
        if (tVFactor <= 0.0d) return CheckRecipeResultRegistry.NO_RECIPE;
        long targetProcessQ = Math.min(targetAvailQ, (long) Math.floor(fluidAmountPerOperation / tVFactor) * IntegratedFluidNetwork.AMOUNT_SCALE);
        if (targetProcessQ <= 0) return CheckRecipeResultRegistry.NO_RECIPE;

        double sVFactor = IntegratedFluidThermoModel.specificVolumeFromPressureAndSpecificEnthalpy(sourceFluid, sourceInNet.getPressure(), sourceInH);
        if (sVFactor <= 0.0d) return CheckRecipeResultRegistry.NO_RECIPE;
        long sourceProcessQ = Math.min(sourceAvailQ, (long) Math.floor(fluidAmountPerOperation / sVFactor) * IntegratedFluidNetwork.AMOUNT_SCALE);
        if (sourceProcessQ <= 0) return CheckRecipeResultRegistry.NO_RECIPE;

        double targetProcessAmt = toAmount(targetProcessQ);
        double sourceProcessAmt = toAmount(sourceProcessQ);

        double targetOutTemp = targetInTemp;
        double targetOutH = targetInH;
        double temperatureDelta = 0.0d;
        long energyCost = 0L;
        boolean passthroughMode = false;

        // The "Cold" reservoir for Carnot COP is ALWAYS the Blue stream,
        // and the "Hot" reservoir is ALWAYS the Red stream, regardless of actual temps.
        // Wait, Carnot efficiency depends on actual temperatures: COP = T_hot / (T_hot - T_cold).
        // To pump heat from Blue to Red, we must do work.
        // Let's define the Source Temp for COP calculation as the starting temp of the stream we are extracting from (Blue).
        double copSourceTemp = blueInTemp;

        // If configuringRed == true, targetHeating MUST be true (we are heating Red by pumping from Blue).
        // If configuringRed == false, targetHeating MUST be false (we are cooling Blue by pumping to Red).
        // We will override user's targetHeating flag to match physical reality to prevent weird bugs.
        boolean actualTargetHeating = configureRed;


        switch (operatingMode) {
            case TARGET_TEMPERATURE:
                targetOutTemp = targetTemperature;
                temperatureDelta = targetOutTemp - targetInTemp;

                // Check if target is physically possible with current direction
                if ((actualTargetHeating && targetOutTemp < targetInTemp) ||
                    (!actualTargetHeating && targetOutTemp > targetInTemp)) {
                    return SimpleCheckRecipeResult.ofFailure("awaiting_configuration");
                }

                if (Math.abs(temperatureDelta) <= (actualTargetHeating ? lowerTemperatureTolerance : upperTemperatureTolerance)) {
                    passthroughMode = true;
                    currentCOP = 0.0f;
                    energyCost = 0;
                    targetOutTemp = targetInTemp;
                    temperatureDelta = 0.0d;
                    currentTemperatureDelta = 0.0f;
                    currentEfficiencyPenalty = 1.0f;
                    effectiveCOP = 0.0f;
                } else {
                    // For COP, Hot is Red, Cold is Blue.
                    float tColdForCop = (float) Math.min(blueInTemp, configureRed ? targetOutTemp : targetInTemp);
                    float tHotForCop = (float) Math.max(redInTemp, configureRed ? targetOutTemp : targetInTemp);
                    currentCOP = FluidThermalProperties.calculateHeatPumpCOP(tColdForCop, tHotForCop);

                    double absDelta = Math.abs(temperatureDelta);
                    double penalty = FluidThermalProperties.calculateTemperaturePenalty((float) absDelta);
                    currentTemperatureDelta = (float) absDelta;
                    currentEfficiencyPenalty = (float) penalty;
                    effectiveCOP = currentCOP / currentEfficiencyPenalty;

                    double hTarget = FluidThermalProperties.getSpecificEnthalpyFromPT(targetFluid, targetOutNet.getPressure(), targetOutTemp);
                    energyCost = IFNMachineThermo.computeHeatPumpEnergyCost(
                        targetInH,
                        hTarget,
                        targetProcessQ,
                        currentCOP,
                        (float) penalty
                    );
                    targetOutH = hTarget;
                }
                break;
            case TARGET_COP:
                if (targetCOP <= 1.0f) targetCOP = 1.1f;

                if (actualTargetHeating) {
                     // Heating Red. Source is Blue.
                     targetOutTemp = (targetCOP * blueInTemp) / (targetCOP - 1.0f);
                } else {
                     // Cooling Blue. Source is Blue (cooling it down). Target is Red.
                     // T_cold = T_hot * (COP - 1) / COP.
                     // Here we know Red's temp (T_hot), want to find Blue's new temp (T_cold).
                     targetOutTemp = redInTemp * (targetCOP - 1.0f) / targetCOP;
                }

                temperatureDelta = targetOutTemp - targetInTemp;
                double absDelta = Math.abs(temperatureDelta);
                if (absDelta < 0.1d) {
                    absDelta = 0.1d;
                    targetOutTemp = targetInTemp + (actualTargetHeating ? absDelta : -absDelta);
                    temperatureDelta = targetOutTemp - targetInTemp;
                }
                currentCOP = targetCOP;
                double penalty = FluidThermalProperties.calculateTemperaturePenalty((float) Math.abs(temperatureDelta));
                currentTemperatureDelta = (float) Math.abs(temperatureDelta);
                currentEfficiencyPenalty = (float) penalty;
                effectiveCOP = currentCOP / currentEfficiencyPenalty;

                double hTarget = FluidThermalProperties.getSpecificEnthalpyFromPT(targetFluid, targetOutNet.getPressure(), targetOutTemp);
                energyCost = IFNMachineThermo.computeHeatPumpEnergyCost(
                    targetInH,
                    hTarget,
                    targetProcessQ,
                    currentCOP,
                    (float) penalty
                );
                targetOutH = hTarget;
                break;
            case TARGET_ENERGY:
                long targetTotalEnergy = (long) targetEnergyPerTick * 20L;
                energyCost = targetTotalEnergy;

                double tempEstimate = targetInTemp;
                double lastTempEstimate;
                for (int i = 0; i < 10; i++) {
                    lastTempEstimate = tempEstimate;

                    float tColdForCop = (float) Math.min(blueInTemp, configureRed ? tempEstimate : targetInTemp);
                    float tHotForCop = (float) Math.max(redInTemp, configureRed ? tempEstimate : targetInTemp);

                    double copLocal = FluidThermalProperties.calculateHeatPumpCOP(tColdForCop, tHotForCop);
                    double delta = Math.abs(tempEstimate - targetInTemp);
                    double penaltyLocal = FluidThermalProperties.calculateTemperaturePenalty((float) delta);
                    double effectiveCopLocal = copLocal / penaltyLocal;
                    double qTransferred = effectiveCopLocal * targetTotalEnergy;

                    targetOutH = targetInH + (actualTargetHeating ? qTransferred : -qTransferred) / targetProcessAmt;
                    tempEstimate = FluidThermalProperties.getTemperatureFromPH(targetFluid, targetOutNet.getPressure(), targetOutH);

                    if (Double.isNaN(tempEstimate) || Double.isInfinite(tempEstimate)) {
                        tempEstimate = lastTempEstimate;
                        break;
                    }
                    if (Math.abs(tempEstimate - lastTempEstimate) < 1e-3) break;
                }

                targetOutTemp = tempEstimate;
                temperatureDelta = targetOutTemp - targetInTemp;
                float finalCold = (float) Math.min(blueInTemp, configureRed ? targetOutTemp : targetInTemp);
                float finalHot = (float) Math.max(redInTemp, configureRed ? targetOutTemp : targetInTemp);
                currentCOP = FluidThermalProperties.calculateHeatPumpCOP(finalCold, finalHot);
                currentEfficiencyPenalty = FluidThermalProperties.calculateTemperaturePenalty((float) Math.abs(temperatureDelta));
                currentTemperatureDelta = (float) Math.abs(temperatureDelta);
                effectiveCOP = currentCOP / currentEfficiencyPenalty;
                break;
        }

        if (!passthroughMode) {
            if (actualTargetHeating && targetOutH < targetInH - 1e-6d) return SimpleCheckRecipeResult.ofFailure("awaiting_configuration");
            if (!actualTargetHeating && targetOutH > targetInH + 1e-6d) return SimpleCheckRecipeResult.ofFailure("awaiting_configuration");
        }

        // Heat flow logic: Q_delivered_to_Red = Q_extracted_from_Blue + W
        // We know the Q change for our Target stream.
        double qTargetTotal = Math.abs(targetOutH - targetInH) * targetProcessAmt;
        double wTotal = energyCost;

        double qSourceTotal;
        double sourceOutH;

        if (configureRed) {
            // We configured Red (Target). We know how much heat went INTO Red (qTargetTotal).
            // Q_extracted_from_Blue = Q_delivered_to_Red - W
            qSourceTotal = qTargetTotal - wTotal;
            if (qSourceTotal < 0) qSourceTotal = 0;
            // Blue loses heat
            sourceOutH = sourceInH - (qSourceTotal / sourceProcessAmt);
        } else {
            // We configured Blue (Target). We know how much heat came OUT OF Blue (qTargetTotal).
            // Q_delivered_to_Red = Q_extracted_from_Blue + W
            qSourceTotal = qTargetTotal + wTotal;
            // Red gains heat
            sourceOutH = sourceInH + (qSourceTotal / sourceProcessAmt);
        }

        // Map Target/Source back to Red/Blue for output logic
        double redOutH = configureRed ? targetOutH : sourceOutH;
        double blueOutH = configureRed ? sourceOutH : targetOutH;
        long redBaseQ = configureRed ? targetProcessQ : sourceProcessQ;
        long blueBaseQ = configureRed ? sourceProcessQ : targetProcessQ;

        long originalRedProcessQ = redBaseQ;
        long originalBlueProcessQ = blueBaseQ;
        long originalEnergyCost = energyCost;
        long redProcessQ = redBaseQ;
        long blueProcessQ = blueBaseQ;

        if (redOutNet != redInNet || blueOutNet != blueInNet) {
            var plan = IFNStateTransferPlanner.planDualStateAddWithSharedRatio(
                redInNet,
                redOutNet,
                redFluid,
                redOutH,
                redBaseQ,
                blueInNet,
                blueOutNet,
                blueFluid,
                blueOutH,
                blueBaseQ,
                IFNPressurePolicy.MACHINE_OUTPUT_TO_INPUT_PRESSURE_RATIO
            );
            if (plan.acceptedRatio <= 0.0d
                || plan.acceptedRedAmountQ < IntegratedFluidNetwork.AMOUNT_SCALE
                || plan.acceptedBlueAmountQ < IntegratedFluidNetwork.AMOUNT_SCALE) {
                return IFNMachineResultMapper.toRecipeResult(plan.status);
            }

            redProcessQ = plan.acceptedRedAmountQ;
            blueProcessQ = plan.acceptedBlueAmountQ;
        }

        var extractedRed = redInNet.extractProportional(redProcessQ, false);
        var extractedBlue = blueInNet.extractProportional(blueProcessQ, false);

        if (extractedRed.amountQ <= 0L || extractedBlue.amountQ <= 0L) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        // Adjust if extraction was partial OR if we scaled down due to pressure
        double ratioRed = extractedRed.amountQ / (double) originalRedProcessQ;
        double ratioBlue = extractedBlue.amountQ / (double) originalBlueProcessQ;
        double finalRatio = Math.min(ratioRed, ratioBlue);

        if (finalRatio < 1.0 && originalEnergyCost > 0L) {
            long scaledRedEnergy = IFNMachineThermo.scaleEnergyCost(originalEnergyCost, originalRedProcessQ, extractedRed.amountQ);
            long scaledBlueEnergy = IFNMachineThermo.scaleEnergyCost(originalEnergyCost, originalBlueProcessQ, extractedBlue.amountQ);
            energyCost = Math.min(scaledRedEnergy, scaledBlueEnergy);
        }

        currentEnergyUsage = energyCost;
        this.totalEnergyCost = (int) ((energyCost + 19) / 20);

        long outRedEnthalpyQ = toEnthalpyQ(redOutH, extractedRed.amountQ);
        long outBlueEnthalpyQ = toEnthalpyQ(blueOutH, extractedBlue.amountQ);

        redOutNet.addState(redFluid, extractedRed.amountQ, outRedEnthalpyQ);
        blueOutNet.addState(blueFluid, extractedBlue.amountQ, outBlueEnthalpyQ);

        currentOutputTemperature = (float) targetOutTemp;

        if (passthroughMode) {
            this.mMaxProgresstime = 5;
            this.mEUt = 0;
        } else {
            this.mMaxProgresstime = 20;
            this.mEfficiency = 10000;
            this.mEUt = (operatingMode == HeatPumpMode.TARGET_ENERGY) ? -targetEnergyPerTick : -this.totalEnergyCost;
        }

        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    private @NotNull CheckRecipeResult processNormalMode() {
        if (hasTooManyHatchesForNormalMode()) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        CheckRecipeResult configResult = validateConfiguration();
        if (configResult != CheckRecipeResultRegistry.SUCCESSFUL) {
            return configResult;
        }

        if (mIntegratedInputHatches.isEmpty() || mIntegratedOutputHatches.isEmpty()) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        MTEIntegratedFluidInputHatch inputHatch = mIntegratedInputHatches.get(0);
        MTEIntegratedFluidOutputHatch outputHatch = mIntegratedOutputHatches.get(0);

        var inputNetwork = inputHatch.getNetwork();
        if (inputNetwork == null) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        var outputNetwork = outputHatch.getNetwork();
        if (outputNetwork == null) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        Fluid inputFluid = inputNetwork.getFluid();
        if (inputFluid == null) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        long availableAmountQ = inputNetwork.getAmountQ();
        if (availableAmountQ <= 0) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        double inputSpecificEnthalpy = inputNetwork.getSpecificEnthalpy();
        double vFactor = IntegratedFluidThermoModel
            .specificVolumeFromPressureAndSpecificEnthalpy(inputFluid, inputNetwork.getPressure(), inputSpecificEnthalpy);
        if (vFactor <= 0.0d) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }
        double desiredVocc = fluidAmountPerOperation;
        long desiredAmountMb = (long) Math.floor(desiredVocc / vFactor);
        long amountToProcessQ = Math.min(availableAmountQ, desiredAmountMb * IntegratedFluidNetwork.AMOUNT_SCALE);
        if (amountToProcessQ <= 0) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }
        double inputTemperature = FluidThermalProperties.getTemperatureFromPH(
            inputFluid,
            inputNetwork.getPressure(),
            inputSpecificEnthalpy
        );
        if (inputTemperature <= 0.0d) {
            inputTemperature = COLD_RESERVOIR_TEMPERATURE;
        }

        double amountToProcess = toAmount(amountToProcessQ);
        double outputTemperature = inputTemperature;
        double temperatureDelta = 0.0d;
        double outputSpecificEnthalpy = inputSpecificEnthalpy;
        long totalEnergyCost = 0L;
        double penalty;
        boolean passthroughMode = false;
        boolean heatingDirection = true;

        switch (operatingMode) {
            case TARGET_TEMPERATURE: {
                outputTemperature = targetTemperature;
                temperatureDelta = outputTemperature - inputTemperature;
                heatingDirection = temperatureDelta >= 0.0d;

                if (inputTemperature >= targetTemperature - lowerTemperatureTolerance
                    && inputTemperature <= targetTemperature + upperTemperatureTolerance) {
                    passthroughMode = true;
                    currentCOP = 0.0f;
                    totalEnergyCost = 0;
                    this.totalEnergyCost = 0;
                    outputTemperature = inputTemperature;
                    temperatureDelta = 0.0d;
                    currentTemperatureDelta = 0.0f;
                    currentEfficiencyPenalty = 1.0f;
                    effectiveCOP = 0.0f;
                } else {
                    float tCold = (float) Math.min(inputTemperature, outputTemperature);
                    float tHot = (float) Math.max(inputTemperature, outputTemperature);
                    currentCOP = FluidThermalProperties.calculateHeatPumpCOP(tCold, tHot);

                    double absDelta = Math.abs(temperatureDelta);
                    penalty = FluidThermalProperties.calculateTemperaturePenalty((float) absDelta);
                    currentTemperatureDelta = (float) absDelta;
                    currentEfficiencyPenalty = (float) penalty;
                    effectiveCOP = currentCOP / currentEfficiencyPenalty;

                    double hTarget = IFNMachineThermo.computeTargetSpecificEnthalpyForStateAdd(
                        outputNetwork,
                        inputFluid,
                        outputTemperature,
                        amountToProcessQ
                    );
                    totalEnergyCost = IFNMachineThermo.computeHeatPumpEnergyCost(
                        inputSpecificEnthalpy,
                        hTarget,
                        amountToProcessQ,
                        currentCOP,
                        (float) penalty
                    );
                    this.totalEnergyCost = (int) ((totalEnergyCost + 19) / 20);
                    outputSpecificEnthalpy = hTarget;
                }
                break;
            }

            case TARGET_COP: {
                if (targetCOP <= 1.0f) {
                    targetCOP = 1.1f;
                }
                if (targetHeating) {
                    outputTemperature = (targetCOP * inputTemperature) / (targetCOP - 1.0f);
                } else {
                    // T_cold = T_hot * (COP_heating - 1) / COP_heating
                    outputTemperature = inputTemperature * (targetCOP - 1.0f) / targetCOP;
                }
                temperatureDelta = outputTemperature - inputTemperature;
                heatingDirection = targetHeating;
                double absDelta = Math.abs(temperatureDelta);
                if (absDelta < 0.1d) {
                    absDelta = 0.1d;
                    outputTemperature = inputTemperature + (targetHeating ? absDelta : -absDelta);
                    temperatureDelta = outputTemperature - inputTemperature;
                }
                currentCOP = targetCOP;
                penalty = FluidThermalProperties.calculateTemperaturePenalty((float) Math.abs(temperatureDelta));
                currentTemperatureDelta = (float) Math.abs(temperatureDelta);
                currentEfficiencyPenalty = (float) penalty;
                effectiveCOP = currentCOP / currentEfficiencyPenalty;

                double hTarget = IFNMachineThermo.computeTargetSpecificEnthalpyForStateAdd(
                    outputNetwork,
                    inputFluid,
                    outputTemperature,
                    amountToProcessQ
                );
                totalEnergyCost = IFNMachineThermo.computeHeatPumpEnergyCost(
                    inputSpecificEnthalpy,
                    hTarget,
                    amountToProcessQ,
                    currentCOP,
                    (float) penalty
                );
                this.totalEnergyCost = (int) ((totalEnergyCost + 19) / 20);
                outputSpecificEnthalpy = hTarget;
                break;
            }

            case TARGET_ENERGY: {
                long targetTotalEnergy = (long) targetEnergyPerTick * 20L;
                if (targetTotalEnergy <= 0L) {
                    return SimpleCheckRecipeResult.ofFailure("awaiting_configuration");
                }
                totalEnergyCost = targetTotalEnergy;

                double tempEstimate = inputTemperature;
                double lastTempEstimate;
                double copLocal = 1.0d;
                double penaltyLocal = 1.0d;
                double effectiveCopLocal = 1.0d;

                for (int i = 0; i < 10; i++) {
                    lastTempEstimate = tempEstimate;
                    float tCold = (float) Math.min(tempEstimate, inputTemperature);
                    float tHot = (float) Math.max(tempEstimate, inputTemperature);
                    copLocal = FluidThermalProperties.calculateHeatPumpCOP(tCold, tHot);
                    double delta = Math.abs(tempEstimate - inputTemperature);
                    penaltyLocal = FluidThermalProperties.calculateTemperaturePenalty((float) delta);
                    effectiveCopLocal = copLocal / penaltyLocal;
                    double qHot = effectiveCopLocal * targetTotalEnergy;
                    outputSpecificEnthalpy = inputSpecificEnthalpy + (targetHeating ? qHot : -qHot) / amountToProcess;
                    tempEstimate = FluidThermalProperties.getTemperatureFromPH(
                        inputFluid,
                        outputNetwork.getPressure(),
                        outputSpecificEnthalpy
                    );

                    if (Double.isNaN(tempEstimate) || Double.isInfinite(tempEstimate)) {
                        tempEstimate = lastTempEstimate;
                        break;
                    }
                    if (Math.abs(tempEstimate - lastTempEstimate) < 1e-3) {
                        break;
                    }
                }

                outputTemperature = tempEstimate;
                temperatureDelta = outputTemperature - inputTemperature;
                heatingDirection = targetHeating;
                currentCOP = (float) copLocal;
                currentEfficiencyPenalty = (float) penaltyLocal;
                currentTemperatureDelta = (float) Math.abs(temperatureDelta);
                effectiveCOP = (float) effectiveCopLocal;
                this.totalEnergyCost = targetEnergyPerTick;
                break;
            }

            default:
                return CheckRecipeResultRegistry.NO_RECIPE;
        }

        currentOutputTemperature = (float) outputTemperature;

        if (heatingDirection) {
            if (outputSpecificEnthalpy < inputSpecificEnthalpy - 1e-6d) {
                return SimpleCheckRecipeResult.ofFailure("awaiting_configuration");
            }
        } else {
            if (outputSpecificEnthalpy > inputSpecificEnthalpy + 1e-6d) {
                return SimpleCheckRecipeResult.ofFailure("awaiting_configuration");
            }
        }

        long originalAmountToProcessQ = amountToProcessQ;
        boolean targetOutputState = !passthroughMode && operatingMode != HeatPumpMode.TARGET_ENERGY;
        double requestedOutputSpecificEnthalpy = outputSpecificEnthalpy;
        double requestedOutputTemperature = outputTemperature;
        IFNSingleOutputProcess.Result processResult = IFNSingleOutputProcess.execute(IFNSingleOutputProcess.Request.of(
            inputNetwork,
            outputNetwork,
            inputFluid,
            amountToProcessQ,
            amountQ -> targetOutputState
                ? IFNMachineThermo.computeTargetSpecificEnthalpyForStateAdd(
                    outputNetwork,
                    inputFluid,
                    requestedOutputTemperature,
                    amountQ)
                : requestedOutputSpecificEnthalpy,
            IFNPressurePolicy.MACHINE_OUTPUT_TO_INPUT_PRESSURE_RATIO));

        if (processResult.getStatus() == IFNSingleOutputProcess.Status.OUTPUT_BLOCKED) {
            return CheckRecipeResultRegistry.ITEM_OUTPUT_FULL;
        }
        if (processResult.getStatus() != IFNSingleOutputProcess.Status.SUCCESS) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        amountToProcessQ = processResult.getAmountQ();
        outputSpecificEnthalpy = processResult.getOutputSpecificEnthalpy();

        if (amountToProcessQ != originalAmountToProcessQ && totalEnergyCost > 0L) {
            totalEnergyCost = IFNMachineThermo.scaleEnergyCost(totalEnergyCost, originalAmountToProcessQ, amountToProcessQ);
            this.totalEnergyCost = (int) ((totalEnergyCost + 19) / 20);
        }

        if (targetOutputState) {
            totalEnergyCost = IFNMachineThermo.computeHeatPumpEnergyCost(
                inputSpecificEnthalpy,
                outputSpecificEnthalpy,
                amountToProcessQ,
                currentCOP,
                currentEfficiencyPenalty
            );
            this.totalEnergyCost = (int) ((totalEnergyCost + 19) / 20);
        }

        currentEnergyUsage = totalEnergyCost;

        if (passthroughMode) {
            this.mMaxProgresstime = 5;
            this.mEUt = 0;
        } else {
            this.mMaxProgresstime = 20;
            this.mEfficiency = 10000;
            if (operatingMode == HeatPumpMode.TARGET_ENERGY) {
                this.mEUt = -targetEnergyPerTick;
            } else {
                long energyPerTick = (totalEnergyCost + 19) / 20;
                this.mEUt = (int) -energyPerTick;
            }
        }

        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    private CheckRecipeResult validateConfiguration() {
        switch (operatingMode) {
            case TARGET_TEMPERATURE:
                break;
            case TARGET_COP:
                if (targetCOP <= 0 || targetCOP < 1.1f) {
                    return SimpleCheckRecipeResult.ofFailure("awaiting_configuration");
                }
                break;
            case TARGET_ENERGY:
                if (targetEnergyPerTick <= 0) {
                    return SimpleCheckRecipeResult.ofFailure("awaiting_configuration");
                }
                break;
            default:
                return SimpleCheckRecipeResult.ofFailure("awaiting_configuration");
        }
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    private MTEIntegratedFluidInputHatch getInputHatchByColor(int color) {
        for (MTEIntegratedFluidInputHatch hatch : mIntegratedInputHatches) {
            if (hatch.getBaseMetaTileEntity().getColorization() == color) return hatch;
        }
        return null;
    }

    private MTEIntegratedFluidOutputHatch getOutputHatchByColor(int color) {
        for (MTEIntegratedFluidOutputHatch hatch : mIntegratedOutputHatches) {
            if (hatch.getBaseMetaTileEntity().getColorization() == color) return hatch;
        }
        return null;
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

    // ===== GUI Methods =====
    @Override
    protected boolean useMui2() {
        return true;
    }

    @Override
    protected @NotNull MTEMultiBlockBaseGui<?> getGui() {
        return new MTEHeatPumpGui(this);
    }

    // ===== Helper Methods for GUI =====
    public float getInputTemperature() {
        if (mIntegratedInputHatches.isEmpty()) return 0.0f;
        var network = mIntegratedInputHatches.get(0).getNetwork();
        return getNetworkTemperature(network);
    }

    public float getOutputTemperature() {
        if (mIntegratedOutputHatches.isEmpty()) return 0.0f;
        var network = mIntegratedOutputHatches.get(0).getNetwork();
        return getNetworkTemperature(network);
    }

    public int getInputNetworkCapacity() {
        if (mIntegratedInputHatches.isEmpty()) return 0;
        var network = mIntegratedInputHatches.get(0).getNetwork();
        return network != null ? network.getTotalCapacity() : 0;
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
        return network != null ? network.getTotalCapacity() : 0;
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
        Fluid fluid = network.getFluid();
        return fluid != null ? fluid.getLocalizedName() : "";
    }

    private static float getNetworkTemperature(IntegratedFluidNetwork network) {
        if (network == null) {
            return 0.0f;
        }
        Fluid fluid = network.getFluid();
        if (fluid == null || network.getAmountQ() <= 0L) {
            return 0.0f;
        }
        double temperature = FluidThermalProperties.getTemperatureFromPH(
            fluid,
            network.getPressure(),
            network.getSpecificEnthalpy()
        );
        return (float) temperature;
    }

    private static long toAmountQ(int amount) {
        return (long) amount * IntegratedFluidNetwork.AMOUNT_SCALE;
    }

    private static double toAmount(long amountQ) {
        return amountQ / (double) IntegratedFluidNetwork.AMOUNT_SCALE;
    }

    private static long toEnthalpyQ(double energyEu) {
        return (long) Math.round(energyEu * IntegratedFluidNetwork.ENTHALPY_SCALE);
    }

    private static long toEnthalpyQ(double specificEnthalpy, long amountQ) {
        return toEnthalpyQ(specificEnthalpy * toAmount(amountQ));
    }

    public float getCOP() {
        return currentCOP;
    }

    public float getEffectiveCOP() {
        return effectiveCOP;
    }

    public float getCurrentTemperatureDelta() {
        return currentTemperatureDelta;
    }

    public float getCurrentEfficiencyPenalty() {
        return currentEfficiencyPenalty;
    }

    public int getTotalEnergyCost() {
        return totalEnergyCost;
    }

    public float getCurrentOutputTemperature() {
        return currentOutputTemperature;
    }

    public HeatPumpMode getOperatingMode() {
        return operatingMode;
    }

    public void setOperatingMode(HeatPumpMode mode) {
        this.operatingMode = mode;
    }

    public int getOperatingModeId() {
        return operatingMode.getId();
    }

    public void setOperatingModeById(int id) {
        this.operatingMode = HeatPumpMode.fromId(id);
    }

    // Target value methods
    public float getTargetTemperatureDelta() {
        return targetTemperature;
    }

    public void setTargetTemperatureDelta(float temperature) {
        this.targetTemperature = temperature;
    }

    // Universal value - interpreted based on operating mode
    public float getUniversalValue() {
        switch (operatingMode) {
            case TARGET_TEMPERATURE:
                return targetTemperature;
            case TARGET_COP:
                return targetCOP;
            case TARGET_ENERGY:
                return (float) targetEnergyPerTick;
            default:
                return 0.0f;
        }
    }

    public void setUniversalValue(float value) {
        switch (operatingMode) {
            case TARGET_TEMPERATURE:
                this.targetTemperature = value;
                break;
            case TARGET_COP:
                this.targetCOP = Math.max(1.1f, Math.min(value, 100.0f));
                break;
            case TARGET_ENERGY:
                this.targetEnergyPerTick = (int) Math.max(5, Math.min(value, 50000));
                break;
        }
    }

    public float getTargetCOP() {
        return targetCOP;
    }

    public void setTargetCOP(float cop) {
        this.targetCOP = Math.max(1.1f, Math.min(cop, 50.0f));
    }

    public int getTargetEnergy() {
        return targetEnergyPerTick;
    }

    public void setTargetEnergy(int energy) {
        this.targetEnergyPerTick = Math.max(5, Math.min(energy, 50000));
    }

    // Fluid amount per operation
    public int getFluidAmountPerOperation() {
        return fluidAmountPerOperation;
    }

    public void setFluidAmountPerOperation(int amount) {
        this.fluidAmountPerOperation = Math.max(1, Math.min(amount, 10000));
    }

    // Temperature tolerance for passthrough mode
    public float getLowerTemperatureTolerance() {
        return lowerTemperatureTolerance;
    }

    public void setLowerTemperatureTolerance(float tolerance) {
        this.lowerTemperatureTolerance = Math.max(0.0f, Math.min(tolerance, 50.0f));
    }

    public float getUpperTemperatureTolerance() {
        return upperTemperatureTolerance;
    }

    public void setUpperTemperatureTolerance(float tolerance) {
        this.upperTemperatureTolerance = Math.max(0.0f, Math.min(tolerance, 50.0f));
    }

    // Heat Exchanger Mode
    public boolean isHeatExchangerMode() {
        return heatExchangerMode;
    }

    public void setHeatExchangerMode(boolean enabled) {
        this.heatExchangerMode = enabled;
        // Mutual exclusion: disable Split Flow if HX is enabled
        if (enabled && splitFlowMode) {
            splitFlowMode = false;
        }
    }

    public boolean isConfiguringHotStream() {
        return configuringHotStream;
    }

    public void setConfiguringHotStream(boolean hot) {
        this.configuringHotStream = hot;
        this.targetHeating = hot;
    }

    // Split Flow Mode
    public boolean isSplitFlowMode() {
        return splitFlowMode;
    }

    public void setSplitFlowMode(boolean enabled) {
        this.splitFlowMode = enabled;
        // Mutual exclusion: disable Heat Exchanger if Split Flow is enabled
        if (enabled && heatExchangerMode) {
            heatExchangerMode = false;
        }
    }

    public float getSplitRatio() {
        return splitRatio;
    }

    public void setSplitRatio(float ratio) {
        this.splitRatio = Math.max(0.1f, Math.min(0.9f, ratio)); // Clamp to 0.1-0.9
    }

    public boolean isTargetHeating() {
        return targetHeating;
    }

    public void setTargetHeating(boolean heating) {
        this.targetHeating = heating;
    }

    /**
     * Checks if Normal Mode has too many hatches (should be exactly 2: 1 input + 1 output).
     * Returns true if there are more than 2 hatches total.
     */
    public boolean hasTooManyHatchesForNormalMode() {
        if (heatExchangerMode || splitFlowMode) {
            return false; // In HX or Split Flow mode, multiple hatches are expected
        }

        int totalHatches = mIntegratedInputHatches.size() + mIntegratedOutputHatches.size();
        return totalHatches > 2;
    }

    /**
     * Checks if Split Flow Mode has valid hatch configuration.
     * Returns true if structure has exactly 1 input and 2 colored outputs (Red and Blue).
     *
     * Note: getColorization() returns MC spray metadata:
     * - Red spray (MC metadata 1) → getColorization() returns 1
     * - Blue spray (MC metadata 4) → getColorization() returns 4
     */
    public boolean hasValidSplitFlowHatches() {
        if (!splitFlowMode) {
            return true; // Not in Split Flow mode, so don't show warning
        }

        // Check input count
        if (mIntegratedInputHatches.size() != 1) {
            return false; // Need exactly 1 input
        }

        // Check output count
        if (mIntegratedOutputHatches.size() != 2) {
            return false; // Need exactly 2 outputs
        }

        // Check that one output is Red (1) and one is Blue (4)
        int color1 = mIntegratedOutputHatches.get(0).getBaseMetaTileEntity().getColorization();
        int color2 = mIntegratedOutputHatches.get(1).getBaseMetaTileEntity().getColorization();

        // Must have exactly one Red (1) and one Blue (4)
        return ((color1 == 1 && color2 == 4) || (color1 == 4 && color2 == 1));
    }

    /**
     * Checks if Heat Exchanger Mode has required colored hatches.
     * Returns true if structure has at least 1 red and 1 blue hatch of each type.
     *
     * Uses caching to avoid checking every GUI frame (was causing spam).
     * Cache is invalidated when structure changes or every 20 ticks.
     *
     * Note: getColorization() returns the ORIGINAL MC spray metadata:
     * - Red spray (MC metadata 1) → getColorization() returns 1
     * - Blue spray (MC metadata 4) → getColorization() returns 4
     */
    public boolean hasValidHeatExchangerHatches() {
        if (!heatExchangerMode) {
            return true; // Not in HX mode, so don't show warning
        }

        // Use cached result if checked recently (within 20 ticks / 1 second)
        long currentTick = getBaseMetaTileEntity().getTimer();
        if (lastValidationCheck > 0 && (currentTick - lastValidationCheck) < 20) {
            return cachedHatchValidation;
        }

        // Perform actual validation
        int redInputs = 0, blueInputs = 0;
        int redOutputs = 0, blueOutputs = 0;

        for (MTEIntegratedFluidInputHatch hatch : mIntegratedInputHatches) {
            int color = hatch.getBaseMetaTileEntity().getColorization();
            if (color == 1) redInputs++; // Red spray (MC metadata 1)
            else if (color == 4) blueInputs++; // Blue spray (MC metadata 4)
        }

        for (MTEIntegratedFluidOutputHatch hatch : mIntegratedOutputHatches) {
            int color = hatch.getBaseMetaTileEntity().getColorization();
            if (color == 1) redOutputs++; // Red spray (MC metadata 1)
            else if (color == 4) blueOutputs++; // Blue spray (MC metadata 4)
        }

        // Cache the result
        cachedHatchValidation = (redInputs >= 1 && blueInputs >= 1 && redOutputs >= 1 && blueOutputs >= 1);
        lastValidationCheck = currentTick;

        return cachedHatchValidation;
    }

    // ===== NBT Methods =====
    @Override
    public void saveNBTData(net.minecraft.nbt.NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);

        // Save operating mode
        aNBT.setInteger("operatingMode", operatingMode.getId());

        // Save target values
        aNBT.setFloat("targetTemperature", targetTemperature);
        aNBT.setFloat("targetCOP", targetCOP);
        aNBT.setInteger("targetEnergyPerTick", targetEnergyPerTick);
        aNBT.setInteger("fluidAmountPerOperation", fluidAmountPerOperation);
        aNBT.setFloat("lowerTemperatureTolerance", lowerTemperatureTolerance);
        aNBT.setFloat("upperTemperatureTolerance", upperTemperatureTolerance);
        aNBT.setBoolean("heatExchangerMode", heatExchangerMode);
        aNBT.setBoolean("configuringHotStream", configuringHotStream);
        aNBT.setBoolean("splitFlowMode", splitFlowMode);
        aNBT.setFloat("splitRatio", splitRatio);
        aNBT.setBoolean("targetHeating", targetHeating);

    }

    @Override
    public void loadNBTData(net.minecraft.nbt.NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);

        // Load operating mode
        if (aNBT.hasKey("operatingMode")) {
            operatingMode = HeatPumpMode.fromId(aNBT.getInteger("operatingMode"));
        }

        // Load target values
        if (aNBT.hasKey("targetTemperature")) {
            targetTemperature = aNBT.getFloat("targetTemperature");
        } else if (aNBT.hasKey("targetTemperatureDelta")) {
            // Backward compatibility - old saves had delta, convert to absolute (assume 300K input)
            targetTemperature = 300.0f + aNBT.getFloat("targetTemperatureDelta");
        }
        if (aNBT.hasKey("targetCOP")) {
            targetCOP = aNBT.getFloat("targetCOP");
        }
        if (aNBT.hasKey("targetEnergyPerTick")) {
            targetEnergyPerTick = aNBT.getInteger("targetEnergyPerTick");
        }
        if (aNBT.hasKey("fluidAmountPerOperation")) {
            fluidAmountPerOperation = aNBT.getInteger("fluidAmountPerOperation");
        }
        if (aNBT.hasKey("lowerTemperatureTolerance")) {
            lowerTemperatureTolerance = aNBT.getFloat("lowerTemperatureTolerance");
        } else if (aNBT.hasKey("targetTemperatureTolerance")) {
            // Backward compatibility - old single tolerance becomes both
            lowerTemperatureTolerance = aNBT.getFloat("targetTemperatureTolerance");
            upperTemperatureTolerance = aNBT.getFloat("targetTemperatureTolerance");
        }
        if (aNBT.hasKey("upperTemperatureTolerance")) {
            upperTemperatureTolerance = aNBT.getFloat("upperTemperatureTolerance");
        }
        if (aNBT.hasKey("heatExchangerMode")) {
            heatExchangerMode = aNBT.getBoolean("heatExchangerMode");
        }
        if (aNBT.hasKey("configuringHotStream")) {
            configuringHotStream = aNBT.getBoolean("configuringHotStream");
        }
        if (aNBT.hasKey("splitFlowMode")) {
            splitFlowMode = aNBT.getBoolean("splitFlowMode");
        }
        if (aNBT.hasKey("splitRatio")) {
            splitRatio = aNBT.getFloat("splitRatio");
        }
        if (aNBT.hasKey("targetHeating")) {
            targetHeating = aNBT.getBoolean("targetHeating");
        }

        if (aNBT.hasKey("targetEnergy")) {
            // Backward compatibility - convert old total energy to per-tick
            targetEnergyPerTick = aNBT.getInteger("targetEnergy") / 20;
        }
    }

    public boolean addIntegratedInputHatch(IGregTechTileEntity aBaseMetaTileEntity, Short aColor) {
        if (aBaseMetaTileEntity == null) return false;
        IMetaTileEntity mte = aBaseMetaTileEntity.getMetaTileEntity();
        if (mte instanceof MTEIntegratedFluidInputHatch hatch) {
            hatch.updateTexture(((BlockCasings2) GregTechAPI.sBlockCasings2).getTextureIndex(0));
            return mIntegratedInputHatches.add(hatch);
        }
        return false;
    }

    public boolean addIntegratedOutputHatch(IGregTechTileEntity aBaseMetaTileEntity, Short aColor) {
        if (aBaseMetaTileEntity == null) return false;
        IMetaTileEntity mte = aBaseMetaTileEntity.getMetaTileEntity();
        if (mte instanceof MTEIntegratedFluidOutputHatch hatch) {
            hatch.updateTexture(((BlockCasings2) GregTechAPI.sBlockCasings2).getTextureIndex(0));
            return mIntegratedOutputHatches.add(hatch);
        }
        return false;
    }
}
