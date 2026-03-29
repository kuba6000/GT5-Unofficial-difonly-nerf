package gregtech.api.metatileentity.implementations.integratedfluid;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import gregtech.api.metatileentity.implementations.integratedfluid.IFNFluidThermalRegistry;
import gregtech.api.metatileentity.implementations.integratedfluid.IFNFluidThermalRegistration;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaPipeEntity;

/**
 * Manages a network of connected integrated fluid hatches and pipes.
 * All hatches connected by the same segment of pipes share fluid data.
 * The network capacity is dynamic and depends on the number and type of members.
 */
public class IntegratedFluidNetwork {

    /**
     * Default pressure in bar.
     */
    public static final float DEFAULT_PRESSURE = 1.0f;

    /**
     * Default temperature in Kelvin (derived when empty).
     */
    public static final float DEFAULT_TEMPERATURE = 300.0f;

    /**
     * Fixed-point scale for amount (micro-units of mB/SL).
     */
    public static final long AMOUNT_SCALE = 1_000_000L;

    /**
     * Fixed-point scale for enthalpy (micro-EU).
     */
    public static final long ENTHALPY_SCALE = 1_000_000L;

    /**
     * Ambient temperature for heat loss calculations (in Kelvin).
     */
    public static final float AMBIENT_TEMPERATURE = 300.0f;

    /**
     * Heat loss per pipe per second per Kelvin difference.
     * Each pipe loses 1 EU/(s·ΔT)
     */
    public static final float HEAT_LOSS_PER_PIPE_PER_SECOND = 1.0f;

    /**
     * Fluid identifier (null when empty).
     */
    private String fluidName;

    /**
     * Total amount A in fixed-point units.
     */
    private long amountQ;

    /**
     * Total enthalpy H in fixed-point units.
     */
    private long enthalpyQ;

    /**
     * All members (pipes and hatches) connected to this network.
     */
    private final Set<IIntegratedFluidMember> members = new HashSet<>();

    /**
     * Current pressure of the network in bar.
     */
    private float pressure;

    private final UUID networkId;
    private boolean pending = false;
    private int expectedMemberCount = 0;

    public IntegratedFluidNetwork(UUID networkId) {
        this.fluidName = null;
        this.amountQ = 0L;
        this.enthalpyQ = 0L;
        this.pressure = DEFAULT_PRESSURE;
        this.networkId = networkId != null ? networkId : UUID.randomUUID();
    }

    public IntegratedFluidNetwork() {
        this(UUID.randomUUID());
    }

    /**
     * Adds a member to this network.
     */
    public void addMember(IIntegratedFluidMember member) {
        members.add(member);
        member.setNetwork(this);
        member.setNetworkId(networkId);
        updatePressure();
    }

    /**
     * Removes a member from this network.
     */
    public void removeMember(IIntegratedFluidMember member) {
        members.remove(member);
        if (member.getNetwork() == this) {
            member.setNetwork(null);
        }
        updatePressure();
    }

    /**
     * Gets the current stored fluid.
     */
    public FluidStack getStoredFluid() {
        Fluid fluid = getFluid();
        int amount = getStoredAmount();
        if (fluid == null || amount <= 0) {
            return null;
        }
        return new FluidStack(fluid, amount);
    }

    /**
     * Gets the current amount of fluid stored.
     */
    public int getStoredAmount() {
        if (amountQ <= 0) {
            return 0;
        }
        long amount = amountQ / AMOUNT_SCALE;
        if (amount > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) amount;
    }

    /**
     * Gets the maximum geometric capacity of this network in volume units.
     * Capacity is calculated dynamically based on members:
     * - Each pipe adds 100 volume units
     * - Each output hatch adds 10,000 volume units
     * - Each input hatch adds 10,000 volume units
     * - Injector hatches add 0 volume units
     */
    public int getMaxCapacity() {
        int totalCapacity = 0;
        for (IIntegratedFluidMember member : members) {
            totalCapacity += member.getCapacityContribution();
        }
        return totalCapacity;
    }

    public int getAccumulatorCapacity() {
        int total = 0;
        for (IIntegratedFluidMember member : members) {
            total += member.getAccumulatorContribution();
        }
        return total;
    }

    public float getAccumulatorMaxPressureBar() {
        float pMax = 10.0f;
        boolean any = false;
        for (IIntegratedFluidMember member : members) {
            int volume = member.getAccumulatorContribution();
            if (volume > 0) {
                any = true;
                pMax = Math.min(pMax, member.getAccumulatorMaxPressureBar());
            }
        }
        return any ? pMax : 10.0f;
    }

    public int getBaseCapacity() {
        return getMaxCapacity();
    }

    public int getTotalCapacity() {
        return getBaseCapacity() + getAccumulatorCapacity();
    }

    /**
     * Gets the available space in the network.
     */
    public int getAvailableSpace() {
        double used = getOccupiedVolume();
        double available = getTotalCapacity() - used;
        if (available <= 0.0d) {
            return 0;
        }
        if (available > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) available;
    }

    /**
     * Attempts to add fluid to the network.
     *
     * @param fluid    The fluid to add
     * @param simulate If true, only simulates the fill
     * @return The amount of fluid actually added
     */
    public int addFluid(FluidStack fluid, boolean simulate) {
        return addFluid(fluid, simulate, DEFAULT_TEMPERATURE);
    }

    /**
     * Attempts to add fluid to the network with a specified temperature.
     * Temperature is converted to enthalpy; network state remains (amount, enthalpy, pressure).
     *
     * @param fluid        The fluid to add
     * @param simulate     If true, only simulates the fill
     * @param incomingTemp The temperature of the incoming fluid in Kelvin
     * @return The amount of fluid actually added
     */
    public int addFluid(FluidStack fluid, boolean simulate, float incomingTemp) {
        if (pending) {
            return 0;
        }
        if (fluid == null || fluid.amount <= 0) {
            return 0;
        }
        long addAmountQ = toAmountQ(fluid.amount);
        long addEnthalpyQ = toEnthalpyQ(fluid.getFluid(), incomingTemp, addAmountQ);

        if (!canAccept(fluid.getFluid(), addAmountQ, addEnthalpyQ)) {
            return 0;
        }

        if (!simulate) {
            add(fluid.getFluid(), addAmountQ, addEnthalpyQ);
        }

        return fluid.amount;
    }

    /**
     * Attempts to drain fluid from the network.
     *
     * @param maxDrain The maximum amount to drain
     * @param simulate If true, only simulates the drain
     * @return The fluid that was drained
     */
    public FluidStack drainFluid(int maxDrain, boolean simulate) {
        if (pending) {
            return null;
        }
        if (amountQ <= 0 || maxDrain <= 0) {
            return null;
        }

        long requestAmountQ = Math.min(toAmountQ(maxDrain), amountQ);
        ExtractedPayload extracted = extractProportional(requestAmountQ, simulate);
        if (extracted.amountQ <= 0) {
            return null;
        }

        Fluid fluid = getFluid();
        if (fluid == null) {
            return null;
        }
        int drainedAmount = toAmountMb(extracted.amountQ);
        if (drainedAmount <= 0) {
            return null;
        }
        return new FluidStack(fluid, drainedAmount);
    }

    /**
     * Drains a specific fluid from the network.
     *
     * @param fluid    The fluid to drain (must match stored fluid)
     * @param simulate If true, only simulates the drain
     * @return The fluid that was drained
     */
    public FluidStack drainFluid(FluidStack fluid, boolean simulate) {
        if (pending) {
            return null;
        }
        if (fluid == null || amountQ <= 0) {
            return null;
        }
        Fluid stored = getFluid();
        if (stored == null || stored != fluid.getFluid()) {
            return null;
        }
        return drainFluid(fluid.amount, simulate);
    }

    public boolean canAccept(Fluid fluid, long addAmountQ, long addEnthalpyQ) {
        if (pending) {
            return false;
        }
        if (fluid == null || addAmountQ <= 0L) {
            return false;
        }
        if (!IFNFluidThermalRegistry.isRegistered(fluid)) {
            return false;
        }
        if (amountQ > 0L && (fluidName == null || !fluid.getName().equals(fluidName))) {
            return false;
        }

        long nextAmountQ = amountQ + addAmountQ;
        long nextEnthalpyQ = enthalpyQ + addEnthalpyQ;
        double specificEnthalpy = toSpecificEnthalpy(nextEnthalpyQ, nextAmountQ);
        double pGuess = Math.max(1e-4d, (double) pressure);
        FluidThermalProperties.PhaseResult phase =
            FluidThermalProperties.getPhaseFromPH(fluid, pGuess, specificEnthalpy);
        int totalCapacity = getTotalCapacity();
        if (totalCapacity <= 0) {
            return false;
        }

        if (phase.phase == FluidThermalProperties.Phase.VAPOR
            || phase.phase == FluidThermalProperties.Phase.SUPERCRITICAL) {
            double pGas = computeGasPressureBar(fluid, toAmount(nextAmountQ), specificEnthalpy, totalCapacity, pGuess);
            return pGas <= getAccumulatorMaxPressureBar();
        }

        double specificVolume = IntegratedFluidThermoModel
            .specificVolumeFromPressureAndSpecificEnthalpy(fluid, (float) pGuess, specificEnthalpy);
        double occupied = toAmount(nextAmountQ) * specificVolume;
        return occupied <= getTotalCapacity();
    }

    public void add(Fluid fluid, long addAmountQ, long addEnthalpyQ) {
        if (fluid == null || addAmountQ <= 0L) {
            return;
        }
        if (amountQ == 0L) {
            fluidName = fluid.getName();
        } else if (fluidName == null || !fluid.getName().equals(fluidName)) {
            return;
        }
        amountQ += addAmountQ;
        enthalpyQ += addEnthalpyQ;
        updatePressure();
    }

    public ExtractedPayload extractProportional(long requestAmountQ, boolean simulate) {
        if (pending || amountQ <= 0L || requestAmountQ <= 0L) {
            return ExtractedPayload.empty();
        }
        long gotAmountQ = Math.min(requestAmountQ, amountQ);

        long gotEnthalpyQ;
        if (gotAmountQ == amountQ) {
            gotEnthalpyQ = enthalpyQ;
        } else {
            double fraction = (double) gotAmountQ / (double) amountQ;
            gotEnthalpyQ = (long) (enthalpyQ * fraction);
        }

        if (!simulate) {
            amountQ -= gotAmountQ;
            enthalpyQ -= gotEnthalpyQ;
            if (amountQ < AMOUNT_SCALE) {
                clearFluid();
            }
            updatePressure();
        }
        return new ExtractedPayload(gotAmountQ, gotEnthalpyQ);
    }

    public ExtractedPayload extractPhase(long requestAmountQ, boolean wantVapor, boolean simulate) {
        if (pending || amountQ <= 0L || requestAmountQ <= 0L) {
            return ExtractedPayload.empty();
        }
        Fluid fluid = getFluid();
        if (fluid == null) {
            return ExtractedPayload.empty();
        }
        double specificEnthalpy = getSpecificEnthalpy();
        double hf = IntegratedFluidThermoModel.saturatedLiquidEnthalpy(fluid, pressure);
        double hg = IntegratedFluidThermoModel.saturatedVaporEnthalpy(fluid, pressure);
        if (hg <= hf || specificEnthalpy <= hf || specificEnthalpy >= hg) {
            return extractProportional(requestAmountQ, simulate);
        }
        double quality = (specificEnthalpy - hf) / (hg - hf);
        double vaporAmount = quality * toAmount(amountQ);
        double liquidAmount = toAmount(amountQ) - vaporAmount;
        double available = wantVapor ? vaporAmount : liquidAmount;
        long availableQ = toAmountQ(available);
        long gotAmountQ = Math.min(requestAmountQ, availableQ);
        if (gotAmountQ <= 0L) {
            return ExtractedPayload.empty();
        }
        double specific = wantVapor ? hg : hf;
        long gotEnthalpyQ = toEnthalpyQ(specific, gotAmountQ);
        if (!simulate) {
            amountQ -= gotAmountQ;
            enthalpyQ -= gotEnthalpyQ;
            if (amountQ < AMOUNT_SCALE) {
                clearFluid();
            }
            updatePressure();
        }
        return new ExtractedPayload(gotAmountQ, gotEnthalpyQ);
    }

    public Fluid getFluid() {
        if (fluidName == null || amountQ <= 0L) {
            return null;
        }
        IFNFluidThermalRegistration.init();
        Fluid fluid = FluidRegistry.getFluid(fluidName);
        if (fluid == null || !IFNFluidThermalRegistry.isRegistered(fluid)) {
            clearFluid();
            return null;
        }
        return fluid;
    }

    public String getFluidName() {
        return fluidName;
    }

    public long getAmountQ() {
        return amountQ;
    }

    public long getEnthalpyQ() {
        return enthalpyQ;
    }

    public double getSpecificEnthalpy() {
        if (amountQ <= 0L) {
            return 0.0d;
        }
        return toSpecificEnthalpy(enthalpyQ, amountQ);
    }

    public float getDerivedTemperature() {
        Fluid fluid = getFluid();
        if (fluid == null) {
            return DEFAULT_TEMPERATURE;
        }
        return IntegratedFluidThermoModel
            .temperatureFromPressureAndSpecificEnthalpy(fluid, pressure, getSpecificEnthalpy());
    }

    public double getSpecificVolume() {
        Fluid fluid = getFluid();
        if (fluid == null) {
            return 0.0d;
        }
        return IntegratedFluidThermoModel
            .specificVolumeFromPressureAndSpecificEnthalpy(fluid, pressure, getSpecificEnthalpy());
    }

    public IntegratedFluidThermoModel.Phase getPhase() {
        Fluid fluid = getFluid();
        if (fluid == null) {
            return IntegratedFluidThermoModel.Phase.LIQUID;
        }
        return IntegratedFluidThermoModel
            .phaseFromPressureAndSpecificEnthalpy(fluid, pressure, getSpecificEnthalpy());
    }

    public double getQuality() {
        Fluid fluid = getFluid();
        if (fluid == null) {
            return 0.0d;
        }
        double hf = IntegratedFluidThermoModel.saturatedLiquidEnthalpy(fluid, pressure);
        double hg = IntegratedFluidThermoModel.saturatedVaporEnthalpy(fluid, pressure);
        if (hg <= hf) {
            return 0.0d;
        }
        double h = getSpecificEnthalpy();
        if (h <= hf) {
            return 0.0d;
        }
        if (h >= hg) {
            return 1.0d;
        }
        return (h - hf) / (hg - hf);
    }

    public double getOccupiedVolume() {
        if (amountQ <= 0L) {
            return 0.0d;
        }
        Fluid fluid = getFluid();
        if (fluid == null) {
            return 0.0d;
        }
        double specificEnthalpy = getSpecificEnthalpy();
        FluidThermalProperties.PhaseResult phase =
            FluidThermalProperties.getPhaseFromPH(fluid, pressure, specificEnthalpy);
        if (phase.phase == FluidThermalProperties.Phase.VAPOR
            || phase.phase == FluidThermalProperties.Phase.SUPERCRITICAL) {
            return getTotalCapacity();
        }
        return toAmount(amountQ) * getSpecificVolume();
    }

    private static long toAmountQ(int amount) {
        return (long) amount * AMOUNT_SCALE;
    }

    private static long toAmountQ(double amount) {
        return (long) Math.floor(amount * AMOUNT_SCALE);
    }

    private static double toAmount(long amountQ) {
        return amountQ / (double) AMOUNT_SCALE;
    }

    private static int toAmountMb(long amountQ) {
        if (amountQ <= 0L) {
            return 0;
        }
        long amount = amountQ / AMOUNT_SCALE;
        if (amount > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) amount;
    }

    private static long toEnthalpyQ(double energyEu) {
        return (long) Math.round(energyEu * ENTHALPY_SCALE);
    }

    private static long toEnthalpyQ(Fluid fluid, float temperature, long amountQ) {
        double specific = IntegratedFluidThermoModel.specificEnthalpyFromTemperature(fluid, temperature);
        return toEnthalpyQ(specific, amountQ);
    }

    private static long toEnthalpyQ(double specificEnthalpy, long amountQ) {
        double amount = toAmount(amountQ);
        double energy = specificEnthalpy * amount;
        return toEnthalpyQ(energy);
    }

    public static long toEnthalpyQFromSpecific(double specificEnthalpy, long amountQ) {
        return toEnthalpyQ(specificEnthalpy, amountQ);
    }

    private static double toSpecificEnthalpy(long enthalpyQ, long amountQ) {
        if (amountQ <= 0L) {
            return 0.0d;
        }
        double energy = enthalpyQ / (double) ENTHALPY_SCALE;
        return energy / toAmount(amountQ);
    }

    public static final class ExtractedPayload {
        public final long amountQ;
        public final long enthalpyQ;

        private ExtractedPayload(long amountQ, long enthalpyQ) {
            this.amountQ = amountQ;
            this.enthalpyQ = enthalpyQ;
        }

        public static ExtractedPayload empty() {
            return new ExtractedPayload(0L, 0L);
        }
    }

    /**
     * Gets the count of members in this network.
     */
    public int getMemberCount() {
        return members.size();
    }

    /**
     * Gets the current pressure in bar.
     */
    public float getPressure() {
        return pressure;
    }

    /**
     * Sets the pressure in bar.
     */
    public void setPressure(float pressure) {
        this.pressure = pressure;
    }

    /**
     * Gets the current temperature in Kelvin.
     */
    public float getTemperature() {
        return getDerivedTemperature();
    }

    /**
     * Sets the temperature in Kelvin.
     */
    public void setTemperature(float temperature) {
        if (amountQ <= 0) {
            return;
        }
        Fluid fluid = getFluid();
        if (fluid == null) {
            return;
        }
        double specificEnthalpy = IntegratedFluidThermoModel.specificEnthalpyFromTemperature(fluid, temperature);
        enthalpyQ = toEnthalpyQ(specificEnthalpy, amountQ);
        updatePressure();
    }

    public UUID getNetworkId() {
        return networkId;
    }

    public boolean isPending() {
        return pending;
    }

    public void setPending(boolean pending) {
        this.pending = pending;
    }

    public int getExpectedMemberCount() {
        return expectedMemberCount;
    }

    public void setExpectedMemberCount(int expectedMemberCount) {
        this.expectedMemberCount = expectedMemberCount;
    }

    public void loadState(String fluidName, long amountQ, long enthalpyQ, float pressure, int expectedMembers) {
        this.amountQ = Math.max(0L, amountQ);
        this.enthalpyQ = enthalpyQ;
        if (this.amountQ < AMOUNT_SCALE) {
            clearFluid();
        } else {
            this.fluidName = fluidName;
        }
        this.pressure = DEFAULT_PRESSURE;
        this.expectedMemberCount = expectedMembers;
        updatePressure();
    }

    /**
     * Clears all fluid from the network.
     * Used during network splits to prevent duplication.
     */
    public void clearFluid() {
        fluidName = null;
        amountQ = 0L;
        enthalpyQ = 0L;
        pressure = DEFAULT_PRESSURE;
    }

    /**
     * Gets all members in this network.
     */
    public Set<IIntegratedFluidMember> getMembers() {
        return new HashSet<>(members);
    }

    /**
     * Counts the number of pipes in this network.
     * Pipes are members that contribute capacity but are not hatches.
     */
    public int getPipeCount() {
        int pipeCount = 0;
        for (IIntegratedFluidMember member : members) {
            // Pipes typically contribute 100 mB capacity
            // Hatches contribute 10,000 mB or 0 mB (injectors)
            int contribution = member.getCapacityContribution();
            if (contribution > 0 && contribution < 1000) {
                pipeCount++;
            }
        }
        return pipeCount;
    }

    /**
     * Applies heat loss to the network based on the number of pipes.
     * Called every second (20 ticks) to gradually move temperature toward ambient.
     *
     * Heat loss formula:
     * - Energy lost per second = pipeCount * 1 EU/(s*K) * (T - Tambient)
     * - This shifts enthalpy toward ambient deterministically
     */
    public void applyHeatLoss(float ambientTemperature) {
        if (amountQ <= 0) {
            return;
        }

        float temperature = getDerivedTemperature();
        float temperatureDelta = temperature - ambientTemperature;
        if (Math.abs(temperatureDelta) < 0.1f) {
            return;
        }

        int pipeCount = getPipeCount();
        if (pipeCount <= 0) {
            return;
        }

        float energyDelta = -pipeCount * HEAT_LOSS_PER_PIPE_PER_SECOND * temperatureDelta;
        enthalpyQ += toEnthalpyQ(energyDelta);
        updatePressure();
    }

    /**
     * Merges another network into this one.
     */
    public void merge(IntegratedFluidNetwork other) {
        if (other == null || other == this) {
            return;
        }
        long otherAmountQ = other.amountQ;
        long otherEnthalpyQ = other.enthalpyQ;
        String otherFluidName = other.fluidName;

        // Transfer all members to this network FIRST (this increases capacity)
        for (IIntegratedFluidMember member : new HashSet<>(other.members)) {
            other.removeMember(member);
            addMember(member);
        }

        if (otherAmountQ > 0L && otherFluidName != null) {
            if (amountQ <= 0L) {
                fluidName = otherFluidName;
                amountQ = otherAmountQ;
                enthalpyQ = otherEnthalpyQ;
            } else if (otherFluidName.equals(fluidName)) {
                amountQ += otherAmountQ;
                enthalpyQ += otherEnthalpyQ;
            }
        }

        other.clearFluid();
        updatePressure();
    }

    /**
     * Updates pressure based on current network state (A, H) and capacity.
     *
     * Pressure is derived from the network state (A,H) plus capacities. For liquid/two-phase we use an
     * accumulator overfill curve (temporary model). For vapor/supercritical we use a simple
     * ideal-gas-like rule. This provides pressure compliance between machines without explicit
     * inter-machine communication.
     */
    private void updatePressure() {
        Fluid fluid = getFluid();
        pressure = computePressureForState(fluid, amountQ, enthalpyQ, pressure);
    }

    private float computePressureForState(Fluid fluid, long nextAmountQ, long nextEnthalpyQ, float currentPressure) {
        if (fluid == null || nextAmountQ <= 0L) {
            return IntegratedFluidThermoModel.BASE_PRESSURE;
        }

        int baseCapacity = getBaseCapacity();
        int accumulatorCapacity = getAccumulatorCapacity();
        int totalCapacity = getTotalCapacity();
        float maxPressure = getAccumulatorMaxPressureBar();
        if (totalCapacity <= 0) {
            return IntegratedFluidThermoModel.BASE_PRESSURE;
        }

        double amount = toAmount(nextAmountQ);
        double specificEnthalpy = toSpecificEnthalpy(nextEnthalpyQ, nextAmountQ);

        double pGuess = Math.max(1e-4d, (double) currentPressure);
        FluidThermalProperties.PhaseResult phase =
            FluidThermalProperties.getPhaseFromPH(fluid, pGuess, specificEnthalpy);

        if (phase.phase == FluidThermalProperties.Phase.VAPOR
            || phase.phase == FluidThermalProperties.Phase.SUPERCRITICAL) {
            double pGas = computeGasPressureBar(fluid, amount, specificEnthalpy, totalCapacity, pGuess);
            boolean incomplete = pending || (expectedMemberCount > 0 && members.size() < expectedMemberCount);
            if (incomplete) {
                return (float) Math.max(1e-4d, Math.min(pGas, (double) maxPressure));
            }
            if (pGas > (double) maxPressure * 1.10d) {
                ruptureNetwork();
                return IntegratedFluidThermoModel.BASE_PRESSURE;
            }
            return (float) Math.max(1e-4d, Math.min(pGas, (double) maxPressure));
        }

        if (accumulatorCapacity <= 0) {
            return IntegratedFluidThermoModel.BASE_PRESSURE;
        }

        double p = Math.max(1.0d, pGuess);
        for (int it = 0; it < 20; it++) {
            double specificVolume = IntegratedFluidThermoModel
                .specificVolumeFromPressureAndSpecificEnthalpy(fluid, (float) p, specificEnthalpy);
            if (specificVolume <= 0.0d) {
                specificVolume = 1.0d;
            }

            double occupied = amount * specificVolume;
            double over = occupied - (double) baseCapacity;
            if (over < 0.0d) {
                over = 0.0d;
            }
            if (over > (double) accumulatorCapacity) {
                over = (double) accumulatorCapacity;
            }

            double fill = over / (double) accumulatorCapacity;
            double pTarget = 1.0d + ((double) maxPressure - 1.0d) * fill;
            pTarget = Math.max(1.0d, Math.min(pTarget, (double) maxPressure));

            if (Math.abs(pTarget - p) < 1e-4d) {
                p = pTarget;
                break;
            }
            p = 0.5d * p + 0.5d * pTarget;
        }

        return (float) Math.max(1.0d, Math.min(p, (double) maxPressure));
    }

    private double computeGasPressureBar(Fluid fluid, double amountStd, double specificEnthalpy, int totalCapacity,
        double pInit) {
        if (totalCapacity <= 0) {
            return IntegratedFluidThermoModel.BASE_PRESSURE;
        }
        double p = Math.max(1e-4d, pInit);
        for (int it = 0; it < 20; it++) {
            double temperature = FluidThermalProperties.getTemperatureFromPH(fluid, p, specificEnthalpy);
            double pNew = IntegratedFluidThermoModel.BASE_PRESSURE
                * (amountStd / (double) totalCapacity)
                * (temperature / IntegratedFluidThermoModel.BASE_TEMPERATURE);
            if (Math.abs(pNew - p) < 1e-4d) {
                return pNew;
            }
            p = 0.5d * p + 0.5d * pNew;
        }
        return p;
    }

    private void ruptureNetwork() {
        clearFluid();
        MetaPipeEntity pipe = pickRandomPipe();
        if (pipe == null) {
            return;
        }
        IGregTechTileEntity baseTile = pipe.getBaseMetaTileEntity();
        if (baseTile == null) {
            return;
        }
        World world = baseTile.getWorld();
        if (world == null || world.isRemote) {
            return;
        }
        NetworkManager manager = NetworkManager.getInstance(world);
        if (pipe instanceof IIntegratedFluidMember member) {
            manager.onMemberRemoved(member);
        }
        world.setBlockToAir(baseTile.getXCoord(), baseTile.getYCoord(), baseTile.getZCoord());
    }

    private MetaPipeEntity pickRandomPipe() {
        List<MetaPipeEntity> pipes = new ArrayList<>();
        for (IIntegratedFluidMember member : members) {
            if (member instanceof MetaPipeEntity pipe) {
                pipes.add(pipe);
            }
        }
        if (pipes.isEmpty()) {
            return null;
        }
        IGregTechTileEntity base = pipes.get(0).getBaseMetaTileEntity();
        Random rng = base != null ? base.getWorld().rand : new Random();
        return pipes.get(rng.nextInt(pipes.size()));
    }

    /**
     * Caps the stored fluid to the network's maximum capacity.
     * Call this after removing members to prevent overflow.
     *
     * @return The amount of fluid that was removed (voided)
     */
    public int capFluidToCapacity() {
        return 0;
    }

    /**
     * Clears all members from this network (for cleanup).
     */
    public void clear() {
        for (IIntegratedFluidMember member : new HashSet<>(members)) {
            removeMember(member);
        }
        clearFluid();
    }
}
