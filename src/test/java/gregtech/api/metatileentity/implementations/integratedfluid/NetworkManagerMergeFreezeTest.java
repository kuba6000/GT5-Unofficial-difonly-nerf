package gregtech.api.metatileentity.implementations.integratedfluid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.HashSet;
import java.util.UUID;

import net.minecraftforge.fluids.Fluid;

import org.junit.jupiter.api.Test;

import gregtech.api.metatileentity.implementations.integratedfluid.state.IFNNetworkStatus;

class NetworkManagerMergeFreezeTest {

    @Test
    void incompatibleManagerMergeFreezesBothNetworksWithoutMovingMembers() {
        NetworkManager manager = new NetworkManager(null, new IntegratedFluidNetworkSavedData());
        Fluid liquid = IFNTestSupport.liquidFluid();
        Fluid vapor = IFNTestSupport.vaporFluid();
        IntegratedFluidNetwork liquidNetwork = seededNetwork(liquid, 2L);
        IntegratedFluidNetwork vaporNetwork = seededNetwork(vapor, 3L);
        TestMember liquidMember = new TestMember();
        TestMember vaporMember = new TestMember();

        liquidNetwork.addMember(liquidMember);
        vaporNetwork.addMember(vaporMember);
        liquidMember.setNetwork(liquidNetwork);
        vaporMember.setNetwork(vaporNetwork);

        manager.mergeIntoNetwork(liquidNetwork, Collections.singleton(vaporNetwork));

        assertEquals(IFNNetworkStatus.FROZEN, liquidNetwork.getNetworkStatus());
        assertEquals(IFNNetworkStatus.FROZEN, vaporNetwork.getNetworkStatus());
        assertSame(liquidNetwork, liquidMember.getNetwork());
        assertSame(vaporNetwork, vaporMember.getNetwork());
        assertEquals(2L * IntegratedFluidNetwork.AMOUNT_SCALE, liquidNetwork.getAmountQ());
        assertEquals(3L * IntegratedFluidNetwork.AMOUNT_SCALE, vaporNetwork.getAmountQ());
    }

    @Test
    void incompatibleAffectedNetworksFreezeBeforeTopologyRebuild() {
        NetworkManager manager = new NetworkManager(null, new IntegratedFluidNetworkSavedData());
        IntegratedFluidNetwork liquidNetwork = seededNetwork(IFNTestSupport.liquidFluid(), 2L);
        IntegratedFluidNetwork vaporNetwork = seededNetwork(IFNTestSupport.vaporFluid(), 3L);
        HashSet<IntegratedFluidNetwork> affectedNetworks = new HashSet<>();
        affectedNetworks.add(liquidNetwork);
        affectedNetworks.add(vaporNetwork);

        boolean frozen = manager.freezeIfIncompatibleNetworks(affectedNetworks);

        assertTrue(frozen);
        assertEquals(IFNNetworkStatus.FROZEN, liquidNetwork.getNetworkStatus());
        assertEquals(IFNNetworkStatus.FROZEN, vaporNetwork.getNetworkStatus());
        assertEquals(2L * IntegratedFluidNetwork.AMOUNT_SCALE, liquidNetwork.getAmountQ());
        assertEquals(3L * IntegratedFluidNetwork.AMOUNT_SCALE, vaporNetwork.getAmountQ());
    }

    private static IntegratedFluidNetwork seededNetwork(Fluid fluid, long refL) {
        IntegratedFluidNetwork network = IFNTestSupport.newNetwork(fluid, 1000, 0, 100.0f);
        long amountQ = refL * IntegratedFluidNetwork.AMOUNT_SCALE;
        network.addState(fluid, amountQ, IntegratedFluidNetwork.toEnthalpyQFromSpecific(300.0d, amountQ));
        return network;
    }

    private static final class TestMember implements IIntegratedFluidMember {

        private IntegratedFluidNetwork network;
        private UUID networkId;

        @Override
        public IntegratedFluidNetwork getNetwork() {
            return network;
        }

        @Override
        public void setNetwork(IntegratedFluidNetwork network) {
            this.network = network;
        }

        @Override
        public UUID getNetworkId() {
            return networkId;
        }

        @Override
        public void setNetworkId(UUID id) {
            this.networkId = id;
        }

        @Override
        public void onNetworkUpdate() {}

        @Override
        public int getCapacityContribution() {
            return 1000;
        }
    }
}
