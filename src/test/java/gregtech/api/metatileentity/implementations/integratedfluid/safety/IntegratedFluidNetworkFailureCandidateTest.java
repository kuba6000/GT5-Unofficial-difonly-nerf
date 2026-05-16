package gregtech.api.metatileentity.implementations.integratedfluid.safety;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.Random;

import net.minecraftforge.fluids.Fluid;

import org.junit.jupiter.api.Test;

import gregtech.api.metatileentity.implementations.integratedfluid.IFNTestSupport;
import gregtech.api.metatileentity.implementations.integratedfluid.IIntegratedFluidMember;
import gregtech.api.metatileentity.implementations.integratedfluid.IntegratedFluidNetwork;

class IntegratedFluidNetworkFailureCandidateTest {

    @Test
    void pressureFailureSelectsWeakestPressureCandidate() {
        IntegratedFluidNetwork network = new IntegratedFluidNetwork();
        IIntegratedFluidMember hatch = member(false, 1.0f, 100.0f, 500.0f);
        IIntegratedFluidMember strongPipe = member(true, 12.0f, 100.0f, 500.0f);
        IIntegratedFluidMember weakPipe = member(true, 6.0f, 100.0f, 500.0f);

        network.addMember(hatch);
        network.addMember(strongPipe);
        network.addMember(weakPipe);
        network.setPressure(12.0f);

        assertSame(weakPipe, network.selectFailureCandidate(network.tickOperationalSafety()).get());
    }

    @Test
    void temperatureFailureSelectsWeakestTemperatureCandidateWhenPressureIsNormal() {
        IntegratedFluidNetwork network = new IntegratedFluidNetwork();
        IIntegratedFluidMember hatch = member(false, 1.0f, 100.0f, 250.0f);
        IIntegratedFluidMember strongPipe = member(true, 12.0f, 100.0f, 500.0f);
        IIntegratedFluidMember weakPipe = member(true, 6.0f, 100.0f, 280.0f);

        network.addMember(hatch);
        network.addMember(strongPipe);
        network.addMember(weakPipe);

        assertSame(weakPipe, network.selectFailureCandidate(network.tickOperationalSafety()).get());
    }

    @Test
    void operationalFailureVoidsNetworkAndReturnsFailureCandidate() {
        Fluid fluid = IFNTestSupport.liquidFluid();
        IntegratedFluidNetwork network = seededNetwork(fluid);
        IIntegratedFluidMember pipe = member(true, 6.0f, 100.0f, 500.0f);

        network.addMember(pipe);
        network.setPressure(12.0f);

        Optional<IIntegratedFluidMember> failed = network.applyOperationalFailure(fixedRandom(0.99d));

        assertSame(pipe, failed.get());
        assertTrue(network.getCanonicalState().isEmpty());
    }

    @Test
    void safeOperationalTickDoesNotVoidNetwork() {
        Fluid fluid = IFNTestSupport.liquidFluid();
        IntegratedFluidNetwork network = seededNetwork(fluid);

        network.addMember(member(true, 6.0f, 100.0f, 500.0f));
        network.setPressure(10.0f);

        Optional<IIntegratedFluidMember> failed = network.applyOperationalFailure(fixedRandom(0.0d));

        assertTrue(!failed.isPresent());
        assertTrue(!network.getCanonicalState().isEmpty());
    }

    private static IIntegratedFluidMember member(boolean failureCandidate, float maxPressureDifferentialBar,
        float accumulatorMaxPressureBar, float maxTemperatureKelvin) {
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
            public float getAccumulatorMaxPressureBar() {
                return accumulatorMaxPressureBar;
            }

            @Override
            public boolean isOperationalFailureCandidate() {
                return failureCandidate;
            }

            @Override
            public float getMaxPressureDifferentialBar() {
                return maxPressureDifferentialBar;
            }

            @Override
            public float getMaxTemperatureKelvin() {
                return maxTemperatureKelvin;
            }
        };
    }

    private static IntegratedFluidNetwork seededNetwork(Fluid fluid) {
        IntegratedFluidNetwork network = IFNTestSupport.newNetwork(fluid, 100, 100, 10.0f);
        long amountQ = 10L * IntegratedFluidNetwork.AMOUNT_SCALE;
        long enthalpyQ = IntegratedFluidNetwork.toEnthalpyQFromSpecific(300.0d, amountQ);
        network.addState(fluid, amountQ, enthalpyQ);
        return network;
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
