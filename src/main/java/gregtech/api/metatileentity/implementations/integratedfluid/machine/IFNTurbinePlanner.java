package gregtech.api.metatileentity.implementations.integratedfluid.machine;

public final class IFNTurbinePlanner {

    private IFNTurbinePlanner() {}

    public static Plan plan(Request request) {
        if (request == null || request.maxFlowLitersPerTick <= 0.0d) {
            return Plan.invalid();
        }

        float pressureDrop = request.inputPressureBar - request.outputPressureBar;
        if (pressureDrop <= 0.0f) {
            return new Plan(Status.BACKPRESSURE_BLOCKED, 0.0d, 0L, 0, false);
        }

        if (request.blade == null) {
            return new Plan(Status.BYPASS, request.maxFlowLitersPerTick, 0L, 0, false);
        }

        if (request.blade.durability <= 0) {
            return new Plan(Status.BYPASS, request.maxFlowLitersPerTick, 0L, 0, false);
        }

        long energyEu = Math.max(0L, Math.round(request.maxFlowLitersPerTick * pressureDrop * request.euPerLiterBar));
        int bladeDamage = computeBladeDamage(request);
        boolean bladeBreaks = bladeDamage >= request.blade.durability;
        return new Plan(Status.READY, request.maxFlowLitersPerTick, energyEu, bladeDamage, bladeBreaks);
    }

    private static int computeBladeDamage(Request request) {
        int damage = 1;
        Blade blade = request.blade;
        if (request.temperatureKelvin < blade.minTemperatureKelvin || request.temperatureKelvin > blade.maxTemperatureKelvin) {
            damage += 2;
        }
        if (request.inputPressureBar < blade.minPressureBar || request.inputPressureBar > blade.maxPressureBar) {
            damage += 2;
        }
        if (request.twoPhaseExpansion) {
            damage += 5;
        }
        return damage;
    }

    public enum Status {
        READY,
        BYPASS,
        BACKPRESSURE_BLOCKED,
        INVALID_REQUEST
    }

    public static final class Blade {

        private final float minTemperatureKelvin;
        private final float maxTemperatureKelvin;
        private final float minPressureBar;
        private final float maxPressureBar;
        private final int durability;

        private Blade(float minTemperatureKelvin, float maxTemperatureKelvin, float minPressureBar,
            float maxPressureBar, int durability) {
            this.minTemperatureKelvin = minTemperatureKelvin;
            this.maxTemperatureKelvin = maxTemperatureKelvin;
            this.minPressureBar = minPressureBar;
            this.maxPressureBar = maxPressureBar;
            this.durability = durability;
        }

        public static Blade of(float minTemperatureKelvin, float maxTemperatureKelvin, float minPressureBar,
            float maxPressureBar, int durability) {
            return new Blade(minTemperatureKelvin, maxTemperatureKelvin, minPressureBar, maxPressureBar, durability);
        }

        public int durability() {
            return durability;
        }
    }

    public static final class Request {

        private final float inputPressureBar;
        private final float outputPressureBar;
        private final float temperatureKelvin;
        private final double maxFlowLitersPerTick;
        private final double euPerLiterBar;
        private final Blade blade;
        private final boolean twoPhaseExpansion;

        private Request(float inputPressureBar, float outputPressureBar, float temperatureKelvin,
            double maxFlowLitersPerTick, double euPerLiterBar, Blade blade, boolean twoPhaseExpansion) {
            this.inputPressureBar = inputPressureBar;
            this.outputPressureBar = outputPressureBar;
            this.temperatureKelvin = temperatureKelvin;
            this.maxFlowLitersPerTick = maxFlowLitersPerTick;
            this.euPerLiterBar = euPerLiterBar;
            this.blade = blade;
            this.twoPhaseExpansion = twoPhaseExpansion;
        }

        public static Request withBlade(float inputPressureBar, float outputPressureBar, float temperatureKelvin,
            double maxFlowLitersPerTick, double euPerLiterBar, Blade blade, boolean twoPhaseExpansion) {
            return new Request(
                inputPressureBar,
                outputPressureBar,
                temperatureKelvin,
                maxFlowLitersPerTick,
                euPerLiterBar,
                blade,
                twoPhaseExpansion);
        }

        public static Request noBlade(float inputPressureBar, float outputPressureBar, double maxFlowLitersPerTick) {
            return new Request(inputPressureBar, outputPressureBar, 0.0f, maxFlowLitersPerTick, 0.0d, null, false);
        }
    }

    public static final class Plan {

        private final Status status;
        private final double flowLitersPerTick;
        private final long energyEu;
        private final int bladeDamage;
        private final boolean bladeBreaks;

        private Plan(Status status, double flowLitersPerTick, long energyEu, int bladeDamage, boolean bladeBreaks) {
            this.status = status;
            this.flowLitersPerTick = flowLitersPerTick;
            this.energyEu = energyEu;
            this.bladeDamage = bladeDamage;
            this.bladeBreaks = bladeBreaks;
        }

        private static Plan invalid() {
            return new Plan(Status.INVALID_REQUEST, 0.0d, 0L, 0, false);
        }

        public Status status() {
            return status;
        }

        public double flowLitersPerTick() {
            return flowLitersPerTick;
        }

        public long energyEu() {
            return energyEu;
        }

        public int bladeDamage() {
            return bladeDamage;
        }

        public boolean bladeBreaks() {
            return bladeBreaks;
        }
    }
}
