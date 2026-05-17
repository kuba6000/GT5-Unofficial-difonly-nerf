package gregtech.api.metatileentity.implementations.integratedfluid.thermal;

import java.util.Collection;

import gregtech.api.metatileentity.implementations.integratedfluid.IIntegratedFluidMember;
import gregtech.api.metatileentity.implementations.integratedfluid.IntegratedFluidNetwork;

public final class IFNHeatExchangeRuntime {

    private IFNHeatExchangeRuntime() {}

    public static boolean apply(IntegratedFluidNetwork network, IFNAmbientContext ambientContext) {
        if (network == null || ambientContext == null || network.isPending()) {
            return false;
        }
        network.applyHeatLoss(ambientContext.ambientTemperatureKelvin());
        return true;
    }

    public static double computeEnergyDelta(double currentTemperatureKelvin, double ambientTemperatureKelvin,
        double heatCapacityEuPerKelvin, double conductanceEuPerKelvinPerSecond) {
        if (!Double.isFinite(currentTemperatureKelvin) || !Double.isFinite(ambientTemperatureKelvin)
            || !Double.isFinite(heatCapacityEuPerKelvin) || !Double.isFinite(conductanceEuPerKelvinPerSecond)
            || heatCapacityEuPerKelvin <= 0.0d || conductanceEuPerKelvinPerSecond <= 0.0d) {
            return 0.0d;
        }

        double temperatureDelta = ambientTemperatureKelvin - currentTemperatureKelvin;
        if (Math.abs(temperatureDelta) < 0.000_001d) {
            return 0.0d;
        }

        double unconstrainedEnergyDelta = conductanceEuPerKelvinPerSecond * temperatureDelta;
        double ambientEnergyDelta = heatCapacityEuPerKelvin * temperatureDelta;
        if (Math.abs(unconstrainedEnergyDelta) > Math.abs(ambientEnergyDelta)) {
            return ambientEnergyDelta;
        }
        return unconstrainedEnergyDelta;
    }

    public static double computePassiveConductance(Collection<? extends IIntegratedFluidMember> members) {
        if (members == null || members.isEmpty()) {
            return 0.0d;
        }
        double conductance = 0.0d;
        for (IIntegratedFluidMember member : members) {
            if (member != null) {
                conductance += Math.max(0.0d, member.getPassiveHeatConductanceEuPerKelvinPerSecond());
            }
        }
        return conductance;
    }
}
