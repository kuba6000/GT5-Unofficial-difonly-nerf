package gregtech.common.gui.modularui.cover;

import static net.minecraft.util.StatCollector.translateToLocal;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.DoubleSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

import gregtech.api.modularui2.CoverGuiData;
import gregtech.api.modularui2.GTGuiTextures;
import gregtech.common.covers.CoverIFNDetectorBase;
import gregtech.common.gui.modularui.cover.base.CoverBaseGui;

public final class CoverIFNDetectorGui extends CoverBaseGui<CoverIFNDetectorBase> {

    public CoverIFNDetectorGui(CoverIFNDetectorBase cover) {
        super(cover);
    }

    @Override
    public void addUIWidgets(PanelSyncManager syncManager, Flow column, CoverGuiData data) {
        column.child(
            makeRowLayout()
                .child(positionRow(makeMinValueRow()))
                .child(positionRow(makeMaxValueRow()))
                .child(positionRow(makeModeRow()))
                .child(positionRow(makeSourceModeRow())));
    }

    private Flow makeMinValueRow() {
        return makeDoubleFieldRow(
            "ifn_detector_min",
            "gt.interact.desc.ifn_detector.min",
            new DoubleSyncValue(cover::getMinValue, cover::setMinValue));
    }

    private Flow makeMaxValueRow() {
        return makeDoubleFieldRow(
            "ifn_detector_max",
            "gt.interact.desc.ifn_detector.max",
            new DoubleSyncValue(cover::getMaxValue, cover::setMaxValue));
    }

    private Flow makeDoubleFieldRow(String name, String labelKey, DoubleSyncValue value) {
        return Flow.row()
            .name(name)
            .child(makeDoubleField().value(value))
            .child(IKey.lang(labelKey).asWidget());
    }

    private TextFieldWidget makeDoubleField() {
        return new TextFieldWidget().setNumbersDouble(CoverIFNDetectorGui::sanitizeRangeValue)
            .width(80)
            .height(12);
    }

    static double sanitizeRangeValue(double value) {
        return value;
    }

    private Flow makeModeRow() {
        BooleanSyncValue linearMode = new BooleanSyncValue(cover::isLinearMode, cover::setLinearMode);
        return Flow.row()
            .name("ifn_detector_mode")
            .child(
                new ToggleButton().value(linearMode)
                    .overlay(true, GTGuiTextures.OVERLAY_BUTTON_CHECKMARK)
                    .overlay(false, GTGuiTextures.OVERLAY_BUTTON_CROSS)
                    .size(16, 16))
            .child(
                IKey.dynamic(
                    () -> translateToLocal(
                        linearMode.getValue()
                            ? "gt.interact.desc.ifn_detector.mode.linear"
                            : "gt.interact.desc.ifn_detector.mode.binary"))
                    .asWidget());
    }

    private Flow makeSourceModeRow() {
        BooleanSyncValue deltaSourceMode = new BooleanSyncValue(cover::isDeltaSourceMode, cover::setDeltaSourceMode);
        return Flow.row()
            .name("ifn_detector_source_mode")
            .child(
                new ToggleButton().value(deltaSourceMode)
                    .overlay(true, GTGuiTextures.OVERLAY_BUTTON_CHECKMARK)
                    .overlay(false, GTGuiTextures.OVERLAY_BUTTON_CROSS)
                    .size(16, 16))
            .child(
                IKey.dynamic(
                    () -> translateToLocal(
                        deltaSourceMode.getValue()
                            ? "gt.interact.desc.ifn_detector.source.delta"
                            : "gt.interact.desc.ifn_detector.source.absolute"))
                    .asWidget());
    }
}
