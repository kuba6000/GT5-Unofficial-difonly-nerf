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

    @Test
    void substanceAmountUsesReferenceLiters() {
        NBTTagCompound tag = new NBTTagCompound();
        List<String> tooltip = new ArrayList<>();

        tag.setBoolean("hasNetwork", true);
        tag.setString("networkStatus", "NORMAL");
        tag.setLong("substanceAmountQ", 12L * IntegratedFluidNetwork.AMOUNT_SCALE);

        IFNWailaFormatter.addNetworkStatus(tag, tooltip);

        assertTrue(tooltip.stream().anyMatch(line -> line.contains("Substance:") && line.contains("12 refL")));
    }

    @Test
    void networkLimitsShowRawMaximumValues() {
        NBTTagCompound tag = new NBTTagCompound();
        List<String> tooltip = new ArrayList<>();

        tag.setBoolean("hasNetwork", true);
        tag.setString("networkStatus", "NORMAL");
        tag.setFloat("maxPressureBar", 32.0f);
        tag.setFloat("maxTemperatureKelvin", 1200.0f);

        IFNWailaFormatter.addNetworkStatus(tag, tooltip);

        assertTrue(
            tooltip.stream().anyMatch(line -> line.contains("Max Pressure:") && line.contains("32.00 bar")),
            tooltip.toString());
        assertTrue(
            tooltip.stream().anyMatch(line -> line.contains("Max Temperature:") && line.contains("1200.00 K")),
            tooltip.toString());
    }

    @Test
    void fluidStorageSummaryUsesOccupiedVolumeInsteadOfFluidStackAmount() {
        NBTTagCompound tag = new NBTTagCompound();
        List<String> tooltip = new ArrayList<>();

        tag.setInteger("maxCapacity", 150);
        tag.setInteger("totalCapacity", 150);
        tag.setDouble("occupiedVolume", 100.0d);
        tag.setString("networkFluidName", "Water");
        tag.setInteger("fluidStackAmount", 200);

        IFNWailaFormatter.addFluidStorageSummary(tag, tooltip, "Network Fluid", false);

        assertTrue(tooltip.stream().anyMatch(line -> line.contains("Network Fluid:")));
        assertTrue(tooltip.stream().anyMatch(line -> line.contains("Occupied:") && line.contains("100/150 L")));
        assertTrue(tooltip.stream().noneMatch(line -> line.contains("Amount:") || line.contains("Std Amount")));
    }
}
