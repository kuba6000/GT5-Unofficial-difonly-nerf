package gregtech.api.metatileentity.implementations.integratedfluid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import gregtech.api.metatileentity.implementations.integratedfluid.amount.EnergyAmount;
import gregtech.api.metatileentity.implementations.integratedfluid.amount.SubstanceAmount;
import gregtech.api.metatileentity.implementations.integratedfluid.state.IFNCanonicalState;
import gregtech.api.metatileentity.implementations.integratedfluid.topology.IFNStateDistributor;

class IFNTopologyDistributionTest {

    @Test
    void splitNeverCreatesExtraSubstanceOrEnergy() {
        IFNCanonicalState source = IFNCanonicalState.of(
            "water",
            SubstanceAmount.fromRawUnits(10),
            EnergyAmount.fromRawUnits(100));

        IFNCanonicalState[] shares = IFNStateDistributor.splitStateByWeights(source, new long[] { 1, 2 });

        assertEquals("water", shares[0].fluidId().get());
        assertEquals("water", shares[1].fluidId().get());

        long splitSubstance = shares[0].substanceAmount().plus(shares[1].substanceAmount()).rawUnits();
        long splitEnergy = shares[0].internalEnergy().plus(shares[1].internalEnergy()).rawUnits();

        assertEquals(9L, splitSubstance);
        assertEquals(99L, splitEnergy);
        assertTrue(splitSubstance <= source.substanceAmount().rawUnits());
        assertTrue(splitEnergy <= source.internalEnergy().rawUnits());
    }
}
