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
    private static final String CONFIG_KEY_SAG = "sag";
    private static final String CONFIG_KEY_SEGMENTS = "segments";

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
     * 设置滑块控件的样式
     */
    private void pushSliderStyle(UITheme.ThemeColors theme) {
        ImGui.pushStyleColor(ImGuiCol.FrameBg, theme.controlBackground);
        ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, theme.buttonHovered);
        ImGui.pushStyleColor(ImGuiCol.FrameBgActive, theme.buttonActive);
        ImGui.pushStyleColor(ImGuiCol.SliderGrab, theme.sliderGrab);
        ImGui.pushStyleColor(ImGuiCol.SliderGrabActive, theme.sliderGrabActive);
        ImGui.pushStyleColor(ImGuiCol.Border, theme.frameBorder);

        ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.GrabRounding, theme.grabRounding);
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

            // === 悬垂参数滑块 (根据模式禁用) ===
            boolean isSagParamDisabled = MODE_UNEVEN.equals(catenaryMode);
            if (isSagParamDisabled) {
                ImGui.beginDisabled();
            }
            String sagTooltip = isSagParamDisabled ? "此参数在样条插值模式下无效" : "控制悬链线下垂程度：值越小，下垂越明显";
            renderSlider("悬垂参数", sagParameter, 0.05f, 5.0f, "%.2f", CONFIG_KEY_SAG, sagTooltip, currentTheme);
            if (isSagParamDisabled) {
                ImGui.endDisabled();
            }
            height += ImGui.getFrameHeightWithSpacing();

            // === 分段数滑块 ===
            renderSlider("分段数", segmentCount, 10, 60, "%d", CONFIG_KEY_SEGMENTS, "控制悬链线的平滑度：值越大，曲线越平滑", currentTheme);
            height += ImGui.getFrameHeightWithSpacing();

            ImGui.getStyle().setFrameRounding(originalRounding);

        } finally {
            ImGui.popID();
        }

        return height;
    }

    /**
     * 渲染滑块控件
     */
    private void renderSlider(String label, Object value, float min, float max, String format,
                            String configKey, String tooltip, UITheme.ThemeColors theme) {
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.alignTextToFramePadding();
        ImGui.text(label);

        pushSliderStyle(theme);

        ImGui.tableNextColumn();
        ImGui.pushItemWidth(-1);

        boolean changed = false;
        if (value instanceof ImFloat) {
            changed = ImGui.sliderFloat("##" + configKey, ((ImFloat)value).getData(), min, max, format);
        } else if (value instanceof ImInt) {
            changed = ImGui.sliderInt("##" + configKey, ((ImInt)value).getData(), (int)min, (int)max, format);
        }

        if (changed) {
            updateToolConfig(configKey, String.valueOf(value instanceof ImFloat ?
                ((ImFloat)value).get() : ((ImInt)value).get()));
        }

        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(tooltip);
        }

        ImGui.popItemWidth();
        ImGui.popStyleVar(2);
        ImGui.popStyleColor(6);
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