package gregtech.api.metatileentity.implementations.integratedfluid;

import java.util.*;
import java.util.stream.Collectors;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaPipeEntity;

/**
 * Centralized network manager for integrated fluid networks.
 * Uses a graph-based approach with flood-fill to properly handle
 * network connections, disconnections, and merges.
 */
public class NetworkManager {

    private static final Map<World, NetworkManager> INSTANCES = new WeakHashMap<>();

    private final World world;
    private final Map<NetworkNode, Set<IntegratedFluidNetwork>> nodeToNetworks = new HashMap<>();
    private final Set<IntegratedFluidNetwork> allNetworks = new HashSet<>();

    // Track last tick time for heat loss application
    private long lastHeatLossTick = 0;

    private NetworkManager(World world) {
        this.world = world;
    }

    /**
     * Gets or creates the network manager for a world.
     */
    public static NetworkManager getInstance(World world) {
        return INSTANCES.computeIfAbsent(world, NetworkManager::new);
    }

    /**
     * Gets the network for a member, or null if not networked.
     */
    public IntegratedFluidNetwork getNetwork(IIntegratedFluidMember member) {
        if (member == null) return null;
        return member.getNetwork();
    }

    /**
     * Called when a new pipe or hatch is added to the world.
     * This will connect it to adjacent networks or create a new one.
     */
    public void onMemberAdded(IIntegratedFluidMember member) {
        if (member == null) return;

        // Find connected neighbors
        List<IIntegratedFluidMember> neighbors = findConnectedNeighbors(member);

        // FORCE AWAKENING: Trigger onPostTick check on all neighbors
        // This ensures they re-evaluate their networks immediately
        for (IIntegratedFluidMember neighbor : neighbors) {
            if (neighbor != null) {
                neighbor.onNetworkUpdate();
            }
        }

        // Collect EXISTING networks from neighbors (ignore member's own network if it has one from NBT)
        IntegratedFluidNetwork memberOldNetwork = member.getNetwork();
        Set<IntegratedFluidNetwork> neighborNetworks = neighbors.stream()
            .map(IIntegratedFluidMember::getNetwork)
            .filter(Objects::nonNull)
            .filter(net -> net != memberOldNetwork) // Ignore member's NBT network
            .collect(Collectors.toSet());

        // Check if we have neighbors but they don't have networks yet (during world load)
        boolean hasNeighborsWithoutNetworks = !neighbors.isEmpty() && neighborNetworks.isEmpty();

        if (neighborNetworks.isEmpty()) {
            // No existing networks nearby
            if (hasNeighborsWithoutNetworks) {
                // Neighbors exist but don't have networks yet - they're probably loading
                // Keep member's NBT network temporarily if it exists, otherwise create new one
                // The neighbors will merge with us when they load, or we'll merge in onPostTick
                if (memberOldNetwork != null) {
                    // Keep NBT network temporarily
                    allNetworks.add(memberOldNetwork);
                } else {
                    // Create temporary network - will merge later
                    createNewNetwork(member);
                }
            } else if (memberOldNetwork != null) {
                // No neighbors at all - member has NBT network (from save)
                // Keep it if it has fluid, otherwise discard
                if (memberOldNetwork.getStoredFluid() != null && memberOldNetwork.getStoredFluid().amount > 0) {
                    // Keep NBT network with fluid
                    allNetworks.add(memberOldNetwork);
                } else {
                    // Discard empty NBT network and create fresh one
                    memberOldNetwork.clear();
                    createNewNetwork(member);
                }
            } else {
                // Brand new member - create new network
                createNewNetwork(member);
            }
        } else if (neighborNetworks.size() == 1) {
            // Single existing network - just add member to it
            IntegratedFluidNetwork existingNetwork = neighborNetworks.iterator().next();

            // If member has NBT network, ALWAYS discard it (existing network is the truth)
            if (memberOldNetwork != null && memberOldNetwork != existingNetwork) {
                memberOldNetwork.clear();
            }

            // Add member to existing network (this does NOT duplicate fluid!)
            existingNetwork.addMember(member);
            member.setNetwork(existingNetwork);
            member.onNetworkUpdate();

            // IMPORTANT: Cap fluid to capacity (member might be removed, decreasing capacity)
            int voided = existingNetwork.capFluidToCapacity();
            if (voided > 0) {
                // Fluid was voided due to capacity decrease
                // This happens when removing members
            }
        } else {
            // Multiple existing networks - merge them
            IntegratedFluidNetwork mainNetwork = mergeNetworks(neighborNetworks);

            // If member has NBT network, discard it
            if (memberOldNetwork != null && memberOldNetwork != mainNetwork) {
                memberOldNetwork.clear();
            }

            // Add member to merged network
            mainNetwork.addMember(member);
            member.setNetwork(mainNetwork);
            member.onNetworkUpdate();

            // IMPORTANT: Cap fluid to capacity after merge
            int voided = mainNetwork.capFluidToCapacity();
            if (voided > 0) {
                // Fluid was voided due to capacity constraints
            }
        }

        // FORCE AWAKENING AGAIN: After network changes, wake up neighbors
        for (IIntegratedFluidMember neighbor : neighbors) {
            if (neighbor != null) {
                neighbor.onNetworkUpdate();
            }
        }
    }

    /**
     * Called when a pipe or hatch is removed from the world.
     * This will split the network if necessary.
     */
    public void onMemberRemoved(IIntegratedFluidMember member) {
        if (member == null) return;

        IntegratedFluidNetwork oldNetwork = member.getNetwork();
        if (oldNetwork == null) return;

        // Remove from network
        oldNetwork.removeMember(member);
        member.setNetwork(null);

        // If network is now empty, remove it
        if (oldNetwork.getMemberCount() == 0) {
            allNetworks.remove(oldNetwork);
            return;
        }

        // Check if network is still connected
        // If not, we need to split it into multiple networks
        List<IIntegratedFluidMember> remainingMembers = new ArrayList<>(oldNetwork.getMembers());
        if (remainingMembers.isEmpty()) {
            allNetworks.remove(oldNetwork);
            return;
        }

        // Use flood-fill to find connected components
        List<Set<IIntegratedFluidMember>> components = findConnectedComponents(remainingMembers);

        if (components.size() == 1) {
            // Still connected - but capacity changed!
            // IMPORTANT: Cap fluid to new capacity and notify all members
            int voided = oldNetwork.capFluidToCapacity();

            // Notify all remaining members to update
            for (IIntegratedFluidMember remainingMember : remainingMembers) {
                remainingMember.onNetworkUpdate();
            }

            return;
        }

        // Network split into multiple components
        // Save old network data BEFORE clearing
        FluidStack oldFluid = oldNetwork.getStoredFluid();
        FluidStack fluidCopy = oldFluid != null ? oldFluid.copy() : null;
        float oldPressure = oldNetwork.getPressure();
        float oldTemperature = oldNetwork.getTemperature();
        int oldCapacity = oldNetwork.getMaxCapacity();

        // Remove all members from old network (but don't clear fluid yet)
        for (IIntegratedFluidMember remainingMember : remainingMembers) {
            oldNetwork.removeMember(remainingMember);
            remainingMember.setNetwork(null);
        }

        allNetworks.remove(oldNetwork);

        // Create new network for each component
        for (Set<IIntegratedFluidMember> component : components) {
            IntegratedFluidNetwork newNetwork = new IntegratedFluidNetwork();
            allNetworks.add(newNetwork);

            // Add all members to new network
            for (IIntegratedFluidMember componentMember : component) {
                newNetwork.addMember(componentMember);
                componentMember.setNetwork(newNetwork);
            }

            // Distribute fluid proportionally by capacity
            if (fluidCopy != null && fluidCopy.amount > 0 && oldCapacity > 0) {
                int newCapacity = newNetwork.getMaxCapacity();
                int proportionalAmount = (int) ((long) fluidCopy.amount * newCapacity / oldCapacity);

                // CAP to network capacity to prevent overflow
                proportionalAmount = Math.min(proportionalAmount, newCapacity);

                if (proportionalAmount > 0) {
                    FluidStack splitFluid = fluidCopy.copy();
                    splitFluid.amount = proportionalAmount;
                    newNetwork.addFluid(splitFluid, false, oldTemperature);
                }
            }

            // SAFETY: Cap fluid to capacity in case of rounding errors
            int voided = newNetwork.capFluidToCapacity();
            if (voided > 0) {
                // Log that fluid was voided due to capacity
                // This shouldn't normally happen with proportional distribution
                // but it's a safety measure
            }

            // Preserve pressure and temperature
            newNetwork.setPressure(oldPressure);
            if (fluidCopy == null || fluidCopy.amount == 0) {
                newNetwork.setTemperature(oldTemperature);
            }

            // Notify members AND wake up neighbors
            for (IIntegratedFluidMember componentMember : component) {
                componentMember.onNetworkUpdate();

                // FORCE AWAKENING: Wake up all neighbors of this member
                List<IIntegratedFluidMember> neighbors = findConnectedNeighbors(componentMember);
                for (IIntegratedFluidMember neighbor : neighbors) {
                    if (neighbor != null && !component.contains(neighbor)) {
                        neighbor.onNetworkUpdate();
                    }
                }
            }
        }
    }

    /**
     * Called when a connection changes (cable connected/disconnected).
     * Triggers a full network rebuild for affected members.
     */
    public void onConnectionChanged(IIntegratedFluidMember member) {
        if (member == null) return;

        // Find ALL potentially affected members by checking neighbors
        Set<IIntegratedFluidMember> allAffectedMembers = new HashSet<>();
        Set<IntegratedFluidNetwork> affectedNetworks = new HashSet<>();

        // Start with the member and its network
        if (member.getNetwork() != null) {
            affectedNetworks.add(member.getNetwork());
            allAffectedMembers.addAll(member.getNetwork().getMembers());
        } else {
            allAffectedMembers.add(member);
        }

        // Also check all neighbors and their networks
        List<IIntegratedFluidMember> neighbors = findConnectedNeighbors(member);
        for (IIntegratedFluidMember neighbor : neighbors) {
            if (neighbor.getNetwork() != null) {
                affectedNetworks.add(neighbor.getNetwork());
                allAffectedMembers.addAll(neighbor.getNetwork().getMembers());
            } else {
                allAffectedMembers.add(neighbor);
            }
        }

        // Save data from ALL affected networks BEFORE any modifications
        int totalFluidAmount = 0;
        double weightedTemperature = 0.0;
        FluidStack combinedFluid = null;
        float avgPressure = 0.0f;
        int totalCapacity = 0;
        int networkCount = 0;

        for (IntegratedFluidNetwork net : affectedNetworks) {
            FluidStack fluid = net.getStoredFluid();
            if (fluid != null) {
                if (combinedFluid == null) {
                    combinedFluid = fluid.copy();
                    totalFluidAmount = fluid.amount;
                    weightedTemperature = fluid.amount * net.getTemperature();
                } else if (combinedFluid.isFluidEqual(fluid)) {
                    totalFluidAmount += fluid.amount;
                    weightedTemperature += fluid.amount * net.getTemperature();
                    combinedFluid.amount += fluid.amount;
                } else {
                    // Different fluids - keep the larger one
                    if (fluid.amount > combinedFluid.amount) {
                        combinedFluid = fluid.copy();
                        totalFluidAmount = fluid.amount;
                        weightedTemperature = fluid.amount * net.getTemperature();
                    }
                }
            }
            avgPressure += net.getPressure();
            totalCapacity += net.getMaxCapacity();
            networkCount++;
        }

        if (networkCount > 0) {
            avgPressure /= networkCount;
        } else {
            avgPressure = IntegratedFluidNetwork.DEFAULT_PRESSURE;
        }

        float finalTemperature = IntegratedFluidNetwork.DEFAULT_TEMPERATURE;
        if (totalFluidAmount > 0) {
            finalTemperature = (float) (weightedTemperature / totalFluidAmount);
        }

        // Remove all members from ALL affected networks
        for (IntegratedFluidNetwork net : affectedNetworks) {
            for (IIntegratedFluidMember m : new ArrayList<>(net.getMembers())) {
                net.removeMember(m);
                m.setNetwork(null);
            }
            allNetworks.remove(net);
        }

        // Rebuild networks from scratch using flood-fill on ALL affected members
        List<Set<IIntegratedFluidMember>> components = findConnectedComponents(new ArrayList<>(allAffectedMembers));

        // If we're merging networks (components = 1 from multiple networks), give all the fluid to the merged network
        // If we're splitting (components > 1), distribute proportionally
        boolean isMerge = components.size() == 1 && affectedNetworks.size() > 1;

        for (Set<IIntegratedFluidMember> component : components) {
            IntegratedFluidNetwork newNetwork = new IntegratedFluidNetwork();
            allNetworks.add(newNetwork);

            for (IIntegratedFluidMember componentMember : component) {
                newNetwork.addMember(componentMember);
                componentMember.setNetwork(newNetwork);
            }

            // Add fluid
            if (combinedFluid != null && combinedFluid.amount > 0) {
                if (isMerge) {
                    // Merging networks - give ALL fluid to the merged network
                    FluidStack mergedFluid = combinedFluid.copy();
                    newNetwork.addFluid(mergedFluid, false, finalTemperature);
                } else if (totalCapacity > 0) {
                    // Splitting network - distribute proportionally
                    int newCapacity = newNetwork.getMaxCapacity();
                    int proportionalAmount = (int) ((long) combinedFluid.amount * newCapacity / totalCapacity);

                    if (proportionalAmount > 0) {
                        FluidStack splitFluid = combinedFluid.copy();
                        splitFluid.amount = proportionalAmount;
                        newNetwork.addFluid(splitFluid, false, finalTemperature);
                    }
                }
            }

            newNetwork.setPressure(avgPressure);
            if (combinedFluid == null || combinedFluid.amount == 0) {
                newNetwork.setTemperature(finalTemperature);
            }

            // Notify all members
            for (IIntegratedFluidMember componentMember : component) {
                componentMember.onNetworkUpdate();
            }
        }
    }

    /**
     * Called every tick to handle network updates.
     * Applies heat loss every second (20 ticks).
     */
    public void onWorldTick(long worldTick) {
        // Apply heat loss every 20 ticks (1 second)
        if (worldTick - lastHeatLossTick >= 20) {
            lastHeatLossTick = worldTick;

            // Apply heat loss to all networks
            for (IntegratedFluidNetwork network : new HashSet<>(allNetworks)) {
                if (network != null) {
                    network.applyHeatLoss();
                }
            }
        }
    }

    /**
     * Finds all connected neighbors of a member.
     * Public so members can check their neighbors in onPostTick.
     */
    public List<IIntegratedFluidMember> findConnectedNeighbors(IIntegratedFluidMember member) {
        List<IIntegratedFluidMember> neighbors = new ArrayList<>();

        // Check if member is a pipe
        if (member instanceof MetaPipeEntity pipe) {
            IGregTechTileEntity baseTile = pipe.getBaseMetaTileEntity();
            if (baseTile != null) {
                for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
                    if (pipe.isConnectedAtSide(side)) {
                        TileEntity neighbor = baseTile.getTileEntityAtSide(side);
                        if (neighbor instanceof IGregTechTileEntity gtNeighbor) {
                            IMetaTileEntity mte = gtNeighbor.getMetaTileEntity();
                            if (mte instanceof IIntegratedFluidMember neighborMember) {
                                // If neighbor is a hatch (not a pipe), check if we're connecting through its front facing
                                if (!(mte instanceof MetaPipeEntity)) {
                                    // This is a hatch - check if we're connecting through its dot side
                                    IGregTechTileEntity neighborBaseTile = mte.getBaseMetaTileEntity();
                                    if (neighborBaseTile != null) {
                                        ForgeDirection hatchFrontFacing = neighborBaseTile.getFrontFacing();
                                        // Only connect if the hatch's front facing points towards us
                                        if (hatchFrontFacing == side.getOpposite()) {
                                            neighbors.add(neighborMember);
                                        }
                                        // Otherwise, ignore - hatch doesn't accept connections from this side
                                    }
                                } else {
                                    // Neighbor is another pipe
                                    neighbors.add(neighborMember);
                                }
                            }
                        }
                    }
                }
            }
        }
        // Also check if member is a hatch or other tile entity
        else if (member instanceof IMetaTileEntity mte) {
            IGregTechTileEntity baseTile = mte.getBaseMetaTileEntity();
            if (baseTile != null) {
                // For hatches: ONLY connect through the front facing (the dot side)
                ForgeDirection allowedSide = baseTile.getFrontFacing();

                TileEntity neighbor = baseTile.getTileEntityAtSide(allowedSide);
                if (neighbor instanceof IGregTechTileEntity gtNeighbor) {
                    IMetaTileEntity neighborMTE = gtNeighbor.getMetaTileEntity();
                    if (neighborMTE instanceof IIntegratedFluidMember neighborMember) {
                        // Check if neighbor is a pipe
                        if (neighborMTE instanceof MetaPipeEntity neighborPipe) {
                            // Check if pipe is connected to us on the opposite side
                            if (neighborPipe.isConnectedAtSide(allowedSide.getOpposite())) {
                                neighbors.add(neighborMember);
                            }
                        }
                        // Or if neighbor is another hatch (not a pipe)
                        else {
                            // neighborMTE is IIntegratedFluidMember but not MetaPipeEntity = must be a hatch
                            IGregTechTileEntity neighborBaseTile = neighborMTE.getBaseMetaTileEntity();
                            if (neighborBaseTile != null) {
                                // Check if neighbor hatch's front facing is pointing back at us
                                ForgeDirection neighborFacing = neighborBaseTile.getFrontFacing();
                                if (neighborFacing == allowedSide.getOpposite()) {
                                    // Both hatches are facing each other - they can connect!
                                    neighbors.add(neighborMember);
                                }
                            }
                        }
                    }
                }
            }
        }

        return neighbors;
    }

    /**
     * Finds all connected components using flood-fill algorithm.
     */
    private List<Set<IIntegratedFluidMember>> findConnectedComponents(List<IIntegratedFluidMember> members) {
        List<Set<IIntegratedFluidMember>> components = new ArrayList<>();
        Set<IIntegratedFluidMember> visited = new HashSet<>();

        for (IIntegratedFluidMember member : members) {
            if (visited.contains(member)) continue;

            Set<IIntegratedFluidMember> component = new HashSet<>();
            floodFill(member, component, visited);

            if (!component.isEmpty()) {
                components.add(component);
            }
        }

        return components;
    }

    /**
     * Flood-fill algorithm to find all connected members.
     */
    private void floodFill(IIntegratedFluidMember start, Set<IIntegratedFluidMember> component, Set<IIntegratedFluidMember> visited) {
        Queue<IIntegratedFluidMember> queue = new LinkedList<>();
        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            IIntegratedFluidMember current = queue.poll();
            component.add(current);

            List<IIntegratedFluidMember> neighbors = findConnectedNeighbors(current);
            for (IIntegratedFluidMember neighbor : neighbors) {
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }
    }

    /**
     * Creates a new isolated network with a single member.
     */
    private void createNewNetwork(IIntegratedFluidMember member) {
        IntegratedFluidNetwork network = new IntegratedFluidNetwork();
        network.addMember(member);
        member.setNetwork(network);
        allNetworks.add(network);
    }

    /**
     * Adds a member to an existing network.
     */
    private void addToNetwork(IIntegratedFluidMember member, IntegratedFluidNetwork network) {
        if (network == null) {
            createNewNetwork(member);
            return;
        }

        network.addMember(member);
        member.setNetwork(network);
        member.onNetworkUpdate();
    }

    /**
     * Merges multiple networks into one.
     */
    private IntegratedFluidNetwork mergeNetworks(Set<IntegratedFluidNetwork> networks) {
        if (networks.isEmpty()) return null;
        if (networks.size() == 1) return networks.iterator().next();

        // Use the largest network as base to minimize data movement
        IntegratedFluidNetwork mainNetwork = networks.stream()
            .max(Comparator.comparingInt(IntegratedFluidNetwork::getMemberCount))
            .orElse(networks.iterator().next());

        for (IntegratedFluidNetwork network : networks) {
            if (network == mainNetwork) continue;

            mainNetwork.merge(network);
            allNetworks.remove(network);
        }

        // Notify all members
        for (IIntegratedFluidMember member : mainNetwork.getMembers()) {
            member.onNetworkUpdate();
        }

        return mainNetwork;
    }

    /**
     * Cleans up all networks (for world unload).
     */
    public void cleanup() {
        for (IntegratedFluidNetwork network : new HashSet<>(allNetworks)) {
            network.clear();
        }
        allNetworks.clear();
        nodeToNetworks.clear();
    }

    /**
     * Internal class representing a network node.
     */
    private static class NetworkNode {
        private final IIntegratedFluidMember member;

        NetworkNode(IIntegratedFluidMember member) {
            this.member = member;
        }

        public IIntegratedFluidMember getMember() {
            return member;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            NetworkNode that = (NetworkNode) o;
            return Objects.equals(member, that.member);
        }

        @Override
        public int hashCode() {
            return Objects.hash(member);
        }
    }
}

