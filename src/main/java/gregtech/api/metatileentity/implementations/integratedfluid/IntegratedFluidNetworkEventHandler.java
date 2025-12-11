package gregtech.api.metatileentity.implementations.integratedfluid;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.world.World;

/**
 * Event handler for Integrated Fluid Network system.
 * Handles world tick events to apply heat loss to networks.
 */
public class IntegratedFluidNetworkEventHandler {

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        // Only run on server side and at the END phase (after all other updates)
        if (event.phase != TickEvent.Phase.END || event.world.isRemote) {
            return;
        }

        World world = event.world;
        NetworkManager manager = NetworkManager.getInstance(world);

        // Call network manager's tick handler
        manager.onWorldTick(world.getTotalWorldTime());
    }
}

