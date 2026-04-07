package com.plot.ui.dialog;

import imgui.ImGui;
import imgui.type.ImInt;
import imgui.type.ImBoolean;
import imgui.flag.ImGuiWindowFlags;
import com.plot.core.state.AppState;
import com.plot.infrastructure.event.EventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LineToBlockSettingsDialog {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/LineToBlockSettingsDialog");
    private static LineToBlockSettingsDialog INSTANCE;

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
        AppState appState = AppState.getInstance();
        EventBus eventBus = EventBus.getInstance();
    }

    public static LineToBlockSettingsDialog getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new LineToBlockSettingsDialog();
        }
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
            ImGui.setNextWindowSize(DialogStyleManager.DialogWidth.STANDARD.value, 0);
            boolean windowVisible = ImGui.begin("线转方块设置##LineToBlockSettings",
                    ImGuiWindowFlags.AlwaysAutoResize | ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoSavedSettings);
            try {
                if (windowVisible) {
                    if (DialogStyleManager.renderTopRightCloseButton("line_to_block")) {
                        close();
                    }

                    DialogLayoutHelper.beginSection("转换参数");
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

                        DialogLayoutHelper.formRowLabel("封闭填充");
                        ImBoolean fillOption = new ImBoolean(fillClosedShapes);
                        if (ImGui.checkbox("##fill_closed_shapes", fillOption)) {
                            fillClosedShapes = fillOption.get();
                        }

                        if (conversionMode == ConversionMode.SIMPLIFIED) {
                            DialogLayoutHelper.formRowLabel("精简比率");
                            float[] ratio = new float[]{simplificationRatio};
                            if (ImGui.sliderFloat("##simplification_ratio", ratio, 0.1f, 1.0f, "%.2f")) {
                                simplificationRatio = ratio[0];
                            }
                        }

                        DialogLayoutHelper.endForm();
                    }

                    DialogLayoutHelper.helpText(conversionMode == ConversionMode.FULL
                            ? "完整转换：线条经过方块投影方格的区域会全部转换为方块。"
                            : String.format("精简转换：仅当线条覆盖长度超过 %.2f 倍方块边长时才转换。", simplificationRatio));
                    DialogLayoutHelper.helpText(fillClosedShapes
                            ? "已启用封闭图形内部填充。"
                            : "当前仅转换封闭图形的边缘轮廓。");

                    DialogLayoutHelper.endSection();
                    DialogLayoutHelper.beginFooter();
                    DialogLayoutHelper.FooterResult action =
                            DialogLayoutHelper.footerConfirmCancelRight("取消", "确定", DialogStyleManager.getContentWidth());

                    if (action.confirmClicked() || DialogLayoutHelper.isConfirmShortcutPressed()) {
                        close();
                    }

                    if (action.cancelClicked() || DialogLayoutHelper.isCancelShortcutPressed()) {
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