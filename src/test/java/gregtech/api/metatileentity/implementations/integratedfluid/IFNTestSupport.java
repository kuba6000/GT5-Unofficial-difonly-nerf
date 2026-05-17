package gregtech.api.metatileentity.implementations.integratedfluid;

import java.util.UUID;

import net.minecraftforge.fluids.Fluid;

import gregtech.api.metatileentity.implementations.integratedfluid.fluid.IFNFluidDefinition;
import gregtech.api.metatileentity.implementations.integratedfluid.fluid.IFNFluidRegistry;

public final class IFNTestSupport {

    private static final String LIQUID_ID = "ifn_test_liquid";
    private static final String VAPOR_ID = "ifn_test_vapor";
    private static final String PRESSURE_SENSITIVE_LIQUID_ID = "ifn_test_pressure_sensitive_liquid";
    private static final String GAME_LIKE_COOLANT_ID = "ifn_test_game_like_coolant";

    private static final double IC2_PC_BAR = 74.0d;
    private static final double IC2_TC_K = 304.0d;
    private static final double IC2_FREEZE_K = 150.0d;
    private static final double IC2_CP_L = 90.0d;
    private static final double IC2_CP_G = 40.0d;
    private static final double IC2_CP_SC = 60.0d;
    private static final double IC2_L0 = 15000.0d;
    private static final double IC2_A0 = 195.0d;
    private static final double IC2_A1 = 12.9d;
    private static final double IC2_A2 = 24.3d;
    private static final double IC2_T_BOIL_1BAR = 195.0d;
    private static final double P_STP_PA = 100000.0d;
    private static final double T_STP_K = 273.15d;
    private static final double IC2_REF_P_BAR = 1.0d;
    private static final double IC2_REF_T_K = 300.0d;
    private static final double IC2_H_REF = ic2SpecificEnthalpyAbsFromPT(IC2_REF_P_BAR, IC2_REF_T_K);

    private static boolean initialized;
    private static Fluid liquidFluid;
    private static Fluid vaporFluid;
    private static Fluid pressureSensitiveLiquidFluid;
    private static Fluid gameLikeCoolantFluid;

    private IFNTestSupport() {}

    public static synchronized Fluid liquidFluid() {
        ensureInitialized();
        return liquidFluid;
    }

    public static synchronized Fluid vaporFluid() {
        ensureInitialized();
        return vaporFluid;
    }

    public static synchronized Fluid pressureSensitiveLiquidFluid() {
        ensureInitialized();
        return pressureSensitiveLiquidFluid;
    }

    public static synchronized Fluid gameLikeCoolantFluid() {
        ensureInitialized();
        return gameLikeCoolantFluid;
    }

    public static synchronized Fluid waterFluid() {
        IFNFluidRegistry.init();
        return new Fluid("water");
    }

    public static IntegratedFluidNetwork newNetwork(Fluid fluid, int baseCapacity, int accumulatorCapacity,
        float maxPressureBar) {
        IntegratedFluidNetwork network = new TestNetwork(UUID.randomUUID(), fluid);
        network.addMember(new TestMember(baseCapacity, accumulatorCapacity, maxPressureBar));
        return network;
    }

    public static SeededState seedNetworkAtPressureAndTemperature(IntegratedFluidNetwork network, Fluid fluid,
        double targetPressureBar, double temperatureK) {
        if (network == null || fluid == null) {
            return new SeededState(0L, IntegratedFluidNetwork.DEFAULT_PRESSURE, IntegratedFluidNetwork.DEFAULT_TEMPERATURE);
        }

        double clampedTargetPressure = IFNPressurePolicy.clampMinimum(targetPressureBar);
        double specificEnthalpy = FluidThermalProperties.getSpecificEnthalpyFromPT(fluid, clampedTargetPressure, temperatureK);
        long amountQ = findAmountForPressure(network, fluid, specificEnthalpy, clampedTargetPressure);

        for (int pass = 0; pass < 4; pass++) {
            network.clearFluid();
            if (amountQ >= IntegratedFluidNetwork.AMOUNT_SCALE) {
                network.addState(fluid, amountQ, IntegratedFluidNetwork.toEnthalpyQFromSpecific(specificEnthalpy, amountQ));
            }

            float actualPressure = network.getPressure();
            double actualTemperature = getNetworkTemperature(network, fluid);
            if (Math.abs(actualPressure - clampedTargetPressure) <= Math.max(1.0e-3d, clampedTargetPressure * 0.02d)) {
                return new SeededState(amountQ, actualPressure, actualTemperature);
            }

            specificEnthalpy = FluidThermalProperties.getSpecificEnthalpyFromPT(
                fluid,
                IFNPressurePolicy.clampMinimum(actualPressure),
                temperatureK
            );
            long refinedAmountQ = findAmountForPressure(network, fluid, specificEnthalpy, clampedTargetPressure);
            if (refinedAmountQ == amountQ) {
                return new SeededState(amountQ, actualPressure, actualTemperature);
            }
            amountQ = refinedAmountQ;
        }

        return new SeededState(amountQ, network.getPressure(), getNetworkTemperature(network, fluid));
    }

    private static void ensureInitialized() {
        if (initialized) {
            return;
        }

        liquidFluid = new Fluid(LIQUID_ID);
        vaporFluid = new Fluid(VAPOR_ID);
        pressureSensitiveLiquidFluid = new Fluid(PRESSURE_SENSITIVE_LIQUID_ID);
        gameLikeCoolantFluid = new Fluid(GAME_LIKE_COOLANT_ID);

        registerTestFluidMetadata(LIQUID_ID, LIQUID_ID, "IFN Test Liquid", 1.0d, 1.0d);
        registerTestFluidMetadata(VAPOR_ID, VAPOR_ID, "IFN Test Vapor", 1.0d, 10.0d);
        registerTestFluidMetadata(PRESSURE_SENSITIVE_LIQUID_ID, PRESSURE_SENSITIVE_LIQUID_ID,
            "IFN Test Pressure Sensitive Liquid", 1.0d, 1.0d);
        registerTestFluidMetadata(GAME_LIKE_COOLANT_ID, GAME_LIKE_COOLANT_ID,
            "IFN Test Game Like Coolant", 1.0d, 1.0d);

        IFNFluidThermalRegistry.register(liquidFluid, builder -> builder
            .setCriticalPressure(100.0d)
            .setCriticalTemperature(1000.0d)
            .setFreezeTemperature(100.0d)
            .setSpecificHeatCapacity(1.0d)
            .setTemperatureFromPH((pBar, h) -> h)
            .setPhaseFromPH((pBar, h) -> new FluidThermalProperties.PhaseResult(FluidThermalProperties.Phase.LIQUID, 0.0d))
            .setSpecificVolumeFromPH((pBar, h) -> 1.0d)
            .setSaturationTemperatureFromP(pBar -> 500.0d)
            .setHfFromP(pBar -> 500.0d)
            .setHgFromP(pBar -> 600.0d)
            .setSpecificEnthalpyFromPT((pBar, temperatureK) -> temperatureK));

        IFNFluidThermalRegistry.register(vaporFluid, builder -> builder
            .setCriticalPressure(100.0d)
            .setCriticalTemperature(1000.0d)
            .setFreezeTemperature(1.0d)
            .setSpecificHeatCapacity(1.0d)
            .setTemperatureFromPH((pBar, h) -> h)
            .setPhaseFromPH((pBar, h) -> new FluidThermalProperties.PhaseResult(FluidThermalProperties.Phase.VAPOR, 1.0d))
            .setSpecificVolumeFromPH((pBar, h) -> 10.0d)
            .setSaturationTemperatureFromP(pBar -> 100.0d)
            .setHfFromP(pBar -> 0.0d)
            .setHgFromP(pBar -> 1.0d)
            .setSpecificEnthalpyFromPT((pBar, temperatureK) -> temperatureK));

        IFNFluidThermalRegistry.register(pressureSensitiveLiquidFluid, builder -> builder
            .setCriticalPressure(100.0d)
            .setCriticalTemperature(1000.0d)
            .setFreezeTemperature(100.0d)
            .setSpecificHeatCapacity(1.0d)
            .setTemperatureFromPH((pBar, h) -> h - pBar)
            .setPhaseFromPH((pBar, h) -> new FluidThermalProperties.PhaseResult(FluidThermalProperties.Phase.LIQUID, 0.0d))
            .setSpecificVolumeFromPH((pBar, h) -> 1.0d)
            .setSaturationTemperatureFromP(pBar -> 500.0d)
            .setHfFromP(pBar -> 500.0d)
            .setHgFromP(pBar -> 600.0d)
            .setSpecificEnthalpyFromPT((pBar, temperatureK) -> temperatureK + pBar));

        IFNFluidThermalRegistry.register(gameLikeCoolantFluid, builder -> builder
            .setCriticalPressure(IC2_PC_BAR)
            .setCriticalTemperature(IC2_TC_K)
            .setFreezeTemperature(IC2_FREEZE_K)
            .setSpecificHeatCapacity(IC2_CP_L)
            .setTemperatureFromPH(IFNTestSupport::ic2TemperatureFromPH)
            .setPhaseFromPH(IFNTestSupport::ic2PhaseFromPH)
            .setSpecificVolumeFromPH(IFNTestSupport::ic2SpecificVolumeFromPH)
            .setSaturationTemperatureFromP(IFNTestSupport::ic2SaturationTemperature)
            .setHfFromP(IFNTestSupport::ic2Hf)
            .setHgFromP(IFNTestSupport::ic2Hg)
            .setSpecificEnthalpyFromPT(IFNTestSupport::ic2SpecificEnthalpyFromPT));

        initialized = true;
    }

    private static void registerTestFluidMetadata(String fluidId, String substanceId, String displayName,
        double liquidSpecificVolume, double vaporSpecificVolumeAtStp) {
        IFNFluidRegistry.registerMetadata(IFNFluidDefinition.builder(fluidId, substanceId)
            .displayName(displayName)
            .criticalPoint(100.0d, 1000.0d)
            .freezeTemperature(1.0d)
            .normalBoilingTemperature(500.0d)
            .saturationSlope(100.0d)
            .specificHeat(1.0d, 1.0d)
            .latentHeat(100.0d)
            .specificVolumes(liquidSpecificVolume, vaporSpecificVolumeAtStp)
            .build());
    }

    private static double ic2SaturationTemperature(double pBar) {
        if (pBar >= IC2_PC_BAR) {
            return IC2_TC_K;
        }
        double u = Math.log10(Math.max(pBar, 1e-6d));
        return IC2_A0 + IC2_A1 * u + IC2_A2 * u * u;
    }

    private static double ic2HfAbs(double pBar) {
        double tsat = ic2SaturationTemperature(pBar);
        return IC2_CP_L * tsat;
    }

    private static double ic2HgAbs(double pBar) {
        double tsat = ic2SaturationTemperature(pBar);
        double latent = IC2_L0 * clamp((IC2_TC_K - tsat) / (IC2_TC_K - IC2_T_BOIL_1BAR), 0.0d, 1.0d);
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
                return new FluidThermalProperties.PhaseResult(FluidThermalProperties.Phase.LIQUID, 0.0d);
            }
            if (hAbs <= hg) {
                double quality = clamp((hAbs - hf) / (hg - hf), 0.0d, 1.0d);
                return new FluidThermalProperties.PhaseResult(FluidThermalProperties.Phase.TWO_PHASE, quality);
            }
            return new FluidThermalProperties.PhaseResult(FluidThermalProperties.Phase.VAPOR, 1.0d);
        }
        return new FluidThermalProperties.PhaseResult(FluidThermalProperties.Phase.SUPERCRITICAL, 0.0d);
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
        double pPa = pBar * 100000.0d;
        switch (phase.phase) {
            case LIQUID:
                return 1.0d;
            case TWO_PHASE:
                double tsat = ic2SaturationTemperature(pBar);
                double vGas = gasVolumeFactor(pPa, tsat);
                return (1.0d - phase.quality) + phase.quality * vGas;
            case VAPOR:
                return gasVolumeFactor(pPa, temperature);
            case SUPERCRITICAL:
            default:
                double dense = clamp(
                    1.0d - Math.abs(temperature - IC2_TC_K) / 30.0d - Math.abs(pBar - IC2_PC_BAR) / 30.0d,
                    0.0d,
                    1.0d
                );
                double vGasSc = gasVolumeFactor(pPa, temperature);
                double vDense = 2.0d;
                return (1.0d - dense) * vGasSc + dense * vDense;
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

    private static long findAmountForPressure(IntegratedFluidNetwork network, Fluid fluid, double specificEnthalpy,
        double targetPressureBar) {
        network.clearFluid();

        long low = IntegratedFluidNetwork.AMOUNT_SCALE;
        long high = IntegratedFluidNetwork.AMOUNT_SCALE;
        float highPressure = predictPressureForAmount(network, fluid, specificEnthalpy, high);

        for (int i = 0; i < 40 && highPressure < targetPressureBar; i++) {
            low = high;
            high = Math.multiplyExact(high, 2L);
            highPressure = predictPressureForAmount(network, fluid, specificEnthalpy, high);
            if (Float.isNaN(highPressure) || Float.isInfinite(highPressure)) {
                break;
            }
        }

        long best = high;
        long searchLow = IntegratedFluidNetwork.AMOUNT_SCALE;
        long searchHigh = high;
        for (int i = 0; i < 45; i++) {
            if (searchLow > searchHigh) {
                break;
            }

            long mid = (searchLow + searchHigh) / 2L;
            if (mid < IntegratedFluidNetwork.AMOUNT_SCALE) {
                mid = IntegratedFluidNetwork.AMOUNT_SCALE;
            }
            float midPressure = predictPressureForAmount(network, fluid, specificEnthalpy, mid);
            if (midPressure >= targetPressureBar) {
                best = mid;
                searchHigh = mid - 1L;
            } else {
                searchLow = mid + 1L;
            }
        }
        return best;
    }

    private static float predictPressureForAmount(IntegratedFluidNetwork network, Fluid fluid, double specificEnthalpy,
        long amountQ) {
        long enthalpyQ = IntegratedFluidNetwork.toEnthalpyQFromSpecific(specificEnthalpy, amountQ);
        return network.predictPressureAfterStateAdd(fluid, amountQ, enthalpyQ);
    }

    private static double getNetworkTemperature(IntegratedFluidNetwork network, Fluid fluid) {
        if (network.getAmountQ() <= 0L) {
            return IntegratedFluidNetwork.DEFAULT_TEMPERATURE;
        }
        return FluidThermalProperties.getTemperatureFromPH(fluid, network.getPressure(), network.getSpecificEnthalpy());
    }

    public static final class SeededState {
        public final long amountQ;
        public final float pressureBar;
        public final double temperatureK;

        private SeededState(long amountQ, float pressureBar, double temperatureK) {
            this.amountQ = amountQ;
            this.pressureBar = pressureBar;
            this.temperatureK = temperatureK;
        }
    }

    private static final class TestMember implements IIntegratedFluidMember {
        private final int baseCapacity;
        private final int accumulatorCapacity;
        private final float maxPressureBar;
        private IntegratedFluidNetwork network;
        private UUID networkId;

        private TestMember(int baseCapacity, int accumulatorCapacity, float maxPressureBar) {
            this.baseCapacity = baseCapacity;
            this.accumulatorCapacity = accumulatorCapacity;
            this.maxPressureBar = maxPressureBar;
        }

        @Override
        public IntegratedFluidNetwork getNetwork() {
            return network;
        }

        @Override
        public void setNetwork(IntegratedFluidNetwork network) {
            this.network = network;
        }

        @Override
        public UUID getNetworkId() {
            return networkId;
        }

        @Override
        public void setNetworkId(UUID id) {
            this.networkId = id;
        }

        @Override
        public void onNetworkUpdate() {}

        @Override
        public int getCapacityContribution() {
            return baseCapacity;
        }

        @Override
        public int getAccumulatorContribution() {
            return accumulatorCapacity;
        }

        @Override
        public float getAccumulatorMaxPressureBar() {
            return maxPressureBar;
        }
    }

    private static final class TestNetwork extends IntegratedFluidNetwork {
        private final Fluid fluid;

        private TestNetwork(UUID networkId, Fluid fluid) {
            super(networkId);
            this.fluid = fluid;
        }

        @Override
        public Fluid getFluid() {
            return getAmountQ() > 0L ? fluid : null;
        }
    }
}
