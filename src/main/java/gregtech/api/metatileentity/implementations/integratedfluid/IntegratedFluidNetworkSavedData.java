package gregtech.api.metatileentity.implementations.integratedfluid;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.storage.MapStorage;
import net.minecraftforge.fluids.FluidStack;

public class IntegratedFluidNetworkSavedData extends WorldSavedData {

    private static final String DATA_NAME = "GregTech_IntegratedFluidNetworkData";
    private static final String TAG_NETWORKS = "Networks";
    private static final String TAG_ID_MOST = "IdMost";
    private static final String TAG_ID_LEAST = "IdLeast";
    private static final String TAG_TEMPERATURE = "Temperature";
    private static final String TAG_PRESSURE = "Pressure";
    private static final String TAG_EXPECTED = "ExpectedMembers";
    private static final String TAG_FLUID = "Fluid";

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
        state.temperature = network.getTemperature();
        state.pressure = network.getPressure();
        state.expectedMemberCount = network.getExpectedMemberCount();
        FluidStack stored = network.getStoredFluid();
        state.fluid = stored != null ? stored.copy() : null;
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
            state.temperature = entry.getFloat(TAG_TEMPERATURE);
            state.pressure = entry.getFloat(TAG_PRESSURE);
            state.expectedMemberCount = entry.getInteger(TAG_EXPECTED);
            if (entry.hasKey(TAG_FLUID)) {
                state.fluid = FluidStack.loadFluidStackFromNBT(entry.getCompoundTag(TAG_FLUID));
            }
            networks.put(id, state);
        }
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
            tag.setFloat(TAG_TEMPERATURE, state.temperature);
            tag.setFloat(TAG_PRESSURE, state.pressure);
            tag.setInteger(TAG_EXPECTED, state.expectedMemberCount);
            if (state.fluid != null) {
                tag.setTag(TAG_FLUID, state.fluid.writeToNBT(new NBTTagCompound()));
            }
            list.appendTag(tag);
        }
        nbt.setTag(TAG_NETWORKS, list);
    }

    public static class NetworkState {
        FluidStack fluid;
        float temperature = IntegratedFluidNetwork.DEFAULT_TEMPERATURE;
        float pressure = IntegratedFluidNetwork.DEFAULT_PRESSURE;
        int expectedMemberCount = 0;
    }
}
