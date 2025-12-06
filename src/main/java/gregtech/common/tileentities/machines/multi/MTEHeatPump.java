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

public class MTEHeatPump extends MTEEnhancedMultiBlockBase<MTEHeatPump> implements ISurvivalConstructable {

    private static final String STRUCTURE_PIECE_MAIN = "main";
    private static final int CASING_INDEX = 16; // Machine casing texture index
    private static final int HEAT_CAPACITY_PER_TICK = 1000; // Max 1000L per tick
    private static final int EU_PER_TICK = 2000; // Base energy consumption

    // Custom hatch lists for Integrated Fluid Hatches
    private final List<MTEIntegratedFluidInputHatch> mIntegratedInputHatches = new ArrayList<>();
    private final List<MTEIntegratedFluidOutputHatch> mIntegratedOutputHatches = new ArrayList<>();

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
                    .casingIndex(CASING_INDEX)
                    .dot(1)
                    .build(),
                // THEN: Accept any remaining GregTech machines (like Integrated Fluid Hatches)
                ofBlockAnyMeta(GregTechAPI.sBlockMachines),
                // FINALLY: Accept casings and count them
                onElementPass(x -> ++x.mCasingAmount, ofBlock(GregTechAPI.sBlockCasings2, 0))))
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
            .addInfo("Increases fluid temperature by 100K")
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

        // Debug logging AFTER checkPiece (which populates mEnergyHatches and mMaintenanceHatches)
        System.out.println("[HeatPump] Structure check result: " + result + ", Casings: " + mCasingAmount);
        System.out.println("[HeatPump] After checkPiece - Energy hatches: " + mEnergyHatches.size() + ", Maintenance hatches: " + mMaintenanceHatches.size());

        // Manually search for Integrated Fluid Hatches in the 3x3x3 structure
        int baseX = aBaseMetaTileEntity.getXCoord();
        int baseY = aBaseMetaTileEntity.getYCoord();
        int baseZ = aBaseMetaTileEntity.getZCoord();

        int totalTiles = 0;
        int totalMachines = 0;
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    totalTiles++;
                    var tile = aBaseMetaTileEntity.getWorld().getTileEntity(baseX + x, baseY + y, baseZ + z);

                    if (tile != null) {
                        System.out.println("[HeatPump] Tile at " + (baseX+x) + "," + (baseY+y) + "," + (baseZ+z)
                            + ": " + tile.getClass().getSimpleName());
                    }

                    if (tile instanceof IGregTechTileEntity gtTile) {
                        IMetaTileEntity mte = gtTile.getMetaTileEntity();
                        if (mte != null) {
                            totalMachines++;
                            System.out.println("[HeatPump]   MTE: " + mte.getClass().getSimpleName());

                            if (mte instanceof MTEIntegratedFluidInputHatch) {
                                mIntegratedInputHatches.add((MTEIntegratedFluidInputHatch) mte);
                                System.out.println("[HeatPump]   ✓ Added INPUT hatch!");
                            } else if (mte instanceof MTEIntegratedFluidOutputHatch) {
                                mIntegratedOutputHatches.add((MTEIntegratedFluidOutputHatch) mte);
                                System.out.println("[HeatPump]   ✓ Added OUTPUT hatch!");
                            }
                        }
                    }
                }
            }
        }

        System.out.println("[HeatPump] Scanned " + totalTiles + " positions, found " + totalMachines + " machines");
        System.out.println("[HeatPump] Input hatches: " + mIntegratedInputHatches.size());
        System.out.println("[HeatPump] Output hatches: " + mIntegratedOutputHatches.size());
        System.out.println("[HeatPump] Energy hatches (from buildHatchAdder): " + mEnergyHatches.size());
        System.out.println("[HeatPump] Maintenance hatches (from buildHatchAdder): " + mMaintenanceHatches.size());

        boolean hasAllHatches = !mIntegratedInputHatches.isEmpty()
            && !mIntegratedOutputHatches.isEmpty()
            && !mEnergyHatches.isEmpty()
            && !mMaintenanceHatches.isEmpty();

        System.out.println("[HeatPump] Final result: " + (result && hasAllHatches));

        return result && hasAllHatches;
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

        // Calculate proportional energy cost
        long energyCost = (long) EU_PER_TICK * fluidToProcess / HEAT_CAPACITY_PER_TICK;

        // Check if we have enough energy
        if (!drainEnergyInput(energyCost)) {
            return SimpleCheckRecipeResult.ofFailure("no_energy");
        }

        // Drain fluid from input network
        FluidStack drainedFluid = inputNetwork.drainFluid(fluidToProcess, false);
        if (drainedFluid == null || drainedFluid.amount != fluidToProcess) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        // Heat the fluid (create copy for output)
        FluidStack heatedFluid = drainedFluid.copy();

        // Add to output network
        int added = outputNetwork.addFluid(heatedFluid, false);
        if (added != heatedFluid.amount) {
            // Couldn't add all fluid - return excess to input
            if (added < heatedFluid.amount) {
                FluidStack excess = heatedFluid.copy();
                excess.amount = heatedFluid.amount - added;
                inputNetwork.addFluid(excess, false);
            }
        }

        // Recipe successful - set to continuous operation
        this.mMaxProgresstime = 20; // 1 second
        this.mEUt = (int) -energyCost; // Negative = consuming

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

