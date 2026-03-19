package com.masterplanner.ui.panel.tool.renderer;

import com.masterplanner.MasterPlannerMod;
import com.masterplanner.core.state.AppState;
import com.masterplanner.core.tool.BaseTool;
import com.masterplanner.ui.tools.impl.modify.SelectionTool;
import com.masterplanner.ui.tools.impl.modify.strategy.SelectionStrategy;
import com.masterplanner.utils.ImGuiUtils;
import imgui.ImGui;
import imgui.ImVec4;
import imgui.flag.ImGuiCol;

import net.minecraft.util.Identifier;

/**
 * 选择工具选项渲染器
 * 负责渲染选择工具的选项界面
 */
public class SelectionToolOptionRenderer extends AbstractToolOptionRenderer {
    
    // 配置键常量
    private static final String CONFIG_KEY_MODE = SelectionTool.CONFIG_SELECTION_MODE;
    
    // 图标纹理ID
    private final int normalSelectIconId;
    private final int lassoSelectIconId;
    
    // 当前选择模式
    private String selectionMode = SelectionTool.CONFIG_VALUE_NORMAL;
    
    public SelectionToolOptionRenderer() {
        super("select");
        
        MasterPlannerMod.LOGGER.info("创建选择工具选项渲染器");
        
        // 加载图标
        this.normalSelectIconId = ImGuiUtils.getTextureId(
            Identifier.of("masterplanner", "textures/gui/tooloptionspanel/select_normal.png"));
        this.lassoSelectIconId = ImGuiUtils.getTextureId(
            Identifier.of("masterplanner", "textures/gui/tooloptionspanel/select_lasso.png"));
        
        MasterPlannerMod.LOGGER.info("选择工具选项渲染器初始化完成，图标ID: normal={}, lasso={}", 
            normalSelectIconId, lassoSelectIconId);
    }
    
    @Override
    public float render() {
        float height = 0;
        float startY = ImGui.getCursorPosY(); // 记录开始Y坐标
        
        ImGui.pushID("selection_options");
        
        try {
            
            // 获取当前选择模式
            try {
                BaseTool currentTool = AppState.getInstance().getCurrentTool();
                if (currentTool instanceof SelectionTool selectionTool) {
                    // 直接从工具获取当前模式
                    SelectionStrategy.SelectionMode currentMode = selectionTool.getCurrentSelectionMode();
                    if (currentMode != null) {
                        selectionMode = switch (currentMode) {
                            case NORMAL -> SelectionTool.CONFIG_VALUE_NORMAL;
                            case LASSO -> SelectionTool.CONFIG_VALUE_LASSO;
                        };
                    } else {
                        selectionMode = SelectionTool.CONFIG_VALUE_NORMAL;
                    }
                } else {
                    selectionMode = SelectionTool.CONFIG_VALUE_NORMAL;
                }
            } catch (Exception e) {
                MasterPlannerMod.LOGGER.error("获取选择模式失败", e);
                selectionMode = SelectionTool.CONFIG_VALUE_NORMAL;
            }
            
            // 选择模式标题
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ImGui.alignTextToFramePadding();
            ImGui.text("选择模式");
            
            // 使用 pushStyleVar 临时设置圆角，避免永久修改共享 ImGui 样式（影响其他模组）
            ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.FrameRounding, BUTTON_CORNER_ROUNDING);
            
            ImGui.tableNextColumn();
            float firstButtonX = ImGui.getCursorPosX();
            
            // 普通选择按钮
            boolean isNormalMode = SelectionTool.CONFIG_VALUE_NORMAL.equals(selectionMode);
            if (isNormalMode) {
                pushSelectedButtonStyle();
            }
            
            ImGui.pushID("normal_select");
            boolean clicked = ImGui.imageButton(normalSelectIconId, BUTTON_SIZE, BUTTON_SIZE);
            ImGui.popID();
            
            if (clicked && !isNormalMode) {
                MasterPlannerMod.LOGGER.info("SelectionToolOptionRenderer: 用户点击普通选择按钮");
                selectionMode = SelectionTool.CONFIG_VALUE_NORMAL;
                updateToolConfig(CONFIG_KEY_MODE, SelectionTool.CONFIG_VALUE_NORMAL);
            }
            
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("普通选择模式");
            }
            
            if (isNormalMode) {
                ImGui.popStyleColor(3);
            }

            // 套索选择按钮（放在同一行）
            ImGui.sameLine();
            ImGui.setCursorPosX(firstButtonX + BUTTON_SIZE + BUTTON_SPACING * 2);
            
            boolean isLassoMode = SelectionTool.CONFIG_VALUE_LASSO.equals(selectionMode);
            if (isLassoMode) {
                pushSelectedButtonStyle();
            }
            
            ImGui.pushID("lasso_select");
            clicked = ImGui.imageButton(lassoSelectIconId, BUTTON_SIZE, BUTTON_SIZE);
            ImGui.popID();
            
            if (clicked && !isLassoMode) {
                MasterPlannerMod.LOGGER.info("SelectionToolOptionRenderer: 用户点击套索选择按钮");
                selectionMode = SelectionTool.CONFIG_VALUE_LASSO;
                updateToolConfig(CONFIG_KEY_MODE, SelectionTool.CONFIG_VALUE_LASSO);
            }
            
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("套索选择模式");
            }
            
            if (isLassoMode) {
                ImGui.popStyleColor(3);
            }

            ImGui.popStyleVar();
            
            // 计算实际渲染的高度
            float endY = ImGui.getCursorPosY();
            height = endY - startY + ImGui.getStyle().getItemSpacing().y; // 添加一些额外空间
            
            MasterPlannerMod.LOGGER.debug("选择工具选项实际渲染高度: {} (从 {} 到 {})", height, startY, endY);
            
        } catch (Exception e) {
            MasterPlannerMod.LOGGER.error("渲染选择工具选项时发生异常", e);
        } finally {
            ImGui.popID();
        }
        
        return height;
    }
    
    @Override
    public void initialize() {
        // 初始化选择工具选项
        selectionMode = SelectionTool.CONFIG_VALUE_NORMAL;
    }
    
    @Override
    public void cleanup() {
        // 清理纹理资源
        ImGuiUtils.deleteTexture(normalSelectIconId);
        ImGuiUtils.deleteTexture(lassoSelectIconId);
    }
    
    /**
     * 设置选中按钮的样式
     */
    private void pushSelectedButtonStyle() {
        ImVec4 tabActive = ImGui.getStyle().getColor(ImGuiCol.TabActive);
        ImVec4 tabHovered = ImGui.getStyle().getColor(ImGuiCol.TabHovered);
        
        int activeColor = (int)(tabActive.w * 255) << 24 | 
                        (int)(tabActive.z * 255) << 16 | 
                        (int)(tabActive.y * 255) << 8 | 
                        (int)(tabActive.x * 255);
        
        int hoveredColor = (int)(tabHovered.w * 255) << 24 | 
                         (int)(tabHovered.z * 255) << 16 | 
                         (int)(tabHovered.y * 255) << 8 | 
                         (int)(tabHovered.x * 255);
        
        ImGui.pushStyleColor(ImGuiCol.Button, activeColor);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, hoveredColor);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, activeColor);
    }
}