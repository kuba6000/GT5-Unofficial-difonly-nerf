package gregtech.test.fullgame;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ForgeDirection;

import gtPlusPlus.core.block.ModBlocks;
import gtPlusPlus.core.tileentities.general.TileEntityInfiniteFluid;
import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.integratedfluid.IIntegratedFluidMember;
import gregtech.api.metatileentity.implementations.integratedfluid.MTEIntegratedFluidInputHatch;
import gregtech.api.metatileentity.implementations.integratedfluid.MTEIntegratedFluidOutputHatch;
import gregtech.api.metatileentity.implementations.integratedfluid.MTEIntegratedFluidPipe;
import gregtech.api.metatileentity.implementations.integratedfluid.NetworkManager;
import gregtech.common.tileentities.machines.multi.MTEHeatPump;

final class FullGameServerQaHarness {

    static final short INTEGRATED_FLUID_PIPE_ID = 5770;
    static final short INTEGRATED_FLUID_INPUT_HATCH_ID = 5771;
    static final short INTEGRATED_FLUID_OUTPUT_HATCH_ID = 5772;
    static final short INTEGRATED_FLUID_INJECTOR_HATCH_ID = 5773;
    static final short INTEGRATED_FLUID_PRESSURE_REGULATOR_ID = 5774;
    static final short SUPER_TANK_LV_ID = 130;
    static final short HEAT_PUMP_CONTROLLER_ID = 32030;

    private final WorldServer world;
    private final List<int[]> trackedPositions = new ArrayList<>();

    private FullGameServerQaHarness(WorldServer world) {
        this.world = world;
    }

    static FullGameServerQaHarness overworld() {
        WorldServer world = MinecraftServer.getServer()
            .worldServerForDimension(0);
        assertNotNull(world, "full-game server QA needs a loaded overworld");
        return new FullGameServerQaHarness(world);
    }

    WorldServer world() {
        return world;
    }

    TileEntityInfiniteFluid placeCreativeTank(int x, int y, int z) {
        trackAndClear(x, y, z);
        assertNotNull(ModBlocks.blockInfiniteFLuidTank, "GT++ infinite fluid tank block must be registered");
        assertTrue(world.setBlock(x, y, z, ModBlocks.blockInfiniteFLuidTank, 0, 3), "placing GT++ creative fluid tank");

        TileEntity tile = world.getTileEntity(x, y, z);
        assertTrue(tile instanceof TileEntityInfiniteFluid, "GT++ creative fluid tank must create its tile entity");
        return (TileEntityInfiniteFluid) tile;
    }

    <T extends IMetaTileEntity> T placeMetaTile(int x, int y, int z, short metaId, Class<T> expectedType) {
        trackAndClear(x, y, z);
        assertNotNull(GregTechAPI.METATILEENTITIES[metaId], "GT meta tile " + metaId + " must be registered");
        int baseTileType = GregTechAPI.METATILEENTITIES[metaId].getTileEntityBaseType();
        assertTrue(world.setBlock(x, y, z, GregTechAPI.sBlockMachines, baseTileType, 3), "placing GT meta tile " + metaId);

        TileEntity tile = world.getTileEntity(x, y, z);
        assertTrue(tile instanceof IGregTechTileEntity, "GT meta tile " + metaId + " must create a GT tile entity");

        IGregTechTileEntity gtTile = (IGregTechTileEntity) tile;
        gtTile.setInitialValuesAsNBT(null, metaId);

        IMetaTileEntity metaTile = gtTile.getMetaTileEntity();
        assertTrue(expectedType.isInstance(metaTile), "GT meta tile " + metaId + " must be " + expectedType.getSimpleName());
        return expectedType.cast(metaTile);
    }

    void tickTrackedTiles(int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            for (int[] position : trackedPositions) {
                TileEntity tile = world.getTileEntity(position[0], position[1], position[2]);
                if (tile != null) {
                    tile.updateEntity();
                }
            }
        }
    }

    void clearTrackedBlocks() {
        for (int[] position : trackedPositions) {
            world.setBlockToAir(position[0], position[1], position[2]);
        }
    }

    void clearBlock(int x, int y, int z) {
        world.setBlockToAir(x, y, z);
    }

    void rebuildNetwork(IIntegratedFluidMember member) {
        NetworkManager.getInstance(world)
            .onConnectionChanged(member);
    }

    static void face(IMetaTileEntity metaTile, ForgeDirection facing) {
        metaTile.getBaseMetaTileEntity()
            .setFrontFacing(facing);
    }

    static void connect(MTEIntegratedFluidPipe pipe, ForgeDirection firstSide, ForgeDirection secondSide) {
        pipe.connect(firstSide);
        pipe.connect(secondSide);
    }

    static void color(IMetaTileEntity metaTile, byte color) {
        metaTile.getBaseMetaTileEntity()
            .setColorization(color);
    }

    static void attachIntegratedHatches(MTEHeatPump heatPump, List<MTEIntegratedFluidInputHatch> inputs,
        List<MTEIntegratedFluidOutputHatch> outputs) throws ReflectiveOperationException {
        replaceFinalListField(heatPump, "mIntegratedInputHatches", inputs);
        replaceFinalListField(heatPump, "mIntegratedOutputHatches", outputs);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void replaceFinalListField(MTEHeatPump heatPump, String fieldName, List values)
        throws ReflectiveOperationException {
        Field field = MTEHeatPump.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        List existing = (List) field.get(heatPump);
        existing.clear();
        existing.addAll(values);
    }

    private void trackAndClear(int x, int y, int z) {
        trackedPositions.add(new int[] { x, y, z });
        world.setBlockToAir(x, y, z);
    }
}
