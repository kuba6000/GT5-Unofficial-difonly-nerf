package gregtech.api.metatileentity.implementations.integratedfluid.topology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;

import net.minecraftforge.fluids.Fluid;

import org.junit.jupiter.api.Test;

import gregtech.api.metatileentity.implementations.integratedfluid.FluidThermalProperties;
import gregtech.api.metatileentity.implementations.integratedfluid.IIntegratedFluidMember;
import gregtech.api.metatileentity.implementations.integratedfluid.IntegratedFluidNetwork;
import gregtech.api.metatileentity.implementations.integratedfluid.amount.EnergyAmount;
import gregtech.api.metatileentity.implementations.integratedfluid.amount.SubstanceAmount;
import gregtech.api.metatileentity.implementations.integratedfluid.fluid.IFNFluidRegistry;
import gregtech.api.metatileentity.implementations.integratedfluid.state.IFNCanonicalState;

class IFNMergeSplitPolicyTest {

    @Test
    void differentPhaseIdsOfSameSubstanceMergeIntoCanonicalSubstance() {
        IFNFluidRegistry.init();

        IFNCanonicalState merged = IFNMergePolicy.merge(
            state("water", 10, 100),
            state("steam", 5, 40)
        );

        assertEquals("water", merged.fluidId().get());
        assertEquals(15, merged.substanceAmount().toWholeRefLiters());
        assertEquals(140, merged.internalEnergy().toWholeEu());
    }

    @Test
    void networkAcceptsDifferentPhaseIdOfSameSubstance() {
        IFNFluidRegistry.init();
        IntegratedFluidNetwork network = new IntegratedFluidNetwork();
        network.addMember(member(100000));
        Fluid water = new Fluid("water");
        Fluid steam = new Fluid("steam");
        long amountQ = IntegratedFluidNetwork.AMOUNT_SCALE;
        long steamEnthalpyQ = enthalpyQ(steam, 450.0d);

        seedNetworkState(network, water.getName(), amountQ, enthalpyQ(water, 300.0d));

        assertTrue(network.canAccept(steam, amountQ, steamEnthalpyQ));
        assertEquals("water", network.getFluidName());
        assertEquals(amountQ, network.getAmountQ());
    }

    private static long enthalpyQ(Fluid fluid, double temperatureKelvin) {
        double specificEnthalpy = FluidThermalProperties.getSpecificEnthalpyFromPT(fluid, 1.0d, temperatureKelvin);
        return IntegratedFluidNetwork.toEnthalpyQFromSpecific(specificEnthalpy, IntegratedFluidNetwork.AMOUNT_SCALE);
    }

    private static void seedNetworkState(IntegratedFluidNetwork network, String fluidId, long amountQ, long enthalpyQ) {
        setField(network, "fluidName", fluidId);
        setField(network, "amountQ", amountQ);
        setField(network, "enthalpyQ", enthalpyQ);
        setField(network, "pressure", 1.0f);
    }

    private static void setField(IntegratedFluidNetwork network, String fieldName, Object value) {
        try {
            Field field = IntegratedFluidNetwork.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(network, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static IIntegratedFluidMember member(int capacity) {
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
                return capacity;
            }

            @Override
            public int getAccumulatorContribution() {
                return 0;
            }
        };
    }

    private static IFNCanonicalState state(String fluidId, long substanceRefLiters, long energyEu) {
        return IFNCanonicalState.of(
            fluidId,
            SubstanceAmount.fromRefLiters(substanceRefLiters),
            EnergyAmount.fromEu(energyEu));
    }
}
