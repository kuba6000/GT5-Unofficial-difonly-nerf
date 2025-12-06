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
import gregtech.api.metatileentity.implementations.integratedfluid.MTEIntegratedFluidInputHatch;
import gregtech.api.metatileentity.implementations.integratedfluid.MTEIntegratedFluidOutputHatch;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.recipe.check.SimpleCheckRecipeResult;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.blocks.BlockCasings2;

public class MTERadiator extends MTEEnhancedMultiBlockBase<MTERadiator> implements ISurvivalConstructable {

    private static final String STRUCTURE_PIECE_MAIN = "main";
    private static final int HEAT_CAPACITY_PER_TICK = 1000; // Max 1000L per tick
    private static final int EU_PER_TICK = 2000; // Base energy consumption
    private static final float TARGET_TEMPERATURE = 300.0f; // Ambient temperature

    // Custom hatch lists for Integrated Fluid Hatches
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
                // FIRST: Let buildHatchAdder capture Energy and Maintenance hatches
                buildHatchAdder(MTERadiator.class)
                    .atLeast(Energy, Maintenance)
                    .casingIndex(((BlockCasings2) GregTechAPI.sBlockCasings2).getTextureIndex(0))
                    .dot(1)
                    .buildAndChain(onElementPass(x -> ++x.mCasingAmount, ofBlock(GregTechAPI.sBlockCasings2, 0))),
                // THEN: Accept any remaining GregTech machines (like Integrated Fluid Hatches)
                ofBlockAnyMeta(GregTechAPI.sBlockMachines)))
        .build();

    private int mCasingAmount;

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
            .addInfo("Cools fluid from Input Hatch to Output Hatch")
            .addInfo("Decreases fluid temperature to 300K (ambient)")
            .addInfo("Processes up to 1000L per tick")
            .addInfo("Energy consumption: 2000 EU/t (proportional to fluid amount)")
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
    public IStructureDefinition<MTERadiator> getStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity baseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int colorIndex, boolean active, boolean redstoneLevel) {
        if (side == facing) {
            if (active) {
                return new ITexture[] {
                    TextureFactory.of(GregTechAPI.sBlockCasings2, 0),
                    TextureFactory.of(Textures.BlockIcons.OVERLAY_FRONT_VACUUM_FREEZER_ACTIVE)
                };
            }
            return new ITexture[] {
                TextureFactory.of(GregTechAPI.sBlockCasings2, 0),
                TextureFactory.of(Textures.BlockIcons.OVERLAY_FRONT_VACUUM_FREEZER)
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
        // Use integrated hatch lists
        if (mIntegratedInputHatches.isEmpty() || mIntegratedOutputHatches.isEmpty()) {
            return CheckRecipeResultRegistry.NO_RECIPE;
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

        // Calculate how much fluid to process
        int fluidToProcess = Math.min(inputFluid.amount, HEAT_CAPACITY_PER_TICK);
        fluidToProcess = Math.min(fluidToProcess, availableSpace);

        if (fluidToProcess <= 0) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        // Calculate proportional energy cost PER TICK
        long energyPerTick = (long) EU_PER_TICK * fluidToProcess / HEAT_CAPACITY_PER_TICK;

        // Recipe runs for 20 ticks, so total energy will be energyPerTick * 20
        long totalEnergyCost = energyPerTick * 20;
        if (!drainEnergyInput(totalEnergyCost)) {
            return SimpleCheckRecipeResult.ofFailure("no_energy");
        }

        // Drain fluid from input network
        FluidStack drainedFluid = inputNetwork.drainFluid(fluidToProcess, false);
        if (drainedFluid == null || drainedFluid.amount != fluidToProcess) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        // Cool the fluid (create copy for output)
        FluidStack cooledFluid = drainedFluid.copy();

        // Get input temperature and cool to ambient (300K)
        float inputTemperature = inputNetwork.getTemperature();
        if (inputTemperature <= 0) {
            inputTemperature = 300.0f; // Room temperature default
        }

        // Add to output network with decreased temperature (cool to TARGET_TEMPERATURE = 300K)
        int added = outputNetwork.addFluid(cooledFluid, false, TARGET_TEMPERATURE);
        if (added != cooledFluid.amount) {
            // Couldn't add all fluid - return excess to input at original temperature
            if (added < cooledFluid.amount) {
                FluidStack excess = cooledFluid.copy();
                excess.amount = cooledFluid.amount - added;
                inputNetwork.addFluid(excess, false, inputTemperature);
            }
        }

        // Recipe successful - set to continuous operation
        this.mMaxProgresstime = 20; // 1 second (20 ticks)
        this.mEUt = (int) -energyPerTick; // Negative = consuming

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
}


