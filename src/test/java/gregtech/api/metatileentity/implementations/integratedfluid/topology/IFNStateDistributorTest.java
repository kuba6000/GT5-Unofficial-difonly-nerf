package gregtech.api.metatileentity.implementations.integratedfluid.topology;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import gregtech.api.metatileentity.implementations.integratedfluid.amount.EnergyAmount;
import gregtech.api.metatileentity.implementations.integratedfluid.amount.SubstanceAmount;

class IFNStateDistributorTest {

    @Test
    void substanceSplitIsProportionalAndLossy() {
        SubstanceAmount[] shares = IFNStateDistributor.splitSubstanceByWeights(
            SubstanceAmount.fromRawUnits(10),
            new long[] { 1, 2 }
        );

        assertArrayEquals(new long[] { 3, 6 }, rawSubstance(shares));
        assertEquals(9, shares[0].plus(shares[1]).rawUnits());
    }

    @Test
    void energySplitUsesSameLossyRoundingRule() {
        EnergyAmount[] shares = IFNStateDistributor.splitEnergyByWeights(
            EnergyAmount.fromRawUnits(10),
            new long[] { 1, 2 }
        );

        assertArrayEquals(new long[] { 3, 6 }, rawEnergy(shares));
        assertEquals(9, shares[0].plus(shares[1]).rawUnits());
    }

    @Test
    void zeroWeightReceivesNoShare() {
        SubstanceAmount[] shares = IFNStateDistributor.splitSubstanceByWeights(
            SubstanceAmount.fromRawUnits(10),
            new long[] { 0, 5 }
        );

        assertArrayEquals(new long[] { 0, 10 }, rawSubstance(shares));
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
}
