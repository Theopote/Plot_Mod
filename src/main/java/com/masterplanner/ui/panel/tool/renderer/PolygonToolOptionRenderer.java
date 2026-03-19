package com.masterplanner.ui.panel.tool.renderer;

import com.masterplanner.MasterPlannerMod;
import com.masterplanner.core.config.ConfigManager;
import com.masterplanner.ui.tools.impl.drawing.PolygonTool;
import com.masterplanner.utils.ImGuiUtils;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import com.masterplanner.ui.theme.ThemeManager;
import com.masterplanner.ui.theme.UITheme;
import net.minecraft.util.Identifier;

/**
 * 正多边形工具选项渲染器
 * <p>
 * 支持多边形的边数和绘制模式配置
 */
public class PolygonToolOptionRenderer extends AbstractToolOptionRenderer {
    // ====== 配置键常量 ======
    private static final String CONFIG_KEY_SIDES = "sides";
    private static final String CONFIG_KEY_MODE = "mode";
    
    // ====== 默认值和范围 ======
    private static final int DEFAULT_SIDES = 6;
    private static final int MIN_SIDES = 3;
    private static final int MAX_SIDES = 20;
    
    // ====== 状态变量 ======
    private final int[] sidesArray = new int[]{DEFAULT_SIDES};  // 用于 sliderInt 的数组
    private PolygonTool.PolygonMode currentMode = PolygonTool.PolygonMode.CENTER_VERTEX;  // 默认为中心-顶点模式
    
    // ====== 图标资源 ======
    private final int centerRadiusIconId;
    private final int centerVertexIconId;

    public PolygonToolOptionRenderer() {
        super("polygon");
        
        // 加载专门的正多边形图标
        this.centerRadiusIconId = ImGuiUtils.getTextureId(
            Identifier.of("masterplanner", "textures/gui/tooloptionspanel/polygon_center_radius.png"));
        this.centerVertexIconId = ImGuiUtils.getTextureId(
            Identifier.of("masterplanner", "textures/gui/tooloptionspanel/polygon_center_vertex.png"));
    }

    @Override
    public float render() {
        float height = 0;
        ImGui.pushID("polygon_options");
        
        try {
            // 获取当前主题
            UITheme.ThemeColors currentTheme = ThemeManager.getInstance().getCurrentTheme();
            
            // ====== 第一行：绘制模式按钮 ======
            height += renderModeButtons(currentTheme);
            
            // ====== 第二行：边数滑动条 ======
            height += renderSidesSlider(currentTheme);
            
        } finally {
            ImGui.popID();
        }
        
        return height;
    }
    
    /**
     * 渲染绘制模式按钮（第一行）
     */
    private float renderModeButtons(UITheme.ThemeColors currentTheme) {
        // 模式标题
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.alignTextToFramePadding();
        ImGui.text("绘制模式");
        
        // 使用 pushStyleVar 临时设置圆角，避免永久修改共享 ImGui 样式
        ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, currentTheme.toolbarControlRounding);
        
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
        String[] modes = {
            PolygonTool.PolygonMode.CENTER_VERTEX.getConfigValue(), 
            PolygonTool.PolygonMode.CENTER_RADIUS.getConfigValue()
        };
        int[] icons = {centerVertexIconId, centerRadiusIconId};
        String[] tooltips = {"中心-顶点模式", "中心-半径模式"};
        String currentModeId = currentMode.getConfigValue();
        
        for (int i = 0; i < modes.length; i++) {
            if (i > 0) {
                ImGui.sameLine();
                ImGui.setCursorPosX(firstButtonX + (BUTTON_SIZE + BUTTON_SPACING * 2) * i);
            }
            
            boolean isSelected = currentModeId.equals(modes[i]);
            
            // 为选中的按钮应用特殊样式
            int pushedColorCount = 0;
            try {
                if (isSelected) {
                    ImGui.pushStyleColor(ImGuiCol.Button, currentTheme.buttonSelected);
                    pushedColorCount++;
                    ImGui.pushStyleColor(ImGuiCol.ButtonHovered, currentTheme.buttonSelectedHovered);
                    pushedColorCount++;
                    ImGui.pushStyleColor(ImGuiCol.ButtonActive, currentTheme.buttonSelectedActive);
                    pushedColorCount++;
                    ImGui.pushStyleColor(ImGuiCol.Border, currentTheme.buttonActiveBorder);
                    pushedColorCount++;
                }
                
                ImGui.pushID("polygon_mode_" + i);
                try {
                    boolean clicked = ImGui.imageButton(icons[i], BUTTON_SIZE, BUTTON_SIZE);
                    if (clicked && !isSelected) {
                        currentMode = PolygonTool.PolygonMode.fromConfigValue(modes[i]);
                        updateToolConfig(CONFIG_KEY_MODE, modes[i]);
                        MasterPlannerMod.LOGGER.debug("多边形绘制模式已更新为: {}", currentMode.getDisplayName());
                    }
                    if (ImGui.isItemHovered()) {
                        ImGui.setTooltip(tooltips[i]);
                    }
                } finally {
                    ImGui.popID();
                }
            } finally {
                // 恢复选中按钮的样式
                if (pushedColorCount > 0) {
                    ImGui.popStyleColor(pushedColorCount);
                }
            }
        }
        
        // 恢复样式（FrameBorderSize、FrameRounding）
        ImGui.popStyleVar();
        ImGui.popStyleVar();
        ImGui.popStyleColor(4);
        
        return BUTTON_SIZE + ImGui.getStyle().getFramePadding().y * 2;
    }
    
    /**
     * 渲染边数滑动条（第二行）
     */
    private float renderSidesSlider(UITheme.ThemeColors currentTheme) {
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.alignTextToFramePadding();
        ImGui.text("边数");
        
        // 应用控件样式
        ImGui.pushStyleColor(ImGuiCol.FrameBg, currentTheme.inputBackground);
        ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, currentTheme.inputBackgroundHovered);
        ImGui.pushStyleColor(ImGuiCol.FrameBgActive, currentTheme.inputBackgroundActive);
        ImGui.pushStyleColor(ImGuiCol.SliderGrab, currentTheme.sliderGrab);
        ImGui.pushStyleColor(ImGuiCol.SliderGrabActive, currentTheme.sliderGrabActive);
        ImGui.pushStyleColor(ImGuiCol.Border, currentTheme.frameBorder);
        
        ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.GrabRounding, currentTheme.grabRounding);
        
        ImGui.tableNextColumn();
        ImGui.pushItemWidth(-1);
        if (ImGui.sliderInt("##" + CONFIG_KEY_SIDES, sidesArray, MIN_SIDES, MAX_SIDES, "%d")) {
            updateToolConfig(CONFIG_KEY_SIDES, String.valueOf(sidesArray[0]));
            MasterPlannerMod.LOGGER.debug("正多边形边数已更新为: {}", sidesArray[0]);
        }
        ImGui.popItemWidth();
        
        ImGui.popStyleVar(2);
        ImGui.popStyleColor(6);
        
        return ImGui.getFrameHeightWithSpacing();
    }

    @Override
    public void initialize() {
        ConfigManager configManager = ConfigManager.getInstance();
        
        // ====== 初始化边数 ======
        sidesArray[0] = configManager.getInt(CONFIG_KEY_SIDES, DEFAULT_SIDES);
        sidesArray[0] = Math.max(MIN_SIDES, Math.min(MAX_SIDES, sidesArray[0]));
        
        // ====== 初始化绘制模式（默认为中心-顶点模式） ======
        String modeValue = configManager.getString(CONFIG_KEY_MODE, PolygonTool.PolygonMode.CENTER_VERTEX.getConfigValue());
        currentMode = PolygonTool.PolygonMode.fromConfigValue(modeValue);
        
        // ====== 发送初始配置到工具 ======
        updateToolConfig(CONFIG_KEY_SIDES, String.valueOf(sidesArray[0]));
        updateToolConfig(CONFIG_KEY_MODE, currentMode.getConfigValue());
        
        MasterPlannerMod.LOGGER.debug("正多边形工具选项已初始化 - 边数: {}, 模式: {}", 
                                    sidesArray[0], currentMode.getDisplayName());
    }

    @Override
    public void cleanup() {
        // 清理纹理资源
        ImGuiUtils.deleteTexture(centerRadiusIconId);
        ImGuiUtils.deleteTexture(centerVertexIconId);
    }
} 