package gregtech.api.metatileentity.implementations.integratedfluid;

import java.util.HashSet;
import java.util.Set;

import net.minecraftforge.fluids.FluidStack;

/**
 * Manages a network of connected integrated fluid hatches and pipes.
 * All hatches connected by the same segment of pipes share fluid data.
 * The network has a maximum capacity of 10,000L.
 */
public class IntegratedFluidNetwork {

    /**
     * Maximum capacity of the network in mB (millibuckets).
     */
    public static final int MAX_CAPACITY = 10000;

    /**
     * Default pressure in bar.
     */
    public static final float DEFAULT_PRESSURE = 1.0f;

    /**
     * Default temperature in Kelvin.
     */
    public static final float DEFAULT_TEMPERATURE = 300.0f;

    /**
     * The fluid stored in this network segment.
     */
    private FluidStack storedFluid;

    /**
     * All members (pipes and hatches) connected to this network.
     */
    private final Set<IIntegratedFluidMember> members = new HashSet<>();

    /**
     * Current pressure of the network in bar.
     */
    private float pressure;

    /**
     * Current temperature of the network in Kelvin.
     */
    private float temperature;

    public IntegratedFluidNetwork() {
        this.storedFluid = null;
        this.pressure = DEFAULT_PRESSURE;
        this.temperature = DEFAULT_TEMPERATURE;
    }

    /**
     * Adds a member to this network.
     */
    public void addMember(IIntegratedFluidMember member) {
        members.add(member);
        member.setNetwork(this);
    }

    /**
     * Removes a member from this network.
     */
    public void removeMember(IIntegratedFluidMember member) {
        members.remove(member);
        if (member.getNetwork() == this) {
            member.setNetwork(null);
        }
    }

    /**
     * Gets the current stored fluid.
     */
    public FluidStack getStoredFluid() {
        return storedFluid;
    }

    /**
     * Gets the current amount of fluid stored.
     */
    public int getStoredAmount() {
        return storedFluid != null ? storedFluid.amount : 0;
    }

    /**
     * Gets the maximum capacity of this network.
     */
    public int getMaxCapacity() {
        return MAX_CAPACITY;
    }

    /**
     * Gets the available space in the network.
     */
    public int getAvailableSpace() {
        return MAX_CAPACITY - getStoredAmount();
    }

    /**
     * Attempts to add fluid to the network.
     * 
     * @param fluid    The fluid to add
     * @param simulate If true, only simulates the fill
     * @return The amount of fluid actually added
     */
    public int addFluid(FluidStack fluid, boolean simulate) {
        return addFluid(fluid, simulate, DEFAULT_TEMPERATURE);
    }

    /**
     * Attempts to add fluid to the network with a specified temperature.
     * The network temperature will be updated using weighted averaging.
     * 
     * @param fluid        The fluid to add
     * @param simulate     If true, only simulates the fill
     * @param incomingTemp The temperature of the incoming fluid in Kelvin
     * @return The amount of fluid actually added
     */
    public int addFluid(FluidStack fluid, boolean simulate, float incomingTemp) {
        if (fluid == null || fluid.amount <= 0) {
            return 0;
        }

        // If we have stored fluid, it must be the same type
        if (storedFluid != null && !storedFluid.isFluidEqual(fluid)) {
            return 0;
        }

        int availableSpace = getAvailableSpace();
        int amountToAdd = Math.min(fluid.amount, availableSpace);

        if (amountToAdd <= 0) {
            return 0;
        }

        if (!simulate) {
            if (storedFluid == null) {
                storedFluid = fluid.copy();
                storedFluid.amount = amountToAdd;
                // Set temperature to incoming temperature for first fluid
                temperature = incomingTemp;
            } else {
                int existingAmount = storedFluid.amount;
                float existingTemp = temperature;

                // Calculate weighted average temperature
                // Formula: T_new = (T_existing * amount_existing + T_incoming * amount_incoming) / (amount_existing +
                // amount_incoming)
                float newTemperature = (existingTemp * existingAmount + incomingTemp * amountToAdd)
                    / (existingAmount + amountToAdd);

                storedFluid.amount += amountToAdd;
                temperature = newTemperature;
            }
        }

        return amountToAdd;
    }

    /**
     * Attempts to drain fluid from the network.
     * 
     * @param maxDrain The maximum amount to drain
     * @param simulate If true, only simulates the drain
     * @return The fluid that was drained
     */
    public FluidStack drainFluid(int maxDrain, boolean simulate) {
        if (storedFluid == null || maxDrain <= 0) {
            return null;
        }

        int amountToDrain = Math.min(maxDrain, storedFluid.amount);
        FluidStack drained = storedFluid.copy();
        drained.amount = amountToDrain;

        if (!simulate) {
            storedFluid.amount -= amountToDrain;
            if (storedFluid.amount <= 0) {
                storedFluid = null;
            }
        }

        return drained;
    }

    /**
     * Drains a specific fluid from the network.
     * 
     * @param fluid    The fluid to drain (must match stored fluid)
     * @param simulate If true, only simulates the drain
     * @return The fluid that was drained
     */
    public FluidStack drainFluid(FluidStack fluid, boolean simulate) {
        if (fluid == null || storedFluid == null || !storedFluid.isFluidEqual(fluid)) {
            return null;
        }
        return drainFluid(fluid.amount, simulate);
    }

    /**
     * Gets the count of members in this network.
     */
    public int getMemberCount() {
        return members.size();
    }

    /**
     * Gets the current pressure in bar.
     */
    public float getPressure() {
        return pressure;
    }

    /**
     * Sets the pressure in bar.
     */
    public void setPressure(float pressure) {
        this.pressure = pressure;
    }

    /**
     * Gets the current temperature in Kelvin.
     */
    public float getTemperature() {
        return temperature;
    }

    /**
     * Sets the temperature in Kelvin.
     */
    public void setTemperature(float temperature) {
        this.temperature = temperature;
    }

    /**
     * Gets all members in this network.
     */
    public Set<IIntegratedFluidMember> getMembers() {
        return new HashSet<>(members);
    }

    /**
     * Merges another network into this one.
     */
    public void merge(IntegratedFluidNetwork other) {
        if (other == null || other == this) {
            return;
        }

        // Transfer fluid from other network
        if (other.storedFluid != null) {
            addFluid(other.storedFluid, false);
            other.storedFluid = null;
        }

        // Transfer all members to this network
        for (IIntegratedFluidMember member : new HashSet<>(other.members)) {
            other.removeMember(member);
            addMember(member);
        }
    }

    /**
     * Clears all members from this network (for cleanup).
     */
    public void clear() {
        for (IIntegratedFluidMember member : new HashSet<>(members)) {
            removeMember(member);
        }
        storedFluid = null;
    }
}
