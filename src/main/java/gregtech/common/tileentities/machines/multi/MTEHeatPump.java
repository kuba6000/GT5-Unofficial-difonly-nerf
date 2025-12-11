package gregtech.common.tileentities.machines.multi;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.*;
import static gregtech.api.enums.HatchElement.*;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
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
    private static final int HEAT_CAPACITY_PER_TICK = 1000; // Max 1000L per tick
    private static final float COLD_RESERVOIR_TEMPERATURE = 300.0f; // Ambient temperature for COP calculation
    private static final float DEFAULT_TARGET_TEMPERATURE = 310.0f; // Default target output temperature (310K)
    private static final float DEFAULT_TARGET_COP = 5.0f; // Default COP target
    private static final int DEFAULT_TARGET_ENERGY_PER_TICK = 100; // Default 100 EU/t

    // Custom hatch lists for Integrated Fluid Hatches
    private final List<MTEIntegratedFluidInputHatch> mIntegratedInputHatches = new ArrayList<>();
    private final List<MTEIntegratedFluidOutputHatch> mIntegratedOutputHatches = new ArrayList<>();

    // Operating mode and targets
    private HeatPumpMode operatingMode = HeatPumpMode.TARGET_TEMPERATURE;
    private float targetTemperature = DEFAULT_TARGET_TEMPERATURE; // For TARGET_TEMPERATURE mode (absolute temperature in K)
    private float targetCOP = DEFAULT_TARGET_COP; // For TARGET_COP mode
    private int targetEnergyPerTick = DEFAULT_TARGET_ENERGY_PER_TICK; // For TARGET_ENERGY mode (EU per tick)

    // Current calculated values for GUI display
    private float currentCOP = 0.0f;
    private float currentOutputTemperature = 0.0f;
    private long currentEnergyUsage = 0L;

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
        // Verify we have integrated fluid hatches
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

        // Calculate how much fluid to process
        int fluidToProcess = Math.min(inputFluid.amount, HEAT_CAPACITY_PER_TICK);
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
        boolean passthroughMode = false; // Flag for energy-free passthrough

        switch (operatingMode) {
            case TARGET_TEMPERATURE:
                // Mode 1: User sets target output temperature (absolute), we calculate delta, COP and energy
                outputTemperature = targetTemperature;
                temperatureDelta = targetTemperature - inputTemperature;

                // PASSTHROUGH MODE: Check if fluid is already at target temperature
                // Allow ±0.5K tolerance to avoid constant micro-heating
                float tempDifference = Math.abs(inputTemperature - targetTemperature);

                if (tempDifference <= 0.5f) {
                    // Fluid is already at target temperature - passthrough without heating!
                    passthroughMode = true;
                    currentCOP = 0.0f; // No heating needed
                    totalEnergyCost = 0; // Zero energy consumption
                    outputTemperature = inputTemperature; // Keep current temperature
                    temperatureDelta = 0.0f;
                } else if (temperatureDelta < 0) {
                    // Target is lower than input - cooling not supported yet
                    return SimpleCheckRecipeResult.ofFailure("awaiting_configuration");
                } else {
                    // Normal heating operation
                    currentCOP = FluidThermalProperties.calculateHeatPumpCOP(
                        COLD_RESERVOIR_TEMPERATURE,
                        outputTemperature
                    );

                    totalEnergyCost = FluidThermalProperties.calculateHeatPumpEnergy(
                        fluidForCalculation,
                        temperatureDelta,
                        COLD_RESERVOIR_TEMPERATURE,
                        outputTemperature
                    );
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

                totalEnergyCost = FluidThermalProperties.calculateHeatPumpEnergy(
                    fluidForCalculation,
                    temperatureDelta,
                    COLD_RESERVOIR_TEMPERATURE,
                    outputTemperature
                );
                break;

            case TARGET_ENERGY:
                // Mode 3: User sets target energy PER TICK, we calculate temperature delta and COP
                // Convert EU/t to total energy for 20-tick operation
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

                outputTemperature = inputTemperature + temperatureDelta;

                currentCOP = FluidThermalProperties.calculateHeatPumpCOP(
                    COLD_RESERVOIR_TEMPERATURE,
                    outputTemperature
                );

                // In TARGET_ENERGY mode, use the target energy directly
                // Don't recalculate - that defeats the purpose of this mode!
                totalEnergyCost = targetTotalEnergy;
                break;

            default:
                return CheckRecipeResultRegistry.NO_RECIPE;
        }

        // Store calculated values for GUI
        currentOutputTemperature = outputTemperature;
        currentEnergyUsage = totalEnergyCost;

        // Recipe runs for 20 ticks (1 second)
        long energyPerTick = (totalEnergyCost + 19) / 20; // Round up division

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
        if (added != heatedFluid.amount) {
            // Couldn't add all fluid - return excess to input at original temperature
            if (added < heatedFluid.amount) {
                FluidStack excess = heatedFluid.copy();
                excess.amount = heatedFluid.amount - added;
                inputNetwork.addFluid(excess, false, inputTemperature);
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

            // IMPORTANT: mEUt is EU consumed PER TICK during the recipe
            // energyPerTick is already calculated as per-tick consumption
            // Total energy consumed will be energyPerTick * mMaxProgresstime
            this.mEUt = (int) -energyPerTick; // Negative = consuming
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

    public float getCurrentOutputTemperature() {
        return currentOutputTemperature;
    }

    public long getCurrentEnergyUsage() {
        return currentEnergyUsage;
    }

    // Operating mode methods
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
        } else if (aNBT.hasKey("targetEnergy")) {
            // Backward compatibility - convert old total energy to per-tick
            targetEnergyPerTick = aNBT.getInteger("targetEnergy") / 20;
        }
    }
}

