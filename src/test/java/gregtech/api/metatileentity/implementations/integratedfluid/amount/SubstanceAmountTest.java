package gregtech.api.metatileentity.implementations.integratedfluid.amount;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SubstanceAmountTest {

    @Test
    void oneGtLiterConvertsToOneReferenceLiter() {
        SubstanceAmount amount = SubstanceAmount.fromRefLiters(1);

        assertEquals(1, amount.toWholeRefLiters());
        assertEquals(SubstanceAmount.SCALE, amount.rawUnits());
    }

    @Test
    void splitByVolumeNeverCreatesSubstance() {
        SubstanceAmount source = SubstanceAmount.fromRawUnits(10);

        SubstanceAmount first = source.proportionalShare(1, 3);
        SubstanceAmount second = source.proportionalShare(2, 3);

        assertEquals(3, first.rawUnits());
        assertEquals(6, second.rawUnits());
        assertEquals(9, first.plus(second).rawUnits());
    }
}
