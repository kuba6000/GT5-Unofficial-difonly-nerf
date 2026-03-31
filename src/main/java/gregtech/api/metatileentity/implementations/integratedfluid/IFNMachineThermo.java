package gregtech.api.metatileentity.implementations.integratedfluid;

import net.minecraftforge.fluids.Fluid;

/**
 * Shared thermodynamic helpers for IFN-powered machines.
 *
 * This layer sits above raw network physics. It helps machines compute target
 * process states without duplicating pressure-aware state-add logic.
 */
public final class IFNMachineThermo {

    private IFNMachineThermo() {}

    public static double computeTargetSpecificEnthalpyForStateAdd(IntegratedFluidNetwork network, Fluid fluid,
        double targetTemperature, long addAmountQ) {
        if (network == null || fluid == null) {
            return 0.0d;
        }

        if (addAmountQ < IntegratedFluidNetwork.AMOUNT_SCALE) {
            float initialPressure = Math.max(1.0e-4f, network.getPressure());
            return FluidThermalProperties.getSpecificEnthalpyFromPT(fluid, initialPressure, targetTemperature);
        }

        float pressureGuess = Math.max(1.0e-4f, network.getPressure());
        double specificEnthalpy = FluidThermalProperties.getSpecificEnthalpyFromPT(fluid, pressureGuess, targetTemperature);

        for (int pass = 0; pass < 4; pass++) {
            long enthalpyQ = toEnthalpyQ(specificEnthalpy, addAmountQ);
            float predictedPressure = network.predictPressureAfterStateAdd(fluid, addAmountQ, enthalpyQ);
            if (Float.isNaN(predictedPressure) || Float.isInfinite(predictedPressure) || predictedPressure <= 0.0f) {
                return specificEnthalpy;
            }

            double correctedSpecificEnthalpy = FluidThermalProperties.getSpecificEnthalpyFromPT(
                fluid,
                predictedPressure,
                targetTemperature
            );
            if (Math.abs(correctedSpecificEnthalpy - specificEnthalpy) < 1.0e-6d) {
                return correctedSpecificEnthalpy;
            }
            specificEnthalpy = correctedSpecificEnthalpy;
        }

        return specificEnthalpy;
    }

    public static long computeHeatPumpEnergyCost(double inputSpecificEnthalpy, double outputSpecificEnthalpy,
        long amountQ, float cop, float efficiencyPenalty) {
        if (amountQ <= 0L) {
            return 0L;
        }

        double desiredDh = Math.abs(outputSpecificEnthalpy - inputSpecificEnthalpy);
        double amount = (double) amountQ / (double) IntegratedFluidNetwork.AMOUNT_SCALE;
        double desiredQ = desiredDh * amount;
        return (long) Math.ceil(desiredQ / cop * efficiencyPenalty);
    }

    public static long scaleEnergyCost(long originalEnergyCost, long originalAmountQ, long actualAmountQ) {
        if (originalEnergyCost <= 0L || originalAmountQ <= 0L || actualAmountQ <= 0L) {
            return 0L;
        }
        double ratio = actualAmountQ / (double) originalAmountQ;
        return (long) Math.ceil(originalEnergyCost * ratio);
    }

    private static long toEnthalpyQ(double energyEu) {
        return (long) Math.round(energyEu * IntegratedFluidNetwork.ENTHALPY_SCALE);
    }

    private static long toEnthalpyQ(double specificEnthalpy, long amountQ) {
        double amount = (double) amountQ / (double) IntegratedFluidNetwork.AMOUNT_SCALE;
        return toEnthalpyQ(specificEnthalpy * amount);
    }
}
