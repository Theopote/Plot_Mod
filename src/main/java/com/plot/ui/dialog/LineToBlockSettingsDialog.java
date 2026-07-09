package com.plot.ui.dialog;

import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import com.plot.utils.PlotI18n;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LineToBlockSettingsDialog {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/LineToBlockSettingsDialog");
    private static final LineToBlockSettingsDialog INSTANCE = new LineToBlockSettingsDialog();

    private boolean isOpen = false;
    private ConversionMode conversionMode = ConversionMode.FULL;
    private float simplificationRatio = 0.5f;
    private boolean fillClosedShapes = false;

    public enum ConversionMode {
        FULL("mode.plot.full_conversion"),
        SIMPLIFIED("mode.plot.simplified_conversion");

        private final String displayName;

        ConversionMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return PlotI18n.tr(displayName);
        }
    }

    private LineToBlockSettingsDialog() {
    }

    public static LineToBlockSettingsDialog getInstance() {
        return INSTANCE;
    }

    public void open() {
        isOpen = true;
    }

    public void close() {
        isOpen = false;
    }

    public void render() {
        if (!isOpen) return;

        DialogStyleManager.DialogStyleScope styleScope = DialogStyleManager.applyDialogStyle();
        
        try {
            var center = ImGui.getMainViewport().getCenter();
            ImGui.setNextWindowPos(center.x, center.y, ImGuiCond.Appearing, 0.5f, 0.5f);
            // 使用更贴近实际内容的初始宽度，避免 Appearing 首帧偏宽后再收窄。
            ImGui.setNextWindowSize(DialogStyleManager.DialogWidth.COMPACT.value, 0, ImGuiCond.Appearing);
            int windowFlags = ImGuiWindowFlags.AlwaysAutoResize
                    | ImGuiWindowFlags.NoCollapse
                    | ImGuiWindowFlags.NoResize
                    | ImGuiWindowFlags.NoScrollbar
                    | ImGuiWindowFlags.NoSavedSettings;
            boolean windowVisible = ImGui.begin(PlotI18n.tr("screen.plot.line_to_block_settings") + "##LineToBlockSettings", windowFlags);
            try {
                if (windowVisible) {
                    if (DialogStyleManager.renderTopRightCloseButton("line_to_block")) {
                        close();
                    }

                    if (DialogLayoutHelper.beginForm("##line_to_block_form")) {
                        String[] modes = new String[]{
                                ConversionMode.FULL.getDisplayName(),
                                ConversionMode.SIMPLIFIED.getDisplayName()
                        };

                        DialogLayoutHelper.formRowLabel(PlotI18n.tr("dialog.plot.conversion_mode"));
                        ImInt currentMode = new ImInt(conversionMode.ordinal());
                        if (ImGui.combo("##conversion_mode", currentMode, modes)) {
                            conversionMode = ConversionMode.values()[currentMode.get()];
                        }
                        DialogLayoutHelper.formRowHelp(conversionMode == ConversionMode.FULL
                                ? PlotI18n.tr("dialog.plot.linetoblock_full_help")
                                : PlotI18n.tr("dialog.plot.linetoblock_simplified_help"));

                        DialogLayoutHelper.formRowLabel(PlotI18n.tr("dialog.plot.closed_fill"));
                        ImBoolean fillOption = new ImBoolean(fillClosedShapes);
                        if (ImGui.checkbox("##fill_closed_shapes", fillOption)) {
                            fillClosedShapes = fillOption.get();
                        }
                        DialogLayoutHelper.formRowHelp(fillClosedShapes
                                ? PlotI18n.tr("dialog.plot.linetoblock_fill_enabled_help")
                                : PlotI18n.tr("dialog.plot.linetoblock_fill_disabled_help"));

                        if (conversionMode == ConversionMode.SIMPLIFIED) {
                            DialogLayoutHelper.formRowLabel(PlotI18n.tr("dialog.plot.simplify_ratio"));
                            float[] ratio = new float[]{simplificationRatio};
                            if (ImGui.sliderFloat("##simplification_ratio", ratio, 0.1f, 1.0f, "%.2f")) {
                                simplificationRatio = ratio[0];
                            }
                            DialogLayoutHelper.formRowHelp(fillClosedShapes
                                    ? PlotI18n.tr("dialog.plot.linetoblock_simplify_threshold_fill", simplificationRatio)
                                    : PlotI18n.tr("dialog.plot.linetoblock_simplify_threshold_line", simplificationRatio));
                        }

                        DialogLayoutHelper.endForm();
                    }

                    ImGui.separator();
                    if (DialogLayoutHelper.footerSingleCentered(PlotI18n.tr("button.plot.close"), DialogStyleManager.getContentWidth())
                            || DialogLayoutHelper.isCancelShortcutPressed()
                            || DialogLayoutHelper.isConfirmShortcutPressed()) {
                        close();
                    }
                }
            } catch (Exception e) {
                LOGGER.error("渲染线转方块设置对话框时发生错误", e);
            } finally {
                ImGui.end();
            }
        } finally {
            DialogStyleManager.popDialogStyle(styleScope);
        }
    }

    public ConversionMode getConversionMode() {
        return conversionMode;
    }

    public float getSimplificationRatio() {
        return simplificationRatio;
    }

    public boolean isFillClosedShapes() {
        return fillClosedShapes;
    }
} 