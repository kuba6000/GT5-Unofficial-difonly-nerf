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
     * The fluid stored in this network segment.
     */
    private FluidStack storedFluid;

    /**
     * All members (pipes and hatches) connected to this network.
     */
    private final Set<IIntegratedFluidMember> members = new HashSet<>();

    public IntegratedFluidNetwork() {
        this.storedFluid = null;
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
            } else {
                storedFluid.amount += amountToAdd;
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
