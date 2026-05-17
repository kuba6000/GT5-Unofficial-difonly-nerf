package gregtech.api.metatileentity.implementations.integratedfluid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import net.minecraftforge.fluids.Fluid;

class IFNStateMutationApplierTest {

    @Test
    void failedOutputMutationRestoresExtractedInputState() {
        Fluid fluid = IFNTestSupport.liquidFluid();
        IntegratedFluidNetwork input = IFNTestSupport.newNetwork(fluid, 10_000, 0, 100.0f);
        IntegratedFluidNetwork output = rejectingOutputNetwork();
        long amountQ = 5L * IntegratedFluidNetwork.AMOUNT_SCALE;
        long enthalpyQ = IntegratedFluidNetwork.toEnthalpyQFromSpecific(300.0d, amountQ);

        input.addState(fluid, amountQ, enthalpyQ);

        IntegratedFluidNetwork.ExtractedPayload extracted = input.extractProportional(amountQ, false);
        boolean applied = IFNStateMutationApplier.addOutputOrRestoreInput(input, output, fluid, extracted, enthalpyQ);

        assertFalse(applied);
        assertEquals(amountQ, input.getAmountQ());
        assertEquals(0L, output.getAmountQ());
    }

    private static IntegratedFluidNetwork rejectingOutputNetwork() {
        return new IntegratedFluidNetwork() {

            @Override
            public boolean addState(Fluid addFluid, long addAmountQ, long addEnthalpyQ) {
                return false;
            }
        };
    }
}
