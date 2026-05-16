package gregtech.api.metatileentity.implementations.integratedfluid;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;

class IFNWailaFormatterTest {

    @Test
    void frozenStatusIncludesReason() {
        NBTTagCompound tag = new NBTTagCompound();
        List<String> tooltip = new ArrayList<>();

        tag.setBoolean("hasNetwork", true);
        tag.setString("networkStatus", "FROZEN");
        tag.setString("frozenReason", "fluid conflict");

        IFNWailaFormatter.addNetworkStatus(tag, tooltip);

        assertTrue(tooltip.stream().anyMatch(line -> line.contains("Status:") && line.contains("Frozen")));
        assertTrue(tooltip.stream().anyMatch(line -> line.contains("fluid conflict")));
    }

    @Test
    void pendingStatusIsVisible() {
        NBTTagCompound tag = new NBTTagCompound();
        List<String> tooltip = new ArrayList<>();

        tag.setBoolean("hasNetwork", true);
        tag.setString("networkStatus", "PENDING");

        IFNWailaFormatter.addNetworkStatus(tag, tooltip);

        assertTrue(tooltip.stream().anyMatch(line -> line.contains("Status:") && line.contains("Pending")));
    }

    @Test
    void overLimitStatusIsVisible() {
        NBTTagCompound tag = new NBTTagCompound();
        List<String> tooltip = new ArrayList<>();

        tag.setBoolean("hasNetwork", true);
        tag.setString("networkStatus", "NORMAL");
        tag.setString("pressureLimitStatus", "WARNING");
        tag.setString("temperatureLimitStatus", "RUPTURE");

        IFNWailaFormatter.addNetworkStatus(tag, tooltip);

        assertTrue(tooltip.stream().anyMatch(line -> line.contains("Pressure") && line.contains("Over Limit")));
        assertTrue(tooltip.stream().anyMatch(line -> line.contains("Temperature") && line.contains("Rupture Risk")));
    }
}
