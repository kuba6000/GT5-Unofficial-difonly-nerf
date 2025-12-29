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

    // Store panel handlers to allow closing them when switching modes
    private IPanelHandler normalSettingsPanel;
    private IPanelHandler hxSettingsPanel;
    private IPanelHandler sfSettingsPanel;

    public MTEHeatPumpGui(MTEHeatPump multiblock) {
        super(multiblock);
    }

    @Override
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager syncManager, ModularPanel parent) {
        FloatSyncValue inputTempSync = syncManager.findSyncHandler("inputTemp", FloatSyncValue.class);
        FloatSyncValue outputTempSync = syncManager.findSyncHandler("outputTemp", FloatSyncValue.class);
        IntSyncValue inputCapacitySync = syncManager.findSyncHandler("inputCapacity", IntSyncValue.class);
        IntSyncValue inputStoredSync = syncManager.findSyncHandler("inputStored", IntSyncValue.class);
        IntSyncValue outputCapacitySync = syncManager.findSyncHandler("outputCapacity", IntSyncValue.class);
        IntSyncValue outputStoredSync = syncManager.findSyncHandler("outputStored", IntSyncValue.class);
        StringSyncValue fluidNameSync = syncManager.findSyncHandler("fluidName", StringSyncValue.class);
        FloatSyncValue copSync = syncManager.findSyncHandler("cop", FloatSyncValue.class);
        IntSyncValue modeSync = syncManager.findSyncHandler("operatingMode", IntSyncValue.class);
        FloatSyncValue universalValueSync = syncManager.findSyncHandler("universalValue", FloatSyncValue.class);
        IntSyncValue heatExchangerModeSync = syncManager.findSyncHandler("heatExchangerMode", IntSyncValue.class);
        IntSyncValue hxValidSync = syncManager.findSyncHandler("hxValid", IntSyncValue.class);
        IntSyncValue tooManyHatchesSync = syncManager.findSyncHandler("tooManyHatches", IntSyncValue.class);
        IntSyncValue splitFlowModeSync = syncManager.findSyncHandler("splitFlowMode", IntSyncValue.class);
        IntSyncValue sfValidSync = syncManager.findSyncHandler("sfValid", IntSyncValue.class);
        IntSyncValue totalEnergyCostSync = syncManager.findSyncHandler("totalEnergyCost", IntSyncValue.class);

        return super.createTerminalTextWidget(syncManager, parent)
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.GRAY + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    .asWidget()
                    .setEnabledIf(w -> multiblock.getBaseMetaTileEntity().isActive()))
            // Mode line - hardcoded variants for each case
            // AWAITING CONFIGURATION (machine OFF + invalid config OR hatch validation errors)
            .child(
                IKey.str(EnumChatFormatting.YELLOW + "⚠ Awaiting Configuration")
                    .asWidget()
                    .setEnabledIf(w -> {
                        if (multiblock.getBaseMetaTileEntity().isActive()) return false;

                        // Check hatch validation errors
                        boolean hasHxError = heatExchangerModeSync.getValue() != 0 && hxValidSync.getValue() == 0;
                        boolean hasSfError = splitFlowModeSync.getValue() != 0 && sfValidSync.getValue() == 0;
                        boolean hasNormalError = heatExchangerModeSync.getValue() == 0 && splitFlowModeSync.getValue() == 0 && tooManyHatchesSync.getValue() != 0;

                        if (hasHxError || hasSfError || hasNormalError) return true;

                        // Check config value validity
                        float value = universalValueSync.getValue();
                        switch (modeSync.getValue()) {
                            case 0: return false;
                            case 1: return value <= 0 || value < 1.1f;
                            case 2: return value <= 0;
                        }
                        return false;
                    }))
            // MODE: TARGET TEMPERATURE
            .child(
                IKey.str(EnumChatFormatting.WHITE + "Mode: " + EnumChatFormatting.YELLOW + "Target Temperature")
                    .asWidget()
                    .setEnabledIf(w -> {
                        if (modeSync.getValue() != 0) return false;

                        // Check hatch validation errors - DON'T show Mode if errors exist
                        boolean hasHxError = heatExchangerModeSync.getValue() != 0 && hxValidSync.getValue() == 0;
                        boolean hasSfError = splitFlowModeSync.getValue() != 0 && sfValidSync.getValue() == 0;
                        boolean hasNormalError = heatExchangerModeSync.getValue() == 0 && splitFlowModeSync.getValue() == 0 && tooManyHatchesSync.getValue() != 0;

                        if (hasHxError || hasSfError || hasNormalError) return false;

                        // Check if config is invalid - if so, DON'T show Mode line
                        return true;
                    }))
            // MODE: TARGET COP
            .child(
                IKey.str(EnumChatFormatting.WHITE + "Mode: " + EnumChatFormatting.YELLOW + "Target COP")
                    .asWidget()
                    .setEnabledIf(w -> {
                        if (modeSync.getValue() != 1) return false;

                        // Check hatch validation errors - DON'T show Mode if errors exist
                        boolean hasHxError = heatExchangerModeSync.getValue() != 0 && hxValidSync.getValue() == 0;
                        boolean hasSfError = splitFlowModeSync.getValue() != 0 && sfValidSync.getValue() == 0;
                        boolean hasNormalError = heatExchangerModeSync.getValue() == 0 && splitFlowModeSync.getValue() == 0 && tooManyHatchesSync.getValue() != 0;

                        if (hasHxError || hasSfError || hasNormalError) return false;

                        // Check if config is invalid - if so, DON'T show Mode line
                        float value = universalValueSync.getValue();
                        if (value <= 0 || value < 1.1f) return false;
                        return true;
                    }))
            // MODE: TARGET ENERGY
            .child(
                IKey.str(EnumChatFormatting.WHITE + "Mode: " + EnumChatFormatting.YELLOW + "Target Energy")
                    .asWidget()
                    .setEnabledIf(w -> {
                        if (modeSync.getValue() != 2) return false;

                        // Check hatch validation errors - DON'T show Mode if errors exist
                        boolean hasHxError = heatExchangerModeSync.getValue() != 0 && hxValidSync.getValue() == 0;
                        boolean hasSfError = splitFlowModeSync.getValue() != 0 && sfValidSync.getValue() == 0;
                        boolean hasNormalError = heatExchangerModeSync.getValue() == 0 && splitFlowModeSync.getValue() == 0 && tooManyHatchesSync.getValue() != 0;

                        if (hasHxError || hasSfError || hasNormalError) return false;

                        // Check if config is invalid - if so, DON'T show Mode line
                        float value = universalValueSync.getValue();
                        if (value <= 0) return false;
                        return true;
                    }))

            // Combined warning widget - all 3 warnings in one place (mutually exclusive)
            .child(
                IKey.dynamic(() -> {
                    // Heat Exchanger Mode warning (highest priority)
                    if (heatExchangerModeSync.getValue() != 0 && hxValidSync.getValue() == 0) {
                        return EnumChatFormatting.RED + "⚠ Heat Exchanger Mode:"
                            + EnumChatFormatting.YELLOW + "\n  Invalid Hatches"
                            + EnumChatFormatting.GRAY + "\n  Need: 2 Red + 2 Blue"
                            + EnumChatFormatting.GRAY + "\n  (1 in + 1 out each)";
                    }
                    // Split Flow Mode warning (second priority)
                    if (splitFlowModeSync.getValue() != 0 && sfValidSync.getValue() == 0) {
                        return EnumChatFormatting.RED + "⚠ Split Flow Mode:"
                            + EnumChatFormatting.YELLOW + "\n  Invalid Hatches"
                            + EnumChatFormatting.GRAY + "\n  Need: 1 Red + 1 Blue output"
                            + EnumChatFormatting.GRAY + "\n  (1 input + 2 colored outputs)";
                    }
                    // Normal Mode warning (lowest priority)
                    if (heatExchangerModeSync.getValue() == 0 && splitFlowModeSync.getValue() == 0 && tooManyHatchesSync.getValue() != 0) {
                        return EnumChatFormatting.RED + "⚠ Normal Mode:"
                            + EnumChatFormatting.YELLOW + "\n  Too many hatches"
                            + EnumChatFormatting.GRAY + "\n   Only 2 needed"
                            + EnumChatFormatting.GRAY + "\n  (1 in + 1 out)";
                    }
                    return "";
                }).asWidget()
                    .setEnabledIf(w -> {
                        // Only render if there's actually a warning to show
                        boolean hasHxWarning = heatExchangerModeSync.getValue() != 0 && hxValidSync.getValue() == 0;
                        boolean hasSfWarning = splitFlowModeSync.getValue() != 0 && sfValidSync.getValue() == 0;
                        boolean hasNormalWarning = heatExchangerModeSync.getValue() == 0 && splitFlowModeSync.getValue() == 0 && tooManyHatchesSync.getValue() != 0;
                        return hasHxWarning || hasSfWarning || hasNormalWarning;
                    }))
            .child(
                IKey.dynamic(() -> {
                    // Dynamic target display that updates based on mode
                    switch (modeSync.getValue()) {
                        case 0: // TARGET_TEMPERATURE
                            return EnumChatFormatting.WHITE + "Target: "
                                + EnumChatFormatting.GOLD + String.format("%.1f", universalValueSync.getValue()) + "K";
                        case 1: // TARGET_COP
                            return EnumChatFormatting.WHITE + "Target: "
                                + EnumChatFormatting.LIGHT_PURPLE + "COP " + String.format("%.2f", universalValueSync.getValue());
                        case 2: // TARGET_ENERGY
                            return EnumChatFormatting.WHITE + "Target: "
                                + EnumChatFormatting.GOLD + GTUtility.formatNumbers(universalValueSync.getValue().intValue()) + " EU/t";
                        default:
                            return "";
                    }
                })
                    .asWidget()
                    .setEnabledIf(w -> multiblock.getBaseMetaTileEntity().isActive()))
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.WHITE + "Fluid: "
                        + EnumChatFormatting.AQUA + fluidNameSync.getValue())
                    .asWidget()
                    .setEnabledIf(w -> multiblock.getBaseMetaTileEntity().isActive()
                        && GTUtility.isStringValid(fluidNameSync.getValue())))
            .child(
                IKey.dynamic(() -> {
                    FloatSyncValue effectiveCopSync = syncManager.findSyncHandler("effectiveCop", FloatSyncValue.class);
                    float cop = copSync.getValue();
                    float effectiveCop = effectiveCopSync != null ? effectiveCopSync.getValue() : cop;

                    if (cop <= 0) {
                        // Passthrough mode - no heating needed
                        return EnumChatFormatting.WHITE + "Mode: "
                            + EnumChatFormatting.GREEN + "PASSTHROUGH (No Heating)";
                    } else {
                        // Normal operation - show both ideal and effective COP
                        String result = EnumChatFormatting.WHITE + "COP: "
                            + EnumChatFormatting.LIGHT_PURPLE + String.format("%.2f", cop);

                        // If there's a penalty, show effective COP in different color
                        if (Math.abs(cop - effectiveCop) > 0.01f) {
                            result += EnumChatFormatting.GRAY + " → "
                                + EnumChatFormatting.GOLD + String.format("%.2f", effectiveCop)
                                + EnumChatFormatting.GRAY + " (real)";
                        }

                        return result;
                    }
                })
                    .asWidget()
                    .setEnabledIf(w -> multiblock.getBaseMetaTileEntity().isActive()))
            // Energy usage with penalty
            .child(
                IKey.dynamic(() -> {
                    IntSyncValue energySync = syncManager.findSyncHandler("totalEnergyCost", IntSyncValue.class);
                    FloatSyncValue penaltySync = syncManager.findSyncHandler("efficiencyPenalty", FloatSyncValue.class);

                    if (energySync == null) return "";

                    int energy = energySync.getValue();
                    float penalty = penaltySync != null ? penaltySync.getValue() : 1.0f;

                    if (energy <= 0) {
                        return ""; // Don't show in passthrough mode
                    }

                    String energyStr = EnumChatFormatting.WHITE + "Energy: "
                        + EnumChatFormatting.GOLD + GTUtility.formatNumbers(energy) + " EU/t";

                    // Add penalty indicator if significant
                    if (penalty > 1.01f) {
                        energyStr += EnumChatFormatting.RED + " (×" + String.format("%.2f", penalty) + ")";
                    }

                    return energyStr;
                })
                    .asWidget()
                    .setEnabledIf(w -> multiblock.getBaseMetaTileEntity().isActive()))
            // Efficiency warning - shows when COP is poor or penalty is applied
            .child(
                IKey.dynamic(() -> {
                    FloatSyncValue penaltySync = syncManager.findSyncHandler("efficiencyPenalty", FloatSyncValue.class);
                    FloatSyncValue deltaSync = syncManager.findSyncHandler("temperatureDelta", FloatSyncValue.class);

                    float penalty = penaltySync != null ? penaltySync.getValue() : 1.0f;
                    float delta = deltaSync != null ? deltaSync.getValue() : 0.0f;
                    float cop = copSync.getValue();

                    // Show warning if penalty is significant or efficiency is poor
                    if (penalty > 1.1f || cop < 2.0f) {
                        String msg = "";

                        // Show penalty warning if significant
                        if (penalty > 1.01f) {
                            msg = EnumChatFormatting.RED + "⚠ Penalty: " + String.format("%.2fx", penalty) + " energy"
                                + EnumChatFormatting.GRAY + " (ΔT=" + String.format("%.1f", delta) + "K)";
                        }

                        // Show cascading suggestion if COP is poor
                        if (cop < 2.0f && cop > 0.0f) {
                            if (!msg.isEmpty()) msg += "\n";
                            msg += EnumChatFormatting.YELLOW + "💡 Consider cascading for better efficiency";
                        }

                        return msg;
                    }
                    return "";
                }).asWidget()
                    .setEnabledIf(w -> multiblock.getBaseMetaTileEntity().isActive()))
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.WHITE + "Input Network:")
                    .asWidget()
                    .setEnabledIf(w -> multiblock.getBaseMetaTileEntity().isActive()))
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.WHITE + "  Temperature: "
                        + EnumChatFormatting.GOLD + String.format("%.1f", inputTempSync.getValue()) + "K")
                    .asWidget()
                    .setEnabledIf(w -> multiblock.getBaseMetaTileEntity().isActive()))
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.WHITE + "  Capacity: "
                        + EnumChatFormatting.GREEN + GTUtility.formatNumbers(inputStoredSync.getValue()) + "L"
                        + EnumChatFormatting.WHITE + " / "
                        + EnumChatFormatting.YELLOW + GTUtility.formatNumbers(inputCapacitySync.getValue()) + "L")
                    .asWidget()
                    .setEnabledIf(w -> multiblock.getBaseMetaTileEntity().isActive()))
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.WHITE + "Output Network:")
                    .asWidget()
                    .setEnabledIf(w -> multiblock.getBaseMetaTileEntity().isActive()))
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.WHITE + "  Temperature: "
                        + EnumChatFormatting.GOLD + String.format("%.1f", outputTempSync.getValue()) + "K")
                    .asWidget()
                    .setEnabledIf(w -> multiblock.getBaseMetaTileEntity().isActive()))
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.WHITE + "  Capacity: "
                        + EnumChatFormatting.GREEN + GTUtility.formatNumbers(outputStoredSync.getValue()) + "L"
                        + EnumChatFormatting.WHITE + " / "
                        + EnumChatFormatting.YELLOW + GTUtility.formatNumbers(outputCapacitySync.getValue()) + "L")
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

        FloatSyncValue effectiveCopSync = new FloatSyncValue(multiblock::getEffectiveCOP);
        syncManager.syncValue("effectiveCop", effectiveCopSync);

        // Temperature delta and efficiency penalty for warnings
        FloatSyncValue temperatureDeltaSync = new FloatSyncValue(multiblock::getCurrentTemperatureDelta);
        syncManager.syncValue("temperatureDelta", temperatureDeltaSync);

        FloatSyncValue efficiencyPenaltySync = new FloatSyncValue(multiblock::getCurrentEfficiencyPenalty);
        syncManager.syncValue("efficiencyPenalty", efficiencyPenaltySync);

        // Total energy cost (per tick) - includes penalty
        IntSyncValue totalEnergyCostSync = new IntSyncValue(multiblock::getTotalEnergyCost);
        syncManager.syncValue("totalEnergyCost", totalEnergyCostSync);

        IntSyncValue operatingModeSync = new IntSyncValue(multiblock::getOperatingModeId, multiblock::setOperatingModeById);
        syncManager.syncValue("operatingMode", operatingModeSync);

        // Universal value - interpreted based on operating mode
        FloatSyncValue universalValueSync = new FloatSyncValue(multiblock::getUniversalValue, multiblock::setUniversalValue);
        syncManager.syncValue("universalValue", universalValueSync);

        IntSyncValue heatDirectionSync = new IntSyncValue(() -> multiblock.isTargetHeating() ? 1 : 0,
            val -> multiblock.setTargetHeating(val != 0));
        syncManager.syncValue("heatDirection", heatDirectionSync);

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

        // Normal Mode validation - too many hatches
        IntSyncValue tooManyHatchesSync = new IntSyncValue(() -> multiblock.hasTooManyHatchesForNormalMode() ? 1 : 0);
        syncManager.syncValue("tooManyHatches", tooManyHatchesSync);

        // Split Flow Mode
        IntSyncValue splitFlowModeSync = new IntSyncValue(() -> multiblock.isSplitFlowMode() ? 1 : 0,
            val -> multiblock.setSplitFlowMode(val != 0));
        syncManager.syncValue("splitFlowMode", splitFlowModeSync);

        FloatSyncValue splitRatioSync = new FloatSyncValue(multiblock::getSplitRatio, multiblock::setSplitRatio);
        syncManager.syncValue("splitRatio", splitRatioSync);

        // Split Flow validation
        IntSyncValue sfValidSync = new IntSyncValue(() -> multiblock.hasValidSplitFlowHatches() ? 1 : 0);
        syncManager.syncValue("sfValid", sfValidSync);
    }

    @Override
    protected Flow createRightPanelGapRow(ModularPanel parent, PanelSyncManager syncManager) {
        return Flow.row()
            .coverChildrenWidth()
            .heightRel(1)
            .align(Alignment.CenterRight)
            .child(createNormalModeButton(syncManager))
            .child(createSplitFlowButton(syncManager))
            .child(createHeatExchangerButton(syncManager))
            .child(createSettingsPanelButton(syncManager, parent))
            .childIf(multiblock.supportsPowerPanel(), createPowerPanelButton(syncManager, parent));
    }

    protected IWidget createNormalModeButton(PanelSyncManager syncManager) {
        IntSyncValue heatExchangerModeSync = syncManager.findSyncHandler("heatExchangerMode", IntSyncValue.class);
        IntSyncValue splitFlowModeSync = syncManager.findSyncHandler("splitFlowMode", IntSyncValue.class);

        return new ButtonWidget<>().size(18, 18)
            .overlay(IKey.dynamic(() -> {
                boolean isActive = heatExchangerModeSync.getValue() == 0 && splitFlowModeSync.getValue() == 0;
                return (isActive ? EnumChatFormatting.WHITE : EnumChatFormatting.GRAY) + "NM";
            }))
            .onMousePressed(d -> {
                // Close all settings panels
                closeAllSettingsPanels();

                // Disable both HX and SF modes (activates Normal mode)
                heatExchangerModeSync.updateCacheFromSource(false);
                heatExchangerModeSync.setValue(0);
                heatExchangerModeSync.syncToServer(1, buffer -> buffer.writeVarIntToBuffer(0));

                splitFlowModeSync.updateCacheFromSource(false);
                splitFlowModeSync.setValue(0);
                splitFlowModeSync.syncToServer(1, buffer -> buffer.writeVarIntToBuffer(0));
                return true;
            })
            .tooltipBuilder(t -> t.addLine(IKey.str("Normal Mode"))
                .addLine(IKey.str("Standard single-stream heat pump"))
                .addLine(IKey.str("Requires: 1 input + 1 output hatch")));
    }

    protected IWidget createSplitFlowButton(PanelSyncManager syncManager) {
        IntSyncValue splitFlowModeSync = syncManager.findSyncHandler("splitFlowMode", IntSyncValue.class);

        return new ButtonWidget<>().size(18, 18)
            .overlay(IKey.dynamic(() -> splitFlowModeSync.getValue() != 0
                ? EnumChatFormatting.AQUA + "SF"
                : EnumChatFormatting.GRAY + "SF"))
            .onMousePressed(d -> {
                // Only activate if not already active
                if (splitFlowModeSync.getValue() == 0) {
                    // Close all settings panels
                    closeAllSettingsPanels();

                    splitFlowModeSync.updateCacheFromSource(false);
                    splitFlowModeSync.setValue(1);
                    splitFlowModeSync.syncToServer(1, buffer -> buffer.writeVarIntToBuffer(1));
                }
                return true;
            })
            .tooltipBuilder(t -> t.addLine(IKey.str("Split Flow Mode"))
                .addLine(IKey.str("Divides input into 2 streams"))
                .addLine(IKey.str("Creates temperature differential"))
                .addLine(IKey.str("Allows chaining for extreme temps"))
                .addLine(IKey.str("Requires: 1 input + Red/Blue outputs")));
    }

    protected IWidget createHeatExchangerButton(PanelSyncManager syncManager) {
        IntSyncValue heatExchangerModeSync = syncManager.findSyncHandler("heatExchangerMode", IntSyncValue.class);

        return new ButtonWidget<>().size(18, 18)
            .overlay(IKey.dynamic(() -> heatExchangerModeSync.getValue() != 0
                ? EnumChatFormatting.GREEN + "HX"
                : EnumChatFormatting.GRAY + "HX"))
            .onMousePressed(d -> {
                // Only activate if not already active
                if (heatExchangerModeSync.getValue() == 0) {
                    // Close all settings panels
                    closeAllSettingsPanels();

                    heatExchangerModeSync.updateCacheFromSource(false);
                    heatExchangerModeSync.setValue(1);
                    heatExchangerModeSync.syncToServer(1, buffer -> buffer.writeVarIntToBuffer(1));
                }
                return true;
            })
            .tooltipBuilder(t -> t.addLine(IKey.str("Heat Exchanger Mode"))
                .addLine(IKey.str("Exchange heat between two fluid streams"))
                .addLine(IKey.str("Requires colored hatches (Hot=Red, Cold=Blue)")));
    }

    /**
     * Closes all settings panels when switching modes
     */
    private void closeAllSettingsPanels() {
        if (normalSettingsPanel != null && normalSettingsPanel.isPanelOpen()) {
            normalSettingsPanel.closePanel();
        }
        if (hxSettingsPanel != null && hxSettingsPanel.isPanelOpen()) {
            hxSettingsPanel.closePanel();
        }
        if (sfSettingsPanel != null && sfSettingsPanel.isPanelOpen()) {
            sfSettingsPanel.closePanel();
        }
    }

    protected IWidget createSettingsPanelButton(PanelSyncManager syncManager, ModularPanel parent) {
        // Get sync handlers from parent syncManager
        IntSyncValue modeSync = syncManager.findSyncHandler("operatingMode", IntSyncValue.class);
        FloatSyncValue universalValueSync = syncManager.findSyncHandler("universalValue", FloatSyncValue.class);
        IntSyncValue fluidAmountSync = syncManager.findSyncHandler("fluidAmount", IntSyncValue.class);
        FloatSyncValue lowerToleranceSync = syncManager.findSyncHandler("lowerTolerance", FloatSyncValue.class);
        FloatSyncValue upperToleranceSync = syncManager.findSyncHandler("upperTolerance", FloatSyncValue.class);
        IntSyncValue heatExchangerModeSync = syncManager.findSyncHandler("heatExchangerMode", IntSyncValue.class);
        IntSyncValue hotStreamSync = syncManager.findSyncHandler("hotStream", IntSyncValue.class);
        IntSyncValue splitFlowModeSync = syncManager.findSyncHandler("splitFlowMode", IntSyncValue.class);
        FloatSyncValue splitRatioSync = syncManager.findSyncHandler("splitRatio", FloatSyncValue.class);
        IntSyncValue heatDirectionSync = syncManager.findSyncHandler("heatDirection", IntSyncValue.class);

        // Three separate panels for different modes - store as fields
        normalSettingsPanel = syncManager
            .panel("normalModeSettings", (p_syncManager, syncHandler) ->
                openNormalModeSettingsPanel(parent, modeSync, universalValueSync, fluidAmountSync, lowerToleranceSync,
                    upperToleranceSync, heatDirectionSync), true);

        hxSettingsPanel = syncManager
            .panel("hxModeSettings", (p_syncManager, syncHandler) ->
                openHXModeSettingsPanel(parent, modeSync, universalValueSync, fluidAmountSync, hotStreamSync,
                    heatDirectionSync), true);

        sfSettingsPanel = syncManager
            .panel("sfModeSettings", (p_syncManager, syncHandler) ->
                openSFModeSettingsPanel(parent, modeSync, universalValueSync, fluidAmountSync, splitRatioSync,
                    heatDirectionSync), true);

        return new ButtonWidget<>().size(18, 18)
            .marginRight(4)
            .onMousePressed(d -> {
                // Open appropriate panel based on current mode
                if (heatExchangerModeSync.getValue() != 0) {
                    // HX Mode active
                    if (!hxSettingsPanel.isPanelOpen()) {
                        hxSettingsPanel.openPanel();
                    } else {
                        hxSettingsPanel.closePanel();
                    }
                } else if (splitFlowModeSync.getValue() != 0) {
                    // SF Mode active
                    if (!sfSettingsPanel.isPanelOpen()) {
                        sfSettingsPanel.openPanel();
                    } else {
                        sfSettingsPanel.closePanel();
                    }
                } else {
                    // Normal Mode active
                    if (!normalSettingsPanel.isPanelOpen()) {
                        normalSettingsPanel.openPanel();
                    } else {
                        normalSettingsPanel.closePanel();
                    }
                }
                return true;
            })
            .tooltipBuilder(t -> t.addLine(IKey.str("Heat Pump Settings"))
                .addLine(IKey.str("Opens settings for current mode")));
    }

    /**
     * Settings panel for Normal Mode - includes all standard heat pump settings
     */
    private ModularPanel openNormalModeSettingsPanel(ModularPanel parent, IntSyncValue modeSync, FloatSyncValue universalValueSync,
        IntSyncValue fluidAmountSync, FloatSyncValue lowerToleranceSync, FloatSyncValue upperToleranceSync,
        IntSyncValue heatDirectionSync) {
        return new ModularPanel("normalModeSettings").relative(parent)
            .leftRel(1)
            .topRel(0)
            .size(140, 220)
            .child(
                new Column().sizeRel(1)
                    .padding(5)
                    .child(
                        new TextWidget<>(EnumChatFormatting.UNDERLINE + "Normal Mode Settings")
                            .alignment(Alignment.Center)
                            .widthRel(1)
                            .height(18)
                            .marginBottom(4))
                    .child(createSettingsButton1(modeSync, universalValueSync))
                    .child(createSettingsButton2(modeSync, universalValueSync))
                    .child(createSettingsButton3(modeSync, universalValueSync))
                    .child(createHeatDirectionRow(modeSync, heatDirectionSync))
                    .child(createUniversalInputField(modeSync, universalValueSync))
                    .child(createFluidAmountField(fluidAmountSync))
                    .child(createToleranceFieldsColumn(modeSync, lowerToleranceSync, upperToleranceSync)));
    }

    /**
     * Settings panel for Heat Exchanger Mode - includes stream selector and per-stream settings
     */
    private ModularPanel openHXModeSettingsPanel(ModularPanel parent, IntSyncValue modeSync, FloatSyncValue universalValueSync,
        IntSyncValue fluidAmountSync, IntSyncValue hotStreamSync, IntSyncValue heatDirectionSync) {
        return new ModularPanel("hxModeSettings").relative(parent)
            .leftRel(1)
            .topRel(0)
            .size(140, 200)
            .child(
                new Column().sizeRel(1)
                    .padding(5)
                    .child(
                        new TextWidget<>(EnumChatFormatting.UNDERLINE + "HX Mode Settings")
                            .alignment(Alignment.Center)
                            .widthRel(1)
                            .height(18)
                            .marginBottom(4))
                    .child(createStreamSelectorRow(hotStreamSync))
                    .child(createSettingsButton1(modeSync, universalValueSync))
                    .child(createSettingsButton2(modeSync, universalValueSync))
                    .child(createSettingsButton3(modeSync, universalValueSync))
                    .child(createUniversalInputField(modeSync, universalValueSync))
                    .child(createFluidAmountField(fluidAmountSync)));
    }

    /**
     * Settings panel for Split Flow Mode - includes split ratio and standard settings
     */
    private ModularPanel openSFModeSettingsPanel(ModularPanel parent, IntSyncValue modeSync, FloatSyncValue universalValueSync,
        IntSyncValue fluidAmountSync, FloatSyncValue splitRatioSync, IntSyncValue heatDirectionSync) {
        return new ModularPanel("sfModeSettings").relative(parent)
            .leftRel(1)
            .topRel(0)
            .size(140, 200)
            .child(
                new Column().sizeRel(1)
                    .padding(5)
                    .child(
                        new TextWidget<>(EnumChatFormatting.UNDERLINE + "SF Mode Settings")
                            .alignment(Alignment.Center)
                            .widthRel(1)
                            .height(18)
                            .marginBottom(4))
                    .child(createSplitRatioField(splitRatioSync))
                    .child(createSettingsButton1(modeSync, universalValueSync))
                    .child(createSettingsButton2(modeSync, universalValueSync))
                    .child(createSettingsButton3(modeSync, universalValueSync))
                    .child(createUniversalInputField(modeSync, universalValueSync))
                    .child(createFluidAmountField(fluidAmountSync))
                    .child(createHeatDirectionRow(modeSync, heatDirectionSync))
                    );
    }

    /**
     * Creates stream selector row for Heat Exchanger Mode.
     * Shows which stream is being configured (Hot or Cold).
     */
    private IWidget createStreamSelectorRow(IntSyncValue hotStreamSync) {
        return new Row().widthRel(1).height(18).marginBottom(4)
            .child(
                new TextWidget<>("Stream:")
                    .width(50)
                    .alignment(Alignment.CenterLeft))
            .child(
                new ButtonWidget<>().width(60)
                    .height(18)
                    .overlay(IKey.dynamic(() -> hotStreamSync.getValue() != 0
                        ? EnumChatFormatting.RED + "HOT"
                        : EnumChatFormatting.BLUE + "COLD"))
                    .onMousePressed(d -> {
                        hotStreamSync.updateCacheFromSource(false);
                        int newValue = hotStreamSync.getValue() == 0 ? 1 : 0;
                        hotStreamSync.setValue(newValue);
                        hotStreamSync.syncToServer(1, buffer -> buffer.writeVarIntToBuffer(newValue));
                        return true;
                    })
                    .tooltipBuilder(t -> t.addLine(IKey.str("Switch between Hot and Cold stream"))
                        .addLine(IKey.str("Hot stream (RED hatches)"))
                        .addLine(IKey.str("Cold stream (BLUE hatches)"))));
    }

    private IWidget createHeatDirectionRow(IntSyncValue modeSync, IntSyncValue heatDirectionSync) {
        return new Row().widthRel(1).height(18).marginTop(4)
            .setEnabledIf(w -> {
                int mode = modeSync.getValue();
                return mode == 1 || mode == 2;
            })
            .child(
                new TextWidget<>("Direction:")
                    .width(60)
                    .alignment(Alignment.CenterLeft))
            .child(
                new ButtonWidget<>().width(60)
                    .height(18)
                    .overlay(IKey.dynamic(() -> heatDirectionSync.getValue() != 0
                        ? EnumChatFormatting.RED + "HOT"
                        : EnumChatFormatting.BLUE + "COLD"))
                    .onMousePressed(d -> {
                        heatDirectionSync.updateCacheFromSource(false);
                        int newValue = heatDirectionSync.getValue() == 0 ? 1 : 0;
                        heatDirectionSync.setValue(newValue);
                        heatDirectionSync.syncToServer(1, buffer -> buffer.writeVarIntToBuffer(newValue));
                        return true;
                    })
                    .tooltipBuilder(t -> t.addLine(IKey.str("Affects COP/Energy modes"))
                        .addLine(IKey.str("HOT = heating, COLD = cooling"))));
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
                boolean isActive = modeSync.getValue() == 0;
                return (isActive ? EnumChatFormatting.GREEN : EnumChatFormatting.GRAY)
                    + "Target Temperature";
            }))
            .onMousePressed(d -> {
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
                boolean isActive = modeSync.getValue() == 1;
                return (isActive ? EnumChatFormatting.GREEN : EnumChatFormatting.GRAY)
                    + "Target COP";
            }))
            .onMousePressed(d -> {
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
                boolean isActive = modeSync.getValue() == 2;
                return (isActive ? EnumChatFormatting.GREEN : EnumChatFormatting.GRAY)
                    + "Target Energy Usage";
            }))
            .onMousePressed(d -> {
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
     * Disabled when not in TARGET_TEMPERATURE mode.
     */
    private IWidget createToleranceFieldsColumn(IntSyncValue modeSync, FloatSyncValue lowerToleranceSync, FloatSyncValue upperToleranceSync) {
        return new Column().widthRel(1).marginTop(4)
            // Lower tolerance field
            .child(new Row().widthRel(1).height(18)
                .setEnabledIf(w -> modeSync.getValue() == 0)
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
                .setEnabledIf(w -> modeSync.getValue() == 0)
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

    /**
     * Creates split ratio field for Split Flow Mode.
     * Ratio determines how much goes to primary (hot) vs secondary (cold) stream.
     */
    private IWidget createSplitRatioField(FloatSyncValue splitRatioSync) {
        return new Row().widthRel(1).height(18).marginBottom(4)
            .child(
                new TextWidget<>("Split Ratio:")
                    .width(80)
                    .alignment(Alignment.CenterLeft))
            .child(
                new TextFieldWidget()
                    .width(50)
                    .height(18)
                    .value(splitRatioSync)
                    .setTextAlignment(Alignment.Center))
            .tooltipBuilder(t -> t.addLine("Fraction for primary stream (0.1-0.9)")
                .addLine("0.5 = equal split (50/50)")
                .addLine("0.1 = 10% primary, 90% secondary")
                .addLine("0.9 = 90% primary, 10% secondary")
                .addLine("Primary stream gets MORE heating"));
    }
}
