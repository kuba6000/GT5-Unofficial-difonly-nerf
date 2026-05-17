package gregtech.api.metatileentity.implementations.integratedfluid.machine;

import java.util.Objects;

import net.minecraft.nbt.NBTTagCompound;

public final class IFNBatchState {

    private static final String KEY_PRESENT = "present";
    private static final String KEY_FLUID_NAME = "fluidName";
    private static final String KEY_AMOUNT_Q = "amountQ";
    private static final String KEY_SPECIFIC_ENTHALPY = "specificEnthalpy";
    private static final String KEY_PRESSURE = "pressure";

    private static final IFNBatchState EMPTY = new IFNBatchState(null, 0L, 0.0d, 0.0f);

    private final String fluidName;
    private final long amountQ;
    private final double specificEnthalpy;
    private final float pressure;

    private IFNBatchState(String fluidName, long amountQ, double specificEnthalpy, float pressure) {
        this.fluidName = fluidName;
        this.amountQ = amountQ;
        this.specificEnthalpy = specificEnthalpy;
        this.pressure = pressure;
    }

    public static IFNBatchState empty() {
        return EMPTY;
    }

    public static IFNBatchState of(String fluidName, long amountQ, double specificEnthalpy, float pressure) {
        if (fluidName == null || fluidName.trim().isEmpty() || amountQ <= 0L) {
            return empty();
        }
        return new IFNBatchState(fluidName, amountQ, specificEnthalpy, pressure);
    }

    public String fluidName() {
        return fluidName;
    }

    public long amountQ() {
        return amountQ;
    }

    public double specificEnthalpy() {
        return specificEnthalpy;
    }

    public float pressure() {
        return pressure;
    }

    public boolean isEmpty() {
        return amountQ <= 0L || fluidName == null;
    }

    public IFNBatchState withAmountQ(long newAmountQ) {
        if (newAmountQ <= 0L || isEmpty()) {
            return empty();
        }
        return new IFNBatchState(fluidName, newAmountQ, specificEnthalpy, pressure);
    }

    public IFNBatchState mergeWith(IFNBatchState other) {
        if (other == null || other.isEmpty()) {
            return this;
        }
        if (isEmpty()) {
            return other;
        }
        if (!Objects.equals(fluidName, other.fluidName)) {
            return empty();
        }
        long mergedAmountQ = amountQ + other.amountQ;
        if (mergedAmountQ <= 0L) {
            return empty();
        }
        double mergedSpecificEnthalpy = ((specificEnthalpy * amountQ) + (other.specificEnthalpy * other.amountQ))
            / mergedAmountQ;
        float mergedPressure = (float) (((double) pressure * amountQ + (double) other.pressure * other.amountQ)
            / mergedAmountQ);
        return new IFNBatchState(fluidName, mergedAmountQ, mergedSpecificEnthalpy, mergedPressure);
    }

    public void writeToNBT(NBTTagCompound tag) {
        tag.setBoolean(KEY_PRESENT, !isEmpty());
        if (isEmpty()) {
            return;
        }
        tag.setString(KEY_FLUID_NAME, fluidName);
        tag.setLong(KEY_AMOUNT_Q, amountQ);
        tag.setDouble(KEY_SPECIFIC_ENTHALPY, specificEnthalpy);
        tag.setFloat(KEY_PRESSURE, pressure);
    }

    public static IFNBatchState readFromNBT(NBTTagCompound tag) {
        if (tag == null || !tag.getBoolean(KEY_PRESENT)) {
            return empty();
        }
        return of(
            tag.getString(KEY_FLUID_NAME),
            tag.getLong(KEY_AMOUNT_Q),
            tag.getDouble(KEY_SPECIFIC_ENTHALPY),
            tag.getFloat(KEY_PRESSURE));
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof IFNBatchState)) {
            return false;
        }
        IFNBatchState that = (IFNBatchState) object;
        return amountQ == that.amountQ && Double.compare(that.specificEnthalpy, specificEnthalpy) == 0
            && Float.compare(that.pressure, pressure) == 0 && Objects.equals(fluidName, that.fluidName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fluidName, amountQ, specificEnthalpy, pressure);
    }
}
