package gregtech.api.metatileentity.implementations.integratedfluid.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import net.minecraftforge.fluids.Fluid;

import org.junit.jupiter.api.Test;

import gregtech.api.metatileentity.implementations.integratedfluid.IFNTestSupport;
import gregtech.api.metatileentity.implementations.integratedfluid.IIntegratedFluidMember;
import gregtech.api.metatileentity.implementations.integratedfluid.IntegratedFluidNetwork;

class IFNNetworkSafetyTickerTest {

    @Test
    void tickAppliesOperationalFailureAndReportsCandidate() {
        Fluid fluid = IFNTestSupport.liquidFluid();
        IntegratedFluidNetwork network = seededNetwork(fluid);
        IIntegratedFluidMember pipe = failureCandidate();
        List<IIntegratedFluidMember> failed = new ArrayList<>();

        network.addMember(pipe);
        network.setPressure(12.0f);

        int failures = new IFNNetworkSafetyTicker()
            .tick(Collections.singleton(network), fixedRandom(0.99d), failed::add);

        assertEquals(1, failures);
        assertEquals(1, failed.size());
        assertSame(pipe, failed.get(0));
        assertTrue(network.getCanonicalState().isEmpty());
    }

    @Test
    void tickSkipsPendingNetwork() {
        Fluid fluid = IFNTestSupport.liquidFluid();
        IntegratedFluidNetwork network = seededNetwork(fluid);
        List<IIntegratedFluidMember> failed = new ArrayList<>();

        network.addMember(failureCandidate());
        network.setPressure(12.0f);
        network.setPending(true);

        int failures = new IFNNetworkSafetyTicker()
            .tick(Collections.singleton(network), fixedRandom(0.99d), failed::add);

        assertEquals(0, failures);
        assertTrue(failed.isEmpty());
        assertTrue(!network.getCanonicalState().isEmpty());
    }

    private static IntegratedFluidNetwork seededNetwork(Fluid fluid) {
        IntegratedFluidNetwork network = IFNTestSupport.newNetwork(fluid, 100, 100, 10.0f);
        long amountQ = 10L * IntegratedFluidNetwork.AMOUNT_SCALE;
        long enthalpyQ = IntegratedFluidNetwork.toEnthalpyQFromSpecific(300.0d, amountQ);
        network.addState(fluid, amountQ, enthalpyQ);
        return network;
    }

    private static IIntegratedFluidMember failureCandidate() {
        return new IIntegratedFluidMember() {

            @Override
            public IntegratedFluidNetwork getNetwork() {
                return null;
            }

            @Override
            public void setNetwork(IntegratedFluidNetwork network) {}

            @Override
            public java.util.UUID getNetworkId() {
                return null;
            }

            @Override
            public void setNetworkId(java.util.UUID id) {}

            @Override
            public void onNetworkUpdate() {}

            @Override
            public int getCapacityContribution() {
                return 0;
            }

            @Override
            public int getAccumulatorContribution() {
                return 1000;
            }

            @Override
            public boolean isOperationalFailureCandidate() {
                return true;
            }

            @Override
            public float getMaxPressureDifferentialBar() {
                return 6.0f;
            }
        };
    }

    private static Random fixedRandom(double value) {
        return new Random(0L) {

            @Override
            public double nextDouble() {
                return value;
            }
        };
    }
}
