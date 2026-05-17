package gregtech.api.metatileentity.implementations.integratedfluid;

import java.util.*;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaPipeEntity;
import gregtech.api.metatileentity.implementations.integratedfluid.safety.IFNNetworkSafetyTicker;
import gregtech.api.metatileentity.implementations.integratedfluid.state.IFNCanonicalState;
import gregtech.api.metatileentity.implementations.integratedfluid.thermal.IFNAmbientContext;
import gregtech.api.metatileentity.implementations.integratedfluid.thermal.IFNHeatExchangeRuntime;
import gregtech.api.metatileentity.implementations.integratedfluid.topology.IFNMergePolicy;
import gregtech.api.metatileentity.implementations.integratedfluid.topology.IFNStateDistributor;
import gregtech.api.metatileentity.implementations.integratedfluid.topology.IFNTopologyRebuilder;

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
    private final IntegratedFluidNetworkSavedData savedData;
    private final IFNNetworkSafetyTicker safetyTicker;
    private final Random safetyRandom;

    // Track last tick time for heat loss application
    private long lastHeatLossTick = 0;

    private NetworkManager(World world) {
        this(world, IntegratedFluidNetworkSavedData.get(world));
    }

    NetworkManager(World world, IntegratedFluidNetworkSavedData savedData) {
        this.world = world;
        this.savedData = savedData;
        this.safetyTicker = new IFNNetworkSafetyTicker();
        this.safetyRandom = new Random();
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

        Set<IIntegratedFluidMember> component = new HashSet<>();
        floodFill(member, component, new HashSet<>());
        if (component.isEmpty()) {
            component.add(member);
        }

        UUID targetId = chooseNetworkId(member, component);
        IntegratedFluidNetwork mainNetwork = getOrCreateNetwork(targetId);

        Set<IntegratedFluidNetwork> networksToMerge = new HashSet<>();
        for (IIntegratedFluidMember componentMember : component) {
            IntegratedFluidNetwork existing = componentMember.getNetwork();
            if (existing != null && existing != mainNetwork) {
                networksToMerge.add(existing);
            }
        }

        if (!networksToMerge.isEmpty()) {
            mergeIntoNetwork(mainNetwork, networksToMerge);
        }

        for (IIntegratedFluidMember componentMember : component) {
            addToNetwork(componentMember, mainNetwork);
        }

        ensureExpectedCount(mainNetwork);
        updatePending(mainNetwork);
        persistNetwork(mainNetwork);

        for (IIntegratedFluidMember componentMember : component) {
            componentMember.onNetworkUpdate();
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

        oldNetwork.removeMember(member);
        member.setNetwork(null);
        member.setNetworkId(null);

        if (oldNetwork.getMemberCount() == 0) {
            removeNetwork(oldNetwork);
            return;
        }

        List<IIntegratedFluidMember> remainingMembers = new ArrayList<>(oldNetwork.getMembers());
        if (remainingMembers.isEmpty()) {
            removeNetwork(oldNetwork);
            return;
        }

        List<Set<IIntegratedFluidMember>> components = findConnectedComponents(remainingMembers);

        if (components.size() == 1) {
            oldNetwork.setExpectedMemberCount(oldNetwork.getMemberCount());
            oldNetwork.setPending(false);
            oldNetwork.capFluidToCapacity();
            persistNetwork(oldNetwork);
            for (IIntegratedFluidMember remainingMember : remainingMembers) {
                remainingMember.onNetworkUpdate();
            }
            return;
        }

        IFNCanonicalState oldState = oldNetwork.getCanonicalState();
        IFNCanonicalState[] splitStates = IFNStateDistributor.splitStateByWeights(
            oldState,
            componentCapacityWeights(components));

        for (IIntegratedFluidMember remainingMember : remainingMembers) {
            oldNetwork.removeMember(remainingMember);
            remainingMember.setNetwork(null);
        }

        UUID oldId = oldNetwork.getNetworkId();
        removeNetwork(oldNetwork);

        Set<IIntegratedFluidMember> primaryComponent = components.stream()
            .max(Comparator.comparingInt(Set::size))
            .orElse(null);

        for (int componentIndex = 0; componentIndex < components.size(); componentIndex++) {
            Set<IIntegratedFluidMember> component = components.get(componentIndex);
            UUID newId = component == primaryComponent ? oldId : UUID.randomUUID();
            IntegratedFluidNetwork newNetwork = createEmptyNetwork(newId);

            for (IIntegratedFluidMember componentMember : component) {
                newNetwork.addMember(componentMember);
                componentMember.setNetwork(newNetwork);
            }

            applyState(newNetwork, splitStates[componentIndex]);

            newNetwork.setExpectedMemberCount(newNetwork.getMemberCount());
            newNetwork.setPending(false);
            newNetwork.capFluidToCapacity();
            persistNetwork(newNetwork);

            for (IIntegratedFluidMember componentMember : component) {
                componentMember.onNetworkUpdate();
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

        Set<IIntegratedFluidMember> allAffectedMembers = new HashSet<>();
        Set<IntegratedFluidNetwork> affectedNetworks = new HashSet<>();

        if (member.getNetwork() != null) {
            affectedNetworks.add(member.getNetwork());
            allAffectedMembers.addAll(member.getNetwork().getMembers());
        } else {
            allAffectedMembers.add(member);
        }

        List<IIntegratedFluidMember> neighbors = findConnectedNeighbors(member);
        for (IIntegratedFluidMember neighbor : neighbors) {
            if (neighbor.getNetwork() != null) {
                affectedNetworks.add(neighbor.getNetwork());
                allAffectedMembers.addAll(neighbor.getNetwork().getMembers());
            } else {
                allAffectedMembers.add(neighbor);
            }
        }

        if (freezeIfIncompatibleNetworks(affectedNetworks)) {
            return;
        }

        IFNCanonicalState combinedState = IFNCanonicalState.empty();
        UUID splitPrimaryId = null;
        if (affectedNetworks.size() == 1) {
            IntegratedFluidNetwork only = affectedNetworks.iterator().next();
            splitPrimaryId = only != null ? only.getNetworkId() : null;
        }

        for (IntegratedFluidNetwork net : affectedNetworks) {
            combinedState = IFNMergePolicy.merge(combinedState, net.getCanonicalState());
        }

        for (IntegratedFluidNetwork net : affectedNetworks) {
            for (IIntegratedFluidMember m : new ArrayList<>(net.getMembers())) {
                net.removeMember(m);
                m.setNetwork(null);
            }
            removeNetwork(net);
        }

        List<Set<IIntegratedFluidMember>> components = findConnectedComponents(new ArrayList<>(allAffectedMembers));
        boolean isMerge = components.size() == 1 && affectedNetworks.size() > 1;
        Set<IIntegratedFluidMember> primaryComponent = null;
        if (!isMerge && components.size() > 1) {
            primaryComponent = components.stream()
                .max(Comparator.comparingInt(Set::size))
                .orElse(null);
        }

        IFNCanonicalState[] splitStates = IFNStateDistributor.splitStateByWeights(
            combinedState,
            componentCapacityWeights(components));

        for (int componentIndex = 0; componentIndex < components.size(); componentIndex++) {
            Set<IIntegratedFluidMember> component = components.get(componentIndex);
            if (component.isEmpty()) continue;
            IIntegratedFluidMember seed = component.iterator().next();
            UUID targetId;
            if (primaryComponent != null) {
                if (component == primaryComponent) {
                    targetId = splitPrimaryId != null ? splitPrimaryId : chooseNetworkId(seed, component);
                } else {
                    targetId = UUID.randomUUID();
                }
            } else {
                targetId = chooseNetworkId(seed, component);
            }
            IntegratedFluidNetwork newNetwork = createEmptyNetwork(targetId);

            for (IIntegratedFluidMember componentMember : component) {
                newNetwork.addMember(componentMember);
                componentMember.setNetwork(newNetwork);
            }

            applyState(newNetwork, isMerge ? combinedState : splitStates[componentIndex]);

            newNetwork.setExpectedMemberCount(newNetwork.getMemberCount());
            newNetwork.setPending(false);
            newNetwork.capFluidToCapacity();
            persistNetwork(newNetwork);

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
        tickOperationalSafety();

        // Apply heat loss every 20 ticks (1 second)
        if (worldTick - lastHeatLossTick >= 20) {
            lastHeatLossTick = worldTick;

            // Apply heat loss to all networks
            IFNAmbientContext ambientContext = IFNAmbientContext.forWorld(world);
            for (IntegratedFluidNetwork network : new HashSet<>(allNetworks)) {
                if (network != null) {
                    IFNHeatExchangeRuntime.apply(network, ambientContext);
                    persistNetwork(network);
                }
            }
        }
    }

    private void tickOperationalSafety() {
        safetyTicker.tick(new HashSet<>(allNetworks), safetyRandom, this::ruptureFailureCandidate);
    }

    private void ruptureFailureCandidate(IIntegratedFluidMember member) {
        if (member == null) {
            return;
        }

        if (member instanceof MetaPipeEntity pipe) {
            IGregTechTileEntity baseTile = pipe.getBaseMetaTileEntity();
            if (baseTile != null) {
                World pipeWorld = baseTile.getWorld();
                if (pipeWorld != null && !pipeWorld.isRemote) {
                    onMemberRemoved(member);
                    pipeWorld.setBlockToAir(baseTile.getXCoord(), baseTile.getYCoord(), baseTile.getZCoord());
                    return;
                }
            }
        }

        onMemberRemoved(member);
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

    private long[] componentCapacityWeights(List<Set<IIntegratedFluidMember>> components) {
        long[] weights = new long[components == null ? 0 : components.size()];
        if (components == null) {
            return weights;
        }
        for (int i = 0; i < components.size(); i++) {
            long capacity = 0L;
            for (IIntegratedFluidMember member : components.get(i)) {
                if (member != null) {
                    capacity += Math.max(0, member.getCapacityContribution());
                    capacity += Math.max(0, member.getAccumulatorContribution());
                }
            }
            weights[i] = capacity;
        }
        return weights;
    }

    private void applyState(IntegratedFluidNetwork network, IFNCanonicalState state) {
        if (network != null && state != null && !state.isEmpty()) {
            network.replaceCanonicalState(state);
        }
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
    private IntegratedFluidNetwork createEmptyNetwork(UUID networkId) {
        IntegratedFluidNetwork network = new IntegratedFluidNetwork(networkId);
        allNetworks.add(network);
        return network;
    }

    private IntegratedFluidNetwork createNewNetwork(UUID networkId) {
        IntegratedFluidNetwork network = createEmptyNetwork(networkId);
        IntegratedFluidNetworkSavedData.NetworkState state = savedData.getState(network.getNetworkId());
        if (state != null) {
            network.loadState(
                state.fluidName,
                state.amountQ,
                state.enthalpyQ,
                IntegratedFluidNetwork.DEFAULT_PRESSURE,
                state.expectedMemberCount
            );
        }
        return network;
    }

    private IntegratedFluidNetwork getOrCreateNetwork(UUID networkId) {
        if (networkId == null) {
            networkId = UUID.randomUUID();
        }
        for (IntegratedFluidNetwork network : allNetworks) {
            if (network != null && networkId.equals(network.getNetworkId())) {
                return network;
            }
        }
        return createNewNetwork(networkId);
    }

    private void persistNetwork(IntegratedFluidNetwork network) {
        if (network == null) return;
        savedData.upsertState(network.getNetworkId(), network);
    }

    private void removeNetwork(IntegratedFluidNetwork network) {
        if (network == null) return;
        allNetworks.remove(network);
        savedData.removeState(network.getNetworkId());
    }

    private void ensureExpectedCount(IntegratedFluidNetwork network) {
        if (network == null) return;
        if (network.getExpectedMemberCount() <= 0) {
            network.setExpectedMemberCount(network.getMemberCount());
        }
    }

    private void updatePending(IntegratedFluidNetwork network) {
        if (network == null) return;
        IFNTopologyRebuilder.Result rebuild =
            IFNTopologyRebuilder.rebuild(network.getMembers(), network.getExpectedMemberCount());
        network.setExpectedMemberCount(rebuild.expectedMemberCount());
        network.setPending(rebuild.pending());
    }

    private UUID chooseNetworkId(IIntegratedFluidMember member, Set<IIntegratedFluidMember> component) {
        Map<UUID, Integer> counts = new HashMap<>();
        UUID memberId = member.getNetworkId();
        if (memberId != null) {
            counts.put(memberId, 1);
        }
        for (IIntegratedFluidMember componentMember : component) {
            UUID id = componentMember.getNetworkId();
            if (id != null) {
                counts.merge(id, 1, Integer::sum);
            }
        }
        UUID best = null;
        int bestCount = 0;
        for (Map.Entry<UUID, Integer> entry : counts.entrySet()) {
            UUID id = entry.getKey();
            int count = entry.getValue();
            if (count > bestCount) {
                best = id;
                bestCount = count;
            } else if (count == bestCount && best != null && id.toString().compareTo(best.toString()) < 0) {
                best = id;
            }
        }
        if (best == null) {
            best = UUID.randomUUID();
        }
        return best;
    }


    /**
     * Adds a member to an existing network.
     */
    private void addToNetwork(IIntegratedFluidMember member, IntegratedFluidNetwork network) {
        if (member == null) return;
        if (network == null) {
            network = getOrCreateNetwork(member.getNetworkId());
        }
        IntegratedFluidNetwork current = member.getNetwork();
        if (current != null && current != network) {
            current.removeMember(member);
        }
        if (member.getNetwork() != network) {
            network.addMember(member);
            member.setNetwork(network);
        }
    }

    boolean freezeIfIncompatibleNetworks(Set<IntegratedFluidNetwork> networks) {
        if (networks == null || networks.size() <= 1) {
            return false;
        }

        IFNCanonicalState mergedState = IFNCanonicalState.empty();
        for (IntegratedFluidNetwork network : networks) {
            if (network == null) {
                continue;
            }
            IFNCanonicalState nextState = network.getCanonicalState();
            if (!IFNMergePolicy.canMerge(mergedState, nextState)) {
                freezeNetworks(networks, "fluid conflict");
                return true;
            }
            mergedState = IFNMergePolicy.merge(mergedState, nextState);
        }
        return false;
    }

    private void freezeNetworks(Set<IntegratedFluidNetwork> networks, String reason) {
        for (IntegratedFluidNetwork network : networks) {
            if (network == null) {
                continue;
            }
            network.freeze(reason);
            persistNetwork(network);
            for (IIntegratedFluidMember member : network.getMembers()) {
                member.onNetworkUpdate();
            }
        }
    }

    /**
     * Merges multiple networks into the target network.
     */
    void mergeIntoNetwork(IntegratedFluidNetwork mainNetwork, Set<IntegratedFluidNetwork> networks) {
        if (mainNetwork == null || networks.isEmpty()) return;

        for (IntegratedFluidNetwork network : networks) {
            if (network == null || network == mainNetwork) continue;
            if (!IFNMergePolicy.canMerge(mainNetwork.getCanonicalState(), network.getCanonicalState())) {
                mainNetwork.merge(network);
                persistNetwork(network);
                for (IIntegratedFluidMember member : network.getMembers()) {
                    member.onNetworkUpdate();
                }
                continue;
            }
            mainNetwork.merge(network);
            removeNetwork(network);
        }

        ensureExpectedCount(mainNetwork);
        updatePending(mainNetwork);
        persistNetwork(mainNetwork);

        for (IIntegratedFluidMember member : mainNetwork.getMembers()) {
            member.onNetworkUpdate();
        }
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
