package gregtech.api.metatileentity.implementations.integratedfluid;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

import gregtech.api.metatileentity.implementations.integratedfluid.fluid.IFNFluidRegistry;

public final class IFNFluidThermalRegistration {

    // IC2 Super Coolant (CO2-like placeholder model)
    private static final String IC2_SUPER_COOLANT_ID = "ic2_super_coolant";
    private static final String IC2_COOLANT_ID = "ic2coolant";
    // Critical point and freeze limit
    private static final double IC2_PC_BAR = 74.0;
    private static final double IC2_TC_K = 304.0;
    private static final double IC2_FREEZE_K = 150.0;
    // Gas constant (J/(mol*K)) and heat capacities (J/(mol*K))
    private static final double IC2_R = 8.314;
    private static final double IC2_CP_L = 90.0;
    private static final double IC2_CP_G = 40.0;
    private static final double IC2_CP_SC = 60.0;
    // Liquid molar specific volume (m^3/mol)
    private static final double IC2_V_L = 5.0e-5;
    // Latent heat at 1 bar (J/mol)
    private static final double IC2_L0 = 15000.0;
    // Tsat(p) curve coefficients in log10(p_bar)
    private static final double IC2_A0 = 195.0;
    private static final double IC2_A1 = 12.9;
    private static final double IC2_A2 = 24.3;
    // Boiling point at 1 bar (K)
    private static final double IC2_T_BOIL_1BAR = 195.0;
    // STP reference for volume factor (mB_real / mB_standard)
    private static final double P_STP_PA = 100000.0;
    private static final double T_STP_K = 273.15;
    // Reference state for IFN: h=0 at 300K, 1 bar
    private static final double IC2_REF_P_BAR = 1.0;
    private static final double IC2_REF_T_K = 300.0;
    private static final double IC2_H_REF = ic2SpecificEnthalpyAbsFromPT(IC2_REF_P_BAR, IC2_REF_T_K);

    private static boolean initialized = false;

    private IFNFluidThermalRegistration() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        IFNFluidRegistry.init();
        try {
            registerIc2SuperCoolant();
            registerIc2Coolant();
        } catch (Throwable ignored) {
            // Unit tests may run without a fully initialized Forge fluid registry.
            // Custom test fluids can still be registered directly in IFNFluidThermalRegistry.
        }
    }

    private static void registerIc2SuperCoolant() {
        Fluid ic2 = FluidRegistry.getFluid(IC2_SUPER_COOLANT_ID);
        if (ic2 == null) {
            return;
        }

        IFNFluidThermalRegistry.register(ic2, builder -> builder
            .setCriticalPressure(IC2_PC_BAR)
            .setCriticalTemperature(IC2_TC_K)
            .setFreezeTemperature(IC2_FREEZE_K)
            .setSpecificHeatCapacity(IC2_CP_L)
            .setTemperatureFromPH(IFNFluidThermalRegistration::ic2TemperatureFromPH)
            .setPhaseFromPH(IFNFluidThermalRegistration::ic2PhaseFromPH)
            .setSpecificVolumeFromPH(IFNFluidThermalRegistration::ic2SpecificVolumeFromPH)
            .setSaturationTemperatureFromP(IFNFluidThermalRegistration::ic2SaturationTemperature)
            .setHfFromP(IFNFluidThermalRegistration::ic2Hf)
            .setHgFromP(IFNFluidThermalRegistration::ic2Hg)
            .setSpecificEnthalpyFromPT(IFNFluidThermalRegistration::ic2SpecificEnthalpyFromPT));
    }

    private static void registerIc2Coolant() {
        Fluid ic2 = FluidRegistry.getFluid(IC2_COOLANT_ID);
        if (ic2 == null) {
            return;
        }

        IFNFluidThermalRegistry.register(ic2, builder -> builder
            .setCriticalPressure(IC2_PC_BAR)
            .setCriticalTemperature(IC2_TC_K)
            .setFreezeTemperature(IC2_FREEZE_K)
            .setSpecificHeatCapacity(IC2_CP_L)
            .setTemperatureFromPH(IFNFluidThermalRegistration::ic2TemperatureFromPH)
            .setPhaseFromPH(IFNFluidThermalRegistration::ic2PhaseFromPH)
            .setSpecificVolumeFromPH(IFNFluidThermalRegistration::ic2SpecificVolumeFromPH)
            .setSaturationTemperatureFromP(IFNFluidThermalRegistration::ic2SaturationTemperature)
            .setHfFromP(IFNFluidThermalRegistration::ic2Hf)
            .setHgFromP(IFNFluidThermalRegistration::ic2Hg)
            .setSpecificEnthalpyFromPT(IFNFluidThermalRegistration::ic2SpecificEnthalpyFromPT));
    }

    private static double ic2SaturationTemperature(double pBar) {
        if (pBar >= IC2_PC_BAR) {
            return IC2_TC_K;
        }
        double u = Math.log10(Math.max(pBar, 1e-6));
        return IC2_A0 + IC2_A1 * u + IC2_A2 * u * u;
    }

    private static double ic2HfAbs(double pBar) {
        double tsat = ic2SaturationTemperature(pBar);
        return IC2_CP_L * tsat;
    }

    private static double ic2HgAbs(double pBar) {
        double tsat = ic2SaturationTemperature(pBar);
        double latent = IC2_L0 * clamp((IC2_TC_K - tsat) / (IC2_TC_K - IC2_T_BOIL_1BAR), 0.0, 1.0);
        return IC2_CP_L * tsat + latent;
    }

    private static double ic2Hf(double pBar) {
        return ic2HfAbs(pBar) - IC2_H_REF;
    }

    private static double ic2Hg(double pBar) {
        return ic2HgAbs(pBar) - IC2_H_REF;
    }

    private static FluidThermalProperties.PhaseResult ic2PhaseFromPH(double pBar, double h) {
        double hAbs = h + IC2_H_REF;
        if (pBar < IC2_PC_BAR) {
            double hf = ic2HfAbs(pBar);
            double hg = ic2HgAbs(pBar);
            if (hAbs < hf) {
                return new FluidThermalProperties.PhaseResult(FluidThermalProperties.Phase.LIQUID, 0.0);
            }
            if (hAbs <= hg) {
                double quality = clamp((hAbs - hf) / (hg - hf), 0.0, 1.0);
                return new FluidThermalProperties.PhaseResult(FluidThermalProperties.Phase.TWO_PHASE, quality);
            }
            return new FluidThermalProperties.PhaseResult(FluidThermalProperties.Phase.VAPOR, 1.0);
        }
        return new FluidThermalProperties.PhaseResult(FluidThermalProperties.Phase.SUPERCRITICAL, 0.0);
    }

    private static double ic2TemperatureFromPH(double pBar, double h) {
        double hAbs = h + IC2_H_REF;
        FluidThermalProperties.PhaseResult result = ic2PhaseFromPH(pBar, h);
        switch (result.phase) {
            case LIQUID:
                return hAbs / IC2_CP_L;
            case TWO_PHASE:
                return ic2SaturationTemperature(pBar);
            case VAPOR:
                return ic2SaturationTemperature(pBar) + (hAbs - ic2HgAbs(pBar)) / IC2_CP_G;
            case SUPERCRITICAL:
            default:
                return IC2_TC_K + (hAbs - IC2_CP_L * IC2_TC_K) / IC2_CP_SC;
        }
    }

    private static double ic2SpecificVolumeFromPH(double pBar, double h) {
        FluidThermalProperties.PhaseResult phase = ic2PhaseFromPH(pBar, h);
        double temperature = ic2TemperatureFromPH(pBar, h);
        double pPa = pBar * 100000.0;
        switch (phase.phase) {
            case LIQUID:
                return 1.0;
            case TWO_PHASE: {
                double tsat = ic2SaturationTemperature(pBar);
                double vGas = gasVolumeFactor(pPa, tsat);
                return (1.0 - phase.quality) + phase.quality * vGas;
            }
            case VAPOR:
                return gasVolumeFactor(pPa, temperature);
            case SUPERCRITICAL:
            default: {
                double dense = clamp(1.0 - Math.abs(temperature - IC2_TC_K) / 30.0
                    - Math.abs(pBar - IC2_PC_BAR) / 30.0, 0.0, 1.0);
                double vGas = gasVolumeFactor(pPa, temperature);
                double vDense = 2.0;
                return (1.0 - dense) * vGas + dense * vDense;
            }
        }
    }

    private static double gasVolumeFactor(double pPa, double temperatureK) {
        return (P_STP_PA / pPa) * (temperatureK / T_STP_K);
    }

    private static double ic2SpecificEnthalpyAbsFromPT(double pBar, double temperatureK) {
        if (pBar < IC2_PC_BAR) {
            double tsat = ic2SaturationTemperature(pBar);
            if (temperatureK < tsat) {
                return IC2_CP_L * temperatureK;
            }
            if (temperatureK > tsat) {
                return ic2HgAbs(pBar) + IC2_CP_G * (temperatureK - tsat);
            }
            return ic2HfAbs(pBar);
        }
        return IC2_CP_L * IC2_TC_K + IC2_CP_SC * (temperatureK - IC2_TC_K);
    }

    private static double ic2SpecificEnthalpyFromPT(double pBar, double temperatureK) {
        return ic2SpecificEnthalpyAbsFromPT(pBar, temperatureK) - IC2_H_REF;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
