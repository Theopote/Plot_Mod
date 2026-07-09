package com.plot.ui.toolbar.group;

import com.plot.core.state.AppState;
import com.plot.infrastructure.event.EventBus;
import com.plot.utils.PlotI18n;
import com.plot.ui.layout.UILayout;
import com.plot.ui.theme.ThemeManager;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 工具栏组抽象基类
 * 提供通用的功能和样式管理
 */
public abstract class AbstractToolbarGroup implements ToolbarGroup {
    protected static final Logger LOGGER = LoggerFactory.getLogger("Plot/ToolbarGroup");
    
    protected final AppState appState;
    protected final EventBus eventBus;
    protected final String groupName;
    protected boolean enabled = true;
    
    @Override
    public String getGroupName() {
        if (groupName != null && groupName.startsWith("toolbar.plot.")) {
            return PlotI18n.tr(groupName);
        }
        return groupName;
    }
    
    protected AbstractToolbarGroup(String groupName, AppState appState, EventBus eventBus) {
        this.groupName = groupName;
        this.appState = appState;
        this.eventBus = eventBus;
    }
    
    protected AbstractToolbarGroup(String groupName) {
        this.groupName = groupName;
        this.appState = AppState.getInstance();
        this.eventBus = EventBus.getInstance();
    }
    
    @Override
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    @Override
    public void render() {
        if (!isEnabled()) {
            return;
        }
        
        try {
            ImGui.beginGroup();
            renderGroupContent();
        } catch (Exception e) {
            LOGGER.error("Error rendering toolbar group: {}", groupName, e);
        } finally {
            ImGui.endGroup();
        }
    }
    
    /**
     * 子类需要实现的具体渲染逻辑
     */
    protected abstract void renderGroupContent();
    
    /**
     * 计算按钮组的宽度
     * @param buttonCount 按钮数量
     * @return 按钮组总宽度
     */
    protected float calculateButtonGroupWidth(int buttonCount) {
        return buttonCount * UILayout.Toolbar.BUTTON_SIZE + (buttonCount - 1) * UILayout.Toolbar.ITEM_SPACING;
    }
    
    /**
     * 推送工具栏按钮的通用样式
     */
    protected void pushButtonStyles() {
        ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1.0f);
        ImGui.pushStyleColor(ImGuiCol.Border, ThemeManager.getInstance().getCurrentTheme().buttonBorder);
    }
    
    /**
     * 弹出工具栏按钮的样式
     */
    protected void popButtonStyles() {
        ImGui.popStyleColor();
        ImGui.popStyleVar();
    }
    
    /**
     * 在组内按钮之间添加间隔
     */
    protected void addButtonSpacing() {
        ImGui.sameLine(0, UILayout.Toolbar.ITEM_SPACING);
    }
    
    @Override
    public void init() {
        LOGGER.debug("Initializing toolbar group: {}", groupName);
    }
    
    @Override
    public void close() throws Exception {
        LOGGER.debug("Closing toolbar group: {}", groupName);
    }
}