package com.plot.ui.dialog;

import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
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
        FULL("完整转换"),
        SIMPLIFIED("精简转换");

        private final String displayName;

        ConversionMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
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
                    | ImGuiWindowFlags.NoResize
                    | ImGuiWindowFlags.NoScrollbar
                    | ImGuiWindowFlags.NoSavedSettings;
            boolean windowVisible = ImGui.begin("线转方块设置##LineToBlockSettings", windowFlags);
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

                        DialogLayoutHelper.formRowLabel("转换模式");
                        ImInt currentMode = new ImInt(conversionMode.ordinal());
                        if (ImGui.combo("##conversion_mode", currentMode, modes)) {
                            conversionMode = ConversionMode.values()[currentMode.get()];
                        }
                        DialogLayoutHelper.formRowHelp(conversionMode == ConversionMode.FULL
                                ? "完整转换会尽量保留线条覆盖到的所有方块。"
                                : "精简转换会根据下方阈值跳过细碎覆盖区域。 ");

                        DialogLayoutHelper.formRowLabel("封闭填充");
                        ImBoolean fillOption = new ImBoolean(fillClosedShapes);
                        if (ImGui.checkbox("##fill_closed_shapes", fillOption)) {
                            fillClosedShapes = fillOption.get();
                        }
                        DialogLayoutHelper.formRowHelp(fillClosedShapes
                                ? "已启用封闭图形内部填充。"
                                : "当前仅转换封闭图形的边缘轮廓。");

                        if (conversionMode == ConversionMode.SIMPLIFIED) {
                            DialogLayoutHelper.formRowLabel("精简比率");
                            float[] ratio = new float[]{simplificationRatio};
                            if (ImGui.sliderFloat("##simplification_ratio", ratio, 0.1f, 1.0f, "%.2f")) {
                                simplificationRatio = ratio[0];
                            }
                            DialogLayoutHelper.formRowHelp(String.format("仅当线条覆盖长度超过 %.2f 倍方块边长时才转换。", simplificationRatio));
                        }

                        DialogLayoutHelper.endForm();
                    }

                    ImGui.separator();
                    if (DialogLayoutHelper.footerSingleCentered("关闭", DialogStyleManager.getContentWidth())
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