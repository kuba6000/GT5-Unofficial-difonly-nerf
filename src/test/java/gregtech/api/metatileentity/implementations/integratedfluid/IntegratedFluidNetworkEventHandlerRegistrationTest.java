package gregtech.api.metatileentity.implementations.integratedfluid;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class IntegratedFluidNetworkEventHandlerRegistrationTest {

    @Test
    void repeatedRegisterOnlyRegistersOneHandlerUntilUnregistered() {
        IntegratedFluidNetworkEventHandlerRegistration registration = new IntegratedFluidNetworkEventHandlerRegistration();
        List<Object> registered = new ArrayList<>();
        List<Object> unregistered = new ArrayList<>();

        registration.register(registered::add);
        registration.register(registered::add);
        registration.unregister(unregistered::add);
        registration.unregister(unregistered::add);

        assertEquals(1, registered.size());
        assertEquals(1, unregistered.size());
        assertEquals(registered.get(0), unregistered.get(0));
    }
}
