package com.masterplanner.ui.panel.tool.renderer;

import com.masterplanner.utils.ImGuiUtils;
import com.masterplanner.ui.theme.ThemeManager;
import com.masterplanner.ui.theme.UITheme;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import imgui.type.ImFloat;
import imgui.type.ImInt;
import net.minecraft.util.Identifier;

/**
 * 悬链线工具选项渲染器
 */
public class CatenaryLineToolOptionRenderer extends AbstractToolOptionRenderer {
    // 配置键
    private static final String CONFIG_KEY_MODE = "mode";

    // 绘制模式常量
    private static final String MODE_STANDARD = "standard";      // 标准悬链线
    private static final String MODE_UNEVEN = "uneven";          // 样条插值悬链线

    // 模式图标ID
    private final int standardModeIconId;
    private final int unevenModeIconId;

    // 悬链线模式：standard（标准悬链线）/ uneven（样条插值悬链线）
    private String catenaryMode = MODE_STANDARD;

    // 悬垂参数 (0.05-5.0)
    private final ImFloat sagParameter = new ImFloat(1.0f);

    // 分段数量 (10-60)
    private final ImInt segmentCount = new ImInt(20);

    public CatenaryLineToolOptionRenderer() {
        super("catenary");

        // 加载图标
        this.standardModeIconId = ImGuiUtils.getTextureId(
            Identifier.of("masterplanner", "textures/gui/tooloptionspanel/catenary_standard.png"));
        this.unevenModeIconId = ImGuiUtils.getTextureId(
            Identifier.of("masterplanner", "textures/gui/tooloptionspanel/catenary_spline.png"));
    }

    /**
     * 设置按钮的样式
     */
    private void pushButtonStyle(UITheme.ThemeColors theme, boolean isSelected) {
        if (isSelected) {
            ImGui.pushStyleColor(ImGuiCol.Button, theme.buttonSelected);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, theme.buttonSelectedHovered);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, theme.buttonSelectedActive);
            ImGui.pushStyleColor(ImGuiCol.Border, theme.buttonActiveBorder);
        } else {
            ImGui.pushStyleColor(ImGuiCol.Button, theme.buttonNormal);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, theme.buttonHovered);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, theme.buttonActive);
            ImGui.pushStyleColor(ImGuiCol.Border, theme.buttonBorder);
        }
    }

    @Override
    public float render() {
        float height = 0;
        ImGui.pushID("catenary_line_options");

        try {
            UITheme.ThemeColors currentTheme = ThemeManager.getInstance().getCurrentTheme();
            float originalRounding = ImGui.getStyle().getFrameRounding();

            // === 绘制模式选择 ===
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ImGui.alignTextToFramePadding();
            ImGui.text("绘制模式");

            ImGui.getStyle().setFrameRounding(currentTheme.toolbarControlRounding);
            ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1.0f);

            ImGui.tableNextColumn();
            float firstButtonX = ImGui.getCursorPosX();

            // 标准模式按钮
            boolean isStandardSelected = MODE_STANDARD.equals(catenaryMode);
            pushButtonStyle(currentTheme, isStandardSelected);
            ImGui.pushID("standard_mode");
            if (ImGui.imageButton(standardModeIconId, BUTTON_SIZE, BUTTON_SIZE)) {
                if (!isStandardSelected) {
                    catenaryMode = MODE_STANDARD;
                    updateToolConfig(CONFIG_KEY_MODE, catenaryMode);
                }
            }
            ImGui.popID();
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("标准模式：曲线对称，第三点控制弧垂深度");
            }
            ImGui.popStyleColor(4);

            ImGui.sameLine();
            ImGui.setCursorPosX(firstButtonX + (BUTTON_SIZE + BUTTON_SPACING * 2));

            // 样条插值模式按钮
            boolean isUnevenSelected = MODE_UNEVEN.equals(catenaryMode);
            pushButtonStyle(currentTheme, isUnevenSelected);
            ImGui.pushID("uneven_mode");
            if (ImGui.imageButton(unevenModeIconId, BUTTON_SIZE, BUTTON_SIZE)) {
                if (!isUnevenSelected) {
                    catenaryMode = MODE_UNEVEN;
                    updateToolConfig(CONFIG_KEY_MODE, catenaryMode);
                }
            }
            ImGui.popID();
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("样条插值模式：非对称曲线，第三点为控制点，可拖拽至任意位置");
            }
            ImGui.popStyleColor(4);
            ImGui.popStyleVar();
            height += BUTTON_SIZE + ImGui.getStyle().getFramePadding().y * 2;

            // 悬垂与分段数为固定参数，不在工具选项面板中显示

            ImGui.getStyle().setFrameRounding(originalRounding);

        } finally {
            ImGui.popID();
        }

        return height;
    }

    @Override
    public void initialize() {
        catenaryMode = MODE_STANDARD;
        sagParameter.set(1.0f);
        segmentCount.set(20);
    }

    @Override
    public void cleanup() {
        ImGuiUtils.deleteTexture(standardModeIconId);
        ImGuiUtils.deleteTexture(unevenModeIconId);
    }
}