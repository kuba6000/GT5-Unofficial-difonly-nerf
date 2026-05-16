package gregtech.api.metatileentity.implementations.integratedfluid.topology;

import java.math.BigInteger;

import gregtech.api.metatileentity.implementations.integratedfluid.amount.EnergyAmount;
import gregtech.api.metatileentity.implementations.integratedfluid.amount.SubstanceAmount;

public final class IFNStateDistributor {

    private IFNStateDistributor() {}

    public static SubstanceAmount[] splitSubstanceByWeights(SubstanceAmount total, long[] weights) {
        long[] rawShares = splitRaw(total == null ? 0L : total.rawUnits(), weights);
        SubstanceAmount[] shares = new SubstanceAmount[rawShares.length];
        for (int i = 0; i < rawShares.length; i++) {
            shares[i] = SubstanceAmount.fromRawUnits(rawShares[i]);
        }
        return shares;
    }

    public static EnergyAmount[] splitEnergyByWeights(EnergyAmount total, long[] weights) {
        long[] rawShares = splitRaw(total == null ? 0L : total.rawUnits(), weights);
        EnergyAmount[] shares = new EnergyAmount[rawShares.length];
        for (int i = 0; i < rawShares.length; i++) {
            shares[i] = EnergyAmount.fromRawUnits(rawShares[i]);
        }
        return shares;
    }

    private static long[] splitRaw(long total, long[] weights) {
        if (weights == null) {
            throw new IllegalArgumentException("weights cannot be null");
        }

        long[] shares = new long[weights.length];
        if (total <= 0L || weights.length == 0) {
            return shares;
        }

        BigInteger weightSum = BigInteger.ZERO;
        for (long weight : weights) {
            if (weight < 0L) {
                throw new IllegalArgumentException("weights cannot be negative");
            }
            weightSum = weightSum.add(BigInteger.valueOf(weight));
        }
        if (weightSum.signum() == 0) {
            return shares;
        }

        BigInteger totalValue = BigInteger.valueOf(total);
        for (int i = 0; i < weights.length; i++) {
            if (weights[i] == 0L) {
                shares[i] = 0L;
            } else {
                shares[i] = totalValue.multiply(BigInteger.valueOf(weights[i])).divide(weightSum).longValueExact();
            }
        }
        return shares;
    }
}
