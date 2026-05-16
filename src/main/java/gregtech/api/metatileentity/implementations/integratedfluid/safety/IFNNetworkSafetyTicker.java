package gregtech.api.metatileentity.implementations.integratedfluid.safety;

import java.util.Collection;
import java.util.Optional;
import java.util.Random;
import java.util.function.Consumer;

import gregtech.api.metatileentity.implementations.integratedfluid.IIntegratedFluidMember;
import gregtech.api.metatileentity.implementations.integratedfluid.IntegratedFluidNetwork;

public final class IFNNetworkSafetyTicker {

    public int tick(Collection<IntegratedFluidNetwork> networks, Random random,
        Consumer<IIntegratedFluidMember> failureConsumer) {
        if (networks == null || random == null) {
            return 0;
        }

        int failures = 0;
        for (IntegratedFluidNetwork network : networks) {
            if (network == null || network.isPending()) {
                continue;
            }

            Optional<IIntegratedFluidMember> failedMember = network.applyOperationalFailure(random);
            if (failedMember.isPresent()) {
                failures++;
                if (failureConsumer != null) {
                    failureConsumer.accept(failedMember.get());
                }
            }
        }
        return failures;
    }
}
