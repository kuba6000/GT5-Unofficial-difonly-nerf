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
     * Called when the network is updated or needs to be recalculated.
     */
    void onNetworkUpdate();
}
