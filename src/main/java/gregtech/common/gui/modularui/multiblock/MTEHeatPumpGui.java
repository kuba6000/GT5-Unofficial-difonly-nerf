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
        FloatSyncValue inputTempSync = syncManager.findSyncHandler("inputTemp", FloatSyncValue.class);
        FloatSyncValue outputTempSync = syncManager.findSyncHandler("outputTemp", FloatSyncValue.class);
        IntSyncValue inputCapacitySync = syncManager.findSyncHandler("inputCapacity", IntSyncValue.class);
        IntSyncValue inputStoredSync = syncManager.findSyncHandler("inputStored", IntSyncValue.class);
        IntSyncValue outputCapacitySync = syncManager.findSyncHandler("outputCapacity", IntSyncValue.class);
        IntSyncValue outputStoredSync = syncManager.findSyncHandler("outputStored", IntSyncValue.class);
        StringSyncValue fluidNameSync = syncManager.findSyncHandler("fluidName", StringSyncValue.class);
        FloatSyncValue copSync = syncManager.findSyncHandler("cop", FloatSyncValue.class);
        IntSyncValue modeSync = syncManager.findSyncHandler("operatingMode", IntSyncValue.class);
        FloatSyncValue targetCOPSync = syncManager.findSyncHandler("targetCOP", FloatSyncValue.class);
        IntSyncValue targetEnergySync = syncManager.findSyncHandler("targetEnergy", IntSyncValue.class);

        return super.createTerminalTextWidget(syncManager, parent)
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.GRAY + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    .asWidget()
                    .setEnabledIf(w -> multiblock.getBaseMetaTileEntity().isActive()))
            .child(
                IKey.dynamic(() -> {
                    String modeName = "";
                    switch (modeSync.getValue()) {
                        case 0: modeName = "Target Temperature"; break;
                        case 1: modeName = "Target COP"; break;
                        case 2: modeName = "Target Energy"; break;
                    }
                    return EnumChatFormatting.WHITE + "Mode: "
                        + EnumChatFormatting.YELLOW + modeName;
                })
                    .asWidget()
                    .setEnabledIf(w -> multiblock.getBaseMetaTileEntity().isActive()))
            .childIf(
                () -> modeSync.getValue() == 1,
                IKey.dynamic(() -> EnumChatFormatting.WHITE + "Target: "
                    + EnumChatFormatting.LIGHT_PURPLE + "COP " + String.format("%.2f", targetCOPSync.getValue()))
                    .asWidget()
                    .setEnabledIf(w -> multiblock.getBaseMetaTileEntity().isActive()))
            .childIf(
                () -> modeSync.getValue() == 2,
                IKey.dynamic(() -> EnumChatFormatting.WHITE + "Target: "
                    + EnumChatFormatting.GOLD + GTUtility.formatNumbers(targetEnergySync.getValue()) + " EU/t")
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
                IKey.dynamic(
                    () -> EnumChatFormatting.WHITE + "COP: "
                        + EnumChatFormatting.LIGHT_PURPLE + String.format("%.2f", copSync.getValue()))
                    .asWidget()
                    .setEnabledIf(w -> multiblock.getBaseMetaTileEntity().isActive()
                        && copSync.getValue() > 0))
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

        IntSyncValue operatingModeSync = new IntSyncValue(multiblock::getOperatingModeId, multiblock::setOperatingModeById);
        syncManager.syncValue("operatingMode", operatingModeSync);

        FloatSyncValue targetCOPSync = new FloatSyncValue(multiblock::getTargetCOP, multiblock::setTargetCOP);
        syncManager.syncValue("targetCOP", targetCOPSync);

        IntSyncValue targetEnergySync = new IntSyncValue(multiblock::getTargetEnergy, multiblock::setTargetEnergy);
        syncManager.syncValue("targetEnergy", targetEnergySync);
    }

    @Override
    protected Flow createRightPanelGapRow(ModularPanel parent, PanelSyncManager syncManager) {
        return Flow.row()
            .coverChildrenWidth()
            .heightRel(1)
            .align(Alignment.CenterRight)
            .child(createSettingsPanelButton(syncManager, parent))
            .childIf(multiblock.supportsPowerPanel(), createPowerPanelButton(syncManager, parent));
    }

    protected IWidget createSettingsPanelButton(PanelSyncManager syncManager, ModularPanel parent) {
        // Get sync handlers from parent syncManager
        IntSyncValue modeSync = syncManager.findSyncHandler("operatingMode", IntSyncValue.class);
        FloatSyncValue targetCOPSync = syncManager.findSyncHandler("targetCOP", FloatSyncValue.class);
        IntSyncValue targetEnergySync = syncManager.findSyncHandler("targetEnergy", IntSyncValue.class);

        IPanelHandler settingsPanel = syncManager
            .panel("heatPumpSettings", (p_syncManager, syncHandler) -> openSettingsPanel(parent, modeSync, targetCOPSync, targetEnergySync), true);

        return new ButtonWidget<>().size(18, 18)
            .marginRight(4)
            .overlay(UITexture.fullImage(GregTech.ID, "gui/overlay_button/gear"))
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

    private ModularPanel openSettingsPanel(ModularPanel parent, IntSyncValue modeSync,
                                          FloatSyncValue targetCOPSync, IntSyncValue targetEnergySync) {

        return new ModularPanel("heatPumpSettings").relative(parent)
            .leftRel(1)
            .topRel(0)
            .size(140, 150)
            .child(
                new Column().sizeRel(1)
                    .padding(5)
                    .child(
                        new TextWidget<>(EnumChatFormatting.UNDERLINE + "Heat Pump Settings")
                            .alignment(Alignment.Center)
                            .widthRel(1)
                            .height(18)
                            .marginBottom(4))
                    .child(createSettingsButton1(modeSync))
                    .child(createSettingsButton2(modeSync))
                    // COP field - shown only when mode == 1
                    .child(createTargetCOPField(targetCOPSync, modeSync))
                    .child(createSettingsButton3(modeSync))
                    // Energy field - shown only when mode == 2
                    .child(createTargetEnergyField(targetEnergySync, modeSync)));
    }

    private IWidget createTargetCOPField(FloatSyncValue targetCOPSync, IntSyncValue modeSync) {
        return new Row().widthRel(1)
            .height(18)
            .marginBottom(4)
            .setEnabledIf(w -> modeSync.getValue() == 1)
            .child(
                new TextWidget<>("COP: ")
                    .width(30)
                    .alignment(Alignment.CenterLeft))
            .child(
                new TextFieldWidget()
                    .widthRel(1)
                    .height(18)
                    .value(targetCOPSync)
                    .setTextAlignment(Alignment.Center));
    }

    private IWidget createTargetEnergyField(IntSyncValue targetEnergySync, IntSyncValue modeSync) {
        return new Row().widthRel(1)
            .height(18)
            .marginBottom(4)
            .setEnabledIf(w -> modeSync.getValue() == 2)
            .child(
                new TextWidget<>("EU/t: ")
                    .width(40)
                    .alignment(Alignment.CenterLeft))
            .child(
                new TextFieldWidget()
                    .widthRel(1)
                    .height(18)
                    .setNumbers(5, 50000)
                    .value(targetEnergySync)
                    .setTextAlignment(Alignment.Center));
    }

    private IWidget createSettingsButton1(IntSyncValue modeSync) {
        return new ButtonWidget<>().widthRel(1)
            .height(18)
            .marginBottom(4)
            .background(UITexture.builder()
                .location(GregTech.ID, "gui/base/button_standard")
                .build())
            .overlay(IKey.dynamic(() -> {
                boolean isActive = modeSync.getValue() == 0;
                return (isActive ? EnumChatFormatting.GREEN : EnumChatFormatting.GRAY)
                    + "Target Temperature";
            }))
            .onMousePressed(d -> {
                modeSync.updateCacheFromSource(false);
                modeSync.setValue(0);
                modeSync.syncToServer(1, buffer -> buffer.writeVarIntToBuffer(0));
                return true;
            })
            .tooltipBuilder(t -> t.addLine(IKey.str("Set target output temperature"))
                .addLine(IKey.str("COP and energy will be calculated")));
    }

    private IWidget createSettingsButton2(IntSyncValue modeSync) {
        return new ButtonWidget<>().widthRel(1)
            .height(18)
            .marginBottom(4)
            .background(UITexture.builder()
                .location(GregTech.ID, "gui/base/button_standard")
                .build())
            .overlay(IKey.dynamic(() -> {
                boolean isActive = modeSync.getValue() == 1;
                return (isActive ? EnumChatFormatting.GREEN : EnumChatFormatting.GRAY)
                    + "Target COP";
            }))
            .onMousePressed(d -> {
                modeSync.updateCacheFromSource(false);
                modeSync.setValue(1);
                modeSync.syncToServer(1, buffer -> buffer.writeVarIntToBuffer(1));
                return true;
            })
            .tooltipBuilder(t -> t.addLine(IKey.str("Set target COP (efficiency)"))
                .addLine(IKey.str("Temperature and energy will be calculated")));
    }

    private IWidget createSettingsButton3(IntSyncValue modeSync) {
        return new ButtonWidget<>().widthRel(1)
            .height(18)
            .background(UITexture.builder()
                .location(GregTech.ID, "gui/base/button_standard")
                .build())
            .overlay(IKey.dynamic(() -> {
                boolean isActive = modeSync.getValue() == 2;
                return (isActive ? EnumChatFormatting.GREEN : EnumChatFormatting.GRAY)
                    + "Target Energy Usage";
            }))
            .onMousePressed(d -> {
                modeSync.updateCacheFromSource(false);
                modeSync.setValue(2);
                modeSync.syncToServer(1, buffer -> buffer.writeVarIntToBuffer(2));
                return true;
            })
            .tooltipBuilder(t -> t.addLine(IKey.str("Set target energy consumption"))
                .addLine(IKey.str("Temperature and COP will be calculated")));
    }
}

