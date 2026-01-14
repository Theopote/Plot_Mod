package com.masterplanner.ui.toolbar.group;

import com.masterplanner.camera.CameraManager;
import com.masterplanner.core.state.AppState;
import com.masterplanner.infrastructure.event.EventBus;
import com.masterplanner.infrastructure.event.view.OpacityChangeEvent;
import com.masterplanner.ui.layout.UILayout;
import com.masterplanner.ui.theme.ThemeManager;
import com.masterplanner.ui.theme.UITheme;
import com.masterplanner.ui.toolbar.ToolbarUIUtils;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;

/**
 * 控制滑动条组
 * 包含视图范围、透明度等滑动条控件
 */
public class ControlSlidersGroup extends AbstractToolbarGroup {
    
    // 本地滑动条状态，避免每帧从管理器获取导致的冲突
    private float[] viewDistanceValue = new float[1];
    private float[] opacityValue = new float[1];
    
    // 上次同步的时间戳，用于避免频繁同步
    private long lastSyncTime = 0;
    private static final long SYNC_INTERVAL_MS = 16; // 约60fps的同步频率
    
    public ControlSlidersGroup() {
        super("控制滑动条");
        initializeValues();
    }
    
    public ControlSlidersGroup(AppState appState, EventBus eventBus) {
        super("控制滑动条", appState, eventBus);
        initializeValues();
    }
    
    /**
     * 初始化滑动条的本地值
     */
    private void initializeValues() {
        syncValuesFromManagers();
    }
    
    /**
     * 从管理器同步当前值到本地状态
     */
    private void syncValuesFromManagers() {
        viewDistanceValue[0] = CameraManager.getInstance().getViewDistance();
        opacityValue[0] = appState != null ? appState.getOpacity() * 100.0f : 0.0f;
        lastSyncTime = System.currentTimeMillis();
    }
    
    @Override
    protected void renderGroupContent() {
        // 定期同步值，但不要在拖拽过程中同步以避免冲突
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastSyncTime > SYNC_INTERVAL_MS && !isAnySliderActive()) {
            syncValuesFromManagers();
        }
        
        // 计算所有标签文本的宽度
        float zoomLabelWidth = ImGui.calcTextSize("视图范围:").x;
        float opacityLabelWidth = ImGui.calcTextSize("透明度:").x;

        // 找出最宽的标签宽度
        float maxLabelWidth = Math.max(zoomLabelWidth, opacityLabelWidth);
        
        // 计算单个滑动条的宽度
        float singleSliderWidth = UILayout.Toolbar.SLIDER_WIDTH + maxLabelWidth;
        // 两个滑动条在一行需要的总宽度（包括间距）
        float twoSlidersWidth = singleSliderWidth * 2 + UILayout.Toolbar.ITEM_SPACING;
        
        // 获取可用宽度（从当前光标位置到窗口右边缘）
        float currentX = ImGui.getCursorPosX();
        float windowWidth = ImGui.getWindowWidth();
        float windowPadding = ImGui.getStyle().getWindowPaddingX();
        float actualAvailableWidth = windowWidth - currentX - windowPadding * 2;
        
        try {
            setupSliderStyles();
            
            // 根据可用宽度决定布局方式
            if (actualAvailableWidth >= twoSlidersWidth) {
                // 宽度足够，两个滑动条在同一行显示
                renderSlidersInOneRow(maxLabelWidth, zoomLabelWidth, opacityLabelWidth);
            } else {
                // 宽度不够，两个滑动条分两行显示
                renderSlidersInTwoRows(maxLabelWidth, zoomLabelWidth, opacityLabelWidth);
            }
        } catch (Exception e) {
            LOGGER.error("Error rendering control sliders group", e);
        } finally {
            cleanupSliderStyles();
        }
    }
    
    /**
     * 检查是否有任何滑动条正在被拖拽
     * 这个方法需要在渲染滑动条之后调用才有效，这里作为简单的实现
     */
    private boolean isAnySliderActive() {
        // 简单实现：如果鼠标正在拖拽，认为可能有滑动条激活
        return ImGui.isMouseDown(0);
    }
    
    /**
     * 设置滑动条样式
     */
    private void setupSliderStyles() {
        UITheme.ThemeColors currentTheme = ThemeManager.getInstance().getCurrentTheme();
        
        // 设置滑动条高度和内边距
        float sliderHeight = UILayout.Toolbar.SLIDER_HEIGHT;
        float textHeight = ImGui.getTextLineHeight();
        float framePaddingY = Math.max(0, (sliderHeight - textHeight) / 2.0f);
        
        // 设置滑动条样式
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 4, framePaddingY);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, currentTheme.toolbarControlRounding);
        
        // 设置滑动条颜色
        ImGui.pushStyleColor(ImGuiCol.Border, currentTheme.buttonBorder);
        ImGui.pushStyleColor(ImGuiCol.FrameBg, currentTheme.buttonNormal);
        ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, currentTheme.buttonHovered);
        ImGui.pushStyleColor(ImGuiCol.FrameBgActive, currentTheme.buttonActive);
        ImGui.pushStyleColor(ImGuiCol.SliderGrab, currentTheme.sliderGrab);
        ImGui.pushStyleColor(ImGuiCol.SliderGrabActive, currentTheme.sliderGrabActive);
    }
    
    /**
     * 清理滑动条样式
     */
    private void cleanupSliderStyles() {
        ImGui.popStyleColor(6);
        ImGui.popStyleVar(3);
    }
    
    /**
     * 在同一行渲染两个滑动条
     */
    private void renderSlidersInOneRow(float maxLabelWidth, float zoomLabelWidth, float opacityLabelWidth) {
        boolean isLocked = CameraManager.getInstance().getOrthographicCamera().isLocked();
        
        // 视图范围滑动条 - 使用本地状态避免值冲突
        ToolbarUIUtils.renderSliderWithInput(
            "视图范围:", maxLabelWidth, UILayout.Toolbar.SLIDER_WIDTH,
            viewDistanceValue, 40.0f, 320.0f, "%.0f", isLocked,
            () -> CameraManager.getInstance().setViewDistance(viewDistanceValue[0]),
            "视图范围输入", "输入视图范围 (40-320)"
        );
        
        // 添加间距
        ImGui.sameLine(0, UILayout.Toolbar.ITEM_SPACING);
        
        // 透明度滑动条 - 使用本地状态避免值冲突
        ToolbarUIUtils.renderSliderWithInput(
                "透明度:", maxLabelWidth, UILayout.Toolbar.SLIDER_WIDTH,
                opacityValue, 0.0f, 100.0f, "%.0f%%", false,
                () -> {
                    float normalizedOpacity = opacityValue[0] / 100.0f;
                    eventBus.publish(new OpacityChangeEvent(normalizedOpacity));
                    appState.setOpacity(normalizedOpacity);
                },
                "透明度输入", "输入透明度 (0-100)"
        );
    }
    
    /**
     * 分两行渲染两个滑动条
     */
    private void renderSlidersInTwoRows(float maxLabelWidth, float zoomLabelWidth, float opacityLabelWidth) {
        boolean isLocked = CameraManager.getInstance().getOrthographicCamera().isLocked();
        
        // 第一行：视图范围滑动条
        ToolbarUIUtils.renderSliderWithInput(
            "视图范围:", maxLabelWidth, UILayout.Toolbar.SLIDER_WIDTH,
            viewDistanceValue, 40.0f, 320.0f, "%.0f", isLocked,
            () -> CameraManager.getInstance().setViewDistance(viewDistanceValue[0]),
            "视图范围输入", "输入视图范围 (40-320)"
        );
        
        // 换行：移动到下一行
        float lineSpacing = UILayout.Toolbar.ITEM_SPACING;
        float framePaddingY = Math.max(0, (UILayout.Toolbar.SLIDER_HEIGHT - ImGui.getTextLineHeight()) / 2.0f);
        ImGui.setCursorPosY(ImGui.getCursorPosY() + lineSpacing - framePaddingY);
        
        // 第二行：透明度滑动条
        ToolbarUIUtils.renderSliderWithInput(
                "透明度:", maxLabelWidth, UILayout.Toolbar.SLIDER_WIDTH,
                opacityValue, 0.0f, 100.0f, "%.0f%%", false,
                () -> {
                    float normalizedOpacity = opacityValue[0] / 100.0f;
                    eventBus.publish(new OpacityChangeEvent(normalizedOpacity));
                    appState.setOpacity(normalizedOpacity);
                },
                "透明度输入", "输入透明度 (0-100)"
        );
    }
    
    @Override
    public float getGroupWidth() {
        // 返回单个滑动条的宽度（用于布局计算）
        // 这样 ControlPanel 会根据可用宽度决定是否换行
        // 如果组内空间不够，会在 renderGroupContent 中自动分两行显示
        float maxLabelWidth = Math.max(
            ImGui.calcTextSize("视图范围:").x,
            ImGui.calcTextSize("透明度:").x
        );
        return UILayout.Toolbar.SLIDER_WIDTH + maxLabelWidth;
    }
    
    @Override
    public boolean needsSeparator() {
        return super.needsSeparator();
    }
}