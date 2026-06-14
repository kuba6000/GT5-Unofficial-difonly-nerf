package gregtech.test.fullgame;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.Random;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.Test;

import gregtech.api.metatileentity.implementations.integratedfluid.IFNFluidThermalRegistration;
import gregtech.api.metatileentity.implementations.integratedfluid.IIntegratedFluidMember;
import gregtech.api.metatileentity.implementations.integratedfluid.IntegratedFluidNetwork;
import gregtech.api.metatileentity.implementations.integratedfluid.IntegratedFluidNetworkSavedData;
import gregtech.api.metatileentity.implementations.integratedfluid.MTEIntegratedFluidInjectorHatch;
import gregtech.api.metatileentity.implementations.integratedfluid.MTEIntegratedFluidInputHatch;
import gregtech.api.metatileentity.implementations.integratedfluid.MTEIntegratedFluidPipe;
import gregtech.api.metatileentity.implementations.integratedfluid.state.IFNNetworkStatus;

final class FullGameServerVfnNetworkBehaviorTest {

    @Test
    void frozenNetworkBlocksTransferUntilClearedInServerWorld() {
        FullGameServerQaHarness qa = FullGameServerQaHarness.overworld();
        try {
            MTEIntegratedFluidInputHatch input = qa.placeMetaTile(
                30,
                72,
                40,
                FullGameServerQaHarness.INTEGRATED_FLUID_INPUT_HATCH_ID,
                MTEIntegratedFluidInputHatch.class);
            qa.tickTrackedTiles(40);
            seedInput(input, 1_000);

            IntegratedFluidNetwork network = input.getNetwork();
            network.freeze("full-game QA freeze");

            assertEquals(IFNNetworkStatus.FROZEN, network.getNetworkStatus(), "frozen VFN must report frozen status");
            assertEquals(0, input.addFluidToNetwork(new FluidStack(FluidRegistry.WATER, 1_000), false));
            assertTrue(network.drainFluid(100, false) == null, "frozen VFN must block machine-side draining");

            network.clearFrozen();
            assertEquals(IFNNetworkStatus.NORMAL, network.getNetworkStatus(), "cleared VFN must return to normal status");
            assertNotNull(network.drainFluid(100, false), "cleared VFN must expose stored water again");
        } finally {
            qa.clearTrackedBlocks();
        }
    }

    @Test
    void pressureCutoffBlocksInjectorFillInServerWorld() {
        FullGameServerQaHarness qa = FullGameServerQaHarness.overworld();
        try {
            ConnectedInjectorNetwork rig = ConnectedInjectorNetwork.place(qa, 40, 72, 40);
            IntegratedFluidNetwork network = rig.input.getNetwork();

            network.setPressure(12.0f);
            assertEquals(
                0,
                rig.injector.fill(ForgeDirection.WEST, new FluidStack(FluidRegistry.WATER, 100), false),
                "over-pressure VFN must reject injector transfer");

            network.setPressure(1.0f);
            assertTrue(
                rig.injector.fill(ForgeDirection.WEST, new FluidStack(FluidRegistry.WATER, 100), false) > 0,
                "normal-pressure VFN must accept injector transfer");
        } finally {
            qa.clearTrackedBlocks();
        }
    }

    @Test
    void injectorRejectsUnsupportedFluidAfterConnectionInServerWorld() {
        FullGameServerQaHarness qa = FullGameServerQaHarness.overworld();
        try {
            ConnectedInjectorNetwork rig = ConnectedInjectorNetwork.place(qa, 50, 72, 40);

            assertTrue(
                rig.injector.fill(ForgeDirection.WEST, new FluidStack(FluidRegistry.WATER, 100), false) > 0,
                "connected injector must accept registered water");
            assertEquals(
                0,
                rig.injector.fill(ForgeDirection.WEST, new FluidStack(FluidRegistry.LAVA, 100), false),
                "connected injector must reject fluid without VFN thermal registration");
        } finally {
            qa.clearTrackedBlocks();
        }
    }

    @Test
    void removingPipeSplitsInjectorAndInputHatchNetworksInServerWorld() {
        FullGameServerQaHarness qa = FullGameServerQaHarness.overworld();
        try {
            ConnectedInjectorNetwork rig = ConnectedInjectorNetwork.place(qa, 60, 72, 40);
            IntegratedFluidNetwork initialNetwork = rig.input.getNetwork();
            assertSame(initialNetwork, rig.injector.getNetwork(), "injector and input hatch start in one VFN");

            qa.clearBlock(61, 72, 40);
            qa.rebuildNetwork(rig.injector);
            qa.rebuildNetwork(rig.input);
            qa.tickTrackedTiles(40);

            assertNotNull(rig.injector.getNetwork(), "injector must still own a VFN after pipe removal");
            assertNotNull(rig.input.getNetwork(), "input hatch must still own a VFN after pipe removal");
            assertFalse(
                rig.injector.getNetwork() == rig.input.getNetwork(),
                "removing the only pipe must split the injector and input hatch VFNs");
        } finally {
            qa.clearTrackedBlocks();
        }
    }

    @Test
    void networkStatePersistsFluidAndAmountToServerWorldNbt() {
        FullGameServerQaHarness qa = FullGameServerQaHarness.overworld();
        try {
            MTEIntegratedFluidInputHatch input = qa.placeMetaTile(
                70,
                72,
                40,
                FullGameServerQaHarness.INTEGRATED_FLUID_INPUT_HATCH_ID,
                MTEIntegratedFluidInputHatch.class);
            qa.tickTrackedTiles(40);
            seedInput(input, 1_000);

            IntegratedFluidNetwork network = input.getNetwork();
            IntegratedFluidNetworkSavedData data = IntegratedFluidNetworkSavedData.get(qa.world());
            data.upsertState(network.getNetworkId(), network);

            NBTTagCompound nbt = new NBTTagCompound();
            data.writeToNBT(nbt);

            NBTTagList networks = nbt.getTagList("Networks", 10);
            assertTrue(networks.tagCount() > 0, "server world VFN saved data must contain network entries");
            assertTrue(
                containsPersistedWaterNetwork(networks, network),
                "server world VFN saved data must persist water fluid and amount");
        } finally {
            qa.clearTrackedBlocks();
        }
    }

    @Test
    void overpressureOperationalFailureVoidsServerWorldPipeNetwork() {
        FullGameServerQaHarness qa = FullGameServerQaHarness.overworld();
        try {
            ConnectedInjectorNetwork rig = ConnectedInjectorNetwork.place(qa, 80, 72, 40);
            assertTrue(rig.injector.fill(ForgeDirection.WEST, new FluidStack(FluidRegistry.WATER, 1_000), true) > 0);

            IntegratedFluidNetwork network = rig.input.getNetwork();
            network.setPressure(12.0f);

            assertTrue(network.getPressureLimitEvaluation()
                .isOverLimit(), "server-world VFN must detect over-pressure before failure");
            Optional<IIntegratedFluidMember> failed = network.applyOperationalFailure(fixedRandom(0.99d));

            assertTrue(failed.isPresent(), "over-pressure VFN must select a server-world failure candidate");
            assertSame(rig.pipe, failed.get(), "the physical pipe is the destructive failure candidate");
            assertTrue(network.getCanonicalState()
                .isEmpty(), "destructive VFN failure must void the stored fluid");
        } finally {
            qa.clearTrackedBlocks();
        }
    }

    private static void seedInput(MTEIntegratedFluidInputHatch input, int amount) {
        IFNFluidThermalRegistration.init();
        IntegratedFluidNetwork network = input.getNetwork();
        assertNotNull(network, "input hatch must own a VFN before seeding fluid");
        long amountQ = amount * IntegratedFluidNetwork.AMOUNT_SCALE;
        network.addState(
            FluidRegistry.WATER,
            amountQ,
            IntegratedFluidNetwork.toEnthalpyQFromSpecific(300.0d, amountQ));
        assertTrue(network.getStoredAmount() > 0, "input hatch VFN must store seeded water");
    }

    private static boolean containsPersistedWaterNetwork(NBTTagList networks, IntegratedFluidNetwork expectedNetwork) {
        for (int i = 0; i < networks.tagCount(); i++) {
            NBTTagCompound entry = networks.getCompoundTagAt(i);
            boolean sameNetwork = entry.getLong("IdMost") == expectedNetwork.getNetworkId()
                .getMostSignificantBits()
                && entry.getLong("IdLeast") == expectedNetwork.getNetworkId()
                    .getLeastSignificantBits();
            if (sameNetwork) {
                return "water".equals(entry.getString("FluidName")) && entry.getLong("AmountQ") > 0L;
            }
        }
        return false;
    }

    private static Random fixedRandom(double value) {
        return new Random(0L) {

            @Override
            public double nextDouble() {
                return value;
            }
        };
    }

    private static final class ConnectedInjectorNetwork {

        private final MTEIntegratedFluidInjectorHatch injector;
        private final MTEIntegratedFluidPipe pipe;
        private final MTEIntegratedFluidInputHatch input;

        private ConnectedInjectorNetwork(MTEIntegratedFluidInjectorHatch injector, MTEIntegratedFluidPipe pipe,
            MTEIntegratedFluidInputHatch input) {
            this.injector = injector;
            this.pipe = pipe;
            this.input = input;
        }

        private static ConnectedInjectorNetwork place(FullGameServerQaHarness qa, int x, int y, int z) {
            IFNFluidThermalRegistration.init();
            MTEIntegratedFluidInjectorHatch injector = qa.placeMetaTile(
                x,
                y,
                z,
                FullGameServerQaHarness.INTEGRATED_FLUID_INJECTOR_HATCH_ID,
                MTEIntegratedFluidInjectorHatch.class);
            MTEIntegratedFluidPipe pipe = qa.placeMetaTile(
                x + 1,
                y,
                z,
                FullGameServerQaHarness.INTEGRATED_FLUID_PIPE_ID,
                MTEIntegratedFluidPipe.class);
            MTEIntegratedFluidInputHatch input = qa.placeMetaTile(
                x + 2,
                y,
                z,
                FullGameServerQaHarness.INTEGRATED_FLUID_INPUT_HATCH_ID,
                MTEIntegratedFluidInputHatch.class);

            FullGameServerQaHarness.face(injector, ForgeDirection.EAST);
            FullGameServerQaHarness.face(input, ForgeDirection.WEST);
            FullGameServerQaHarness.connect(pipe, ForgeDirection.WEST, ForgeDirection.EAST);
            qa.tickTrackedTiles(40);
            qa.rebuildNetwork(injector);
            qa.rebuildNetwork(pipe);
            qa.rebuildNetwork(input);

            IntegratedFluidNetwork network = input.getNetwork();
            assertNotNull(network, "connected input hatch must own a VFN");
            assertSame(network, pipe.getNetwork(), "connected pipe must join input hatch VFN");
            assertSame(network, injector.getNetwork(), "connected injector must join input hatch VFN");
            return new ConnectedInjectorNetwork(injector, pipe, input);
        }
    }
}
