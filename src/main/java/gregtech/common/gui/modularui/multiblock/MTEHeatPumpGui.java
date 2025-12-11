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

        return super.createTerminalTextWidget(syncManager, parent)
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.GRAY + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
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
        IPanelHandler settingsPanel = syncManager
            .panel("heatPumpSettings", (p_syncManager, syncHandler) -> openSettingsPanel(p_syncManager, parent), true);
        return new ButtonWidget<>().size(18, 18)
            .marginRight(4)
            .overlay(UITexture.fullImage(GregTech.ID, "gui/overlay_button/settings"))
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

    private ModularPanel openSettingsPanel(PanelSyncManager syncManager, ModularPanel parent) {
        return new ModularPanel("heatPumpSettings").relative(parent)
            .leftRel(1)
            .topRel(0)
            .size(140, 110)
            .child(
                new Column().sizeRel(1)
                    .padding(5)
                    .child(
                        new TextWidget<>(EnumChatFormatting.UNDERLINE + "Heat Pump Settings")
                            .alignment(Alignment.Center)
                            .widthRel(1)
                            .height(18)
                            .marginBottom(4))
                    .child(createSettingsButton1())
                    .child(createSettingsButton2())
                    .child(createSettingsButton3()));
    }

    private IWidget createSettingsButton1() {
        return new ButtonWidget<>().widthRel(1)
            .height(18)
            .marginBottom(4)
            .background(UITexture.builder()
                .location(GregTech.ID, "gui/base/button_standard")
                .build())
            .overlay(IKey.str("Option 1"))
            .onMousePressed(d -> {
                // TODO: Implement option 1 functionality
                return true;
            })
            .tooltipBuilder(t -> t.addLine(IKey.str("Option 1 - Not yet implemented")));
    }

    private IWidget createSettingsButton2() {
        return new ButtonWidget<>().widthRel(1)
            .height(18)
            .marginBottom(4)
            .background(UITexture.builder()
                .location(GregTech.ID, "gui/base/button_standard")
                .build())
            .overlay(IKey.str("Option 2"))
            .onMousePressed(d -> {
                // TODO: Implement option 2 functionality
                return true;
            })
            .tooltipBuilder(t -> t.addLine(IKey.str("Option 2 - Not yet implemented")));
    }

    private IWidget createSettingsButton3() {
        return new ButtonWidget<>().widthRel(1)
            .height(18)
            .background(UITexture.builder()
                .location(GregTech.ID, "gui/base/button_standard")
                .build())
            .overlay(IKey.str("Option 3"))
            .onMousePressed(d -> {
                // TODO: Implement option 3 functionality
                return true;
            })
            .tooltipBuilder(t -> t.addLine(IKey.str("Option 3 - Not yet implemented")));
    }
}

