package com.masterplanner.ui.panel.tool.renderer;

import com.masterplanner.ui.theme.ThemeManager;
import com.masterplanner.ui.tools.impl.modify.AlignTool;
import com.masterplanner.ui.tools.impl.modify.strategy.AlignStrategy;
import imgui.ImGui;
import imgui.type.ImBoolean;
import com.masterplanner.MasterPlannerMod;

/**
 * 对齐工具属性面板渲染器
 * 
 * <p>提供对齐工具的配置选项，包括：</p>
 * <ul>
 *   <li>对齐模式选择（左对齐、右对齐、中心对齐等）</li>
 *   <li>参考模式选择（选择边界、第一个选中、最大图形等）</li>
 *   <li>分布设置（水平分布、垂直分布）</li>
 *   <li>使用说明和快捷键提示</li>
 * </ul>
 * 
 * @author MasterPlanner Team
 * @version 1.0 - 对齐工具选项渲染器
 */
public class AlignToolOptionRenderer extends AbstractToolOptionRenderer {
    
    // 配置键常量
    private static final String CONFIG_KEY_SCALE_ENABLED = "scale_enabled";
    
    // 当前配置状态
    private final ImBoolean scaleEnabled = new ImBoolean(false); // 默认不开启缩放
    
    // 依赖注入支持
    private AlignTool alignTool;
    
    public AlignToolOptionRenderer() {
        super("align");
    }

    @Override
    public float render() {
        // 同步工具状态到UI
        syncToolState();
        
        float height = 0;
        ImGui.pushID("align_options");
        
        try {
            MasterPlannerMod.LOGGER.debug("AlignToolOptionRenderer: 开始渲染对齐工具选项");
            
            // === 缩放设置 ===
            height += renderScaleSettings();
            
            // === 使用说明 ===
            height += renderUsageInstructions();
            
            // === 快捷键提示 ===
            height += renderShortcutTips();
            
        } catch (Exception e) {
            MasterPlannerMod.LOGGER.error("渲染对齐工具选项失败: {}", e.getMessage(), e);
        } finally {
            ImGui.popID();
        }
        
        return height;
    }
    
    /**
     * 渲染对齐模式选择器
     */
    @SuppressWarnings("unused")
    private float renderAlignModeSelector() {
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.alignTextToFramePadding();
        ImGui.text("对齐模式(已简化)");
        
        ImGui.tableNextColumn();
        ImGui.pushItemWidth(-1);
        
        float height = 0;
        ImGui.textDisabled("通过点对点交互完成对齐，无需选择模式");
        ImGui.popItemWidth();
        
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("选择对齐方式，快捷键可直接切换");
        }
        
        height += ImGui.getFrameHeightWithSpacing();
        return height;
    }
    
    /**
     * 渲染参考模式选择器
     */
    @SuppressWarnings("unused")
    private float renderReferenceModeSelector() {
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.alignTextToFramePadding();
        ImGui.text("参考模式(已简化)");
        
        ImGui.tableNextColumn();
        ImGui.pushItemWidth(-1);
        
        float height = 0;
        ImGui.textDisabled("以点对点选择为参考，无需选择参考模式");
        ImGui.popItemWidth();
        
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("选择对齐的参考对象");
        }
        
        height += ImGui.getFrameHeightWithSpacing();
        return height;
    }
    
    /**
     * 渲染缩放设置
     */
    private float renderScaleSettings() {
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.alignTextToFramePadding();
        ImGui.text("缩放设置");
        
        ImGui.tableNextColumn();
        ImGui.pushItemWidth(-1);
        
        float height = 0;
        
        // 允许缩放复选框（参考螺旋线工具的勾选样式）
        var theme = com.masterplanner.ui.theme.ThemeManager.getInstance().getCurrentTheme();
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.FrameBg, theme.controlBackground);
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.FrameBgHovered, theme.buttonHovered);
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.FrameBgActive, theme.buttonActive);
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.CheckMark, theme.accent);
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.Border, theme.buttonBorder);
        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.FrameBorderSize, 1.0f);
        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.FramePadding, 4.0f, 4.0f);

        if (ImGui.checkbox("允许缩放", scaleEnabled)) {
            updateToolConfig(CONFIG_KEY_SCALE_ENABLED, String.valueOf(scaleEnabled.get()));
        }

        ImGui.popStyleVar(2);
        ImGui.popStyleColor(5);
        
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("开启后将对齐时进行缩放以精确匹配目标尺寸");
        }
        
        height += ImGui.getFrameHeightWithSpacing();
        ImGui.popItemWidth();
        
        return height;
    }
    
    /**
     * 渲染增强吸附功能开关
     */
    @SuppressWarnings("unused")
    private float renderEnhancedSnapToggle() { return 0; }
    
    /**
     * 渲染使用说明
     */
    private float renderUsageInstructions() {
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.alignTextToFramePadding();
        ImGui.textColored(ThemeManager.getInstance().getCurrentTheme().mutedText, "使用说明");
        
        ImGui.tableNextColumn();
        ImGui.textWrapped(getUsageInstructions());
        
        return ImGui.getTextLineHeightWithSpacing() * 3; // 估算文本高度
    }
    
    /**
     * 渲染快捷键提示
     */
    private float renderShortcutTips() {
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.alignTextToFramePadding();
        ImGui.textColored(ThemeManager.getInstance().getCurrentTheme().mutedText, "快捷键");
        
        ImGui.tableNextColumn();
        ImGui.textWrapped(getShortcutTips());
        
        return ImGui.getTextLineHeightWithSpacing() * 2; // 估算文本高度
    }
    
    /**
     * 获取带快捷键的模式标签
     */
    @SuppressWarnings("unused")
    private String getModeLabelWithShortcut(AlignStrategy.AlignMode mode) {
        return switch (mode) {
            case LEFT -> "左对齐 (L)";
            case RIGHT -> "右对齐 (R)";
            case CENTER -> "中心对齐 (C)";
            case TOP -> "顶部对齐 (T)";
            case BOTTOM -> "底部对齐 (B)";
            case MIDDLE -> "中间对齐 (M)";
            case DISTRIBUTE_H -> "水平分布";
            case DISTRIBUTE_V -> "垂直分布";
        };
    }
    
    /**
     * 获取参考模式标签
     */
    @SuppressWarnings("unused")
    private String getReferenceModeLabel(AlignStrategy.ReferenceMode mode) {
        return switch (mode) {
            case SELECTION_BOUNDS -> "选择边界";
            case FIRST_SELECTED -> "第一个选中";
            case LAST_SELECTED -> "最后选中";
            case LARGEST -> "最大图形";
        };
    }
    
    /**
     * 同步工具状态到UI
     */
    private void syncToolState() {
        try {
            // 简化模式下，主要同步缩放设置
            // 注意：这里暂时不进行配置读取，因为基类没有提供getToolConfig方法
            // 缩放设置将在用户交互时通过updateToolConfig更新
        } catch (Exception e) {
            MasterPlannerMod.LOGGER.warn("同步对齐工具状态失败: {}", e.getMessage());
        }
    }
    
    /**
     * 获取当前对齐工具实例
     */
    @SuppressWarnings("unused")
    private AlignTool getCurrentTool() {
        // 优先使用依赖注入的实例
        if (alignTool != null) {
            return alignTool;
        }
        
        // 回退到全局状态获取
        try {
            var appState = com.masterplanner.core.state.AppState.getInstance();
            if (appState != null) {
                var toolManager = appState.getCurrentTool();
                if (toolManager instanceof AlignTool) {
                    return (AlignTool) toolManager;
                }
            }
        } catch (Exception e) {
            MasterPlannerMod.LOGGER.warn("获取当前对齐工具失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 获取使用说明
     */
    private String getUsageInstructions() {
        return """
                1. 选择要对齐的图形（至少1个）
                2. 第一次点击：选择源点1（在图形上或附近）
                3. 第二次点击：选择目标点1
                4. 第三次点击：选择源点2
                5. 第四次点击：选择目标点2，完成对齐（CAD式流程）""";
    }
    
    /**
     * 获取快捷键提示
     */
    private String getShortcutTips() {
        return """
                ESC - 取消操作""";
    }
    
    @Override
    public void initialize() {
        // 初始化配置
        // 配置会在渲染时自动同步
    }
    
    @Override
    public void cleanup() {
        // 清理资源
        alignTool = null;
        MasterPlannerMod.LOGGER.debug("AlignToolOptionRenderer 清理完成");
    }
} 