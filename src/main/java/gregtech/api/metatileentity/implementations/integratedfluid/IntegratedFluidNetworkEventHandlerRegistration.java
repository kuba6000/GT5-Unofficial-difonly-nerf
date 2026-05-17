package gregtech.api.metatileentity.implementations.integratedfluid;

import java.util.function.Consumer;

public final class IntegratedFluidNetworkEventHandlerRegistration {

    private IntegratedFluidNetworkEventHandler handler;

    public void register(Consumer<Object> register) {
        if (handler != null) {
            return;
        }
        handler = new IntegratedFluidNetworkEventHandler();
        register.accept(handler);
    }

    public void unregister(Consumer<Object> unregister) {
        if (handler == null) {
            return;
        }
        unregister.accept(handler);
        handler = null;
    }
}
