package gregtech.api.metatileentity.implementations.integratedfluid.machine;

public final class IFNPumpPlanner {

    private IFNPumpPlanner() {}

    public static Plan plan(Request request) {
        if (request == null || request.maxPressureDifferentialBar <= 0.0f || request.maxFlowLitersPerTick <= 0.0d) {
            return Plan.invalid();
        }

        float machineOutputPressure = computeMachineOutputPressure(request);
        if (machineOutputPressure <= request.outputPressureBar) {
            return new Plan(Status.BACKPRESSURE_BLOCKED, machineOutputPressure, 0.0d);
        }

        double pressureFraction = Math.min(
            1.0d,
            Math.max(
                0.0d,
                (machineOutputPressure - request.outputPressureBar) / (double) request.maxPressureDifferentialBar));
        double flow = request.maxFlowLitersPerTick * pressureFraction;
        if (request.mode == Mode.TARGET_FLOW) {
            flow = Math.min(flow, request.targetFlowLitersPerTick);
        }
        if (flow <= 0.0d) {
            return new Plan(Status.BACKPRESSURE_BLOCKED, machineOutputPressure, 0.0d);
        }
        return new Plan(Status.READY, machineOutputPressure, flow);
    }

    private static float computeMachineOutputPressure(Request request) {
        float unclampedTarget;
        switch (request.mode) {
            case TARGET_OUTPUT_PRESSURE:
                unclampedTarget = request.targetOutputPressureBar;
                break;
            case TARGET_PRESSURE_DIFFERENTIAL:
                unclampedTarget = request.inputPressureBar + request.targetPressureDifferentialBar;
                break;
            case TARGET_FLOW:
                unclampedTarget = request.inputPressureBar + request.maxPressureDifferentialBar;
                break;
            default:
                return request.inputPressureBar;
        }
        float maxReachablePressure = request.inputPressureBar + request.maxPressureDifferentialBar;
        return Math.min(unclampedTarget, maxReachablePressure);
    }

    public enum Mode {
        TARGET_OUTPUT_PRESSURE,
        TARGET_PRESSURE_DIFFERENTIAL,
        TARGET_FLOW
    }

    public enum Status {
        READY,
        BACKPRESSURE_BLOCKED,
        INVALID_REQUEST
    }

    public static final class Request {

        private final Mode mode;
        private final float inputPressureBar;
        private final float outputPressureBar;
        private final float targetOutputPressureBar;
        private final float targetPressureDifferentialBar;
        private final double targetFlowLitersPerTick;
        private final float maxPressureDifferentialBar;
        private final double maxFlowLitersPerTick;

        private Request(Mode mode, float inputPressureBar, float outputPressureBar, float targetOutputPressureBar,
            float targetPressureDifferentialBar, double targetFlowLitersPerTick, float maxPressureDifferentialBar,
            double maxFlowLitersPerTick) {
            this.mode = mode;
            this.inputPressureBar = inputPressureBar;
            this.outputPressureBar = outputPressureBar;
            this.targetOutputPressureBar = targetOutputPressureBar;
            this.targetPressureDifferentialBar = targetPressureDifferentialBar;
            this.targetFlowLitersPerTick = targetFlowLitersPerTick;
            this.maxPressureDifferentialBar = maxPressureDifferentialBar;
            this.maxFlowLitersPerTick = maxFlowLitersPerTick;
        }

        public static Request targetOutputPressure(float inputPressureBar, float outputPressureBar,
            float targetOutputPressureBar, float maxPressureDifferentialBar, double maxFlowLitersPerTick) {
            return new Request(
                Mode.TARGET_OUTPUT_PRESSURE,
                inputPressureBar,
                outputPressureBar,
                targetOutputPressureBar,
                0.0f,
                0.0d,
                maxPressureDifferentialBar,
                maxFlowLitersPerTick);
        }

        public static Request targetPressureDifferential(float inputPressureBar, float outputPressureBar,
            float targetPressureDifferentialBar, float maxPressureDifferentialBar, double maxFlowLitersPerTick) {
            return new Request(
                Mode.TARGET_PRESSURE_DIFFERENTIAL,
                inputPressureBar,
                outputPressureBar,
                0.0f,
                targetPressureDifferentialBar,
                0.0d,
                maxPressureDifferentialBar,
                maxFlowLitersPerTick);
        }

        public static Request targetFlow(float inputPressureBar, float outputPressureBar, double targetFlowLitersPerTick,
            float maxPressureDifferentialBar, double maxFlowLitersPerTick) {
            return new Request(
                Mode.TARGET_FLOW,
                inputPressureBar,
                outputPressureBar,
                0.0f,
                0.0f,
                targetFlowLitersPerTick,
                maxPressureDifferentialBar,
                maxFlowLitersPerTick);
        }
    }

    public static final class Plan {

        private final Status status;
        private final float machineOutputPressureBar;
        private final double flowLitersPerTick;

        private Plan(Status status, float machineOutputPressureBar, double flowLitersPerTick) {
            this.status = status;
            this.machineOutputPressureBar = machineOutputPressureBar;
            this.flowLitersPerTick = flowLitersPerTick;
        }

        private static Plan invalid() {
            return new Plan(Status.INVALID_REQUEST, 0.0f, 0.0d);
        }

        public Status status() {
            return status;
        }

        public float machineOutputPressureBar() {
            return machineOutputPressureBar;
        }

        public double flowLitersPerTick() {
            return flowLitersPerTick;
        }
    }
}
