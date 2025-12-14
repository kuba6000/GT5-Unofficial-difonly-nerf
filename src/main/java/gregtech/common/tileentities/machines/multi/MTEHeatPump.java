package gregtech.common.tileentities.machines.multi;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.*;
import static gregtech.api.enums.HatchElement.*;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

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

    // Pending fluid - stores heated fluid that couldn't be added to output network yet
    private FluidStack pendingOutputFluid = null;
    private float pendingOutputTemperature = 0.0f;

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
                    .dot(1)
                    .buildAndChain(onElementPass(x -> ++x.mCasingAmount, ofBlock(GregTechAPI.sBlockCasings2, 0))),
                // THEN: Accept any remaining GregTech machines (like Integrated Fluid Hatches)
                ofBlockAnyMeta(GregTechAPI.sBlockMachines)))
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

        boolean result = checkPiece(STRUCTURE_PIECE_MAIN, 1, 1, 0) && mCasingAmount >= 18;

        // Manually search for Integrated Fluid Hatches in the 3x3x3 structure
        int baseX = aBaseMetaTileEntity.getXCoord();
        int baseY = aBaseMetaTileEntity.getYCoord();
        int baseZ = aBaseMetaTileEntity.getZCoord();

        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    var tile = aBaseMetaTileEntity.getWorld().getTileEntity(baseX + x, baseY + y, baseZ + z);

                    if (tile instanceof IGregTechTileEntity gtTile) {
                        IMetaTileEntity mte = gtTile.getMetaTileEntity();
                        if (mte != null) {
                            if (mte instanceof MTEIntegratedFluidInputHatch hatch) {
                                mIntegratedInputHatches.add(hatch);
                                // Set texture to match multiblock casing (Steel Machine Casing texture index = 16)
                                hatch.updateTexture(((BlockCasings2) GregTechAPI.sBlockCasings2).getTextureIndex(0));
                            } else if (mte instanceof MTEIntegratedFluidOutputHatch hatch) {
                                mIntegratedOutputHatches.add(hatch);
                                // Set texture to match multiblock casing (Steel Machine Casing texture index = 16)
                                hatch.updateTexture(((BlockCasings2) GregTechAPI.sBlockCasings2).getTextureIndex(0));
                            }
                        }
                    }
                }
            }
        }

        return result
            && !mIntegratedInputHatches.isEmpty()
            && !mIntegratedOutputHatches.isEmpty()
            && !mEnergyHatches.isEmpty()
            && !mMaintenanceHatches.isEmpty();
    }

    @Override
    public @NotNull CheckRecipeResult checkProcessing() {
        // CHECK: Split Flow Mode validation
        if (splitFlowMode) {
            // In Split Flow mode, we need 1 input + 2 differently colored outputs
            if (!hasValidSplitFlowHatches()) {
                // Return NO_RECIPE to avoid showing error text
                // GUI already shows nice colored warning message
                return CheckRecipeResultRegistry.NO_RECIPE;
            }
            // TODO: Implement split flow logic here
            // For now, return NO_RECIPE (no error text)
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        // CHECK: Normal Mode - too many hatches
        if (!heatExchangerMode && !splitFlowMode && hasTooManyHatchesForNormalMode()) {
            // Return NO_RECIPE to avoid showing error text
            // GUI already shows nice colored warning message
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        // CHECK: Heat Exchanger Mode validation
        if (heatExchangerMode) {
            // In HX mode, we need colored hatches - validate structure
            if (!hasValidHeatExchangerHatches()) {
                // Return NO_RECIPE to avoid showing any error text
                // GUI already shows nice colored warning message
                return CheckRecipeResultRegistry.NO_RECIPE;
            }
            // TODO: Implement heat exchanger logic here
            // For now, return NO_RECIPE (no error text)
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        // NORMAL MODE: Single-stream heat pump operation

        // ...existing code...
        if (pendingOutputFluid != null && pendingOutputFluid.amount > 0) {
            if (mIntegratedOutputHatches.isEmpty()) {
                return CheckRecipeResultRegistry.NO_RECIPE;
            }

            MTEIntegratedFluidOutputHatch outputHatch = mIntegratedOutputHatches.get(0);
            var outputNetwork = outputHatch.getNetwork();

            if (outputNetwork == null) {
                return SimpleCheckRecipeResult.ofFailure("no_output_network");
            }

            // Try to add pending fluid to output network
            int added = outputNetwork.addFluid(pendingOutputFluid, false, pendingOutputTemperature);

            if (added > 0) {
                // Successfully added some or all pending fluid
                pendingOutputFluid.amount -= added;

                if (pendingOutputFluid.amount <= 0) {
                    // All pending fluid was added - clear it and can start new recipe
                    pendingOutputFluid = null;
                    pendingOutputTemperature = 0.0f;
                } else {
                    // Still have pending fluid - can't start new recipe yet
                    return SimpleCheckRecipeResult.ofFailure("output_full");
                }
            } else {
                // Couldn't add any fluid - output is full
                return SimpleCheckRecipeResult.ofFailure("output_full");
            }
        }

        // THEN: Verify we have integrated fluid hatches
        if (mIntegratedInputHatches.isEmpty() || mIntegratedOutputHatches.isEmpty()) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        // Validate configuration based on operating mode
        switch (operatingMode) {
            case TARGET_TEMPERATURE:
                // Validate target temperature (must be reasonable, e.g., 200K-500K)
                if (targetTemperature <= 0 || targetTemperature < 200.0f || targetTemperature > 500.0f) {
                    return SimpleCheckRecipeResult.ofFailure("awaiting_configuration");
                }
                break;
            case TARGET_COP:
                // Validate COP
                if (targetCOP <= 0 || targetCOP < 1.1f) {
                    return SimpleCheckRecipeResult.ofFailure("awaiting_configuration");
                }
                break;
            case TARGET_ENERGY:
                // Validate energy per tick
                if (targetEnergyPerTick <= 0) {
                    return SimpleCheckRecipeResult.ofFailure("awaiting_configuration");
                }
                break;
            default:
                return SimpleCheckRecipeResult.ofFailure("awaiting_configuration");
        }

        MTEIntegratedFluidInputHatch inputHatch = mIntegratedInputHatches.get(0);
        MTEIntegratedFluidOutputHatch outputHatch = mIntegratedOutputHatches.get(0);

        // Get input fluid from network
        var inputNetwork = inputHatch.getNetwork();
        if (inputNetwork == null) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        FluidStack inputFluid = inputNetwork.getStoredFluid();
        if (inputFluid == null || inputFluid.amount <= 0) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        // Check output capacity
        var outputNetwork = outputHatch.getNetwork();
        if (outputNetwork == null) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        FluidStack outputFluid = outputNetwork.getStoredFluid();
        int outputCapacity = outputNetwork.getMaxCapacity();
        int outputUsed = outputFluid != null ? outputFluid.amount : 0;
        int availableSpace = outputCapacity - outputUsed;

        if (availableSpace <= 0) {
            return CheckRecipeResultRegistry.ITEM_OUTPUT_FULL;
        }

        // IMPORTANT: Remember input temperature BEFORE draining!
        // This preserves temperature for output calculation even if network becomes empty
        float inputTemperature = inputNetwork.getTemperature();
        if (inputTemperature <= 0) {
            inputTemperature = 300.0f; // Room temperature default
        }

        // Calculate how much fluid to process - use the configured amount per operation
        int fluidToProcess = Math.min(inputFluid.amount, fluidAmountPerOperation);
        fluidToProcess = Math.min(fluidToProcess, availableSpace);

        if (fluidToProcess <= 0) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        // Create a fluid stack for thermal calculations
        FluidStack fluidForCalculation = inputFluid.copy();
        fluidForCalculation.amount = fluidToProcess;

        // Calculate based on operating mode
        float temperatureDelta;
        float outputTemperature;
        long totalEnergyCost;
        float penalty; // For efficiency penalty calculation
        boolean passthroughMode = false; // Flag for energy-free passthrough

        switch (operatingMode) {
            case TARGET_TEMPERATURE:
                // Mode 1: User sets target output temperature (absolute), we calculate delta, COP and energy
                outputTemperature = targetTemperature;
                temperatureDelta = targetTemperature - inputTemperature;

                // PASSTHROUGH MODE: Check if fluid is already at target temperature
                // Use configurable tolerances:
                // - lowerTemperatureTolerance: how much BELOW target is acceptable (no heating needed)
                // - upperTemperatureTolerance: how much ABOVE target is acceptable (no cooling needed)

                if (inputTemperature >= targetTemperature - lowerTemperatureTolerance
                    && inputTemperature <= targetTemperature + upperTemperatureTolerance) {
                    // Fluid is within tolerance range - passthrough without heating/cooling!
                    passthroughMode = true;
                    currentCOP = 0.0f; // No heating needed
                    totalEnergyCost = 0; // Zero energy consumption
                    this.totalEnergyCost = 0; // Store for GUI
                    outputTemperature = inputTemperature; // Keep current temperature
                    temperatureDelta = 0.0f;
                    currentTemperatureDelta = 0.0f;
                    currentEfficiencyPenalty = 1.0f; // No penalty in passthrough
                } else if (temperatureDelta < 0) {
                    // Target is lower than input - cooling not supported yet
                    return SimpleCheckRecipeResult.ofFailure("awaiting_configuration");
                } else {
                    // Normal heating operation
                    currentCOP = FluidThermalProperties.calculateHeatPumpCOP(
                        COLD_RESERVOIR_TEMPERATURE,
                        outputTemperature
                    );

                    // Calculate penalty for large temperature jumps (same as other modes)
                    penalty = FluidThermalProperties.calculateTemperaturePenalty(temperatureDelta);
                    currentTemperatureDelta = temperatureDelta;
                    currentEfficiencyPenalty = penalty;

                    // Calculate effective COP (real COP including penalty)
                    effectiveCOP = currentCOP / penalty;

                    // Calculate base energy
                    long baseEnergy = FluidThermalProperties.calculateHeatPumpEnergy(
                        fluidForCalculation,
                        temperatureDelta,
                        COLD_RESERVOIR_TEMPERATURE,
                        outputTemperature
                    );

                    // Apply penalty
                    totalEnergyCost = (long) (baseEnergy * penalty);
                    this.totalEnergyCost = (int) ((totalEnergyCost + 19) / 20); // Store per-tick for GUI (round up)
                }
                break;

            case TARGET_COP:
                // Mode 2: User sets target COP, we calculate temperature delta and energy
                // For a heat pump: COP = T_hot / (T_hot - T_cold)
                // Where T_cold is the cold reservoir (ambient 300K) and T_hot is output temperature
                //
                // We want to find the temperature delta (ΔT) that gives us the target COP
                // Given: inputTemp, targetCOP, T_cold = 300K
                // We need: outputTemp such that COP = outputTemp / (outputTemp - 300K)
                // Then: temperatureDelta = outputTemp - inputTemp

                if (targetCOP <= 1.0f) {
                    targetCOP = 1.1f; // Minimum sensible COP
                }

                // Rearrange COP formula to find T_hot (output temperature)
                // COP = T_hot / (T_hot - T_cold)
                // COP * (T_hot - T_cold) = T_hot
                // COP * T_hot - COP * T_cold = T_hot
                // COP * T_hot - T_hot = COP * T_cold
                // T_hot * (COP - 1) = COP * T_cold
                // T_hot = (COP * T_cold) / (COP - 1)
                outputTemperature = (targetCOP * COLD_RESERVOIR_TEMPERATURE) / (targetCOP - 1.0f);
                temperatureDelta = outputTemperature - inputTemperature;

                // Validate temperature delta
                if (temperatureDelta < 0.1f) {
                    // If calculated output temp is below input temp, use minimum delta
                    temperatureDelta = 0.1f;
                    outputTemperature = inputTemperature + temperatureDelta;
                    // Recalculate actual COP with this temperature
                    currentCOP = FluidThermalProperties.calculateHeatPumpCOP(
                        COLD_RESERVOIR_TEMPERATURE,
                        outputTemperature
                    );
                } else {
                    currentCOP = targetCOP;
                }

                // Calculate penalty for large temperature jumps
                penalty = FluidThermalProperties.calculateTemperaturePenalty(temperatureDelta);
                currentTemperatureDelta = temperatureDelta;
                currentEfficiencyPenalty = penalty;

                // Calculate effective COP (real COP including penalty)
                effectiveCOP = currentCOP / penalty;

                // Calculate base energy cost
                long baseEnergy = FluidThermalProperties.calculateHeatPumpEnergy(
                    fluidForCalculation,
                    temperatureDelta,
                    COLD_RESERVOIR_TEMPERATURE,
                    outputTemperature
                );

                // Apply penalty
                totalEnergyCost = (long) (baseEnergy * penalty);
                this.totalEnergyCost = (int) ((totalEnergyCost + 19) / 20); // Store per-tick for GUI (round up)
                break;

            case TARGET_ENERGY:
                // Mode 3: User sets target energy PER TICK directly
                // We calculate what temperature delta this energy can achieve
                // NOTE: In this mode, penalty REDUCES achievable temperature delta, not increases energy cost

                // Energy per tick * 20 ticks = total energy for the cycle
                long targetTotalEnergy = (long) targetEnergyPerTick * 20;

                // Energy = (m * c * ΔT) / COP
                // COP = T_hot / (T_hot - T_cold)
                //
                // This expands to a quadratic equation:
                // Energy * (T_input + ΔT) = m * c * ΔT * (T_input + ΔT - T_cold)
                //
                // Quadratic form: a*ΔT² + b*ΔT + c = 0

                float heatCapacity = FluidThermalProperties.getTotalHeatCapacity(fluidForCalculation);

                // Coefficients for quadratic equation
                float a = heatCapacity; // m * c
                float b = COLD_RESERVOIR_TEMPERATURE * heatCapacity - heatCapacity * inputTemperature - targetTotalEnergy;
                float c = -targetTotalEnergy * inputTemperature;

                // Solve using quadratic formula: ΔT = (-b ± sqrt(b² - 4ac)) / 2a
                float discriminant = b * b - 4 * a * c;

                if (discriminant < 0) {
                    // No real solution - use minimum temperature delta
                    temperatureDelta = 0.1f;
                } else {
                    // Two solutions - we want the positive one that makes physical sense
                    float sqrtDiscriminant = (float) Math.sqrt(discriminant);
                    float solution1 = (-b + sqrtDiscriminant) / (2 * a);
                    float solution2 = (-b - sqrtDiscriminant) / (2 * a);

                    // Pick the positive solution (both might be positive, pick smaller reasonable one)
                    if (solution1 > 0.1f && solution1 < 200.0f) {
                        temperatureDelta = solution1;
                    } else if (solution2 > 0.1f && solution2 < 200.0f) {
                        temperatureDelta = solution2;
                    } else {
                        // If neither is in reasonable range, use the closer one clamped
                        temperatureDelta = Math.max(0.1f, Math.min(Math.max(solution1, solution2), 200.0f));
                    }
                }

                // Calculate penalty for large temperature jumps
                // In TARGET_ENERGY mode, penalty REDUCES achievable temperature delta
                penalty = FluidThermalProperties.calculateTemperaturePenalty(temperatureDelta);

                // Apply penalty by reducing the achievable temperature delta
                // If penalty is 1.5x, we only achieve 1/1.5 = 67% of the ideal temperature rise
                temperatureDelta = temperatureDelta / penalty;

                currentTemperatureDelta = temperatureDelta;
                currentEfficiencyPenalty = penalty;

                outputTemperature = inputTemperature + temperatureDelta;

                currentCOP = FluidThermalProperties.calculateHeatPumpCOP(
                    COLD_RESERVOIR_TEMPERATURE,
                    outputTemperature
                );

                // Calculate effective COP (real COP including penalty)
                effectiveCOP = currentCOP / penalty;

                // Energy cost is exactly what user requested (penalty affects temperature, not energy)
                totalEnergyCost = targetTotalEnergy;
                this.totalEnergyCost = targetEnergyPerTick; // Store per-tick for GUI (user's exact value)
                break;

            default:
                return CheckRecipeResultRegistry.NO_RECIPE;
        }

        // Store calculated values for GUI
        currentOutputTemperature = outputTemperature;
        currentEnergyUsage = totalEnergyCost;


        // NOTE: We DON'T drain energy upfront! GTTileEntity will drain mEUt per tick automatically.
        // Draining upfront would cause double consumption (upfront + per tick)!

        // Drain fluid from input network
        FluidStack drainedFluid = inputNetwork.drainFluid(fluidToProcess, false);
        if (drainedFluid == null || drainedFluid.amount != fluidToProcess) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        // Heat the fluid (create copy for output)
        FluidStack heatedFluid = drainedFluid.copy();

        // Use the calculated output temperature from the operating mode
        float heatedTemperature = outputTemperature;

        // Add to output network with increased temperature
        // The network will automatically calculate weighted average if mixing with existing fluid
        int added = outputNetwork.addFluid(heatedFluid, false, heatedTemperature);

        if (added < heatedFluid.amount) {
            // Couldn't add all fluid - store remainder as pending for next cycle
            // This prevents losing heated fluid and wasted energy!
            FluidStack excess = heatedFluid.copy();
            excess.amount = heatedFluid.amount - added;

            // Store as pending fluid to be added in next cycle
            if (pendingOutputFluid == null) {
                pendingOutputFluid = excess;
                pendingOutputTemperature = heatedTemperature;
            } else {
                // Already have pending fluid - merge with weighted temperature
                float totalAmount = pendingOutputFluid.amount + excess.amount;
                float newTemp = (pendingOutputFluid.amount * pendingOutputTemperature + excess.amount * heatedTemperature) / totalAmount;
                pendingOutputFluid.amount += excess.amount;
                pendingOutputTemperature = newTemp;
            }
        }

        // Recipe successful - set to continuous operation
        if (passthroughMode) {
            // Passthrough mode: fast transfer with zero energy
            this.mMaxProgresstime = 5; // Only 5 ticks (0.25 seconds) for passthrough
            this.mEUt = 0; // Zero energy consumption
        } else {
            // Normal heating operation
            this.mMaxProgresstime = 20; // 1 second (20 ticks)
            this.mEfficiency = 10000; // Full efficiency

            // Set energy per tick based on mode
            if (operatingMode == HeatPumpMode.TARGET_ENERGY) {
                // In TARGET_ENERGY mode, use the user-specified value directly!
                this.mEUt = -targetEnergyPerTick; // Negative = consuming
            } else {
                // In other modes, calculate from totalEnergyCost
                long energyPerTick = (totalEnergyCost + 19) / 20; // Round up division
                this.mEUt = (int) -energyPerTick; // Negative = consuming
            }
        }

        return CheckRecipeResultRegistry.SUCCESSFUL;
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
        return network != null ? network.getTemperature() : 0.0f;
    }

    public float getOutputTemperature() {
        if (mIntegratedOutputHatches.isEmpty()) return 0.0f;
        var network = mIntegratedOutputHatches.get(0).getNetwork();
        return network != null ? network.getTemperature() : 0.0f;
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
        this.targetTemperature = Math.max(200.0f, Math.min(temperature, 500.0f));
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
                this.targetTemperature = Math.max(200.0f, Math.min(value, 500.0f));
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

        // Save pending output fluid
        if (pendingOutputFluid != null) {
            aNBT.setTag("pendingOutputFluid", pendingOutputFluid.writeToNBT(new NBTTagCompound()));
            aNBT.setFloat("pendingOutputTemperature", pendingOutputTemperature);
        }
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

        // Load pending output fluid
        if (aNBT.hasKey("pendingOutputFluid")) {
            pendingOutputFluid = FluidStack.loadFluidStackFromNBT(aNBT.getCompoundTag("pendingOutputFluid"));
            if (aNBT.hasKey("pendingOutputTemperature")) {
                pendingOutputTemperature = aNBT.getFloat("pendingOutputTemperature");
            }
        } else if (aNBT.hasKey("targetEnergy")) {
            // Backward compatibility - convert old total energy to per-tick
            targetEnergyPerTick = aNBT.getInteger("targetEnergy") / 20;
        }
    }
}

