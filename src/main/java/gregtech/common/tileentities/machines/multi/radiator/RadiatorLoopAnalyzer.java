package gregtech.common.tileentities.machines.multi.radiator;

import static gregtech.common.tileentities.machines.multi.radiator.RadiatorThermo.PRESSURE_DROP_PER_SEGMENT_BAR;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import gregtech.api.GregTechAPI;

public final class RadiatorLoopAnalyzer {

    public static final int PORT_META = 8;
    public static final int LOOP_PIPE_META = 9;
    public static final int CONDUCTION_META = 10;
    public static final int HEAT_EXCHANGE_META = 11;

    private static final double BARE_PIPE_CONDUCTIVE = 4.0d;
    private static final double BARE_PIPE_RADIATIVE = 2.0e-7d;

    private RadiatorLoopAnalyzer() {}

    public static RadiatorLoopSnapshot analyze(World world, int controllerX, int controllerY, int controllerZ,
        ForgeDirection frontFacing) {
        if (world == null) {
            return RadiatorLoopSnapshot.invalid("no_loop");
        }
        if (frontFacing == ForgeDirection.UNKNOWN) {
            return RadiatorLoopSnapshot.invalid("radiator_loop_ports");
        }

        Set<Long> machineVolume = getMachineVolume(controllerX, controllerY, controllerZ, frontFacing);
        List<PortCandidate> ports = findPorts(world, controllerX, controllerY, controllerZ, frontFacing, machineVolume);
        if (ports.size() != 2) {
            return RadiatorLoopSnapshot.invalid("radiator_loop_ports");
        }

        PortCandidate portA = ports.get(0);
        PortCandidate portB = ports.get(1);

        NodePos startPipe = portA.worldPos.offset(portA.outward);
        NodePos endPipe = portB.worldPos.offset(portB.outward);
        if (!isLoopPipe(world, startPipe) || !isLoopPipe(world, endPipe)) {
            return RadiatorLoopSnapshot.invalid("radiator_loop_missing");
        }

        List<NodePos> path = tracePipePath(world, startPipe, endPipe);
        if (path == null || path.isEmpty()) {
            return RadiatorLoopSnapshot.invalid("radiator_loop_invalid");
        }

        AnalyzerState state = new AnalyzerState();
        List<RadiatorLoopSnapshot.ThermalBranch> branches = new ArrayList<>();
        Set<Long> claimedThermalNodes = new HashSet<>();
        for (int i = 0; i < path.size(); i++) {
            NodePos current = path.get(i);
            ForgeDirection prevDirection = i == 0 ? portA.outward.getOpposite() : current.directionTo(path.get(i - 1));
            ForgeDirection nextDirection = i == path.size() - 1 ? portB.outward.getOpposite() : current.directionTo(path.get(i + 1));

            for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
                if (side == prevDirection || side == nextDirection) {
                    continue;
                }
                NodePos adjacent = current.offset(side);
                boolean addBareExposure = !isPort(world, adjacent) && !isLoopPipe(world, adjacent);
                RadiatorModuleRegistry.ModuleData adjacentModule = getModuleData(world, adjacent);
                if (adjacentModule != null && adjacentModule.type == RadiatorModuleRegistry.ModuleType.HEAT_EXCHANGE) {
                    if (!claimedThermalNodes.add(adjacent.pack())) {
                        if (addBareExposure) {
                            branches.add(RadiatorLoopSnapshot.ThermalBranch.exchanger(
                                BARE_PIPE_CONDUCTIVE,
                                BARE_PIPE_RADIATIVE
                            ));
                        }
                        continue;
                    }
                    state.heatExchangeModules++;
                    branches.add(RadiatorLoopSnapshot.ThermalBranch.exchanger(
                        adjacentModule.conductiveCoefficient,
                        adjacentModule.radiativeCoefficient
                    ));
                } else if (adjacentModule != null
                    && adjacentModule.type == RadiatorModuleRegistry.ModuleType.CONDUCTION) {
                    RadiatorLoopSnapshot.ThermalBranch branch = analyzeConductionTree(
                        world,
                        adjacent,
                        side.getOpposite(),
                        claimedThermalNodes,
                        new HashSet<Long>(),
                        state
                    );
                    if (state.invalid) {
                        return RadiatorLoopSnapshot.invalid("radiator_loop_invalid");
                    }
                    if (branch != null) {
                        branches.add(branch);
                    }
                } else if (!isLoopStructureBlock(world, adjacent)) {
                    branches.add(RadiatorLoopSnapshot.ThermalBranch.exchanger(BARE_PIPE_CONDUCTIVE, BARE_PIPE_RADIATIVE));
                    continue;
                }

                if (addBareExposure) {
                    branches.add(RadiatorLoopSnapshot.ThermalBranch.exchanger(
                        BARE_PIPE_CONDUCTIVE,
                        BARE_PIPE_RADIATIVE
                    ));
                }
            }
        }

        return RadiatorLoopSnapshot.valid(
            path.size(),
            state.conductionModules,
            state.heatExchangeModules,
            path.size() * PRESSURE_DROP_PER_SEGMENT_BAR,
            branches
        );
    }

    private static List<PortCandidate> findPorts(World world, int controllerX, int controllerY, int controllerZ,
        ForgeDirection frontFacing, Set<Long> machineVolume) {
        List<PortCandidate> ports = new ArrayList<>();
        ForgeDirection backFacing = frontFacing.getOpposite();
        ForgeDirection rightFacing = getRight(frontFacing);

        for (int localX = -1; localX <= 1; localX++) {
            for (int localY = -1; localY <= 1; localY++) {
                for (int localDepth = 0; localDepth <= 2; localDepth++) {
                    NodePos pos = toWorldPos(controllerX, controllerY, controllerZ, localX, localY, localDepth,
                        backFacing, rightFacing);
                    if (isPort(world, pos)) {
                        ForgeDirection outward = determinePortOutward(world, pos, machineVolume);
                        if (outward == ForgeDirection.UNKNOWN) {
                            return new ArrayList<>();
                        }
                        ports.add(new PortCandidate(pos, outward));
                    }
                }
            }
        }
        return ports;
    }

    private static List<NodePos> tracePipePath(World world, NodePos startPipe, NodePos endPipe) {
        List<NodePos> path = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        NodePos previous = null;
        NodePos current = startPipe;

        while (true) {
            if (!visited.add(current.pack())) {
                return null;
            }
            path.add(current);
            if (current.equals(endPipe)) {
                return path;
            }

            List<NodePos> nextCandidates = new ArrayList<>();
            for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
                NodePos neighbor = current.offset(direction);
                if (neighbor.equals(previous)) {
                    continue;
                }
                if (isLoopPipe(world, neighbor)) {
                    nextCandidates.add(neighbor);
                }
            }

            if (nextCandidates.size() != 1) {
                return null;
            }

            previous = current;
            current = nextCandidates.get(0);
        }
    }

    private static RadiatorLoopSnapshot.ThermalBranch analyzeConductionTree(World world, NodePos node,
        ForgeDirection backDirection, Set<Long> claimedNodes, Set<Long> visited, AnalyzerState state) {
        if (!visited.add(node.pack())) {
            state.invalid = true;
            return null;
        }
        if (!claimedNodes.add(node.pack())) {
            return null;
        }

        RadiatorModuleRegistry.ModuleData moduleData = getModuleData(world, node);
        if (moduleData != null && moduleData.type == RadiatorModuleRegistry.ModuleType.HEAT_EXCHANGE) {
            state.heatExchangeModules++;
            return RadiatorLoopSnapshot.ThermalBranch.exchanger(
                moduleData.conductiveCoefficient,
                moduleData.radiativeCoefficient
            );
        }
        if (moduleData == null || moduleData.type != RadiatorModuleRegistry.ModuleType.CONDUCTION) {
            return null;
        }

        state.conductionModules++;
        List<RadiatorLoopSnapshot.ThermalBranch> children = new ArrayList<>();
        for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
            if (direction == backDirection) {
                continue;
            }
            NodePos neighbor = node.offset(direction);
            RadiatorModuleRegistry.ModuleData childData = getModuleData(world, neighbor);
            if (childData != null) {
                RadiatorLoopSnapshot.ThermalBranch child = analyzeConductionTree(
                    world,
                    neighbor,
                    direction.getOpposite(),
                    claimedNodes,
                    visited,
                    state
                );
                if (state.invalid) {
                    return null;
                }
                if (child != null) {
                    children.add(child);
                }
            }
        }

        if (children.isEmpty()) {
            return null;
        }
        return RadiatorLoopSnapshot.ThermalBranch.conduction(moduleData.conductiveCoefficient, children);
    }

    public static boolean isPort(World world, int x, int y, int z) {
        return isPort(world, new NodePos(x, y, z));
    }

    public static boolean isPort(World world, NodePos pos) {
        return isCasingMeta(world, pos, PORT_META);
    }

    public static boolean isLoopPipe(World world, NodePos pos) {
        return isCasingMeta(world, pos, LOOP_PIPE_META);
    }

    public static boolean isConductionModule(World world, NodePos pos) {
        return RadiatorModuleRegistry.isModule(
            world.getBlock(pos.x, pos.y, pos.z),
            world.getBlockMetadata(pos.x, pos.y, pos.z),
            RadiatorModuleRegistry.ModuleType.CONDUCTION
        );
    }

    public static boolean isHeatExchangeModule(World world, NodePos pos) {
        return RadiatorModuleRegistry.isModule(
            world.getBlock(pos.x, pos.y, pos.z),
            world.getBlockMetadata(pos.x, pos.y, pos.z),
            RadiatorModuleRegistry.ModuleType.HEAT_EXCHANGE
        );
    }

    public static boolean isLoopStructureBlock(World world, NodePos pos) {
        return isPort(world, pos) || isLoopPipe(world, pos) || isConductionModule(world, pos)
            || isHeatExchangeModule(world, pos);
    }

    private static boolean isCasingMeta(World world, NodePos pos, int meta) {
        Block block = world.getBlock(pos.x, pos.y, pos.z);
        return block == GregTechAPI.sBlockCasings11 && world.getBlockMetadata(pos.x, pos.y, pos.z) == meta;
    }

    private static RadiatorModuleRegistry.ModuleData getModuleData(World world, NodePos pos) {
        return RadiatorModuleRegistry.getModuleData(
            world.getBlock(pos.x, pos.y, pos.z),
            world.getBlockMetadata(pos.x, pos.y, pos.z)
        );
    }

    private static NodePos toWorldPos(int controllerX, int controllerY, int controllerZ, int localX, int localY,
        int localDepth, ForgeDirection backFacing, ForgeDirection rightFacing) {
        return new NodePos(
            controllerX + localX * rightFacing.offsetX + localDepth * backFacing.offsetX,
            controllerY + localY,
            controllerZ + localX * rightFacing.offsetZ + localDepth * backFacing.offsetZ
        );
    }

    private static ForgeDirection determinePortOutward(World world, NodePos portPos, Set<Long> machineVolume) {
        ForgeDirection outward = ForgeDirection.UNKNOWN;
        for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
            NodePos adjacent = portPos.offset(direction);
            if (machineVolume.contains(adjacent.pack())) {
                continue;
            }
            if (!isLoopPipe(world, adjacent)) {
                continue;
            }
            if (outward != ForgeDirection.UNKNOWN) {
                return ForgeDirection.UNKNOWN;
            }
            outward = direction;
        }
        return outward;
    }

    private static ForgeDirection getRight(ForgeDirection frontFacing) {
        return switch (frontFacing) {
            case NORTH -> ForgeDirection.EAST;
            case SOUTH -> ForgeDirection.WEST;
            case WEST -> ForgeDirection.NORTH;
            case EAST -> ForgeDirection.SOUTH;
            default -> ForgeDirection.UNKNOWN;
        };
    }

    private static Set<Long> getMachineVolume(int controllerX, int controllerY, int controllerZ,
        ForgeDirection frontFacing) {
        Set<Long> positions = new HashSet<>();
        ForgeDirection backFacing = frontFacing.getOpposite();
        ForgeDirection rightFacing = getRight(frontFacing);
        for (int localX = -1; localX <= 1; localX++) {
            for (int localY = -1; localY <= 1; localY++) {
                for (int localDepth = 0; localDepth <= 2; localDepth++) {
                    positions.add(
                        toWorldPos(controllerX, controllerY, controllerZ, localX, localY, localDepth, backFacing,
                            rightFacing).pack()
                    );
                }
            }
        }
        return positions;
    }

    private static final class AnalyzerState {
        private int conductionModules;
        private int heatExchangeModules;
        private boolean invalid;
    }

    private static final class PortCandidate {
        private final NodePos worldPos;
        private final ForgeDirection outward;

        private PortCandidate(NodePos worldPos, ForgeDirection outward) {
            this.worldPos = worldPos;
            this.outward = outward;
        }
    }

    public static final class NodePos {
        public final int x;
        public final int y;
        public final int z;

        public NodePos(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public NodePos offset(ForgeDirection direction) {
            return new NodePos(x + direction.offsetX, y + direction.offsetY, z + direction.offsetZ);
        }

        public NodePos offset(int dx, int dy, int dz) {
            return new NodePos(x + dx, y + dy, z + dz);
        }
        public ForgeDirection directionTo(NodePos other) {
            int dx = Integer.signum(other.x - x);
            int dy = Integer.signum(other.y - y);
            int dz = Integer.signum(other.z - z);
            for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
                if (direction.offsetX == dx && direction.offsetY == dy && direction.offsetZ == dz) {
                    return direction;
                }
            }
            return ForgeDirection.UNKNOWN;
        }

        public long pack() {
            long px = ((long) x & 0x3FFFFFFL) << 38;
            long pz = ((long) z & 0x3FFFFFFL) << 12;
            long py = (long) y & 0xFFFL;
            return px | pz | py;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof NodePos other)) {
                return false;
            }
            return x == other.x && y == other.y && z == other.z;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y, z);
        }
    }
}
