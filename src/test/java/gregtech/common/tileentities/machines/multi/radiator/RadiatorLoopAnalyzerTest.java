package gregtech.common.tileentities.machines.multi.radiator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraftforge.common.util.ForgeDirection;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import gregtech.api.GregTechAPI;
import gregtech.common.GTMockWorld;
import gregtech.common.tileentities.machines.multi.radiator.RadiatorLoopAnalyzer.NodePos;

class RadiatorLoopAnalyzerTest {

    private static final int CONTROLLER_X = 0;
    private static final int CONTROLLER_Y = 0;
    private static final int CONTROLLER_Z = 0;
    private static final ForgeDirection FRONT_FACING = ForgeDirection.NORTH;
    private static final Block TEST_CASING_BLOCK = new Block(Material.iron) {};

    private static Block previousCasings11;

    @BeforeAll
    static void setUpLegacyCasingBlock() {
        previousCasings11 = GregTechAPI.sBlockCasings11;
        GregTechAPI.sBlockCasings11 = TEST_CASING_BLOCK;
    }

    @AfterAll
    static void restoreLegacyCasingBlock() {
        GregTechAPI.sBlockCasings11 = previousCasings11;
    }

    @Test
    void straightLoopCountsSegmentsAndPressureDrop() {
        GTMockWorld world = new GTMockWorld();
        placeDefaultPorts(world);
        placePath(world, overTheTopPath());
        assertTrue(RadiatorLoopAnalyzer.isPort(world, -1, 0, 1));
        assertTrue(RadiatorLoopAnalyzer.isPort(world, 1, 0, 1));
        assertTrue(RadiatorLoopAnalyzer.isLoopPipe(world, pos(-2, 0, 1)));

        RadiatorLoopSnapshot snapshot = analyze(world);

        assertTrue(snapshot.valid, snapshot.statusKey);
        assertEquals(9, snapshot.segmentCount);
        assertEquals(0, snapshot.conductionModuleCount);
        assertEquals(0, snapshot.heatExchangeModuleCount);
        assertEquals(9 * RadiatorThermo.PRESSURE_DROP_PER_SEGMENT_BAR, snapshot.pressureDropBar, 1.0e-6f);
    }

    @Test
    void cornerModulesAreDetectedAndInsideCornerExchangerIsNotDoubleCounted() {
        GTMockWorld world = new GTMockWorld();
        placeDefaultPorts(world);
        placePath(world, elevatedBackLoopPath());

        // Adjacent to a bend, but only touching one pipe segment.
        placeHeatExchange(world, -1, 2, 1);
        // Inside-corner placement that touches two pipe segments; should still count once.
        placeHeatExchange(world, -1, 2, 2);

        RadiatorLoopSnapshot snapshot = analyze(world);

        assertTrue(snapshot.valid, snapshot.statusKey);
        assertEquals(13, snapshot.segmentCount);
        assertEquals(2, snapshot.heatExchangeModuleCount);
    }

    @Test
    void conductionChainsAreCountedAndIncreaseEffectiveConductance() {
        GTMockWorld baseWorld = new GTMockWorld();
        placeDefaultPorts(baseWorld);
        placePath(baseWorld, elevatedBackLoopPath());

        GTMockWorld exchangerWorld = new GTMockWorld();
        placeDefaultPorts(exchangerWorld);
        placePath(exchangerWorld, elevatedBackLoopPath());
        placeHeatExchange(exchangerWorld, 0, 3, 3);

        GTMockWorld conductionWorld = new GTMockWorld();
        placeDefaultPorts(conductionWorld);
        placePath(conductionWorld, elevatedBackLoopPath());
        placeConduction(conductionWorld, 0, 3, 3);
        placeConduction(conductionWorld, 0, 4, 3);
        placeHeatExchange(conductionWorld, 0, 5, 3);
        placeHeatExchange(conductionWorld, 1, 3, 3);

        RadiatorLoopSnapshot baseSnapshot = analyze(baseWorld);
        RadiatorLoopSnapshot exchangerSnapshot = analyze(exchangerWorld);
        RadiatorLoopSnapshot conductionSnapshot = analyze(conductionWorld);

        assertTrue(baseSnapshot.valid, baseSnapshot.statusKey);
        assertTrue(exchangerSnapshot.valid, exchangerSnapshot.statusKey);
        assertTrue(conductionSnapshot.valid, conductionSnapshot.statusKey);
        assertEquals(0, baseSnapshot.conductionModuleCount);
        assertEquals(0, baseSnapshot.heatExchangeModuleCount);
        assertEquals(0, exchangerSnapshot.conductionModuleCount);
        assertEquals(1, exchangerSnapshot.heatExchangeModuleCount);
        assertEquals(2, conductionSnapshot.conductionModuleCount);
        assertEquals(2, conductionSnapshot.heatExchangeModuleCount);

        double baseConductance = RadiatorThermo.computeEffectiveConductance(baseSnapshot, 500.0d);
        double exchangerConductance = RadiatorThermo.computeEffectiveConductance(exchangerSnapshot, 500.0d);
        double conductionConductance = RadiatorThermo.computeEffectiveConductance(conductionSnapshot, 500.0d);

        assertTrue(exchangerConductance > baseConductance);
        assertTrue(conductionConductance > exchangerConductance);
    }

    @Test
    void longerLoopProducesLargerPressureDropThanShorterLoop() {
        GTMockWorld shortWorld = new GTMockWorld();
        placeDefaultPorts(shortWorld);
        placePath(shortWorld, overTheTopPath());

        GTMockWorld longWorld = new GTMockWorld();
        placeDefaultPorts(longWorld);
        placePath(longWorld, elevatedBackLoopPath());

        RadiatorLoopSnapshot shortSnapshot = analyze(shortWorld);
        RadiatorLoopSnapshot longSnapshot = analyze(longWorld);

        assertTrue(shortSnapshot.valid, shortSnapshot.statusKey);
        assertTrue(longSnapshot.valid, longSnapshot.statusKey);
        assertTrue(longSnapshot.segmentCount > shortSnapshot.segmentCount);
        assertTrue(longSnapshot.pressureDropBar > shortSnapshot.pressureDropBar);
    }

    private static RadiatorLoopSnapshot analyze(GTMockWorld world) {
        return RadiatorLoopAnalyzer.analyze(world, CONTROLLER_X, CONTROLLER_Y, CONTROLLER_Z, FRONT_FACING);
    }

    private static void placeDefaultPorts(GTMockWorld world) {
        placePort(world, -1, 0, 1);
        placePort(world, 1, 0, 1);
    }

    private static List<NodePos> overTheTopPath() {
        return Arrays.asList(
            pos(-2, 0, 1),
            pos(-2, 1, 1),
            pos(-2, 2, 1),
            pos(-1, 2, 1),
            pos(0, 2, 1),
            pos(1, 2, 1),
            pos(2, 2, 1),
            pos(2, 1, 1),
            pos(2, 0, 1)
        );
    }

    private static List<NodePos> elevatedBackLoopPath() {
        return Arrays.asList(
            pos(-2, 0, 1),
            pos(-2, 1, 1),
            pos(-2, 2, 1),
            pos(-2, 2, 2),
            pos(-2, 2, 3),
            pos(-1, 2, 3),
            pos(0, 2, 3),
            pos(1, 2, 3),
            pos(2, 2, 3),
            pos(2, 2, 2),
            pos(2, 2, 1),
            pos(2, 1, 1),
            pos(2, 0, 1)
        );
    }

    private static void placePath(GTMockWorld world, List<NodePos> path) {
        for (NodePos pos : path) {
            placeLoopPipe(world, pos.x, pos.y, pos.z);
        }
    }

    private static NodePos pos(int x, int y, int z) {
        return new NodePos(x, y, z);
    }

    private static void placePort(GTMockWorld world, int x, int y, int z) {
        placeLegacyCasing(world, x, y, z, RadiatorLoopAnalyzer.PORT_META);
    }

    private static void placeLoopPipe(GTMockWorld world, int x, int y, int z) {
        placeLegacyCasing(world, x, y, z, RadiatorLoopAnalyzer.LOOP_PIPE_META);
    }

    private static void placeConduction(GTMockWorld world, int x, int y, int z) {
        placeLegacyCasing(world, x, y, z, RadiatorLoopAnalyzer.CONDUCTION_META);
    }

    private static void placeHeatExchange(GTMockWorld world, int x, int y, int z) {
        placeLegacyCasing(world, x, y, z, RadiatorLoopAnalyzer.HEAT_EXCHANGE_META);
    }

    private static void placeLegacyCasing(GTMockWorld world, int x, int y, int z, int meta) {
        world.setBlock(x, y, z, GregTechAPI.sBlockCasings11, meta, 3);
    }
}
