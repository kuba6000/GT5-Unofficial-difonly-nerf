package gregtech.api.metatileentity.implementations.integratedfluid.safety;

import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

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
}
