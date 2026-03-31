package gregtech.common.tileentities.machines.multi.radiator;

import java.util.Collections;
import java.util.List;

public final class RadiatorLoopSnapshot {

    public static final class ThermalBranch {
        public final double conductiveCoefficient;
        public final double radiativeCoefficient;
        public final boolean exchangerLeaf;
        public final List<ThermalBranch> children;

        private ThermalBranch(double conductiveCoefficient, double radiativeCoefficient, boolean exchangerLeaf,
            List<ThermalBranch> children) {
            this.conductiveCoefficient = conductiveCoefficient;
            this.radiativeCoefficient = radiativeCoefficient;
            this.exchangerLeaf = exchangerLeaf;
            this.children = children;
        }

        public static ThermalBranch exchanger(double conductiveCoefficient, double radiativeCoefficient) {
            return new ThermalBranch(conductiveCoefficient, radiativeCoefficient, true, Collections.emptyList());
        }

        public static ThermalBranch conduction(double conductiveCoefficient, List<ThermalBranch> children) {
            return new ThermalBranch(conductiveCoefficient, 0.0d, false, Collections.unmodifiableList(children));
        }
    }

    public final boolean valid;
    public final String statusKey;
    public final int segmentCount;
    public final int conductionModuleCount;
    public final int heatExchangeModuleCount;
    public final float pressureDropBar;
    public final List<ThermalBranch> surfaceBranches;

    private RadiatorLoopSnapshot(boolean valid, String statusKey, int segmentCount, int conductionModuleCount,
        int heatExchangeModuleCount, float pressureDropBar, List<ThermalBranch> surfaceBranches) {
        this.valid = valid;
        this.statusKey = statusKey;
        this.segmentCount = segmentCount;
        this.conductionModuleCount = conductionModuleCount;
        this.heatExchangeModuleCount = heatExchangeModuleCount;
        this.pressureDropBar = pressureDropBar;
        this.surfaceBranches = surfaceBranches;
    }

    public static RadiatorLoopSnapshot invalid(String statusKey) {
        return new RadiatorLoopSnapshot(false, statusKey, 0, 0, 0, 0.0f, Collections.emptyList());
    }

    public static RadiatorLoopSnapshot valid(int segmentCount, int conductionModuleCount, int heatExchangeModuleCount,
        float pressureDropBar, List<ThermalBranch> surfaceBranches) {
        return new RadiatorLoopSnapshot(
            true,
            "ok",
            segmentCount,
            conductionModuleCount,
            heatExchangeModuleCount,
            pressureDropBar,
            Collections.unmodifiableList(surfaceBranches)
        );
    }
}
