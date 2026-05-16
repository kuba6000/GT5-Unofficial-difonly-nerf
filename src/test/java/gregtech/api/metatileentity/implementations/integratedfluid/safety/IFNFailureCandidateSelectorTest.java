package gregtech.api.metatileentity.implementations.integratedfluid.safety;

import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import gregtech.api.metatileentity.implementations.integratedfluid.IIntegratedFluidMember;
import gregtech.api.metatileentity.implementations.integratedfluid.IntegratedFluidNetwork;
import gregtech.api.metatileentity.implementations.integratedfluid.MTEIntegratedFluidPipe;

class IFNFailureCandidateSelectorTest {

    @Test
    void pressureCandidateUsesWeakestPressureDifferentialAndIgnoresNonCandidates() {
        IIntegratedFluidMember hatch = member(false, 1.0f, 500.0f);
        IIntegratedFluidMember strongPipe = member(true, 12.0f, 500.0f);
        IIntegratedFluidMember weakPipe = member(true, 6.0f, 500.0f);

        assertSame(weakPipe, IFNFailureCandidateSelector.selectWeakestPressureCandidate(
                Arrays.asList(hatch, strongPipe, weakPipe)).get());
    }

    @Test
    void temperatureCandidateUsesWeakestTemperatureAndIgnoresNonCandidates() {
        IIntegratedFluidMember hatch = member(false, 10.0f, 300.0f);
        IIntegratedFluidMember strongPipe = member(true, 10.0f, 1200.0f);
        IIntegratedFluidMember weakPipe = member(true, 10.0f, 800.0f);

        assertSame(weakPipe, IFNFailureCandidateSelector.selectWeakestTemperatureCandidate(
                Arrays.asList(hatch, strongPipe, weakPipe)).get());
    }

    @Test
    void integratedFluidPipeIsFailureCandidate() {
        MTEIntegratedFluidPipe pipe = new MTEIntegratedFluidPipe("test.integrated.fluid.pipe");

        assertSame(pipe, IFNFailureCandidateSelector.selectWeakestPressureCandidate(
                Arrays.asList(pipe)).get());
    }

    private static IIntegratedFluidMember member(boolean failureCandidate, float maxPressureDifferentialBar,
        float maxTemperatureKelvin) {
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
