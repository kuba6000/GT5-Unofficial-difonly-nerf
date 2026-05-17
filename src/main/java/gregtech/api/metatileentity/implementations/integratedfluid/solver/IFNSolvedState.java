package gregtech.api.metatileentity.implementations.integratedfluid.solver;

import net.minecraftforge.fluids.Fluid;

public final class IFNSolvedState {

    private final Fluid fluid;
    private final long amountQ;
    private final long enthalpyQ;
    private final double pressureBar;
    private final double temperatureKelvin;
    private final double specificEnthalpy;
    private final double specificVolume;
    private final double occupiedVolumeLiters;
    private final IFNPhaseComposition phaseComposition;

    IFNSolvedState(
        Fluid fluid,
        long amountQ,
        long enthalpyQ,
        double pressureBar,
        double temperatureKelvin,
        double specificEnthalpy,
        double specificVolume,
        double occupiedVolumeLiters,
        IFNPhaseComposition phaseComposition) {
        this.fluid = fluid;
        this.amountQ = amountQ;
        this.enthalpyQ = enthalpyQ;
        this.pressureBar = pressureBar;
        this.temperatureKelvin = temperatureKelvin;
        this.specificEnthalpy = specificEnthalpy;
        this.specificVolume = specificVolume;
        this.occupiedVolumeLiters = occupiedVolumeLiters;
        this.phaseComposition = phaseComposition;
    }

    public Fluid fluid() {
        return fluid;
    }

    public long amountQ() {
        return amountQ;
    }

    public long enthalpyQ() {
        return enthalpyQ;
    }

    public double pressureBar() {
        return pressureBar;
    }

    public double temperatureKelvin() {
        return temperatureKelvin;
    }

    public double specificEnthalpy() {
        return specificEnthalpy;
    }

    public double specificVolume() {
        return specificVolume;
    }

    public double occupiedVolumeLiters() {
        return occupiedVolumeLiters;
    }

    public IFNPhaseComposition phaseComposition() {
        return phaseComposition;
    }

    public boolean isEmpty() {
        return amountQ <= 0L || fluid == null;
    }
}
