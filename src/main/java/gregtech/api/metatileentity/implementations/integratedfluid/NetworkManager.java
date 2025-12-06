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

        NetworkNode node = new NetworkNode(member);
        List<IIntegratedFluidMember> neighbors = findConnectedNeighbors(member);

        if (neighbors.isEmpty()) {
            // No neighbors - create new isolated network
            createNewNetwork(member);
        } else {
            // Has neighbors - merge with their networks
            Set<IntegratedFluidNetwork> neighborNetworks = neighbors.stream()
                .map(IIntegratedFluidMember::getNetwork)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

            if (neighborNetworks.isEmpty()) {
                // Neighbors exist but have no networks - create new network with all of them
                createNewNetwork(member);
                for (IIntegratedFluidMember neighbor : neighbors) {
                    addToNetwork(neighbor, member.getNetwork());
                }
            } else if (neighborNetworks.size() == 1) {
                // All neighbors in same network - just add to it
                IntegratedFluidNetwork network = neighborNetworks.iterator().next();
                addToNetwork(member, network);
            } else {
                // Multiple networks - merge them all
                IntegratedFluidNetwork mainNetwork = mergeNetworks(neighborNetworks);
                addToNetwork(member, mainNetwork);
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
            // Still connected - nothing to do
            return;
        }

        // Network split into multiple components
        // Clear old network and create new ones
        FluidStack oldFluid = oldNetwork.getStoredFluid();
        float oldPressure = oldNetwork.getPressure();
        float oldTemperature = oldNetwork.getTemperature();
        int oldCapacity = oldNetwork.getMaxCapacity();

        oldNetwork.clear();
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
            if (oldFluid != null && oldFluid.amount > 0) {
                int newCapacity = newNetwork.getMaxCapacity();
                int proportionalAmount = (int) ((long) oldFluid.amount * newCapacity / oldCapacity);

                if (proportionalAmount > 0) {
                    FluidStack splitFluid = oldFluid.copy();
                    splitFluid.amount = proportionalAmount;
                    newNetwork.addFluid(splitFluid, false, oldTemperature);
                }
            }

            // Preserve pressure
            newNetwork.setPressure(oldPressure);

            // Notify members
            for (IIntegratedFluidMember componentMember : component) {
                componentMember.onNetworkUpdate();
            }
        }
    }

    /**
     * Called when a connection changes (cable connected/disconnected).
     * Triggers a full network rebuild for affected members.
     */
    public void onConnectionChanged(IIntegratedFluidMember member) {
        if (member == null) return;

        // Get old network
        IntegratedFluidNetwork oldNetwork = member.getNetwork();
        List<IIntegratedFluidMember> oldMembers = oldNetwork != null
            ? new ArrayList<>(oldNetwork.getMembers())
            : Collections.singletonList(member);

        // Remove all members from old network
        if (oldNetwork != null) {
            for (IIntegratedFluidMember oldMember : oldMembers) {
                oldNetwork.removeMember(oldMember);
                oldMember.setNetwork(null);
            }
            allNetworks.remove(oldNetwork);
        }

        // Rebuild networks from scratch using flood-fill
        Set<IIntegratedFluidMember> processed = new HashSet<>();
        List<Set<IIntegratedFluidMember>> components = findConnectedComponents(oldMembers);

        // Store old network data for distribution
        FluidStack oldFluid = oldNetwork != null ? oldNetwork.getStoredFluid() : null;
        float oldPressure = oldNetwork != null ? oldNetwork.getPressure() : IntegratedFluidNetwork.DEFAULT_PRESSURE;
        float oldTemperature = oldNetwork != null ? oldNetwork.getTemperature() : IntegratedFluidNetwork.DEFAULT_TEMPERATURE;
        int oldCapacity = oldNetwork != null ? oldNetwork.getMaxCapacity() : 0;

        for (Set<IIntegratedFluidMember> component : components) {
            IntegratedFluidNetwork newNetwork = new IntegratedFluidNetwork();
            allNetworks.add(newNetwork);

            for (IIntegratedFluidMember componentMember : component) {
                newNetwork.addMember(componentMember);
                componentMember.setNetwork(newNetwork);
                processed.add(componentMember);
            }

            // Distribute fluid proportionally
            if (oldFluid != null && oldFluid.amount > 0 && oldCapacity > 0) {
                int newCapacity = newNetwork.getMaxCapacity();
                int proportionalAmount = (int) ((long) oldFluid.amount * newCapacity / oldCapacity);

                if (proportionalAmount > 0) {
                    FluidStack splitFluid = oldFluid.copy();
                    splitFluid.amount = proportionalAmount;
                    newNetwork.addFluid(splitFluid, false, oldTemperature);
                }
            }

            newNetwork.setPressure(oldPressure);

            // Notify all members
            for (IIntegratedFluidMember componentMember : component) {
                componentMember.onNetworkUpdate();
            }
        }
    }

    /**
     * Finds all connected neighbors of a member.
     */
    private List<IIntegratedFluidMember> findConnectedNeighbors(IIntegratedFluidMember member) {
        List<IIntegratedFluidMember> neighbors = new ArrayList<>();

        if (member instanceof MetaPipeEntity pipe) {
            IGregTechTileEntity baseTile = pipe.getBaseMetaTileEntity();
            if (baseTile != null) {
                for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
                    if (pipe.isConnectedAtSide(side)) {
                        TileEntity neighbor = baseTile.getTileEntityAtSide(side);
                        if (neighbor instanceof IGregTechTileEntity gtNeighbor) {
                            IMetaTileEntity mte = gtNeighbor.getMetaTileEntity();
                            if (mte instanceof IIntegratedFluidMember neighborMember) {
                                neighbors.add(neighborMember);
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

