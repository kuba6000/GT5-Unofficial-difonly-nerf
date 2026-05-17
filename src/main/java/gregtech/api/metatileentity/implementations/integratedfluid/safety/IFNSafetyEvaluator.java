package gregtech.api.metatileentity.implementations.integratedfluid.safety;

import gregtech.api.metatileentity.implementations.integratedfluid.IntegratedFluidNetwork;

public final class IFNSafetyEvaluator {

    private IFNSafetyEvaluator() {}

    public static IFNOperationalSafetyEvaluation evaluate(IntegratedFluidNetwork network) {
        if (network == null) {
            return new IFNOperationalSafetyEvaluation(
                IFNPressureLimitEvaluation.evaluate(0.0d, IFNOperationalLimits.ofAccumulatorMaxPressureBar(Float.MAX_VALUE),
                    true),
                IFNTemperatureLimitEvaluation.evaluate(0.0d, Float.POSITIVE_INFINITY, true),
                false,
                false);
        }
        return new IFNOperationalSafetyEvaluation(
            network.getPressureLimitEvaluation(),
            network.getTemperatureLimitEvaluation(),
            false,
            false);
    }
}
