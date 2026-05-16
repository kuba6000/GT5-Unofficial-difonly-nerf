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
    }
}
