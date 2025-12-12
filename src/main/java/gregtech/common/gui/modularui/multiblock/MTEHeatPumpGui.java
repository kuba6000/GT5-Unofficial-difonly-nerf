package gregtech.common.gui.modularui.multiblock;

import static gregtech.api.enums.Mods.GregTech;

import net.minecraft.util.EnumChatFormatting;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.FloatSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Column;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.layout.Row;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

import gregtech.api.util.GTUtility;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;
import gregtech.common.tileentities.machines.multi.MTEHeatPump;

public class MTEHeatPumpGui extends MTEMultiBlockBaseGui<MTEHeatPump> {

    public MTEHeatPumpGui(MTEHeatPump multiblock) {
        super(multiblock);
    }

    @Override
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager syncManager, ModularPanel parent) {
        // NOTE: We use multiblock getters directly in lambdas instead of sync values
        // This avoids null reference issues since multiblock is always available

        return super.createTerminalTextWidget(syncManager, parent)
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.GRAY + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    .asWidget()
                    .setEnabledIf(w -> multiblock.getBaseMetaTileEntity().isActive()))
            // Show "Awaiting Configuration" if machine is idle due to invalid config
            .child(
                IKey.dynamic(() -> {
                    if (!multiblock.getBaseMetaTileEntity().isActive()) {
                        // Check if configuration is invalid
                        boolean invalidConfig = false;
                        float value = multiblock.getUniversalValue();
                        int mode = multiblock.getOperatingModeId();
                        switch (mode) {
                            case 0: // TARGET_TEMPERATURE
                                if (value <= 0 || value < 200.0f || value > 500.0f) {
                                    invalidConfig = true;
                                }
                                break;
                            case 1: // TARGET_COP
                                if (value <= 0 || value < 1.1f) {
                                    invalidConfig = true;
                                }
                                break;
                            case 2: // TARGET_ENERGY
                                if (value <= 0) {
                                    invalidConfig = true;
                                }
                                break;
                        }
                        if (invalidConfig) {
                            return EnumChatFormatting.YELLOW + "⚠ Awaiting Configuration";
                        }
                    }
                    return "";
                }).asWidget()
                    .setEnabledIf(w -> !multiblock.getBaseMetaTileEntity().isActive()))
            // Heat Exchanger Mode warning - missing colored hatches
            .child(
                IKey.dynamic(() -> {
                    if (multiblock.isHeatExchangerMode() && !multiblock.hasValidHeatExchangerHatches()) {
                        return EnumChatFormatting.RED + "⚠ Heat Exchanger Mode:"
                            + EnumChatFormatting.YELLOW + "\n  Missing colored hatches!"
                            + EnumChatFormatting.GRAY + "\n  Required: 2 Red + 2 Blue"
                            + EnumChatFormatting.GRAY + "\n  (1 input + 1 output of each color)";
                    }
                    return "";
                }).asWidget())
            .child(
                IKey.dynamic(() -> {
                    String modeName = "";
                    int mode = multiblock.getOperatingModeId();
                    switch (mode) {
                        case 0: modeName = "Target Temperature"; break;
                        case 1: modeName = "Target COP"; break;
                        case 2: modeName = "Target Energy"; break;
                    }
                    return EnumChatFormatting.WHITE + "Mode: "
                        + EnumChatFormatting.YELLOW + modeName;
                })
                    .asWidget()
                    .setEnabledIf(w -> multiblock.getBaseMetaTileEntity().isActive()))
            .child(
                IKey.dynamic(() -> {
                    // Dynamic target display that updates based on mode
                    int mode = multiblock.getOperatingModeId();
                    float value = multiblock.getUniversalValue();
                    switch (mode) {
                        case 0: // TARGET_TEMPERATURE
                            return EnumChatFormatting.WHITE + "Target: "
                                + EnumChatFormatting.GOLD + String.format("%.1f", value) + "K";
                        case 1: // TARGET_COP
                            return EnumChatFormatting.WHITE + "Target: "
                                + EnumChatFormatting.LIGHT_PURPLE + "COP " + String.format("%.2f", value);
                        case 2: // TARGET_ENERGY
                            return EnumChatFormatting.WHITE + "Target: "
                                + EnumChatFormatting.GOLD + GTUtility.formatNumbers((int)value) + " EU/t";
                        default:
                            return "";
                    }
                })
                    .asWidget()
                    .setEnabledIf(w -> multiblock.getBaseMetaTileEntity().isActive()))
            .child(
                IKey.dynamic(() -> {
                    String fluidName = multiblock.getFluidName();
                    return EnumChatFormatting.WHITE + "Fluid: "
                        + EnumChatFormatting.AQUA + fluidName;
                })
                    .asWidget()
                    .setEnabledIf(w -> multiblock.getBaseMetaTileEntity().isActive()
                        && GTUtility.isStringValid(multiblock.getFluidName())))
            .child(
                IKey.dynamic(() -> {
                    float cop = multiblock.getCOP();
                    if (cop <= 0) {
                        // Passthrough mode - no heating needed
                        return EnumChatFormatting.WHITE + "Mode: "
                            + EnumChatFormatting.GREEN + "PASSTHROUGH (No Heating)";
                    } else {
                        // Normal operation - show COP
                        return EnumChatFormatting.WHITE + "COP: "
                            + EnumChatFormatting.LIGHT_PURPLE + String.format("%.2f", cop);
                    }
                })
                    .asWidget()
                    .setEnabledIf(w -> multiblock.getBaseMetaTileEntity().isActive()))
            .child(
                IKey.dynamic(() -> EnumChatFormatting.WHITE + "Input Network:")
                    .asWidget()
                    .setEnabledIf(w -> multiblock.getBaseMetaTileEntity().isActive()))
            .child(
                IKey.dynamic(() -> {
                    float temp = multiblock.getInputTemperature();
                    return EnumChatFormatting.WHITE + "  Temperature: "
                        + EnumChatFormatting.GOLD + String.format("%.1f", temp) + "K";
                })
                    .asWidget()
                    .setEnabledIf(w -> multiblock.getBaseMetaTileEntity().isActive()))
            .child(
                IKey.dynamic(() -> {
                    int stored = multiblock.getInputNetworkStored();
                    int capacity = multiblock.getInputNetworkCapacity();
                    return EnumChatFormatting.WHITE + "  Capacity: "
                        + EnumChatFormatting.GREEN + GTUtility.formatNumbers(stored) + "L"
                        + EnumChatFormatting.WHITE + " / "
                        + EnumChatFormatting.YELLOW + GTUtility.formatNumbers(capacity) + "L";
                })
                    .asWidget()
                    .setEnabledIf(w -> multiblock.getBaseMetaTileEntity().isActive()))
            .child(
                IKey.dynamic(() -> EnumChatFormatting.WHITE + "Output Network:")
                    .asWidget()
                    .setEnabledIf(w -> multiblock.getBaseMetaTileEntity().isActive()))
            .child(
                IKey.dynamic(() -> {
                    float temp = multiblock.getOutputTemperature();
                    return EnumChatFormatting.WHITE + "  Temperature: "
                        + EnumChatFormatting.GOLD + String.format("%.1f", temp) + "K";
                })
                    .asWidget()
                    .setEnabledIf(w -> multiblock.getBaseMetaTileEntity().isActive()))
            .child(
                IKey.dynamic(() -> {
                    int stored = multiblock.getOutputNetworkStored();
                    int capacity = multiblock.getOutputNetworkCapacity();
                    return EnumChatFormatting.WHITE + "  Capacity: "
                        + EnumChatFormatting.GREEN + GTUtility.formatNumbers(stored) + "L"
                        + EnumChatFormatting.WHITE + " / "
                        + EnumChatFormatting.YELLOW + GTUtility.formatNumbers(capacity) + "L";
                })
                    .asWidget()
                    .setEnabledIf(w -> multiblock.getBaseMetaTileEntity().isActive()));
    }

    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);

        FloatSyncValue inputTempSync = new FloatSyncValue(multiblock::getInputTemperature);
        syncManager.syncValue("inputTemp", inputTempSync);

        FloatSyncValue outputTempSync = new FloatSyncValue(multiblock::getOutputTemperature);
        syncManager.syncValue("outputTemp", outputTempSync);

        IntSyncValue inputCapacitySync = new IntSyncValue(multiblock::getInputNetworkCapacity);
        syncManager.syncValue("inputCapacity", inputCapacitySync);

        IntSyncValue inputStoredSync = new IntSyncValue(multiblock::getInputNetworkStored);
        syncManager.syncValue("inputStored", inputStoredSync);

        IntSyncValue outputCapacitySync = new IntSyncValue(multiblock::getOutputNetworkCapacity);
        syncManager.syncValue("outputCapacity", outputCapacitySync);

        IntSyncValue outputStoredSync = new IntSyncValue(multiblock::getOutputNetworkStored);
        syncManager.syncValue("outputStored", outputStoredSync);

        StringSyncValue fluidNameSync = new StringSyncValue(multiblock::getFluidName);
        syncManager.syncValue("fluidName", fluidNameSync);

        FloatSyncValue copSync = new FloatSyncValue(multiblock::getCOP);
        syncManager.syncValue("cop", copSync);

        IntSyncValue operatingModeSync = new IntSyncValue(multiblock::getOperatingModeId, multiblock::setOperatingModeById);
        syncManager.syncValue("operatingMode", operatingModeSync);

        // Universal value - interpreted based on operating mode
        FloatSyncValue universalValueSync = new FloatSyncValue(multiblock::getUniversalValue, multiblock::setUniversalValue);
        syncManager.syncValue("universalValue", universalValueSync);

        // Fluid amount per operation (in mB/L)
        IntSyncValue fluidAmountSync = new IntSyncValue(multiblock::getFluidAmountPerOperation, multiblock::setFluidAmountPerOperation);
        syncManager.syncValue("fluidAmount", fluidAmountSync);

        // Temperature tolerances for passthrough mode (in K)
        FloatSyncValue lowerToleranceSync = new FloatSyncValue(multiblock::getLowerTemperatureTolerance, multiblock::setLowerTemperatureTolerance);
        syncManager.syncValue("lowerTolerance", lowerToleranceSync);

        FloatSyncValue upperToleranceSync = new FloatSyncValue(multiblock::getUpperTemperatureTolerance, multiblock::setUpperTemperatureTolerance);
        syncManager.syncValue("upperTolerance", upperToleranceSync);

        // Heat Exchanger Mode
        IntSyncValue heatExchangerModeSync = new IntSyncValue(() -> multiblock.isHeatExchangerMode() ? 1 : 0,
            val -> multiblock.setHeatExchangerMode(val != 0));
        syncManager.syncValue("heatExchangerMode", heatExchangerModeSync);

        IntSyncValue hotStreamSync = new IntSyncValue(() -> multiblock.isConfiguringHotStream() ? 1 : 0,
            val -> multiblock.setConfiguringHotStream(val != 0));
        syncManager.syncValue("hotStream", hotStreamSync);

        // Heat Exchanger validation
        IntSyncValue hxValidSync = new IntSyncValue(() -> multiblock.hasValidHeatExchangerHatches() ? 1 : 0);
        syncManager.syncValue("hxValid", hxValidSync);
    }

    @Override
    protected Flow createRightPanelGapRow(ModularPanel parent, PanelSyncManager syncManager) {
        return Flow.row()
            .coverChildrenWidth()
            .heightRel(1)
            .align(Alignment.CenterRight)
            .child(createHeatExchangerButton(syncManager))
            .child(createSettingsPanelButton(syncManager, parent))
            .childIf(multiblock.supportsPowerPanel(), createPowerPanelButton(syncManager, parent));
    }

    protected IWidget createHeatExchangerButton(PanelSyncManager syncManager) {
        final IntSyncValue heatExchangerModeSync = syncManager.findSyncHandler("heatExchangerMode", IntSyncValue.class);

        return new ButtonWidget<>().size(18, 18)
            .marginRight(4)
            .overlay(IKey.dynamic(() -> {
                if (heatExchangerModeSync != null) {
                    return heatExchangerModeSync.getValue() != 0
                        ? EnumChatFormatting.GREEN + "HX"
                        : EnumChatFormatting.GRAY + "HX";
                }
                return EnumChatFormatting.GRAY + "HX";
            }))
            .onMousePressed(d -> {
                if (heatExchangerModeSync == null) return false;
                heatExchangerModeSync.updateCacheFromSource(false);
                int newValue = heatExchangerModeSync.getValue() == 0 ? 1 : 0;
                heatExchangerModeSync.setValue(newValue);
                heatExchangerModeSync.syncToServer(1, buffer -> buffer.writeVarIntToBuffer(newValue));
                return true;
            })
            .tooltipBuilder(t -> t.addLine(IKey.str("Heat Exchanger Mode"))
                .addLine(IKey.str("Exchange heat between two fluid streams"))
                .addLine(IKey.str("Requires colored hatches (Hot=Red, Cold=Blue)")));
    }

    protected IWidget createSettingsPanelButton(PanelSyncManager syncManager, ModularPanel parent) {
        // Get sync handlers from parent syncManager
        final IntSyncValue modeSync = syncManager.findSyncHandler("operatingMode", IntSyncValue.class);
        final FloatSyncValue universalValueSync = syncManager.findSyncHandler("universalValue", FloatSyncValue.class);
        final IntSyncValue fluidAmountSync = syncManager.findSyncHandler("fluidAmount", IntSyncValue.class);
        final FloatSyncValue lowerToleranceSync = syncManager.findSyncHandler("lowerTolerance", FloatSyncValue.class);
        final FloatSyncValue upperToleranceSync = syncManager.findSyncHandler("upperTolerance", FloatSyncValue.class);
        final IntSyncValue heatExchangerModeSync = syncManager.findSyncHandler("heatExchangerMode", IntSyncValue.class);
        final IntSyncValue hotStreamSync = syncManager.findSyncHandler("hotStream", IntSyncValue.class);

        IPanelHandler settingsPanel = syncManager
            .panel("heatPumpSettings", (p_syncManager, syncHandler) -> openSettingsPanel(parent, modeSync, universalValueSync, fluidAmountSync, lowerToleranceSync, upperToleranceSync, heatExchangerModeSync, hotStreamSync), true);

        return new ButtonWidget<>().size(18, 18)
            .marginRight(4)
            .onMousePressed(d -> {
                if (!settingsPanel.isPanelOpen()) {
                    settingsPanel.openPanel();
                } else {
                    settingsPanel.closePanel();
                }
                return true;
            })
            .tooltipBuilder(t -> t.addLine(IKey.str("Heat Pump Settings")));
    }

    private ModularPanel openSettingsPanel(ModularPanel parent, IntSyncValue modeSync, FloatSyncValue universalValueSync, IntSyncValue fluidAmountSync, FloatSyncValue lowerToleranceSync, FloatSyncValue upperToleranceSync, IntSyncValue heatExchangerModeSync, IntSyncValue hotStreamSync) {
        return new ModularPanel("heatPumpSettings").relative(parent)
            .leftRel(1)
            .topRel(0)
            .size(140, 220)
            .child(
                new Column().sizeRel(1)
                    .padding(5)
                    .child(
                        new TextWidget<>(EnumChatFormatting.UNDERLINE + "Heat Pump Settings")
                            .alignment(Alignment.Center)
                            .widthRel(1)
                            .height(18)
                            .marginBottom(4))
                    // Stream selector - always present but only visible in Heat Exchanger Mode
                    .child(createStreamSelectorRow(heatExchangerModeSync, hotStreamSync))
                    .child(createSettingsButton1(modeSync, universalValueSync))
                    .child(createSettingsButton2(modeSync, universalValueSync))
                    .child(createSettingsButton3(modeSync, universalValueSync))
                    // ONE universal input field with dynamic label
                    .child(createUniversalInputField(modeSync, universalValueSync))
                    // Fluid amount per operation field
                    .child(createFluidAmountField(fluidAmountSync))
                    // Temperature tolerance fields (only visible in TARGET_TEMPERATURE mode)
                    .child(createToleranceFieldsColumn(modeSync, lowerToleranceSync, upperToleranceSync)));
    }

    /**
     * Creates stream selector row for Heat Exchanger Mode.
     * Shows which stream is being configured (Hot or Cold).
     * Dynamically shows/hides based on Heat Exchanger Mode state.
     */
    private IWidget createStreamSelectorRow(IntSyncValue heatExchangerModeSync, IntSyncValue hotStreamSync) {
        return new Row().widthRel(1).height(18).marginBottom(4)
            .setEnabledIf(w -> heatExchangerModeSync != null && heatExchangerModeSync.getValue() != 0) // Only active when HX mode is ON
            .child(
                new TextWidget<>("Stream:")
                    .width(50)
                    .alignment(Alignment.CenterLeft))
            .child(
                new ButtonWidget<>().width(60)
                    .height(18)
                    .overlay(IKey.dynamic(() -> {
                        if (hotStreamSync != null) {
                            return hotStreamSync.getValue() != 0
                                ? EnumChatFormatting.RED + "HOT"
                                : EnumChatFormatting.BLUE + "COLD";
                        }
                        return "N/A";
                    }))
                    .onMousePressed(d -> {
                        if (heatExchangerModeSync == null || hotStreamSync == null) return false;
                        if (heatExchangerModeSync.getValue() == 0) return false; // Ignore clicks when disabled
                        hotStreamSync.updateCacheFromSource(false);
                        int newValue = hotStreamSync.getValue() == 0 ? 1 : 0;
                        hotStreamSync.setValue(newValue);
                        hotStreamSync.syncToServer(1, buffer -> buffer.writeVarIntToBuffer(newValue));
                        return true;
                    })
                    .tooltipBuilder(t -> t.addLine(IKey.str("Switch between Hot and Cold stream configuration"))
                        .addLine(IKey.str("Hot stream (RED hatches)"))
                        .addLine(IKey.str("Cold stream (BLUE hatches)"))));
    }

    /**
     * Creates ONE universal input field with dynamic label.
     * Backend interprets the value based on operating mode.
     */
    private IWidget createUniversalInputField(IntSyncValue modeSync, FloatSyncValue universalValueSync) {
        return new Row().widthRel(1).height(18).marginTop(4)
            .child(
                // Dynamic label widget
                IKey.dynamic(() -> {
                    if (modeSync == null) return "Value:";
                    switch (modeSync.getValue()) {
                        case 0: return "Target (K):";
                        case 1: return "COP:";
                        case 2: return "EU/t:";
                        default: return "Value:";
                    }
                }).asWidget()
                    .width(60)
                    .alignment(Alignment.CenterLeft))
            .child(
                // ONE text field, value interpreted by backend
                new TextFieldWidget()
                    .width(70)
                    .height(18)
                    .value(universalValueSync)
                    .setTextAlignment(Alignment.Center));
    }

    /**
     * Creates field for setting fluid amount per operation.
     */
    private IWidget createFluidAmountField(IntSyncValue fluidAmountSync) {
        return new Row().widthRel(1).height(18).marginTop(4)
            .child(
                new TextWidget<>("Fluid/cycle (L):")
                    .width(80)
                    .alignment(Alignment.CenterLeft))
            .child(
                new TextFieldWidget()
                    .width(50)
                    .height(18)
                    .setNumbers(1, 10000)
                    .value(fluidAmountSync)
                    .setTextAlignment(Alignment.Center));
    }

    private IWidget createSettingsButton1(IntSyncValue modeSync, FloatSyncValue universalValueSync) {
        return new ButtonWidget<>().widthRel(1)
            .height(18)
            .marginBottom(4)
            .overlay(IKey.dynamic(() -> {
                if (modeSync == null) return EnumChatFormatting.GRAY + "Target Temperature";
                boolean isActive = modeSync.getValue() == 0;
                return (isActive ? EnumChatFormatting.GREEN : EnumChatFormatting.GRAY)
                    + "Target Temperature";
            }))
            .onMousePressed(d -> {
                if (modeSync == null || universalValueSync == null) return false;
                modeSync.updateCacheFromSource(false);
                modeSync.setValue(0);
                modeSync.syncToServer(1, buffer -> buffer.writeVarIntToBuffer(0));
                // Force universal value to refresh from backend
                universalValueSync.updateCacheFromSource(true);
                return true;
            })
            .tooltipBuilder(t -> t.addLine("Set target output temperature")
                .addLine("COP and energy will be calculated"));
    }

    private IWidget createSettingsButton2(IntSyncValue modeSync, FloatSyncValue universalValueSync) {
        return new ButtonWidget<>().widthRel(1)
            .height(18)
            .marginBottom(4)
            .overlay(IKey.dynamic(() -> {
                if (modeSync == null) return EnumChatFormatting.GRAY + "Target COP";
                boolean isActive = modeSync.getValue() == 1;
                return (isActive ? EnumChatFormatting.GREEN : EnumChatFormatting.GRAY)
                    + "Target COP";
            }))
            .onMousePressed(d -> {
                if (modeSync == null || universalValueSync == null) return false;
                modeSync.updateCacheFromSource(false);
                modeSync.setValue(1);
                modeSync.syncToServer(1, buffer -> buffer.writeVarIntToBuffer(1));
                // Force universal value to refresh from backend
                universalValueSync.updateCacheFromSource(true);
                return true;
            })
            .tooltipBuilder(t -> t.addLine("Set target COP (efficiency)")
                .addLine("Temperature and energy will be calculated"));
    }

    private IWidget createSettingsButton3(IntSyncValue modeSync, FloatSyncValue universalValueSync) {
        return new ButtonWidget<>().widthRel(1)
            .height(18)
            .overlay(IKey.dynamic(() -> {
                if (modeSync == null) return EnumChatFormatting.GRAY + "Target Energy Usage";
                boolean isActive = modeSync.getValue() == 2;
                return (isActive ? EnumChatFormatting.GREEN : EnumChatFormatting.GRAY)
                    + "Target Energy Usage";
            }))
            .onMousePressed(d -> {
                if (modeSync == null || universalValueSync == null) return false;
                modeSync.updateCacheFromSource(false);
                modeSync.setValue(2);
                modeSync.syncToServer(1, buffer -> buffer.writeVarIntToBuffer(2));
                // Force universal value to refresh from backend
                universalValueSync.updateCacheFromSource(true);
                return true;
            })
            .tooltipBuilder(t -> t.addLine("Set target energy consumption")
                .addLine("Temperature and COP will be calculated"));
    }

    /**
     * Creates column with temperature tolerance fields (lower and upper).
     * Fields are dynamically shown/hidden based on operating mode.
     * Uses setEnabledIf to react to mode changes.
     */
    private IWidget createToleranceFieldsColumn(IntSyncValue modeSync, FloatSyncValue lowerToleranceSync, FloatSyncValue upperToleranceSync) {
        return new Column().widthRel(1)
            .height(38) // 18px + 2px margin + 18px = 38px total
            .marginTop(4)
            // Lower tolerance field
            .child(new Row().widthRel(1).height(18)
                .setEnabledIf(w -> modeSync != null && modeSync.getValue() == 0) // Show only in TARGET_TEMPERATURE mode
                .child(
                    new TextWidget<>("Lower (K):")
                        .width(80)
                        .alignment(Alignment.CenterLeft))
                .child(
                    new TextFieldWidget()
                        .width(50)
                        .height(18)
                        .value(lowerToleranceSync)
                        .setTextAlignment(Alignment.Center)))
            // Upper tolerance field
            .child(new Row().widthRel(1).height(18).marginTop(2)
                .setEnabledIf(w -> modeSync != null && modeSync.getValue() == 0) // Show only in TARGET_TEMPERATURE mode
                .child(
                    new TextWidget<>("Upper (K):")
                        .width(80)
                        .alignment(Alignment.CenterLeft))
                .child(
                    new TextFieldWidget()
                        .width(50)
                        .height(18)
                        .value(upperToleranceSync)
                        .setTextAlignment(Alignment.Center)))
            .tooltipBuilder(t -> t.addLine("Temperature tolerance for passthrough mode")
                .addLine("Lower: allow temp below target (target - lower)")
                .addLine("Upper: allow temp above target (target + upper)")
                .addLine("Fluid within range won't be processed")
                .addLine("Default: 0.5K, Range: 0-50K"));
    }
}

