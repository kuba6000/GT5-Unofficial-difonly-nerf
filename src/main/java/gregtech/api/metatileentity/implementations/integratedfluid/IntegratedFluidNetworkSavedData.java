package gregtech.api.metatileentity.implementations.integratedfluid;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.storage.MapStorage;
import net.minecraftforge.fluids.Fluid;

public class IntegratedFluidNetworkSavedData extends WorldSavedData {

    private static final String DATA_NAME = "GregTech_IntegratedFluidNetworkData";
    private static final String TAG_NETWORKS = "Networks";
    private static final String TAG_ID_MOST = "IdMost";
    private static final String TAG_ID_LEAST = "IdLeast";
    private static final String TAG_EXPECTED = "ExpectedMembers";
    private static final String TAG_FLUID_NAME = "FluidName";
    private static final String TAG_AMOUNT_Q = "AmountQ";
    private static final String TAG_ENTHALPY_Q = "EnthalpyQ";
    private static final String TAG_TEMPERATURE_OLD = "Temperature";
    private static final String TAG_FLUID_OLD = "Fluid";

    private final Map<UUID, NetworkState> networks = new HashMap<>();

    public IntegratedFluidNetworkSavedData() {
        super(DATA_NAME);
    }

    public IntegratedFluidNetworkSavedData(String name) {
        super(name);
    }

    public static IntegratedFluidNetworkSavedData get(World world) {
        MapStorage storage = world.mapStorage;
        IntegratedFluidNetworkSavedData data =
            (IntegratedFluidNetworkSavedData) storage.loadData(IntegratedFluidNetworkSavedData.class, DATA_NAME);
        if (data == null) {
            data = new IntegratedFluidNetworkSavedData();
            storage.setData(DATA_NAME, data);
        }
        return data;
    }

    public NetworkState getState(UUID id) {
        return networks.get(id);
    }

    public void upsertState(UUID id, IntegratedFluidNetwork network) {
        if (id == null || network == null) return;
        NetworkState state = networks.computeIfAbsent(id, ignored -> new NetworkState());
        state.expectedMemberCount = network.getExpectedMemberCount();
        state.fluidName = network.getFluidName();
        state.amountQ = network.getAmountQ();
        state.enthalpyQ = network.getEnthalpyQ();
        markDirty();
    }

    public void removeState(UUID id) {
        if (id == null) return;
        networks.remove(id);
        markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        networks.clear();
        NBTTagList list = nbt.getTagList(TAG_NETWORKS, 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            long most = entry.getLong(TAG_ID_MOST);
            long least = entry.getLong(TAG_ID_LEAST);
            UUID id = new UUID(most, least);
            NetworkState state = new NetworkState();
            state.expectedMemberCount = entry.getInteger(TAG_EXPECTED);
            state.fluidName = entry.hasKey(TAG_FLUID_NAME) ? entry.getString(TAG_FLUID_NAME) : null;
            state.amountQ = entry.getLong(TAG_AMOUNT_Q);
            state.enthalpyQ = entry.getLong(TAG_ENTHALPY_Q);
            if (state.fluidName == null && entry.hasKey(TAG_FLUID_OLD)) {
                migrateLegacyFluidStack(entry, state);
            }
            networks.put(id, state);
        }
    }

    private static void migrateLegacyFluidStack(NBTTagCompound entry, NetworkState state) {
        NBTTagCompound legacy = entry.getCompoundTag(TAG_FLUID_OLD);
        String fluidName = legacy.getString(TAG_FLUID_NAME);
        int amount = legacy.getInteger("Amount");
        if (fluidName == null || fluidName.trim().isEmpty() || amount <= 0) {
            return;
        }

        state.fluidName = fluidName;
        state.amountQ = (long) amount * IntegratedFluidNetwork.AMOUNT_SCALE;
        float temperature = entry.hasKey(TAG_TEMPERATURE_OLD)
            ? entry.getFloat(TAG_TEMPERATURE_OLD)
            : IntegratedFluidNetwork.DEFAULT_TEMPERATURE;
        Fluid legacyFluid = new Fluid(fluidName);
        double specific = IntegratedFluidThermoModel.specificEnthalpyFromTemperature(legacyFluid, temperature);
        long energyQ = (long) Math.round(specific * amount * IntegratedFluidNetwork.ENTHALPY_SCALE);
        state.enthalpyQ = energyQ;
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        NBTTagList list = new NBTTagList();
        for (Map.Entry<UUID, NetworkState> entry : networks.entrySet()) {
            UUID id = entry.getKey();
            NetworkState state = entry.getValue();
            NBTTagCompound tag = new NBTTagCompound();
            tag.setLong(TAG_ID_MOST, id.getMostSignificantBits());
            tag.setLong(TAG_ID_LEAST, id.getLeastSignificantBits());
            tag.setInteger(TAG_EXPECTED, state.expectedMemberCount);
            if (state.fluidName != null) {
                tag.setString(TAG_FLUID_NAME, state.fluidName);
            }
            tag.setLong(TAG_AMOUNT_Q, state.amountQ);
            tag.setLong(TAG_ENTHALPY_Q, state.enthalpyQ);
            list.appendTag(tag);
        }
        nbt.setTag(TAG_NETWORKS, list);
    }

    public static class NetworkState {
        String fluidName;
        long amountQ = 0L;
        long enthalpyQ = 0L;
        int expectedMemberCount = 0;
    }
}
