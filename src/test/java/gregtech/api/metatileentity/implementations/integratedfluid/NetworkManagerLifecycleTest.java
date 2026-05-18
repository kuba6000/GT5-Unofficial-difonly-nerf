package gregtech.api.metatileentity.implementations.integratedfluid;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.mockito.Mockito.mock;

import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;

import org.junit.jupiter.api.Test;

class NetworkManagerLifecycleTest {

    @Test
    void clearingWorldInstanceDropsRuntimeManagerState() {
        World world = mock(World.class);
        world.mapStorage = mock(MapStorage.class);
        NetworkManager first = NetworkManager.getInstance(world);

        NetworkManager.clearInstance(world);
        NetworkManager second = NetworkManager.getInstance(world);

        assertNotSame(first, second);
    }
}
