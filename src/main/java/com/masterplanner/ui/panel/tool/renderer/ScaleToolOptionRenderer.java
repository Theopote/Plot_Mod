package com.masterplanner.ui.panel.tool.renderer;

import com.masterplanner.ui.tools.impl.modify.strategy.ScaleStrategy;
import com.masterplanner.ui.theme.ThemeManager;
import com.masterplanner.ui.theme.UITheme;
import com.masterplanner.utils.ImGuiUtils;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiTreeNodeFlags;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 缩放工具选项渲染器 (重新设计版)
 *
 * <p>提供缩放工具的配置选项，包括三个缩放中心模式按钮：</p>
 * <ul>
 *   <li>自定义中心：用户点击确定缩放中心，三点缩放</li>
 *   <li>选择中心：以选择框中心为缩放中心，两点缩放</li>
 *   <li>图形中心：以每个图形中心为缩放中心，两点缩放</li>
 * </ul>
 *
 * @author MasterPlanner Team
 * @version 3.0 - 重新设计版
 */
public class ScaleToolOptionRenderer extends AbstractToolOptionRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScaleToolOptionRenderer.class);

    // 配置键常量
    private static final String CONFIG_KEY_MODE = "mode";
    private static final String CONFIG_KEY_CENTER = "center";
    // UI调整：删除保持宽高比与缩放步长；新增轴向选择键（仅用于前端UI传递）
    private static final String CONFIG_KEY_AXIS = "axis"; // 可选值：X / Y / BOTH
    
    // 缩放中心模式常量
    private static final String CENTER_MODE_CUSTOM = "CUSTOM";
    private static final String CENTER_MODE_SELECTION = "SELECTION";
    private static final String CENTER_MODE_SHAPE = "SHAPE";
    
    // UI状态变量
    private ScaleStrategy.ScaleMode currentMode;
    private String currentCenterMode;
    // 缩放方式：0=等比缩放，1=仅X轴，2=仅Y轴
    private int scaleModeSelectionIndex = 0;
    
    // 图标ID
    private final int customCenterIconId;
    private final int selectionCenterIconId;
    private final int shapeCenterIconId;
    
    // 资源管理标志
    private boolean resourcesInitialized;
    
    // 按钮尺寸常量
    private static final float BUTTON_SIZE = 32.0f;
    private static final float BUTTON_SPACING = 8.0f;

    public ScaleToolOptionRenderer() {
        super("scale");
        
        // 加载图标
        this.customCenterIconId = ImGuiUtils.getTextureId(
            Identifier.of("masterplanner", "textures/gui/tooloptionspanel/custom_center.png"));
        this.selectionCenterIconId = ImGuiUtils.getTextureId(
            Identifier.of("masterplanner", "textures/gui/tooloptionspanel/selection_center.png"));
        this.shapeCenterIconId = ImGuiUtils.getTextureId(
            Identifier.of("masterplanner", "textures/gui/tooloptionspanel/shape_center.png"));
        this.resourcesInitialized = true;
    }

    @Override
    public void initialize() {
        // 初始化默认值
        this.currentMode = ScaleStrategy.ScaleMode.UNIFORM;
        this.currentCenterMode = CENTER_MODE_CUSTOM; // 默认为自定义中心
        this.scaleModeSelectionIndex = 0; // 等比缩放
        
        LOGGER.debug("缩放工具选项渲染器已初始化");
    }

    @Override
    public float render() {
        float height = 0;
        ImGui.pushID("scale_options");
        
        // 样式栈计数器，用于确保正确清理
        int styleColorCount = 0;
        int styleVarCount = 0;
        
        try {
            // 安全检查：确保currentCenterMode不为null
            if (currentCenterMode == null) {
                LOGGER.warn("currentCenterMode为null，重新初始化");
                currentCenterMode = CENTER_MODE_CUSTOM;
            }
            
            UITheme.ThemeColors currentTheme = ThemeManager.getInstance().getCurrentTheme();
            float originalRounding = ImGui.getStyle().getFrameRounding();
            
            // === 缩放中心模式选择 ===
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ImGui.alignTextToFramePadding();
            ImGui.text("缩放中心");
            
            ImGui.getStyle().setFrameRounding(currentTheme.toolbarControlRounding);
            ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1.0f);
            styleVarCount++;
            
            ImGui.tableNextColumn();
            float firstButtonX = ImGui.getCursorPosX();
            
            // 自定义中心按钮
            boolean isCustomSelected = CENTER_MODE_CUSTOM.equals(currentCenterMode);
            pushButtonStyle(currentTheme, isCustomSelected);
            styleColorCount += 4;
            ImGui.pushID("custom_center");
            if (ImGui.imageButton(customCenterIconId, BUTTON_SIZE, BUTTON_SIZE)) {
                if (!isCustomSelected) {
                    currentCenterMode = CENTER_MODE_CUSTOM;
                    updateToolConfig(CONFIG_KEY_CENTER, CENTER_MODE_CUSTOM.toLowerCase());
                }
            }
            ImGui.popID();
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("自定义中心：点击确定缩放中心，三点缩放");
            }
            ImGui.popStyleColor(4);
            styleColorCount -= 4;
            
            ImGui.sameLine();
            ImGui.setCursorPosX(firstButtonX + (BUTTON_SIZE + BUTTON_SPACING));
            
            // 选择中心按钮
            boolean isSelectionSelected = CENTER_MODE_SELECTION.equals(currentCenterMode);
            pushButtonStyle(currentTheme, isSelectionSelected);
            styleColorCount += 4;
            ImGui.pushID("selection_center");
            if (ImGui.imageButton(selectionCenterIconId, BUTTON_SIZE, BUTTON_SIZE)) {
                if (!isSelectionSelected) {
                    currentCenterMode = CENTER_MODE_SELECTION;
                    updateToolConfig(CONFIG_KEY_CENTER, CENTER_MODE_SELECTION.toLowerCase());
                }
            }
            ImGui.popID();
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("选择中心：以选择框中心为缩放中心，两点缩放");
            }
            ImGui.popStyleColor(4);
            styleColorCount -= 4;
            
            ImGui.sameLine();
            ImGui.setCursorPosX(firstButtonX + (BUTTON_SIZE + BUTTON_SPACING) * 2);
            
            // 图形中心按钮
            boolean isShapeSelected = CENTER_MODE_SHAPE.equals(currentCenterMode);
            pushButtonStyle(currentTheme, isShapeSelected);
            styleColorCount += 4;
            ImGui.pushID("shape_center");
            if (ImGui.imageButton(shapeCenterIconId, BUTTON_SIZE, BUTTON_SIZE)) {
                if (!isShapeSelected) {
                    currentCenterMode = CENTER_MODE_SHAPE;
                    updateToolConfig(CONFIG_KEY_CENTER, CENTER_MODE_SHAPE.toLowerCase());
                }
            }
            ImGui.popID();
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("图形中心：以每个图形中心为缩放中心，两点缩放");
            }
            ImGui.popStyleColor(4);
            styleColorCount -= 4;
            ImGui.popStyleVar();
            styleVarCount--;
            
            height += BUTTON_SIZE + ImGui.getStyle().getFramePadding().y * 2;
            
            // === 缩放方式（下拉列表） ===
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ImGui.alignTextToFramePadding();
            ImGui.text("缩放方式");

            ImGui.tableNextColumn();
            ImGui.pushItemWidth(-1);
            String currentLabel = switch (scaleModeSelectionIndex) {
                case 1 -> "仅X轴";
                case 2 -> "仅Y轴";
                default -> "等比缩放";
            };
            if (ImGui.beginCombo("##scale_mode_combo", currentLabel)) {
                if (ImGui.selectable("等比缩放", scaleModeSelectionIndex == 0)) {
                    scaleModeSelectionIndex = 0;
                    currentMode = ScaleStrategy.ScaleMode.UNIFORM;
                    updateToolConfig(CONFIG_KEY_MODE, currentMode.name());
                    updateToolConfig(CONFIG_KEY_AXIS, "BOTH");
                }
                if (ImGui.selectable("仅X轴", scaleModeSelectionIndex == 1)) {
                    scaleModeSelectionIndex = 1;
                    currentMode = ScaleStrategy.ScaleMode.NON_UNIFORM;
                    updateToolConfig(CONFIG_KEY_MODE, currentMode.name());
                    updateToolConfig(CONFIG_KEY_AXIS, "X");
                }
                if (ImGui.selectable("仅Y轴", scaleModeSelectionIndex == 2)) {
                    scaleModeSelectionIndex = 2;
                    currentMode = ScaleStrategy.ScaleMode.NON_UNIFORM;
                    updateToolConfig(CONFIG_KEY_MODE, currentMode.name());
                    updateToolConfig(CONFIG_KEY_AXIS, "Y");
                }
                ImGui.endCombo();
            }
            ImGui.popItemWidth();
            height += ImGui.getFrameHeightWithSpacing();
            
            // 恢复原始的圆角设置
            ImGui.getStyle().setFrameRounding(originalRounding);
            
            // === 使用说明 ===
            renderUsageInstructions();
            
            // === 快捷键提示 ===
            renderShortcutTips();
            
        } catch (Exception e) {
            LOGGER.error("缩放工具选项渲染器渲染失败: {}", e.getMessage(), e);
            
            // 紧急清理样式栈
            while (styleColorCount > 0) {
                try {
                    ImGui.popStyleColor();
                    styleColorCount--;
                } catch (Exception ex) {
                    LOGGER.warn("清理样式颜色栈失败: {}", ex.getMessage());
                    break;
                }
            }
            
            while (styleVarCount > 0) {
                try {
                    ImGui.popStyleVar();
                    styleVarCount--;
                } catch (Exception ex) {
                    LOGGER.warn("清理样式变量栈失败: {}", ex.getMessage());
                    break;
                }
            }
        } finally {
            ImGui.popID();
        }
        
        return height;
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
    
    /**
     * 渲染使用说明
     */
    private void renderUsageInstructions() {
        if (ImGui.collapsingHeader("使用说明", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.textWrapped("缩放工具使用步骤：");
            ImGui.spacing();
            
            ImGui.bulletText("1. 使用选择工具选择要缩放的图形");
            ImGui.bulletText("2. 切换到缩放工具");
            
            // 根据当前模式显示不同的交互步骤
            String centerMode = currentCenterMode != null ? currentCenterMode : CENTER_MODE_CUSTOM;
            switch (centerMode) {
                case CENTER_MODE_SELECTION -> {
                    ImGui.textColored(0.6f, 1.0f, 0.6f, 1.0f, "选择中心模式：");
                    ImGui.bulletText("3. 第一次点击：设置参考点（确定基准距离）");
                    ImGui.bulletText("4. 移动鼠标缩放图形，第二次点击完成缩放");
                }
                case CENTER_MODE_SHAPE -> {
                    ImGui.textColored(0.6f, 1.0f, 0.6f, 1.0f, "图形中心模式：");
                    ImGui.bulletText("3. 第一次点击：设置参考点（确定基准距离）");
                    ImGui.bulletText("4. 移动鼠标缩放图形，第二次点击完成缩放");
                }
                default -> {
                    ImGui.textColored(1.0f, 1.0f, 0.6f, 1.0f, "自定义中心模式：");
                    ImGui.bulletText("3. 第一次点击：设置缩放中心点");
                    ImGui.bulletText("4. 第二次点击：设置参考点（确定基准距离）");
                    ImGui.bulletText("5. 移动鼠标缩放图形，第三次点击完成缩放");
                }
            }
            
            ImGui.spacing();
            ImGui.textWrapped("缩放方式说明：");
            ImGui.bulletText("等比缩放：X和Y方向使用相同的缩放因子");
            ImGui.bulletText("仅X轴：仅沿X方向缩放");
            ImGui.bulletText("仅Y轴：仅沿Y方向缩放");
            
            ImGui.spacing();
            ImGui.textWrapped("缩放中心说明：");
            ImGui.bulletText("自定义中心：用户指定的点作为缩放中心，三点缩放");
            ImGui.bulletText("选择中心：以选择框的中心为缩放中心，两点缩放");
            ImGui.bulletText("图形中心：以每个图形的中心为缩放中心，两点缩放");
        }
    }
    
    /**
     * 渲染快捷键提示
     */
    private void renderShortcutTips() {
        if (ImGui.collapsingHeader("快捷键", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.textColored(0.9f, 0.6f, 0.3f, 1.0f, "快捷键提示：");
            ImGui.spacing();
            ImGui.bulletText("Shift：临时启用统一缩放模式");
            ImGui.bulletText("右键 / Esc：取消当前缩放操作");
            
            ImGui.spacing();
            ImGui.textColored(0.7f, 0.7f, 0.7f, 1.0f, "提示：");
            ImGui.textWrapped("""
                    • 等比缩放适合等比例调整图形大小
                    • 仅X轴/仅Y轴适合沿单一方向调整尺寸
                    • 缩放时会显示实时预览效果""");
        }
    }

    @Override
    public void cleanup() {
        // 释放纹理资源
        if (resourcesInitialized) {
            try {
                ImGuiUtils.deleteTexture(customCenterIconId);
                ImGuiUtils.deleteTexture(selectionCenterIconId);
                ImGuiUtils.deleteTexture(shapeCenterIconId);
                resourcesInitialized = false;
                LOGGER.debug("缩放工具选项渲染器资源已释放");
            } catch (Exception e) {
                LOGGER.warn("释放缩放工具选项渲染器资源失败: {}", e.getMessage());
            }
        }
    }
} 