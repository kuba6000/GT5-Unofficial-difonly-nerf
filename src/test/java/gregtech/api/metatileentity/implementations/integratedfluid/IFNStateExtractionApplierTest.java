package gregtech.api.metatileentity.implementations.integratedfluid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import net.minecraftforge.fluids.Fluid;

class IFNStateExtractionApplierTest {

    @Test
    void failedSecondExtractionRestoresFirstInputState() {
        Fluid fluid = IFNTestSupport.liquidFluid();
        IntegratedFluidNetwork firstInput = IFNTestSupport.newNetwork(fluid, 10_000, 0, 100.0f);
        IntegratedFluidNetwork secondInput = emptyExtractingNetwork();
        long firstAmountQ = 5L * IntegratedFluidNetwork.AMOUNT_SCALE;
        long secondAmountQ = 7L * IntegratedFluidNetwork.AMOUNT_SCALE;

        firstInput.addState(
            fluid,
            firstAmountQ,
            IntegratedFluidNetwork.toEnthalpyQFromSpecific(300.0d, firstAmountQ));

        IFNStateExtractionApplier.TwoInputExtraction result = IFNStateExtractionApplier.extractTwoOrRestoreFirst(
            firstInput,
            fluid,
            firstAmountQ,
            secondInput,
            fluid,
            secondAmountQ);

        assertFalse(result.isSuccess());
        assertEquals(firstAmountQ, firstInput.getAmountQ());
        assertEquals(0L, secondInput.getAmountQ());
    }

    private static IntegratedFluidNetwork emptyExtractingNetwork() {
        return new IntegratedFluidNetwork() {

            @Override
            public ExtractedPayload extractProportional(long requestedAmountQ, boolean simulate) {
                return ExtractedPayload.empty();
            }
        };
    }
}
