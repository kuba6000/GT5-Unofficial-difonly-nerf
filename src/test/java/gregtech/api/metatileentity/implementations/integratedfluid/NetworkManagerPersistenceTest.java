package gregtech.api.metatileentity.implementations.integratedfluid;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;

import gregtech.api.metatileentity.implementations.integratedfluid.state.IFNNetworkStatus;

class NetworkManagerPersistenceTest {

    @Test
    void frozenNetworkStateSurvivesManagerReload() {
        UUID networkId = UUID.randomUUID();
        IntegratedFluidNetworkSavedData savedData = new IntegratedFluidNetworkSavedData();
        IntegratedFluidNetwork frozenNetwork = new IntegratedFluidNetwork(networkId);
        frozenNetwork.setExpectedMemberCount(1);
        frozenNetwork.freeze("fluid conflict");
        savedData.upsertState(networkId, frozenNetwork);

        NetworkManager manager = new NetworkManager(null, savedData);
        TestMember member = new TestMember();
        member.setNetworkId(networkId);

        manager.onMemberAdded(member);

        assertEquals(IFNNetworkStatus.FROZEN, member.getNetwork().getNetworkStatus());
        assertEquals("fluid conflict", member.getNetwork().getFrozenReason());
    }

    @Test
    void frozenNetworkReasonSurvivesSavedDataNbtRoundTrip() {
        UUID networkId = UUID.randomUUID();
        IntegratedFluidNetworkSavedData savedData = new IntegratedFluidNetworkSavedData();
        IntegratedFluidNetwork frozenNetwork = new IntegratedFluidNetwork(networkId);
        frozenNetwork.setExpectedMemberCount(2);
        frozenNetwork.freeze("fluid conflict");
        savedData.upsertState(networkId, frozenNetwork);

        NBTTagCompound nbt = new NBTTagCompound();
        savedData.writeToNBT(nbt);

        IntegratedFluidNetworkSavedData reloadedData = new IntegratedFluidNetworkSavedData();
        reloadedData.readFromNBT(nbt);
        NetworkManager manager = new NetworkManager(null, reloadedData);
        TestMember member = new TestMember();
        member.setNetworkId(networkId);

        manager.onMemberAdded(member);

        assertEquals(IFNNetworkStatus.FROZEN, member.getNetwork().getNetworkStatus());
        assertEquals("fluid conflict", member.getNetwork().getFrozenReason());
        assertEquals(2, member.getNetwork().getExpectedMemberCount());
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
