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

    @Test
    void failedSecondOutputMutationRestoresInputsAndFirstOutputSnapshot() {
        Fluid fluid = IFNTestSupport.liquidFluid();
        IntegratedFluidNetwork firstInput = IFNTestSupport.newNetwork(fluid, 10_000, 0, 100.0f);
        IntegratedFluidNetwork secondInput = IFNTestSupport.newNetwork(fluid, 10_000, 0, 100.0f);
        IntegratedFluidNetwork firstOutput = IFNTestSupport.newNetwork(fluid, 10_000, 0, 100.0f);
        IntegratedFluidNetwork secondOutput = rejectingOutputNetwork();
        long firstAmountQ = 5L * IntegratedFluidNetwork.AMOUNT_SCALE;
        long secondAmountQ = 7L * IntegratedFluidNetwork.AMOUNT_SCALE;
        long seedAmountQ = 2L * IntegratedFluidNetwork.AMOUNT_SCALE;
        long firstEnthalpyQ = IntegratedFluidNetwork.toEnthalpyQFromSpecific(300.0d, firstAmountQ);
        long secondEnthalpyQ = IntegratedFluidNetwork.toEnthalpyQFromSpecific(310.0d, secondAmountQ);
        long seedEnthalpyQ = IntegratedFluidNetwork.toEnthalpyQFromSpecific(290.0d, seedAmountQ);

        firstInput.addState(fluid, firstAmountQ, firstEnthalpyQ);
        secondInput.addState(fluid, secondAmountQ, secondEnthalpyQ);
        firstOutput.addState(fluid, seedAmountQ, seedEnthalpyQ);

        IntegratedFluidNetwork.ExtractedPayload firstExtracted = firstInput.extractProportional(firstAmountQ, false);
        IntegratedFluidNetwork.ExtractedPayload secondExtracted = secondInput.extractProportional(secondAmountQ, false);
        boolean applied = IFNStateMutationApplier.addTwoOutputsOrRestoreInputs(
            firstInput,
            firstOutput,
            fluid,
            firstExtracted,
            firstEnthalpyQ,
            secondInput,
            secondOutput,
            fluid,
            secondExtracted,
            secondEnthalpyQ);

        assertFalse(applied);
        assertEquals(firstAmountQ, firstInput.getAmountQ());
        assertEquals(secondAmountQ, secondInput.getAmountQ());
        assertEquals(seedAmountQ, firstOutput.getAmountQ());
        assertEquals(0L, secondOutput.getAmountQ());
    }

    @Test
    void failedSecondSplitOutputMutationRestoresSharedInputAndFirstOutputSnapshot() {
        Fluid fluid = IFNTestSupport.liquidFluid();
        IntegratedFluidNetwork input = IFNTestSupport.newNetwork(fluid, 10_000, 0, 100.0f);
        IntegratedFluidNetwork firstOutput = IFNTestSupport.newNetwork(fluid, 10_000, 0, 100.0f);
        IntegratedFluidNetwork secondOutput = rejectingOutputNetwork();
        long totalAmountQ = 12L * IntegratedFluidNetwork.AMOUNT_SCALE;
        long firstAmountQ = 5L * IntegratedFluidNetwork.AMOUNT_SCALE;
        long secondAmountQ = totalAmountQ - firstAmountQ;
        long seedAmountQ = 2L * IntegratedFluidNetwork.AMOUNT_SCALE;
        long totalEnthalpyQ = IntegratedFluidNetwork.toEnthalpyQFromSpecific(300.0d, totalAmountQ);
        long firstExtractedEnthalpyQ = (long) (totalEnthalpyQ * ((double) firstAmountQ / (double) totalAmountQ));
        long secondExtractedEnthalpyQ = totalEnthalpyQ - firstExtractedEnthalpyQ;
        long firstOutputEnthalpyQ = IntegratedFluidNetwork.toEnthalpyQFromSpecific(330.0d, firstAmountQ);
        long secondOutputEnthalpyQ = IntegratedFluidNetwork.toEnthalpyQFromSpecific(280.0d, secondAmountQ);
        long seedEnthalpyQ = IntegratedFluidNetwork.toEnthalpyQFromSpecific(290.0d, seedAmountQ);

        input.addState(fluid, totalAmountQ, totalEnthalpyQ);
        firstOutput.addState(fluid, seedAmountQ, seedEnthalpyQ);

        input.extractProportional(totalAmountQ, false);
        boolean applied = IFNStateMutationApplier.addTwoOutputsOrRestoreInputs(
            input,
            firstOutput,
            fluid,
            IntegratedFluidNetwork.ExtractedPayload.of(firstAmountQ, firstExtractedEnthalpyQ),
            firstOutputEnthalpyQ,
            input,
            secondOutput,
            fluid,
            IntegratedFluidNetwork.ExtractedPayload.of(secondAmountQ, secondExtractedEnthalpyQ),
            secondOutputEnthalpyQ);

        assertFalse(applied);
        assertEquals(totalAmountQ, input.getAmountQ());
        assertEquals(seedAmountQ, firstOutput.getAmountQ());
        assertEquals(0L, secondOutput.getAmountQ());
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
