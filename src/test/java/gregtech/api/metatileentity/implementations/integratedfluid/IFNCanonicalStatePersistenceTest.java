package gregtech.api.metatileentity.implementations.integratedfluid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.Fluid;

import org.junit.jupiter.api.Test;

class IFNCanonicalStatePersistenceTest {

    @Test
    void persistedNetworkStateStoresCanonicalSubstanceAndEnergyOnly() {
        Fluid fluid = IFNTestSupport.liquidFluid();
        IntegratedFluidNetwork network = IFNTestSupport.newNetwork(fluid, 1_000, 0, 20.0f);
        long amountQ = 12L * IntegratedFluidNetwork.AMOUNT_SCALE;
        long enthalpyQ = 34L * IntegratedFluidNetwork.ENTHALPY_SCALE;
        UUID id = new UUID(1L, 2L);

        network.addState(fluid, amountQ, enthalpyQ);

        IntegratedFluidNetworkSavedData data = new IntegratedFluidNetworkSavedData();
        data.upsertState(id, network);
        NBTTagCompound root = new NBTTagCompound();
        data.writeToNBT(root);

        NBTTagCompound entry = onlyNetworkEntry(root);
        assertEquals(fluid.getName(), entry.getString("FluidName"));
        assertEquals(amountQ, entry.getLong("AmountQ"));
        assertEquals(enthalpyQ, entry.getLong("EnthalpyQ"));
        assertFalse(entry.hasKey("Pressure"));
        assertFalse(entry.hasKey("Temperature"));
    }

    @Test
    void canonicalStateRoundTripsThroughSavedData() {
        Fluid fluid = IFNTestSupport.liquidFluid();
        long amountQ = 7L * IntegratedFluidNetwork.AMOUNT_SCALE;
        long enthalpyQ = 11L * IntegratedFluidNetwork.ENTHALPY_SCALE;
        UUID id = new UUID(3L, 4L);
        NBTTagCompound entry = new NBTTagCompound();
        entry.setLong("IdMost", id.getMostSignificantBits());
        entry.setLong("IdLeast", id.getLeastSignificantBits());
        entry.setInteger("ExpectedMembers", 5);
        entry.setString("FluidName", fluid.getName());
        entry.setLong("AmountQ", amountQ);
        entry.setLong("EnthalpyQ", enthalpyQ);
        entry.setFloat("Pressure", 123.0f);
        entry.setFloat("Temperature", 456.0f);
        NBTTagList networks = new NBTTagList();
        networks.appendTag(entry);
        NBTTagCompound root = new NBTTagCompound();
        root.setTag("Networks", networks);

        IntegratedFluidNetworkSavedData data = new IntegratedFluidNetworkSavedData();
        data.readFromNBT(root);

        IntegratedFluidNetworkSavedData.NetworkState state = data.getState(id);
        assertNotNull(state);
        assertEquals(fluid.getName(), state.fluidName);
        assertEquals(amountQ, state.amountQ);
        assertEquals(enthalpyQ, state.enthalpyQ);
        assertEquals(5, state.expectedMemberCount);
    }

    private static NBTTagCompound onlyNetworkEntry(NBTTagCompound root) {
        NBTTagList networks = root.getTagList("Networks", 10);
        assertEquals(1, networks.tagCount());
        return networks.getCompoundTagAt(0);
    }
}
