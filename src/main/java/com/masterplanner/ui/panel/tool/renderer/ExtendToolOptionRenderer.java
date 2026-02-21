package com.masterplanner.ui.panel.tool.renderer;

import com.masterplanner.ui.theme.ThemeManager;
import com.masterplanner.ui.theme.UITheme;
import com.masterplanner.ui.tools.impl.modify.ExtendTool;
import com.masterplanner.ui.tools.impl.modify.strategy.ExtendWithSelectionStrategy;
import imgui.ImGui;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 延伸工具属性面板渲染器
 * 
 * <p>提供延伸工具的配置选项，采用真正的无状态设计：</p>
 * <ul>
 *   <li><strong>无状态设计</strong>：UI不维护任何内部状态，所有状态都从工具实时获取</li>
 *   <li><strong>单一事实来源</strong>：所有状态都存储在ExtendTool中，UI只是视图层</li>
 *   <li><strong>强封装</strong>：UI不直接访问策略对象，只通过ExtendTool的公共API</li>
 *   <li><strong>自动模式</strong>：延伸工具现在使用自动模式，无需手动选择延伸方式</li>
 *   <li><strong>推荐使用</strong>：优先使用 {@link #render(ExtendTool)} 方法，避免全局单例依赖</li>
 * </ul>
 * 
 * @author MasterPlanner Team
 * @version 5.0 - 自动模式版本
 */
public class ExtendToolOptionRenderer extends AbstractToolOptionRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExtendToolOptionRenderer.class);
    
    // 配置键常量 - 引用ExtendTool中的常量（模式配置已移除）
    
    // 资源管理标志
    private boolean resourcesInitialized;
    
    public ExtendToolOptionRenderer() {
        super("extend");
        
        // 图标已移除，现在使用自动模式
        this.resourcesInitialized = true;
        
        LOGGER.debug("延伸工具选项渲染器初始化完成 - 自动模式");
    }
    
    @Override
    public float render() {
        // 默认实现：尝试从上下文获取工具（向后兼容）
        ExtendTool currentTool = getCurrentToolFromContext();
        return render(currentTool);
    }
    
    /**
     * 渲染延伸工具选项面板（推荐方式）
     * 
     * @param currentTool 当前延伸工具实例，不能为空
     * @return 渲染的高度
     */
    public float render(ExtendTool currentTool) {
        float height = 0;
        ImGui.pushID("extend_options");
        
        try {
            if (currentTool == null) {
                LOGGER.debug("当前工具为 null，跳过渲染");
                return 0;
            }

            LOGGER.debug("开始渲染延伸工具选项面板，当前工具: {}", currentTool.getClass().getSimpleName());
            LOGGER.debug("延伸工具使用自动模式");
            LOGGER.debug("可用区域: {}x{}", ImGui.getContentRegionAvail().x, ImGui.getContentRegionAvail().y);

            float originalRounding = ImGui.getStyle().getFrameRounding();
            
            // === 工具状态信息 ===
            height += renderToolStatusInfo(currentTool);
            LOGGER.debug("工具状态信息渲染完成，高度: {}", height);
            
            // 恢复原始的圆角设置
            ImGui.getStyle().setFrameRounding(originalRounding);
            
        } catch (Exception e) {
            LOGGER.error("渲染延伸工具选项时发生错误: {}", e.getMessage(), e);
        } finally {
            ImGui.popID();
        }
        
        LOGGER.debug("延伸工具选项面板渲染完成，总高度: {}", height);
        return height;
    }
    
    /**
     * 渲染工具状态信息
     */
    private float renderToolStatusInfo(ExtendTool currentTool) {
        float height = 0;
        UITheme.ThemeColors theme = ThemeManager.getInstance().getCurrentTheme();
        
        // 显示当前工具状态信息
        try {
            ExtendTool.ExtendToolState toolState = currentTool.getToolState();
            if (toolState != null && toolState.selectedBoundaries() != null) {
                int boundaryCount = toolState.selectedBoundaries().size();
                if (boundaryCount > 0) {
                    // 显示边界图形数量
                    ImGui.textColored(theme.successText, 
                        String.format("已选择 %d 个边界图形", boundaryCount));
                    height += 20;
                    
                    // 显示边界缓存状态
                    String cacheStatus = currentTool.getBoundaryCacheStatus();
                    ImGui.textColored(theme.mutedText, cacheStatus);
                    height += 20;
                    
                    // 显示当前延伸状态
                    String stateDescription = toolState.currentState().getDescription();
                    ImGui.textColored(theme.infoText, "状态: " + stateDescription);
                    height += 20;
                    
                    // 显示当前延伸模式（自动模式）
                    ImGui.textColored(theme.warningText, "模式: 自动延伸");
                    height += 20;
                    
                    // 显示操作提示
                    String operationHint = getOperationHint(toolState.currentState(), boundaryCount);
                    ImGui.textColored(theme.mutedText, "提示: " + operationHint);
                    
                    // 如果是延伸模式，显示连续延伸提示
                    if (toolState.currentState() == ExtendWithSelectionStrategy.ExtendState.EXTENDING) {
                        ImGui.textColored(theme.warningText, "✓ 延伸模式已激活，可连续延伸多个图形");
                        height += 20;
                    }
                } else {
                    // 没有选择边界图形时的提示
                    ImGui.textColored(theme.errorText, "未选择边界图形");
                    height += 20;
                    
                    ImGui.textColored(theme.mutedText, "提示: 请先选择边界图形，然后右键确认");
                }
                height += 20;
            }
        } catch (Exception e) {
            LOGGER.debug("获取工具状态失败: {}", e.getMessage());
        }
        
        // 模式选择已移除，现在显示自动模式信息
        ImGui.textColored(theme.successText, "自动延伸模式");
        ImGui.textWrapped("工具会自动选择最佳延伸方式：先尝试标准延伸，如果没有交点则自动使用投影延伸。");
        height += 60;
        
        return height;
    }
    
    /**
     * 从上下文获取当前工具 - 备用方案，推荐使用render(ExtendTool)方法
     * 
     * <p>注意：此方法依赖全局单例，降低了组件的独立性。
     * 推荐的使用方式是直接调用 {@link #render(ExtendTool)} 方法，
     * 通过参数传递工具实例，避免对全局状态的依赖。</p>
     * 
     * @return 当前ExtendTool实例，如果不是ExtendTool或获取失败则返回null
     */
    private ExtendTool getCurrentToolFromContext() {
        try {
            // 使用AppState获取当前工具 - 这是备用方案，不是推荐方式
            com.masterplanner.core.state.AppState appState = com.masterplanner.core.state.AppState.getInstance();
            if (appState == null) {
                LOGGER.debug("AppState未初始化，无法获取当前工具（这是正常的，如果使用render(ExtendTool)方法）");
                return null;
            }
            
            com.masterplanner.core.tool.BaseTool currentTool = appState.getCurrentTool();
            
            // 计算工具类型名称，避免重复代码
            String toolTypeName = currentTool != null ? currentTool.getClass().getSimpleName() : "null";
            
            LOGGER.debug("AppState获取到的当前工具: {}", toolTypeName);
            
            if (currentTool instanceof ExtendTool) {
                LOGGER.debug("当前工具是ExtendTool，返回成功");
                return (ExtendTool) currentTool;
            } else {
                LOGGER.debug("当前工具不是ExtendTool，类型: {}。建议使用render(ExtendTool)方法直接传递工具实例。", toolTypeName);
            }
        } catch (Exception e) {
            LOGGER.debug("获取当前工具失败: {}。建议使用render(ExtendTool)方法直接传递工具实例。", e.getMessage());
        }
        return null;
    }
    
    @Override
    public void initialize() {
        // 无状态设计：不需要维护本地状态，所有状态都从工具获取
        // 模式配置已移除，现在使用自动模式
        LOGGER.debug("ExtendToolOptionRenderer 已初始化（自动模式版本）");
    }
    
    @Override
    public void cleanup() {
        // 资源管理
        if (resourcesInitialized) {
            resourcesInitialized = false;
            LOGGER.debug("延伸工具选项渲染器已清理");
        }
    }
    
    /**
     * 根据当前状态和边界数量生成操作提示
     */
    private String getOperationHint(ExtendWithSelectionStrategy.ExtendState currentState, int boundaryCount) {
        switch (currentState) {
            case SELECTING_BOUNDARY:
                if (boundaryCount > 0) {
                    return String.format("已选择 %d 个边界图形，右键确认边界选择", boundaryCount);
                } else {
                    return "请选择边界图形，支持点选和框选";
                }
            case EXTENDING:
                return String.format("延伸模式已激活，点击要延伸的图形端点执行延伸（当前边界数: %d）", boundaryCount);
            default:
                return "请按照提示进行操作";
        }
    }
} 