package gregtech.api.metatileentity.implementations.integratedfluid.topology;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import gregtech.api.metatileentity.implementations.integratedfluid.amount.EnergyAmount;
import gregtech.api.metatileentity.implementations.integratedfluid.amount.SubstanceAmount;
import gregtech.api.metatileentity.implementations.integratedfluid.state.IFNCanonicalState;

class IFNStateDistributorTest {

    @Test
    void substanceSplitIsProportionalAndConservesRemainder() {
        SubstanceAmount[] shares = IFNStateDistributor.splitSubstanceByWeights(
            SubstanceAmount.fromRawUnits(10),
            new long[] { 1, 2 }
        );

        assertArrayEquals(new long[] { 3, 7 }, rawSubstance(shares));
        assertEquals(10, shares[0].plus(shares[1]).rawUnits());
    }

    @Test
    void energySplitUsesSameConservingRoundingRule() {
        EnergyAmount[] shares = IFNStateDistributor.splitEnergyByWeights(
            EnergyAmount.fromRawUnits(10),
            new long[] { 1, 2 }
        );

        assertArrayEquals(new long[] { 3, 7 }, rawEnergy(shares));
        assertEquals(10, shares[0].plus(shares[1]).rawUnits());
    }

    @Test
    void zeroWeightReceivesNoShare() {
        SubstanceAmount[] shares = IFNStateDistributor.splitSubstanceByWeights(
            SubstanceAmount.fromRawUnits(10),
            new long[] { 0, 5 }
        );

        assertArrayEquals(new long[] { 0, 10 }, rawSubstance(shares));
    }

    @Test
    void canonicalStateSplitKeepsFluidAndUsesSameConservingRounding() {
        IFNCanonicalState[] shares = IFNStateDistributor.splitStateByWeights(
            IFNCanonicalState.of("water", SubstanceAmount.fromRawUnits(10), EnergyAmount.fromRawUnits(100)),
            new long[] { 1, 2 }
        );

        assertEquals("water", shares[0].fluidId().get());
        assertEquals("water", shares[1].fluidId().get());
        assertArrayEquals(new long[] { 3, 7 }, rawSubstance(shares));
        assertArrayEquals(new long[] { 33, 67 }, rawEnergy(shares));
    }

    private static long[] rawSubstance(SubstanceAmount[] shares) {
        long[] raw = new long[shares.length];
        for (int i = 0; i < shares.length; i++) {
            raw[i] = shares[i].rawUnits();
        }
        return raw;
    }

    private static long[] rawEnergy(EnergyAmount[] shares) {
        long[] raw = new long[shares.length];
        for (int i = 0; i < shares.length; i++) {
            raw[i] = shares[i].rawUnits();
        }
        return raw;
    }

    private static long[] rawSubstance(IFNCanonicalState[] shares) {
        long[] raw = new long[shares.length];
        for (int i = 0; i < shares.length; i++) {
            raw[i] = shares[i].substanceAmount().rawUnits();
        }
        return raw;
    }

    private static long[] rawEnergy(IFNCanonicalState[] shares) {
        long[] raw = new long[shares.length];
        for (int i = 0; i < shares.length; i++) {
            raw[i] = shares[i].internalEnergy().rawUnits();
        }
        return raw;
    }
}
