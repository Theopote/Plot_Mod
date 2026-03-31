package com.plot.ui.dialog;

import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import imgui.type.ImInt;
import imgui.type.ImBoolean;
import imgui.flag.ImGuiWindowFlags;
import com.plot.core.state.AppState;
import com.plot.infrastructure.event.EventBus;
import com.plot.ui.theme.ThemeManager;
import com.plot.ui.theme.UITheme;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LineToBlockSettingsDialog {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/LineToBlockSettingsDialog");
    private static LineToBlockSettingsDialog INSTANCE;
    private final AppState appState;
    private final EventBus eventBus;
    
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
        this.appState = AppState.getInstance();
        this.eventBus = EventBus.getInstance();
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

        UITheme.ThemeColors theme = ThemeManager.getInstance().getCurrentTheme();

        ImGui.pushStyleColor(ImGuiCol.WindowBg, theme.panelBackground);
        ImGui.pushStyleColor(ImGuiCol.Border, theme.buttonBorder);
        ImGui.pushStyleColor(ImGuiCol.Text, theme.text);
        ImGui.pushStyleColor(ImGuiCol.FrameBg, theme.inputBackground);
        ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, theme.inputBackgroundHovered);
        ImGui.pushStyleColor(ImGuiCol.FrameBgActive, theme.inputBackgroundActive);
        ImGui.pushStyleColor(ImGuiCol.Button, theme.buttonNormal);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, theme.buttonHovered);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, theme.buttonActive);
        ImGui.pushStyleColor(ImGuiCol.Header, theme.tabNormal);
        ImGui.pushStyleColor(ImGuiCol.HeaderHovered, theme.tabHovered);
        ImGui.pushStyleColor(ImGuiCol.HeaderActive, theme.tabActive);
        ImGui.pushStyleColor(ImGuiCol.Separator, theme.separatorColor);
        ImGui.pushStyleColor(ImGuiCol.SeparatorHovered, theme.buttonHovered);
        ImGui.pushStyleColor(ImGuiCol.SeparatorActive, theme.buttonActive);

        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 0.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.ChildRounding, 0.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.PopupRounding, 0.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, 0.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.GrabRounding, 0.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.ScrollbarRounding, 0.0f);

        ImGui.setNextWindowSize(400, 0);
        // 重要：无论 begin() 返回 true/false，都必须 end()，否则会触发 ImGui 的窗口栈断言
        boolean windowVisible = ImGui.begin("线转方块设置##LineToBlockSettings", ImGuiWindowFlags.AlwaysAutoResize);
        try {
            if (windowVisible) {
                // 转换模式选择
                String[] modes = new String[]{
                    ConversionMode.FULL.getDisplayName(),
                    ConversionMode.SIMPLIFIED.getDisplayName()
                };
                ImInt currentMode = new ImInt(conversionMode.ordinal());
                if (ImGui.combo("转换模式##conversion_mode", currentMode, modes)) {
                    conversionMode = ConversionMode.values()[currentMode.get()];
                }

                // 显示模式说明
                ImGui.textWrapped(conversionMode == ConversionMode.FULL ?
                    "完整转换：线条经过方块投影方格的区域全部转换为方块" :
                    "精简转换：经过投影方格的线条的长度超过方块边长的指定比例时才转换");

                ImGui.separator();
                UITheme.ThemeColors currentTheme = ThemeManager.getInstance().getCurrentTheme();
                ImGui.pushStyleColor(ImGuiCol.FrameBg, currentTheme.controlBackground);
                ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, currentTheme.buttonHovered);
                ImGui.pushStyleColor(ImGuiCol.FrameBgActive, currentTheme.buttonActive);
                ImGui.pushStyleColor(ImGuiCol.CheckMark, currentTheme.accent);
                ImGui.pushStyleColor(ImGuiCol.Border, currentTheme.buttonBorder);
                ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1.0f);
                ImBoolean fillOption = new ImBoolean(fillClosedShapes);
                if (ImGui.checkbox("封闭图形填充##fill_closed_shapes", fillOption)) {
                    fillClosedShapes = fillOption.get();
                }
                ImGui.popStyleVar();
                ImGui.popStyleColor(5);
                ImGui.textWrapped(fillClosedShapes
                        ? "启用：封闭图形会填充内部区域。"
                        : "关闭：封闭图形只转换边缘轮廓。");

                // 精简比率滑动条（仅在精简转换模式下显示）
                if (conversionMode == ConversionMode.SIMPLIFIED) {
                    ImGui.separator();
                    ImGui.text("精简比率");
                    float[] ratio = new float[]{simplificationRatio};
                    if (ImGui.sliderFloat("##simplification_ratio", ratio, 0.1f, 1.0f, "%.2f")) {
                        simplificationRatio = ratio[0];
                    }
                    ImGui.textWrapped(String.format("当线条在方块中的长度超过方块边长的 %.2f 倍时才会转换为方块", simplificationRatio));
                }

                ImGui.separator();

                // 按钮
                float buttonWidth = ImGui.getWindowWidth() * 0.3f;
                ImGui.setCursorPosX((ImGui.getWindowWidth() - buttonWidth * 2 - 10) * 0.5f);
                
                if (ImGui.button("确定##line_to_block_settings_ok", buttonWidth, 0)) {
                    close();
                }
                
                ImGui.sameLine();
                
                if (ImGui.button("取消##line_to_block_settings_cancel", buttonWidth, 0)) {
                    close();
                }
            }
        } catch (Exception e) {
            LOGGER.error("渲染线转方块设置对话框时发生错误", e);
        } finally {
            ImGui.end();
            ImGui.popStyleColor(15);
            ImGui.popStyleVar(6);
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