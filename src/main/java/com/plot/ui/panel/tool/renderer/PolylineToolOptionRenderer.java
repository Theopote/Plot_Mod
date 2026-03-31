package com.plot.ui.panel.tool.renderer;

import com.plot.utils.ImGuiUtils;
import com.plot.ui.theme.ThemeManager;
import com.plot.ui.theme.UITheme;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import net.minecraft.util.Identifier;

/**
 * 路径工具选项渲染器
 * 支持折线模式和钢笔模式
 */
public class PolylineToolOptionRenderer extends AbstractToolOptionRenderer {
    private static final String CONFIG_KEY_MODE = "mode";
    
    // 模式常量
    private static final String MODE_POLYLINE = "polyline";
    private static final String MODE_PEN = "pen";

    private final int polylineModeIconId;
    private final int penModeIconId;

    private String drawMode = MODE_POLYLINE;  // 默认使用折线模式

    public PolylineToolOptionRenderer() {
        super("polyline");

        // 加载图标
        this.polylineModeIconId = ImGuiUtils.getTextureId(
            Identifier.of("plot", "textures/gui/tooloptionspanel/polyline_normal.png"));
        this.penModeIconId = ImGuiUtils.getTextureId(
            Identifier.of("plot", "textures/gui/tooloptionspanel/pen_mode.png"));

        // 初始化时发送默认模式配置
        updateToolConfig(CONFIG_KEY_MODE, MODE_POLYLINE);
    }

    @Override
    public float render() {
        float height = 0;
        ImGui.pushID("polyline_options");

        try {
            // 获取当前主题
            UITheme.ThemeColors currentTheme = ThemeManager.getInstance().getCurrentTheme();

            // 使用 pushStyleVar 临时设置圆角，避免永久修改共享 ImGui 样式（影响其他模组）
            ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, currentTheme.toolbarControlRounding);

            // 绘制模式选择
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ImGui.alignTextToFramePadding();
            ImGui.text("绘制模式");

            // 设置按钮颜色样式
            ImGui.pushStyleColor(ImGuiCol.Button, currentTheme.buttonNormal);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, currentTheme.buttonHovered);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, currentTheme.buttonActive);
            ImGui.pushStyleColor(ImGuiCol.Border, currentTheme.buttonBorder);

            // 设置边框样式
            ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1.0f);

            ImGui.tableNextColumn();
            float firstButtonX = ImGui.getCursorPosX();

            // 渲染两个模式按钮
            String[] modes = {MODE_POLYLINE, MODE_PEN};
            int[] icons = {polylineModeIconId, penModeIconId};
            String[] tooltips = {"折线模式 - 点击添加直线段", "钢笔模式 - 点击创建锚点，拖动创建曲线控制点"};

            for (int i = 0; i < modes.length; i++) {
                if (i > 0) {
                    ImGui.sameLine();
                    ImGui.setCursorPosX(firstButtonX + (BUTTON_SIZE + BUTTON_SPACING * 2) * i);
                }

                boolean isSelected = drawMode.equals(modes[i]);

                // 为选中的按钮应用特殊样式
                if (isSelected) {
                    ImGui.pushStyleColor(ImGuiCol.Button, currentTheme.buttonSelected);
                    ImGui.pushStyleColor(ImGuiCol.ButtonHovered, currentTheme.buttonSelectedHovered);
                    ImGui.pushStyleColor(ImGuiCol.ButtonActive, currentTheme.buttonSelectedActive);
                    ImGui.pushStyleColor(ImGuiCol.Border, currentTheme.buttonActiveBorder);
                }

                ImGui.pushID("polyline_mode_" + i);
                boolean clicked = com.plot.ui.component.UIUtils.imageButtonNoPadding(icons[i], BUTTON_SIZE, BUTTON_SIZE);
                ImGui.popID();

                if (clicked && !isSelected) {
                    drawMode = modes[i];
                    updateToolConfig(CONFIG_KEY_MODE, modes[i]);
                }

                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(tooltips[i]);
                }

                // 恢复选中按钮的样式
                if (isSelected) {
                    ImGui.popStyleColor(4);
                }
            }

            // 恢复样式
            ImGui.popStyleVar();
            ImGui.popStyleColor(4);
            ImGui.popStyleVar();  // FrameRounding

            height += BUTTON_SIZE + ImGui.getStyle().getFramePadding().y * 2;

        } finally {
            ImGui.popID();
        }

        return height;
    }


    @Override
    public void initialize() {
        drawMode = MODE_POLYLINE;
    }

    @Override
    public void cleanup() {
        // 清理纹理资源
        ImGuiUtils.deleteTexture(polylineModeIconId);
        ImGuiUtils.deleteTexture(penModeIconId);
    }
} 