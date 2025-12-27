package gregtech.api.metatileentity.implementations.integratedfluid;

/**
 * Interface for components that can be part of an integrated fluid network.
 * This includes integrated fluid pipes and hatches.
 */
public interface IIntegratedFluidMember {

    /**
     * Gets the network this member belongs to.
     */
    IntegratedFluidNetwork getNetwork();

    /**
     * Sets the network this member belongs to.
     */
    void setNetwork(IntegratedFluidNetwork network);

    /**
     * Gets the persistent network ID for this member.
     */
    java.util.UUID getNetworkId();

    /**
     * Sets the persistent network ID for this member.
     */
    void setNetworkId(java.util.UUID id);

    /**
     * Called when the network is updated or needs to be recalculated.
     */
    void onNetworkUpdate();

    /**
     * Returns the capacity contribution of this member to the network in mB (millibuckets).
     * - Pipes: 100L (100 mB)
     * - Hatches: 10,000L (10,000 mB)
     * - Injector Hatches: 0L (0 mB)
     */
    int getCapacityContribution();
}
