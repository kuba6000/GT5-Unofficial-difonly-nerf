package gregtech.api.metatileentity.implementations.integratedfluid;

import static com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil.formatNumber;

import java.util.List;
import java.util.Locale;

import gregtech.api.metatileentity.implementations.integratedfluid.solver.IFNSolvedState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.fluids.FluidStack;

public final class IFNWailaFormatter {

    private IFNWailaFormatter() {}

    public static void writeNetworkStatus(IntegratedFluidNetwork network, NBTTagCompound tag) {
        if (network == null || tag == null) {
            return;
        }

        tag.setString("networkStatus", network.getNetworkStatus().name());
        String frozenReason = network.getFrozenReason();
        if (frozenReason != null && !frozenReason.isEmpty()) {
            tag.setString("frozenReason", frozenReason);
        }
        tag.setLong("substanceAmountQ", network.getAmountQ());
        tag.setDouble("occupiedVolume", network.getOccupiedVolume());
        if (network.getAmountQ() > 0L) {
            IFNSolvedState solvedState = network.getSolvedState();
            tag.setString("phase", solvedState.phaseComposition().primaryPhase().name());
        }
        tag.setFloat("maxPressureBar", network.getOperationalLimits().accumulatorMaxPressureBar());
        tag.setFloat("maxTemperatureKelvin", network.getOperationalLimits().maxTemperatureKelvin());
        tag.setString("pressureLimitStatus", network.getPressureLimitEvaluation().status().name());
        tag.setString("temperatureLimitStatus", network.getTemperatureLimitEvaluation().status().name());
    }

    public static void addNetworkStatus(NBTTagCompound tag, List<String> currenttip) {
        if (tag == null || currenttip == null || !tag.getBoolean("hasNetwork")) {
            return;
        }

        String status = tag.getString("networkStatus");
        if (status == null || status.isEmpty()) {
            return;
        }

        if ("FROZEN".equals(status)) {
            currenttip.add("Status: " + EnumChatFormatting.RED + "Frozen" + EnumChatFormatting.RESET);
            String reason = tag.getString("frozenReason");
            if (reason != null && !reason.isEmpty()) {
                currenttip.add(EnumChatFormatting.RED + reason + EnumChatFormatting.RESET);
            }
        } else if ("PENDING".equals(status)) {
            currenttip.add("Status: " + EnumChatFormatting.YELLOW + "Pending" + EnumChatFormatting.RESET);
        } else {
            currenttip.add("Status: " + EnumChatFormatting.GREEN + "Normal" + EnumChatFormatting.RESET);
        }

        addSubstanceAmount(tag, currenttip);
        addPhase(tag, currenttip);
        addOccupiedVolume(tag, currenttip);
        addNetworkLimits(tag, currenttip);
        addLimitStatus(tag.getString("pressureLimitStatus"), "Pressure", currenttip);
        addLimitStatus(tag.getString("temperatureLimitStatus"), "Temperature", currenttip);
    }

    private static void addSubstanceAmount(NBTTagCompound tag, List<String> currenttip) {
        long substanceAmountQ = tag.getLong("substanceAmountQ");
        if (substanceAmountQ <= 0L) {
            return;
        }

        long wholeRefLiters = substanceAmountQ / IntegratedFluidNetwork.AMOUNT_SCALE;
        currenttip.add("Substance: " + EnumChatFormatting.GRAY + wholeRefLiters + " refL" + EnumChatFormatting.RESET);
    }

    private static void addPhase(NBTTagCompound tag, List<String> currenttip) {
        String phase = tag.getString("phase");
        if (phase == null || phase.isEmpty() || !hasDisplayableFluidState(tag)) {
            return;
        }

        currenttip.add("Phase: " + EnumChatFormatting.GRAY + formatPhaseName(phase) + EnumChatFormatting.RESET);
    }

    private static void addOccupiedVolume(NBTTagCompound tag, List<String> currenttip) {
        if (!tag.hasKey("occupiedVolume")) {
            return;
        }

        int occupiedVolume = (int) Math.round(tag.getDouble("occupiedVolume"));
        if (occupiedVolume <= 0) {
            return;
        }

        currenttip.add(
            "Occupied Volume: " + EnumChatFormatting.GREEN
                + formatNumber(occupiedVolume)
                + " L"
                + EnumChatFormatting.RESET);
    }

    private static boolean hasDisplayableFluidState(NBTTagCompound tag) {
        return tag.getLong("substanceAmountQ") > 0L || tag.getDouble("occupiedVolume") > 0.0d || tag.hasKey("networkFluid")
            || tag.hasKey("networkFluidName");
    }

    private static String formatPhaseName(String phase) {
        String normalized = phase.toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder builder = new StringBuilder(normalized.length());
        boolean startOfWord = true;
        for (int i = 0; i < normalized.length(); i++) {
            char value = normalized.charAt(i);
            if (value == ' ') {
                builder.append(value);
                startOfWord = true;
            } else if (startOfWord) {
                builder.append(Character.toUpperCase(value));
                startOfWord = false;
            } else {
                builder.append(value);
            }
        }
        return builder.toString();
    }

    private static void addNetworkLimits(NBTTagCompound tag, List<String> currenttip) {
        if (tag.hasKey("maxPressureBar")) {
            currenttip.add(
                "Max Pressure: " + EnumChatFormatting.YELLOW
                    + String.format(Locale.ROOT, "%.2f bar", tag.getFloat("maxPressureBar"))
                    + EnumChatFormatting.RESET);
        }
        if (tag.hasKey("maxTemperatureKelvin")) {
            float maxTemperatureKelvin = tag.getFloat("maxTemperatureKelvin");
            if (!Float.isInfinite(maxTemperatureKelvin) && maxTemperatureKelvin < Float.MAX_VALUE) {
                currenttip.add(
                    "Max Temperature: " + EnumChatFormatting.RED
                        + String.format(Locale.ROOT, "%.2f K", maxTemperatureKelvin)
                        + EnumChatFormatting.RESET);
            }
        }
    }

    public static void addFluidStorageSummary(NBTTagCompound tag, List<String> currenttip, String fluidLabel,
        boolean detailedCapacity) {
        if (tag == null || currenttip == null) {
            return;
        }

        int maxCapacity = tag.getInteger("maxCapacity");
        int accumulatorCapacity = tag.getInteger("accumulatorCapacity");
        int totalCapacity = tag.hasKey("totalCapacity") ? tag.getInteger("totalCapacity") : maxCapacity + accumulatorCapacity;

        String fluidName = tag.getString("networkFluidName");
        int fluidStackAmount = tag.getInteger("fluidStackAmount");
        FluidStack fluid = null;
        if (tag.hasKey("networkFluid")) {
            fluid = FluidStack.loadFluidStackFromNBT(tag.getCompoundTag("networkFluid"));
            if (fluid != null) {
                fluidName = fluid.getLocalizedName();
                fluidStackAmount = fluid.amount;
            }
        }

        if (fluidName == null || fluidName.isEmpty()) {
            if (detailedCapacity) {
                currenttip.add("Empty (Capacity: " + formatNumber(totalCapacity) + " L)");
                addCapacitySummary(currenttip, maxCapacity, accumulatorCapacity, totalCapacity, true);
            } else {
                currenttip.add("Network: Empty");
            }
            return;
        }

        currenttip.add(fluidLabel + ": " + EnumChatFormatting.AQUA + fluidName + EnumChatFormatting.RESET);
        int occupiedVolume = tag.hasKey("occupiedVolume")
            ? (int) Math.round(tag.getDouble("occupiedVolume"))
            : fluidStackAmount;
        currenttip.add(
            "Occupied: " + EnumChatFormatting.GREEN
                + formatNumber(occupiedVolume)
                + "/"
                + formatNumber(totalCapacity)
                + " L"
                + EnumChatFormatting.RESET);
        addCapacitySummary(currenttip, maxCapacity, accumulatorCapacity, totalCapacity, detailedCapacity);
    }

    private static void addCapacitySummary(List<String> currenttip, int maxCapacity, int accumulatorCapacity,
        int totalCapacity, boolean detailedCapacity) {
        if (accumulatorCapacity <= 0) {
            return;
        }

        if (detailedCapacity) {
            currenttip.add(
                EnumChatFormatting.GRAY + "Capacity: "
                    + formatNumber(maxCapacity)
                    + " + "
                    + formatNumber(accumulatorCapacity)
                    + " = "
                    + formatNumber(totalCapacity)
                    + " L" + EnumChatFormatting.RESET);
            return;
        }

        currenttip.add(
            EnumChatFormatting.GRAY + "(+"
                + formatNumber(accumulatorCapacity)
                + " Hydrophore capacity)" + EnumChatFormatting.RESET);
    }

    private static void addLimitStatus(String status, String label, List<String> currenttip) {
        if ("RUPTURE".equals(status)) {
            currenttip.add(label + ": " + EnumChatFormatting.RED + "Rupture Risk" + EnumChatFormatting.RESET);
        } else if ("WARNING".equals(status)) {
            currenttip.add(label + ": " + EnumChatFormatting.YELLOW + "Over Limit" + EnumChatFormatting.RESET);
        }
    }
}
