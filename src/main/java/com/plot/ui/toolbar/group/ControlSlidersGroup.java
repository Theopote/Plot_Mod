package com.plot.ui.toolbar.group;

import com.plot.camera.CameraManager;
import com.plot.core.state.AppState;
import com.plot.infrastructure.event.EventBus;
import com.plot.infrastructure.event.view.OpacityChangeEvent;
import com.plot.ui.layout.UILayout;
import com.plot.ui.theme.ThemeManager;
import com.plot.ui.theme.UITheme;
import com.plot.ui.toolbar.ToolbarUIUtils;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;

/**
 * 控制滑动条组
 * 包含视图范围、画布透明度等滑动条控件
 */
public class ControlSlidersGroup extends AbstractToolbarGroup {
    
    // 本地滑动条状态，避免每帧从管理器获取导致的冲突
    private float[] viewDistanceValue = new float[1];
    private float[] opacityValue = new float[1];
    
    // 上次同步的时间戳，用于避免频繁同步
    private long lastSyncTime = 0;
    private static final long SYNC_INTERVAL_MS = 16; // 约60fps的同步频率
    
    public ControlSlidersGroup() {
        super("toolbar.plot.group.control_sliders");
        initializeValues();
    }
    
    public ControlSlidersGroup(AppState appState, EventBus eventBus) {
        super("toolbar.plot.group.control_sliders", appState, eventBus);
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
        
        // 计算所有标签文本的宽度（用于布局计算，但标题和滑动条现在是分两行的）
        float zoomLabelWidth = ImGui.calcTextSize(PlotI18n.tr("panel.plot.view_range") + ":").x;
        float opacityLabelWidth = ImGui.calcTextSize(PlotI18n.tr("panel.plot.canvas_opacity") + ":").x;

        // 找出最宽的标签宽度（虽然标题和滑动条分两行，但保留用于可能的布局计算）
        float maxLabelWidth = Math.max(zoomLabelWidth, opacityLabelWidth);
        
        // 计算单个滑动条的宽度（标题在上，滑动条在下，所以只需要滑动条宽度）
        float singleSliderWidth = UILayout.Toolbar.SLIDER_WIDTH;
        // 两个滑动条在一行需要的总宽度（包括间距）
        float twoSlidersWidth = singleSliderWidth * 2 + UILayout.Toolbar.ITEM_SPACING;
        
        // 获取可用宽度（从当前光标位置到内容区域右边缘）
        float currentX = ImGui.getCursorPosX();
        float contentMaxX = ImGui.getWindowContentRegionMaxX();
        float actualAvailableWidth = contentMaxX - currentX;
        
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
     * 顶部控制面板紧凑渲染（单行）
     */
    public void renderCompactSingleRow(float targetHeight) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastSyncTime > SYNC_INTERVAL_MS && !isAnySliderActive()) {
            syncValuesFromManagers();
        }

        float textHeight = ImGui.getTextLineHeight();
        float framePaddingY = Math.max(0, (targetHeight - textHeight) / 2.0f);
        setupCompactSliderStyles(framePaddingY);
        try {
            boolean isLocked = CameraManager.getInstance().getOrthographicCamera().isLocked();

            // 视距
            ImGui.text(PlotI18n.tr("panel.plot.view_range"));
            ImGui.sameLine(0, UILayout.Toolbar.ITEM_SPACING);
            ImGui.pushItemWidth(240.0f);
            try {
                if (isLocked) {
                    ImGui.beginDisabled();
                }
                String sliderID1 = "##slider_viewDistance_compact_" + System.identityHashCode(viewDistanceValue);
                if (ImGui.sliderFloat(sliderID1, viewDistanceValue, 40.0f, 600.0f, "%.0f")) {
                    CameraManager.getInstance().setViewDistance(viewDistanceValue[0]);
                }
                if (isLocked) {
                    ImGui.endDisabled();
                }
                if (ImGui.isItemHovered() && ImGui.isMouseClicked(1)) {
                    String uniquePopupTitle = "视图范围输入_" + System.identityHashCode(viewDistanceValue);
                    ImGui.openPopup(uniquePopupTitle);
                }
            } finally {
                ImGui.popItemWidth();
            }
            ToolbarUIUtils.renderInputPopup(
                "视图范围输入_" + System.identityHashCode(viewDistanceValue),
                PlotI18n.tr("toolbar.plot.input_view_range"), viewDistanceValue[0], 40.0f, 600.0f,
                newValue -> {
                    viewDistanceValue[0] = newValue;
                    CameraManager.getInstance().setViewDistance(newValue);
                }
            );

            ImGui.sameLine(0, UILayout.Toolbar.ITEM_SPACING * 2.0f);

            // 透明
            ImGui.text(PlotI18n.tr("panel.plot.canvas_opacity"));
            ImGui.sameLine(0, UILayout.Toolbar.ITEM_SPACING);
            ImGui.pushItemWidth(240.0f);
            try {
                String sliderID2 = "##slider_opacity_compact_" + System.identityHashCode(opacityValue);
                if (ImGui.sliderFloat(sliderID2, opacityValue, 0.0f, 100.0f, "%.0f%%")) {
                    float normalizedOpacity = opacityValue[0] / 100.0f;
                    eventBus.publish(new OpacityChangeEvent(normalizedOpacity));
                    appState.setOpacity(normalizedOpacity);
                }
                if (ImGui.isItemHovered() && ImGui.isMouseClicked(1)) {
                    String uniquePopupTitle2 = "画布透明度输入_" + System.identityHashCode(opacityValue);
                    ImGui.openPopup(uniquePopupTitle2);
                }
            } finally {
                ImGui.popItemWidth();
            }
            ToolbarUIUtils.renderInputPopup(
                "画布透明度输入_" + System.identityHashCode(opacityValue),
                PlotI18n.tr("toolbar.plot.input_opacity"), opacityValue[0], 0.0f, 100.0f,
                newValue -> {
                    opacityValue[0] = newValue;
                    float normalizedOpacity = newValue / 100.0f;
                    eventBus.publish(new OpacityChangeEvent(normalizedOpacity));
                    appState.setOpacity(normalizedOpacity);
                }
            );
        } finally {
            cleanupCompactSliderStyles();
        }
    }

    private void setupCompactSliderStyles(float framePaddingY) {
        UITheme.ThemeColors currentTheme = ThemeManager.getInstance().getCurrentTheme();
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 4, framePaddingY);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, 0.0f);
        ImGui.pushStyleColor(ImGuiCol.Border, currentTheme.buttonBorder);
        ImGui.pushStyleColor(ImGuiCol.FrameBg, currentTheme.buttonNormal);
        ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, currentTheme.buttonHovered);
        ImGui.pushStyleColor(ImGuiCol.FrameBgActive, currentTheme.buttonActive);
        ImGui.pushStyleColor(ImGuiCol.SliderGrab, currentTheme.sliderGrab);
        ImGui.pushStyleColor(ImGuiCol.SliderGrabActive, currentTheme.sliderGrabActive);
    }

    private void cleanupCompactSliderStyles() {
        ImGui.popStyleColor(6);
        ImGui.popStyleVar(3);
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
        ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, 0.0f);  // 滑动条不需要圆角
        
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
     * 在同一行渲染两个滑动条（标题在上，滑动条在下）
     */
    private void renderSlidersInOneRow(float maxLabelWidth, float zoomLabelWidth, float opacityLabelWidth) {
        boolean isLocked = CameraManager.getInstance().getOrthographicCamera().isLocked();
        
        // 保存起始位置
        float startX = ImGui.getCursorPosX();
        float startY = ImGui.getCursorPosY();
        float sliderHeight = UILayout.Toolbar.SLIDER_HEIGHT;
        // 标题和滑动条之间的间距使用 ITEM_SPACING 的一半，使间距更紧凑
        float lineSpacing = UILayout.Toolbar.ITEM_SPACING * 0.5f;
        
        // 第一个滑动条：视图范围
        // 第一行：标签
        ImGui.setCursorPos(startX, startY);
        ImGui.text(PlotI18n.tr("panel.plot.view_range") + ":");
        
        // 第二行：滑动条
        // ImGui.text() 已经将光标移动到文本底部，所以只需要加上间距即可
        float sliderY = ImGui.getCursorPosY() + lineSpacing;
        ImGui.setCursorPos(startX, sliderY);
        ImGui.pushItemWidth(UILayout.Toolbar.SLIDER_WIDTH);
        try {
            if (isLocked) {
                ImGui.beginDisabled();
            }
            String sliderID1 = "##slider_viewDistance_" + System.identityHashCode(viewDistanceValue);
            if (ImGui.sliderFloat(sliderID1, viewDistanceValue, 40.0f, 600.0f, "%.0f")) {
                CameraManager.getInstance().setViewDistance(viewDistanceValue[0]);
            }
            if (ImGui.isItemHovered() && ImGui.isMouseClicked(1)) {
                String uniquePopupTitle = "视图范围输入_" + System.identityHashCode(viewDistanceValue);
                ImGui.openPopup(uniquePopupTitle);
            }
            if (isLocked) {
                ImGui.endDisabled();
            }
        } finally {
            ImGui.popItemWidth();
        }
        // 渲染输入弹窗
        String uniquePopupTitle1 = "视图范围输入_" + System.identityHashCode(viewDistanceValue);
        ToolbarUIUtils.renderInputPopup(uniquePopupTitle1, PlotI18n.tr("toolbar.plot.input_view_range"), viewDistanceValue[0], 40.0f, 600.0f, newValue -> {
            viewDistanceValue[0] = newValue;
            CameraManager.getInstance().setViewDistance(newValue);
        });
        
        // 第二个滑动条：画布透明度（右侧）
        float secondX = startX + UILayout.Toolbar.SLIDER_WIDTH + UILayout.Toolbar.ITEM_SPACING;
        
        // 第一行：标签
        ImGui.setCursorPos(secondX, startY);
        ImGui.text(PlotI18n.tr("panel.plot.canvas_opacity") + ":");
        
        // 第二行：滑动条
        // ImGui.text() 已经将光标移动到文本底部，所以只需要加上间距即可
        float sliderY2 = ImGui.getCursorPosY() + lineSpacing;
        ImGui.setCursorPos(secondX, sliderY2);
        ImGui.pushItemWidth(UILayout.Toolbar.SLIDER_WIDTH);
        try {
            String sliderID2 = "##slider_opacity_" + System.identityHashCode(opacityValue);
            if (ImGui.sliderFloat(sliderID2, opacityValue, 0.0f, 100.0f, "%.0f%%")) {
                float normalizedOpacity = opacityValue[0] / 100.0f;
                eventBus.publish(new OpacityChangeEvent(normalizedOpacity));
                appState.setOpacity(normalizedOpacity);
            }
            if (ImGui.isItemHovered() && ImGui.isMouseClicked(1)) {
                String uniquePopupTitle2 = "画布透明度输入_" + System.identityHashCode(opacityValue);
                ImGui.openPopup(uniquePopupTitle2);
            }
        } finally {
            ImGui.popItemWidth();
        }
        // 渲染输入弹窗
        String uniquePopupTitle2 = "画布透明度输入_" + System.identityHashCode(opacityValue);
        ToolbarUIUtils.renderInputPopup(uniquePopupTitle2, PlotI18n.tr("toolbar.plot.input_opacity"), opacityValue[0], 0.0f, 100.0f, newValue -> {
            opacityValue[0] = newValue;
            float normalizedOpacity = newValue / 100.0f;
            eventBus.publish(new OpacityChangeEvent(normalizedOpacity));
            appState.setOpacity(normalizedOpacity);
        });
        
        // 移动到下一行
        // 使用两个滑动条中较高的那个作为总高度
        float totalHeight = Math.max(sliderY + sliderHeight - startY, sliderY2 + sliderHeight - startY);
        ImGui.setCursorPosY(startY + totalHeight);
    }
    
    /**
     * 分两行渲染两个滑动条（标题在上，滑动条在下）
     */
    private void renderSlidersInTwoRows(float maxLabelWidth, float zoomLabelWidth, float opacityLabelWidth) {
        boolean isLocked = CameraManager.getInstance().getOrthographicCamera().isLocked();
        
        // 第一个滑动条：视图范围（标题在上，滑动条在下）
        float startY = ImGui.getCursorPosY();
        ToolbarUIUtils.renderSliderTwoRowsWithInput(
            PlotI18n.tr("panel.plot.view_range") + ":", UILayout.Toolbar.SLIDER_WIDTH,
            viewDistanceValue, 40.0f, 600.0f, "%.0f", isLocked,
            () -> CameraManager.getInstance().setViewDistance(viewDistanceValue[0]),
            "view_range_input", PlotI18n.tr("toolbar.plot.input_view_range")
        );
        
        // 计算第一个滑动条的总高度（标签高度 + 间距 + 滑动条高度）
        float labelHeight = ImGui.getTextLineHeight();
        float sliderHeight = UILayout.Toolbar.SLIDER_HEIGHT;
        float totalHeight = labelHeight + UILayout.Toolbar.ITEM_SPACING + sliderHeight;
        
        // 换行：移动到下一行（上下间距与按钮间距统一）
        float lineSpacing = UILayout.Toolbar.ITEM_SPACING;
        ImGui.setCursorPosY(startY + totalHeight + lineSpacing);
        
        // 第二个滑动条：画布透明度（标题在上，滑动条在下）
        ToolbarUIUtils.renderSliderTwoRowsWithInput(
                PlotI18n.tr("panel.plot.canvas_opacity") + ":", UILayout.Toolbar.SLIDER_WIDTH,
                opacityValue, 0.0f, 100.0f, "%.0f%%", false,
                () -> {
                    float normalizedOpacity = opacityValue[0] / 100.0f;
                    eventBus.publish(new OpacityChangeEvent(normalizedOpacity));
                    appState.setOpacity(normalizedOpacity);
                },
                "opacity_input", PlotI18n.tr("toolbar.plot.input_opacity")
        );
    }
    
    @Override
    public float getGroupWidth() {
        // 返回单个滑动条的宽度（用于布局计算）
        // 标题和滑动条现在是分两行的，所以只需要滑动条宽度
        // 这样 ControlPanel 会根据可用宽度决定是否换行
        // 如果组内空间不够，会在 renderGroupContent 中自动分两行显示
        return UILayout.Toolbar.SLIDER_WIDTH;
    }
    
    @Override
    public boolean needsSeparator() {
        return super.needsSeparator();
    }
}
