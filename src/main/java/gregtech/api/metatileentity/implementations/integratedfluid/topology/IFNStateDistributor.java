package gregtech.api.metatileentity.implementations.integratedfluid.topology;

import java.math.BigInteger;
import java.util.Arrays;

import gregtech.api.metatileentity.implementations.integratedfluid.amount.EnergyAmount;
import gregtech.api.metatileentity.implementations.integratedfluid.amount.SubstanceAmount;
import gregtech.api.metatileentity.implementations.integratedfluid.state.IFNCanonicalState;

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

    public static IFNCanonicalState[] splitStateByWeights(IFNCanonicalState total, long[] weights) {
        SubstanceAmount[] substanceShares = splitSubstanceByWeights(
            total == null ? SubstanceAmount.ZERO : total.substanceAmount(),
            weights);
        EnergyAmount[] energyShares = splitEnergyByWeights(
            total == null ? EnergyAmount.ZERO : total.internalEnergy(),
            weights);
        IFNCanonicalState[] shares = new IFNCanonicalState[substanceShares.length];
        String fluidId = total == null ? null : total.fluidId().orElse(null);
        for (int i = 0; i < shares.length; i++) {
            shares[i] = IFNCanonicalState.of(fluidId, substanceShares[i], energyShares[i]);
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
        BigInteger[] remainders = new BigInteger[weights.length];
        long allocated = 0L;
        for (int i = 0; i < weights.length; i++) {
            if (weights[i] == 0L) {
                shares[i] = 0L;
                remainders[i] = BigInteger.ZERO;
            } else {
                BigInteger weightedTotal = totalValue.multiply(BigInteger.valueOf(weights[i]));
                BigInteger[] quotientAndRemainder = weightedTotal.divideAndRemainder(weightSum);
                shares[i] = quotientAndRemainder[0].longValueExact();
                remainders[i] = quotientAndRemainder[1];
                allocated += shares[i];
            }
        }
        long remainderToAssign = total - allocated;
        if (remainderToAssign <= 0L) {
            return shares;
        }

        Integer[] indexesByRemainder = new Integer[weights.length];
        for (int i = 0; i < indexesByRemainder.length; i++) {
            indexesByRemainder[i] = i;
        }
        Arrays.sort(indexesByRemainder, (left, right) -> {
            int remainderOrder = remainders[right].compareTo(remainders[left]);
            return remainderOrder != 0 ? remainderOrder : Integer.compare(left, right);
        });

        for (int index : indexesByRemainder) {
            if (remainderToAssign == 0L) {
                break;
            }
            if (weights[index] > 0L && remainders[index].signum() > 0) {
                shares[index]++;
                remainderToAssign--;
            }
        }
        return shares;
    }
}
