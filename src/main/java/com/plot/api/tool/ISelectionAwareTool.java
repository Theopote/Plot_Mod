package com.plot.api.tool;

/**
 * 选择感知工具接口
 * 
 * <p>实现此接口的工具能够根据当前的选择状态（是否有选中的图形）
 * 自动调整其可用性和行为。这是一个优雅的设计，将"根据选择状态
 * 更新工具"的逻辑从管理器中移到了工具本身。</p>
 * 
 * <p><strong>设计优势：</strong></p>
 * <ul>
 *   <li>职责清晰：每个工具自己决定如何响应选择变化</li>
 *   <li>解耦合：ToolManager 不需要了解具体工具的逻辑</li>
 *   <li>可扩展：新工具只需实现此接口即可获得选择感知能力</li>
 *   <li>一致性：统一的选择状态更新机制</li>
 * </ul>
 * 
 * <p><strong>使用场景：</strong></p>
 * <ul>
 *   <li>变换工具（移动、旋转、缩放）：需要选中对象才能操作</li>
 *   <li>编辑工具（修剪、延伸、圆角）：需要选中对象才能编辑</li>
 *   <li>分析工具（测量、属性查看）：根据选择显示相关信息</li>
 * </ul>
 * 
 * @author Plot Team
 * @version 1.0
 */
public interface ISelectionAwareTool {
    
    /**
     * 当选择状态发生变化时调用
     * 
     * <p>工具应该根据选择状态更新自己的可用性、UI状态或行为。
     * 例如：移动工具只有在有选中对象时才应该启用。</p>
     * 
     * @param hasSelection 是否有选中的图形
     *                    - true: 当前有一个或多个图形被选中
     *                    - false: 当前没有图形被选中
     */
    void onSelectionChanged(boolean hasSelection);
    
    /**
     * 获取工具是否需要选中对象才能使用
     * 
     * <p>这是一个可选的方法，用于声明工具的依赖关系。
     * 默认实现返回true，表示工具需要选中对象。</p>
     * 
     * @return true 如果工具需要选中对象才能使用，false 否则
     */
    default boolean requiresSelection() {
        return true;
    }
    
    /**
     * 获取工具在没有选择时的行为策略
     * 
     * <p>定义了工具在没有选中对象时应该如何表现。</p>
     */
    enum NoSelectionBehavior {
        /** 禁用工具 - 最常见的行为 */
        DISABLE,
        
        /** 隐藏工具 - 用于上下文相关的工具 */
        HIDE,
        
        /** 保持启用但显示提示 - 用于可以引导用户选择的工具 */
        SHOW_HINT,
        
        /** 无操作 - 工具行为不受选择状态影响 */
        NO_CHANGE
    }
    
    /**
     * 获取工具在没有选择时的行为策略
     * 
     * <p>默认实现返回禁用策略。</p>
     * 
     * @return 无选择时的行为策略
     */
    default NoSelectionBehavior getNoSelectionBehavior() {
        return NoSelectionBehavior.DISABLE;
    }
} 