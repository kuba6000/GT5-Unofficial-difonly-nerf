package gregtech.api.metatileentity.implementations.integratedfluid;

import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;

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

        addLimitStatus(tag.getString("pressureLimitStatus"), "Pressure", currenttip);
        addLimitStatus(tag.getString("temperatureLimitStatus"), "Temperature", currenttip);
    }

    private static void addLimitStatus(String status, String label, List<String> currenttip) {
        if ("RUPTURE".equals(status)) {
            currenttip.add(label + ": " + EnumChatFormatting.RED + "Rupture Risk" + EnumChatFormatting.RESET);
        } else if ("WARNING".equals(status)) {
            currenttip.add(label + ": " + EnumChatFormatting.YELLOW + "Over Limit" + EnumChatFormatting.RESET);
        }
    }
}
