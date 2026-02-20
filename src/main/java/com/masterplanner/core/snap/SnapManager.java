package com.masterplanner.core.snap;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.api.snap.ISnapManager;
import com.masterplanner.core.model.Shape;
import com.masterplanner.core.state.AppState;
import com.masterplanner.infrastructure.event.EventBus;
import com.masterplanner.infrastructure.event.tool.SnapSettingsChangedEvent;
import imgui.ImColor;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import com.masterplanner.ui.theme.ThemeManager;
import com.masterplanner.ui.theme.UITheme;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import imgui.flag.ImGuiCond;
import com.masterplanner.core.geometry.BoundingBox;

import java.util.List;
import com.masterplanner.ui.tools.snap.SnapVisualStyle;

/**
 * 吸附管理器
 * 负责处理所有与吸附相关的功能
 */
public class SnapManager implements ISnapManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(SnapManager.class);
    private static SnapManager INSTANCE;
    private static final int SHIFT_KEY_CODE = 16;  // Shift 键的键码

    private final EventBus eventBus;
    private final AppState appState; // 保留引用以便未来扩展（例如按图层过滤），当前未直接使用
    private boolean isEnabled = true;
    private boolean showSettings = false;
    private SnapSettings settings;

    private SnapManager() {
        this.eventBus = EventBus.getInstance();
        this.appState = AppState.getInstance();
        this.settings = new SnapSettings();
    }

    public static SnapManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new SnapManager();
        }
        return INSTANCE;
    }

    /**
     * 渲染吸附设置窗口
     */
    public void renderSettingsWindow() {
        if (!showSettings) return;

        UITheme.ThemeColors currentTheme = ThemeManager.getInstance().getCurrentTheme();

        // 记录推送的样式数量
        int styleColorCount;

        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 10.0f, 10.0f);

        // 基础样式
        ImGui.pushStyleColor(ImGuiCol.WindowBg, currentTheme.panelBackground);
        ImGui.pushStyleColor(ImGuiCol.Border, currentTheme.buttonBorder);
        ImGui.pushStyleColor(ImGuiCol.FrameBg, currentTheme.controlBackground);
        ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, currentTheme.buttonHovered);
        ImGui.pushStyleColor(ImGuiCol.FrameBgActive, currentTheme.buttonActive);
        ImGui.pushStyleColor(ImGuiCol.CheckMark, ImColor.rgba(255, 255, 255, 255));
        styleColorCount = 6;  // 更新计数

        try {
            ImGui.setNextWindowSizeConstraints(300, 0, 500, Float.MAX_VALUE);

            // 设置初始位置（居中）
            ImGui.setNextWindowPos(
                    ImGui.getIO().getDisplaySizeX() * 0.5f - 150,
                    50.0f,
                    ImGuiCond.FirstUseEver  // 只在第一次显示时设置位置
            );

            int windowFlags = ImGuiWindowFlags.NoCollapse |
                    ImGuiWindowFlags.AlwaysAutoResize |
                    ImGuiWindowFlags.NoResize |
                    ImGuiWindowFlags.NoNav;  // 移除 AlwaysUseWindowPadding，允许窗口拖动

            if (ImGui.begin("吸附设置##snap_settings", windowFlags)) {

                boolean settingsChanged = false;

                // 几何特征吸附
                if (ImGui.collapsingHeader("几何特征吸附")) {
                    ImGui.indent(10);

                    settingsChanged |= ImGui.checkbox("端点吸附", settings.endPointSnap);
                    settingsChanged |= ImGui.checkbox("中点吸附", settings.midPointSnap);
                    settingsChanged |= ImGui.checkbox("圆心吸附", settings.centerPointSnap);
                    settingsChanged |= ImGui.checkbox("中心点吸附", settings.centroidSnap);
                    settingsChanged |= ImGui.checkbox("角点吸附", settings.vertexSnap);
                    settingsChanged |= ImGui.checkbox("象限点吸附", settings.quadrantSnap);
                    settingsChanged |= ImGui.checkbox("网格点吸附", settings.gridPointSnap);
                    settingsChanged |= ImGui.checkbox("垂足吸附", settings.perpendicularSnap);
                    settingsChanged |= ImGui.checkbox("交点吸附", settings.intersectionSnap);
                    settingsChanged |= ImGui.checkbox("最近点吸附", settings.nearestPointSnap);
                    settingsChanged |= ImGui.checkbox("控制点吸附", settings.controlPointSnap);
                    settingsChanged |= ImGui.checkbox("切点吸附", settings.tangentPointSnap);

                    ImGui.unindent(10);
                }

                // 几何关系约束
                if (ImGui.collapsingHeader("几何关系约束")) {
                    ImGui.indent(10);
                    settingsChanged |= ImGui.checkbox("水平约束", settings.horizontalSnap);
                    settingsChanged |= ImGui.checkbox("竖直约束", settings.verticalSnap);
                    settingsChanged |= ImGui.checkbox("平行约束", settings.parallelSnap);
                    settingsChanged |= ImGui.checkbox("延长线约束", settings.extensionSnap);
                    ImGui.unindent(10);
                }

                // 吸附设置
                if (ImGui.collapsingHeader("吸附设置")) {
                    ImGui.indent(10);
                    ImGui.text("吸附半径：");
                    ImGui.sameLine();
                    ImGui.pushItemWidth(100);

                    // 根据当前单位模式获取值和范围
                    float[] snapRadius = settings.isPixelMode() ?
                            new float[] { settings.getSnapRadiusInPixels() } :
                            new float[] { settings.getSnapRadiusInMM() };

                    float minRadius = settings.isPixelMode() ? 1.0f : 0.2f;
                    float maxRadius = settings.isPixelMode() ? 50.0f : 15.0f;
                    String format = settings.isPixelMode() ? "%.0f px" : "%.1f mm";

                    // 渲染滑动条
                    if (ImGui.sliderFloat("##snap_radius",
                            snapRadius, minRadius, maxRadius, format
                    )) {
                        settings.setSnapRadius(snapRadius[0]);
                        settingsChanged = true;
                    }

                    // 显示单位切换提示
                    if (ImGui.isItemHovered()) {
                        ImGui.setTooltip("按住Alt键切换单位\n当前单位: " +
                                (settings.isPixelMode() ? "像素" : "毫米"));
                    }

                    // 处理单位切换
                    if (ImGui.isItemActive() && ImGui.getIO().getKeyAlt()) {
                        settings.toggleUnitMode();
                        settingsChanged = true;
                    }

                    ImGui.popItemWidth();

                    // 标记设置
                    ImGui.spacing();
                    ImGui.text("标记大小：");
                    ImGui.sameLine();
                    ImGui.pushItemWidth(100);
                    float[] markerSize = new float[] { settings.getMarkerSize() };
                    if (ImGui.sliderFloat("##marker_size", markerSize, 1.5f, 5.0f, "%.1f px")) {
                        settings.setMarkerSize(markerSize[0]);
                        settingsChanged = true;
                    }
                    ImGui.popItemWidth();

                    ImGui.spacing();

                    ImGui.text("吸附层级：");
                    ImGui.sameLine();
                    String[] levels = {"全局", "当前工具", "当前图层"};
                    ImGui.pushItemWidth(100);
                    settingsChanged |= ImGui.combo("##snap_level", settings.snapLevel, levels);
                    ImGui.popItemWidth();

                    ImGui.spacing();

                    ImGui.text("优先级策略：");
                    ImGui.sameLine();
                    String[] priorities = {"类型优先", "距离优先"};
                    ImGui.pushItemWidth(100);
                    settingsChanged |= ImGui.combo("##snap_priority", settings.snapPriority, priorities);
                    ImGui.popItemWidth();

                    settingsChanged |= ImGui.checkbox("排除隐藏图层", settings.excludeHiddenLayers);
                    settingsChanged |= ImGui.checkbox("临时禁用 (Shift)", settings.tempDisableWithShift);
                    settingsChanged |= ImGui.checkbox("吸附标记预览", settings.showSnapMarkers);
                    if (settings.showSnapMarkers.get()) {  // 只在启用预览时显示动画选项
                        ImGui.indent(20);
                        settingsChanged |= ImGui.checkbox("标记动画", settings.enableMarkerPulse);
                        ImGui.unindent(20);
                    }
                    ImGui.unindent(10);
                }

                // 底部按钮
                ImGui.separator();
                ImGui.spacing();
                float windowWidth = ImGui.getWindowWidth();
                float buttonWidth = 70.0f;
                float buttonSpacing = 10;
                float totalButtonWidth = buttonWidth * 3 + buttonSpacing * 2;
                float startX = (windowWidth - totalButtonWidth) * 0.5f;
                ImGui.setCursorPosX(startX);

                if (ImGui.button("确定", buttonWidth, 24.0f)) {
                    if (settingsChanged) {
                        applySettings();
                    }
                    showSettings = false;
                }
                ImGui.sameLine(0, buttonSpacing);
                if (ImGui.button("取消", buttonWidth, 24.0f)) {
                    showSettings = false;
                }
                ImGui.sameLine(0, buttonSpacing);
                if (ImGui.button("重置默认", buttonWidth, 24.0f)) {
                    resetToDefaults();
                    settingsChanged = true;
                }
                ImGui.spacing();
            }

            ImGui.end();

        } finally {
            ImGui.popStyleColor(styleColorCount);
            ImGui.popStyleVar();
        }
    }

    /**
     * 计算吸附点
     * @param point 原始点
     * @param shapes 要考虑的图形列表
     * @return 吸附后的点
     */
    public Vec2d getSnapPoint(Vec2d point, List<Shape> shapes) {
        if (!isEnabled || (settings.tempDisableWithShift.get() && ImGui.isKeyDown(SHIFT_KEY_CODE))) {
            return point;
        }

        try {
            // 修复：使用更大的视图边界，避免右侧区域图形无法捕捉的问题
            BoundingBox viewBounds = new BoundingBox(
                    new Vec2d(-100000, -100000),  // 使用更大的边界范围
                    new Vec2d(100000, 100000)     // 覆盖更大的画布区域
            );
            
            SnapCalculator calculator = new SnapCalculator(settings, shapes, viewBounds);

            // 计算吸附点
            Vec2d snapPoint = calculator.findNearestSnapPoint(point);

            // 如果启用了吸附标记预览，显示吸附点
            if (settings.showSnapMarkers.get() && !point.equals(snapPoint)) {
                renderSnapMarker(snapPoint, calculator.getSnapType());
            }

            return snapPoint;
        } catch (Exception e) {
            LOGGER.error("计算吸附点时发生错误", e);
            return point;
        }
    }

    /**
     * 渲染吸附标记
     * 使用 BackgroundDrawList 避免在没有活动 ImGui 窗口时创建临时窗口导致闪烁
     * point 是世界坐标，需要转换为屏幕坐标后再绘制
     */
    private void renderSnapMarker(Vec2d point, SnapPriorityEvaluator.SnapType type) {
        ImDrawList drawList = ImGui.getBackgroundDrawList();
        if (drawList == null) {
            return; // 背景绘制列表不可用时跳过
        }
        
        // 将世界坐标转换为屏幕坐标
        Vec2d screenPoint = point;
        try {
            com.masterplanner.ui.canvas.Canvas canvas = appState.getCanvas();
            if (canvas != null) {
                com.masterplanner.ui.canvas.CanvasCamera camera = canvas.getCamera();
                if (camera != null) {
                    screenPoint = camera.worldToScreen(point);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("无法转换吸附标记坐标，使用原始坐标: {}", e.getMessage());
            // 如果转换失败，使用原始坐标（可能是屏幕坐标）
        }
        
        float size = settings.getMarkerSize();
        float pulseScale;

        if (settings.enableMarkerPulse.get()) {
            float time = (float)ImGui.getTime();
            pulseScale = 1.0f + (float)Math.sin(time * 4.0f) * 0.1f;
            size *= pulseScale;
        }

        float x = (float)screenPoint.x;
        float y = (float)screenPoint.y;
        
        switch (type) {
            case END_POINT:
                drawList.addRectFilled(
                        x - size/2, y - size/2,
                        x + size/2, y + size/2,
                        SnapVisualStyle.imGuiColorFor(SnapPriorityEvaluator.SnapType.END_POINT, 204)
                );
                break;

            case MID_POINT:
                drawList.addQuad(
                        x, y - size/2,
                        x + size/2, y,
                        x, y + size/2,
                        x - size/2, y,
                        SnapVisualStyle.imGuiColorFor(SnapPriorityEvaluator.SnapType.MID_POINT, 204), 1.0f
                );
                break;

            case CENTER_POINT:
                drawList.addCircle(
                        x, y, size/2,
                        SnapVisualStyle.imGuiColorFor(SnapPriorityEvaluator.SnapType.CENTER_POINT, 204), 12, 1.0f
                );
                break;

            case CENTROID:
                drawList.addCircleFilled(
                        x, y, size/2,
                        SnapVisualStyle.imGuiColorFor(SnapPriorityEvaluator.SnapType.CENTROID, 204)
                );
                break;

            case VERTEX:
                drawList.addTriangle(
                        x, y - size/2,
                        x - size/2, y + size/2,
                        x + size/2, y + size/2,
                        SnapVisualStyle.imGuiColorFor(SnapPriorityEvaluator.SnapType.VERTEX, 204), 1.0f
                );
                break;

            case QUADRANT:
                drawList.addLine(
                        x - size/2, y,
                        x + size/2, y,
                        SnapVisualStyle.imGuiColorFor(SnapPriorityEvaluator.SnapType.QUADRANT, 204), 1.0f
                );
                drawList.addLine(
                        x, y - size/2,
                        x, y + size/2,
                        SnapVisualStyle.imGuiColorFor(SnapPriorityEvaluator.SnapType.QUADRANT, 204), 1.0f
                );
                break;

            case GRID_POINT:
                drawList.addCircleFilled(
                        x, y, size/4,
                        SnapVisualStyle.imGuiColorFor(SnapPriorityEvaluator.SnapType.GRID_POINT, 204)
                );
                break;

            case PERPENDICULAR:
                drawList.addCircleFilled(
                        x, y, size/4,
                        SnapVisualStyle.imGuiColorFor(SnapPriorityEvaluator.SnapType.PERPENDICULAR, 204)
                );
                drawList.addLine(
                        x, y - size/2,
                        x, y + size/2,
                        SnapVisualStyle.imGuiColorFor(SnapPriorityEvaluator.SnapType.PERPENDICULAR, 204), 1.0f
                );
                break;

            case INTERSECTION:
                drawList.addLine(
                        x - size/2, y - size/2,
                        x + size/2, y + size/2,
                        SnapVisualStyle.imGuiColorFor(SnapPriorityEvaluator.SnapType.INTERSECTION, 204), 1.0f
                );
                drawList.addLine(
                        x + size/2, y - size/2,
                        x - size/2, y + size/2,
                        SnapVisualStyle.imGuiColorFor(SnapPriorityEvaluator.SnapType.INTERSECTION, 204), 1.0f
                );
                break;

            case CONTROL_POINT:
                drawList.addCircleFilled(
                        x, y, size/2,
                        SnapVisualStyle.imGuiColorFor(SnapPriorityEvaluator.SnapType.CONTROL_POINT, 204)
                );
                break;

            case TANGENT:
                drawList.addLine(
                        x - size/2, y,
                        x + size/2, y,
                        SnapVisualStyle.imGuiColorFor(SnapPriorityEvaluator.SnapType.TANGENT, 204), 1.0f
                );
                drawList.addCircleFilled(
                        x, y, size/4,
                        SnapVisualStyle.imGuiColorFor(SnapPriorityEvaluator.SnapType.TANGENT, 204)
                );
                break;

            default:
                drawList.addCircleFilled(
                        x, y, size/2,
                        SnapVisualStyle.imGuiColorFor(SnapPriorityEvaluator.SnapType.NONE, 204)
                );
        }
    }

    public void toggleSettings() {
        showSettings = !showSettings;
    }

    public void setEnabled(boolean enabled) {
        isEnabled = enabled;
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    public boolean isEndPointSnapEnabled() {
        return settings.endPointSnap.get();
    }

    public void setEndPointSnapEnabled(boolean enabled) {
        settings.endPointSnap.set(enabled);
        applySettings();
    }

    public boolean isMidPointSnapEnabled() {
        return settings.midPointSnap.get();
    }

    public void setMidPointSnapEnabled(boolean enabled) {
        settings.midPointSnap.set(enabled);
        applySettings();
    }

    public boolean isCenterPointSnapEnabled() {
        return settings.centerPointSnap.get();
    }

    public void setCenterPointSnapEnabled(boolean enabled) {
        settings.centerPointSnap.set(enabled);
        applySettings();
    }

    public boolean isCentroidSnapEnabled() {
        return settings.centroidSnap.get();
    }

    public void setCentroidSnapEnabled(boolean enabled) {
        settings.centroidSnap.set(enabled);
        applySettings();
    }

    public boolean isShowSnapMarkersEnabled() {
        return settings.showSnapMarkers.get();
    }

    public void setShowSnapMarkersEnabled(boolean enabled) {
        settings.showSnapMarkers.set(enabled);
        applySettings();
    }

    public float getMarkerSize() {
        return settings.getMarkerSize();
    }

    public void setMarkerSize(float markerSize) {
        settings.setMarkerSize(markerSize);
        applySettings();
    }

    private void applySettings() {
        eventBus.publish(new SnapSettingsChangedEvent(settings));
    }

    private void resetToDefaults() {
        settings.resetToDefaults();
        applySettings();
    }
    
    // ====== ISnapManager接口实现 ======
    
    @Override
    public Vec2d snapPoint(Vec2d point, List<Shape> snapTargets) {
        return getSnapPoint(point, snapTargets);
    }
    
    @Override
    public Vec2d snapPoint(Vec2d point, Vec2d startPoint, List<Shape> snapTargets) {
        // 简化实现：忽略起始点，使用标准吸附
        return getSnapPoint(point, snapTargets);
    }
    
    @Override
    public boolean isSnapEnabled() {
        return isEnabled();
    }
    
    @Override
    public void setSnapEnabled(boolean enabled) {
        setEnabled(enabled);
    }
    
    @Override
    public double getSnapDistance() {
        return settings.getSnapRadiusInPixels();
    }
    
    @Override
    public void setSnapDistance(double distance) {
        settings.setSnapRadius((float) distance);
    }
    
    @Override
    public boolean isGridSnapEnabled() {
        return settings.gridPointSnap.get();
    }
    
    @Override
    public boolean isObjectSnapEnabled() {
        return settings.endPointSnap.get() || settings.midPointSnap.get() || 
               settings.centerPointSnap.get() || settings.vertexSnap.get();
    }
    
    @Override
    public String getConfigInfo() {
        return String.format("SnapManager - 启用: %s, 距离: %.1f, 网格吸附: %s, 对象吸附: %s",
                           isSnapEnabled(), getSnapDistance(), 
                           isGridSnapEnabled(), isObjectSnapEnabled());
    }
    
    @Override
    public void reset() {
        resetToDefaults();
    }
    
    @Override
    public void dispose() {
        try {
            LOGGER.debug("SnapManager 开始资源清理");
            
            // 重置设置到默认状态
            if (settings != null) {
                settings.resetToDefaults();
            }
            
            // 关闭设置窗口
            showSettings = false;
            
            // 禁用吸附功能
            isEnabled = false;
            
            LOGGER.debug("SnapManager 资源清理完成");
            
        } catch (Exception e) {
            LOGGER.error("SnapManager 资源清理时出错", e);
        }
    }
} 