package gregtech.api.metatileentity.implementations.integratedfluid.amount;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;

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

    @Test
    void proportionalShareHandlesLargeProductsWithoutIntermediateOverflow() {
        SubstanceAmount source = SubstanceAmount.fromRawUnits(Long.MAX_VALUE - 1L);

        SubstanceAmount share = source.proportionalShare(2L, 3L);

        long expected = BigInteger.valueOf(Long.MAX_VALUE - 1L)
            .multiply(BigInteger.valueOf(2L))
            .divide(BigInteger.valueOf(3L))
            .longValueExact();
        assertEquals(expected, share.rawUnits());
    }

    @Test
    void refLiterFactoryRejectsOverflow() {
        long overflowingRefLiters = Long.MAX_VALUE / SubstanceAmount.SCALE + 1L;

        assertThrows(ArithmeticException.class, () -> SubstanceAmount.fromRefLiters(overflowingRefLiters));
    }

    @Test
    void plusRejectsOverflow() {
        SubstanceAmount almostMax = SubstanceAmount.fromRawUnits(Long.MAX_VALUE);
        SubstanceAmount one = SubstanceAmount.fromRawUnits(1L);

        assertThrows(ArithmeticException.class, () -> almostMax.plus(one));
    }
}
