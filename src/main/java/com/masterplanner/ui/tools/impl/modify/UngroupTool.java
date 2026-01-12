package com.masterplanner.ui.tools.impl.modify;

import com.masterplanner.api.snap.ISnapManager;
import com.masterplanner.api.state.IAppState;
import com.masterplanner.core.group.ShapeGroupManager;
import com.masterplanner.core.state.AppState;
import com.masterplanner.api.tool.ITool;
import com.masterplanner.core.tool.BaseTool;
import com.masterplanner.core.tool.ToolManager;
import com.masterplanner.infrastructure.event.EventBus;
import com.masterplanner.ui.component.ToolPanelIcons;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 解组工具
 * 用于将选中的组解散为独立图形
 * 执行操作后自动切换回之前的工具
 */
public class UngroupTool extends BaseTool {
    private static final Logger LOGGER = LoggerFactory.getLogger(UngroupTool.class);
    
    private final ShapeGroupManager groupManager;
    private BaseTool previousTool;
    
    /**
     * 构造函数（推荐方式 - 依赖注入）
     * @param appState 应用状态管理器
     * @param snapManager 吸附管理器（虽然不使用，但保持接口一致性）
     */
    public UngroupTool(IAppState appState, ISnapManager snapManager) {
        super("ungroup", "解组", ToolPanelIcons.UNGROUP, "将选中的组解散为独立图形");
        this.groupManager = new ShapeGroupManager((AppState) appState, EventBus.getInstance());
        LOGGER.debug("UngroupTool 已创建");
    }
    
    /**
     * 构造函数（兼容方式 - 单例获取）
     * @deprecated 推荐使用依赖注入构造函数
     */
    @Deprecated
    public UngroupTool() {
        super("ungroup", "解组", ToolPanelIcons.UNGROUP, "将选中的组解散为独立图形");
        this.groupManager = new ShapeGroupManager(AppState.getInstance(), EventBus.getInstance());
        LOGGER.debug("UngroupTool 已创建（兼容模式）");
    }
    
    @Override
    public void activate() {
        // 在激活前保存之前的工具
        ToolManager toolManager = ToolManager.getInstance();
        ITool currentActiveTool = toolManager.getActiveTool();
        if (currentActiveTool instanceof BaseTool activeTool && activeTool != this) {
            previousTool = activeTool;
        } else {
            // 如果没有之前的工具，默认切换到选择工具
            previousTool = (BaseTool) toolManager.getTool("select");
        }
        
        super.activate();
    }
    
    @Override
    public void onActivate() {
        super.onActivate();
        LOGGER.debug("UngroupTool 已激活");
        
        // 检查是否可以解组
        var canUngroupResult = groupManager.canUngroup();
        if (!canUngroupResult.isValid()) {
            LOGGER.warn("无法解组: {}", canUngroupResult.getMessage());
            // 即使无法解组，也切换回之前的工具
            switchBackToPreviousTool();
            return;
        }
        
        // 执行解组操作
        var result = groupManager.ungroupSelectedShapes();
        if (result.isSuccess()) {
            LOGGER.info("解组操作成功: {}", result.getMessage());
        } else {
            LOGGER.warn("解组操作失败: {}", result.getMessage());
        }
        
        // 执行操作后，切换回之前的工具
        switchBackToPreviousTool();
    }
    
    /**
     * 切换回之前的工具
     */
    private void switchBackToPreviousTool() {
        if (previousTool != null) {
            ToolManager toolManager = ToolManager.getInstance();
            toolManager.setActiveTool(previousTool);
            LOGGER.debug("已切换回之前的工具: {}", previousTool.getId());
        }
    }
    
    @Override
    public void onDeactivate() {
        super.onDeactivate();
        LOGGER.debug("UngroupTool 已停用");
    }
}
