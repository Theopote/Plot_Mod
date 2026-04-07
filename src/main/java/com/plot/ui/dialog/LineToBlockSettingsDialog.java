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

                    String[] modes = new String[]{
                        ConversionMode.FULL.getDisplayName(),
                        ConversionMode.SIMPLIFIED.getDisplayName()
                    };
                    ImInt currentMode = new ImInt(conversionMode.ordinal());
                    if (ImGui.combo("转换模式##conversion_mode", currentMode, modes)) {
                        conversionMode = ConversionMode.values()[currentMode.get()];
                    }

                    DialogLayoutHelper.helpText(conversionMode == ConversionMode.FULL
                            ? "完整转换：线条经过方块投影方格的区域全部转换为方块"
                            : "精简转换：经过投影方格的线条的长度超过方块边长的指定比例时才转换");

                    DialogLayoutHelper.sectionSeparator();
                    ImBoolean fillOption = new ImBoolean(fillClosedShapes);
                    if (ImGui.checkbox("封闭图形填充##fill_closed_shapes", fillOption)) {
                        fillClosedShapes = fillOption.get();
                    }
                    DialogLayoutHelper.helpText(fillClosedShapes
                            ? "启用：封闭图形会填充内部区域。"
                            : "关闭：封闭图形只转换边缘轮廓。");

                    if (conversionMode == ConversionMode.SIMPLIFIED) {
                        DialogLayoutHelper.sectionSeparator();
                        ImGui.text("精简比率");
                        float[] ratio = new float[]{simplificationRatio};
                        if (ImGui.sliderFloat("##simplification_ratio", ratio, 0.1f, 1.0f, "%.2f")) {
                            simplificationRatio = ratio[0];
                        }
                        DialogLayoutHelper.helpText(String.format(
                                "当线条在方块中的长度超过方块边长的 %.2f 倍时才会转换为方块", simplificationRatio));
                    }

                    DialogLayoutHelper.endSection();
                    DialogLayoutHelper.beginFooter();
                    DialogLayoutHelper.FooterResult action =
                            DialogLayoutHelper.footerConfirmCancelRight("取消", "确定", DialogStyleManager.getContentWidth());

                    if (action.confirmClicked()) {
                        close();
                    }

                    if (action.cancelClicked()) {
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