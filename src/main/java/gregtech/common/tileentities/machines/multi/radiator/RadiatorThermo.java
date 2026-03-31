package gregtech.common.tileentities.machines.multi.radiator;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import gregtech.api.metatileentity.implementations.integratedfluid.FluidThermalProperties;

public final class RadiatorThermo {

    public static final float AMBIENT_TEMPERATURE = 300.0f;
    public static final float MIN_TARGET_DISTANCE_FROM_AMBIENT = 0.5f;
    public static final float PRESSURE_DROP_PER_SEGMENT_BAR = 0.01f;
    private static final double CONDUCTION_FIN_BONUS_FACTOR = 0.35d;

    private RadiatorThermo() {}

    public static boolean movesTowardAmbient(double inputTemperature, double ambientTemperature,
        double targetTemperature) {
        double inputDelta = inputTemperature - ambientTemperature;
        double targetDelta = targetTemperature - ambientTemperature;
        if (Math.abs(inputDelta) < 1.0e-6d) {
            return Math.abs(targetDelta) < MIN_TARGET_DISTANCE_FROM_AMBIENT;
        }
        return Math.signum(inputDelta) == Math.signum(targetDelta) && Math.abs(targetDelta) < Math.abs(inputDelta);
    }

    public static double computeEffectiveConductance(RadiatorLoopSnapshot snapshot, double meanTemperatureK) {
        if (snapshot == null || !snapshot.valid) {
            return 0.0d;
        }

        double total = 0.0d;
        for (RadiatorLoopSnapshot.ThermalBranch branch : snapshot.surfaceBranches) {
            total += computeBranchConductance(branch, meanTemperatureK);
        }
        return total;
    }

    public static double computeOutputTemperatureForFixedTime(RadiatorLoopSnapshot snapshot, FluidStack fluidStack,
        double inputTemperature, double ambientTemperature, int processTicks) {
        if (fluidStack == null) {
            return inputTemperature;
        }
        return computeOutputTemperatureForFixedTime(
            snapshot,
            fluidStack.getFluid(),
            fluidStack.amount,
            inputTemperature,
            ambientTemperature,
            processTicks
        );
    }

    public static double computeOutputTemperatureForFixedTime(RadiatorLoopSnapshot snapshot, Fluid fluid, int amount,
        double inputTemperature, double ambientTemperature, int processTicks) {
        if (fluid == null || amount <= 0) {
            return inputTemperature;
        }

        double heatCapacity = FluidThermalProperties.getSpecificHeatCapacity(fluid) * amount;
        if (heatCapacity <= 0.0d || processTicks <= 0) {
            return inputTemperature;
        }

        double meanTemperature = 0.5d * (inputTemperature + ambientTemperature);
        double conductance = computeEffectiveConductance(snapshot, meanTemperature);
        if (conductance <= 0.0d) {
            return inputTemperature;
        }

        double factor = Math.exp(-(conductance * processTicks) / heatCapacity);
        return ambientTemperature + (inputTemperature - ambientTemperature) * factor;
    }

    public static int computeRequiredProcessTicks(RadiatorLoopSnapshot snapshot, FluidStack fluidStack,
        double inputTemperature, double ambientTemperature, double targetTemperature) {
        if (fluidStack == null) {
            return 0;
        }
        return computeRequiredProcessTicks(
            snapshot,
            fluidStack.getFluid(),
            fluidStack.amount,
            inputTemperature,
            ambientTemperature,
            targetTemperature
        );
    }

    public static int computeRequiredProcessTicks(RadiatorLoopSnapshot snapshot, Fluid fluid, int amount,
        double inputTemperature, double ambientTemperature, double targetTemperature) {
        if (fluid == null || amount <= 0) {
            return 0;
        }

        double inputDelta = Math.abs(inputTemperature - ambientTemperature);
        double targetDelta = Math.abs(targetTemperature - ambientTemperature);
        if (inputDelta < MIN_TARGET_DISTANCE_FROM_AMBIENT) {
            return 1;
        }
        if (targetDelta < MIN_TARGET_DISTANCE_FROM_AMBIENT) {
            targetDelta = MIN_TARGET_DISTANCE_FROM_AMBIENT;
        }

        double heatCapacity = FluidThermalProperties.getSpecificHeatCapacity(fluid) * amount;
        if (heatCapacity <= 0.0d) {
            return 0;
        }

        double meanTemperature = 0.5d * (targetTemperature + ambientTemperature);
        double conductance = computeEffectiveConductance(snapshot, meanTemperature);
        if (conductance <= 0.0d) {
            return 0;
        }

        double exactTicks = (heatCapacity / conductance) * Math.log(inputDelta / targetDelta);
        if (Double.isNaN(exactTicks) || Double.isInfinite(exactTicks) || exactTicks < 0.0d) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(exactTicks));
    }

    public static float computeLoopOutletPressure(float sourcePressureBar, RadiatorLoopSnapshot snapshot) {
        if (snapshot == null || !snapshot.valid) {
            return sourcePressureBar;
        }
        return Math.max(0.01f, sourcePressureBar - snapshot.pressureDropBar);
    }

    private static double computeBranchConductance(RadiatorLoopSnapshot.ThermalBranch branch, double meanTemperatureK) {
        if (branch == null) {
            return 0.0d;
        }

        if (branch.exchangerLeaf) {
            return branch.conductiveCoefficient + branch.radiativeCoefficient * Math.pow(meanTemperatureK, 3.0d);
        }

        double childConductance = 0.0d;
        for (RadiatorLoopSnapshot.ThermalBranch child : branch.children) {
            childConductance += computeBranchConductance(child, meanTemperatureK);
        }
        if (childConductance <= 0.0d || branch.conductiveCoefficient <= 0.0d) {
            return 0.0d;
        }

        // Conduction modules act like fin extenders: they may add diminishing-return transfer
        // capability, but should never reduce the effectiveness of the exchanger subtree they carry.
        double transmittedConductance = (branch.conductiveCoefficient * childConductance)
            / (branch.conductiveCoefficient + childConductance);
        return childConductance + transmittedConductance * CONDUCTION_FIN_BONUS_FACTOR;
    }
}
