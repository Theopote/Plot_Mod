package com.plot.core.snap;

import com.plot.api.geometry.Vec2d;
import com.plot.api.snap.ISnapManager;
import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.infrastructure.event.EventBus;
import com.plot.infrastructure.event.tool.SnapSettingsChangedEvent;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import com.plot.ui.dialog.DialogLayoutHelper;
import com.plot.ui.dialog.DialogStyleManager;
import com.plot.utils.PlotI18n;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import imgui.flag.ImGuiCond;
import com.plot.core.geometry.BoundingBox;

import java.util.List;
import com.plot.ui.tools.snap.SnapVisualStyle;

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
    private SnapPriorityEvaluator.SnapType lastResolvedSnapType = SnapPriorityEvaluator.SnapType.NONE;
    private Shape lastResolvedSnapSourceShape = null;

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

        DialogStyleManager.DialogStyleScope styleScope = DialogStyleManager.applyDialogStyle();
        try {
            ImGui.setNextWindowSize(DialogStyleManager.DialogWidth.STANDARD.value, 0, ImGuiCond.Appearing);

            int windowFlags = ImGuiWindowFlags.NoCollapse |
                    ImGuiWindowFlags.NoResize |
                    ImGuiWindowFlags.NoScrollbar |
                    ImGuiWindowFlags.NoSavedSettings |
                    ImGuiWindowFlags.NoNav |
                    ImGuiWindowFlags.AlwaysAutoResize;

            boolean windowVisible = ImGui.begin(PlotI18n.tr("snap.plot.constraint_settings") + "##snap_settings", windowFlags);
            try {
                if (windowVisible) {
                    if (DialogStyleManager.renderTopRightCloseButton("snap_settings")) {
                        showSettings = false;
                    }

                    boolean settingsChanged = false;

                    DialogLayoutHelper.helpText(PlotI18n.tr("snap.plot.settings_help"));
//                    DialogLayoutHelper.endSection();

                    if (ImGui.collapsingHeader(PlotI18n.tr("snap.plot.geom_features"))) {
                        ImGui.indent(10);

                        settingsChanged |= ImGui.checkbox(PlotI18n.tr("snap.plot.end_point"), settings.endPointSnap);
                        settingsChanged |= ImGui.checkbox(PlotI18n.tr("snap.plot.mid_point"), settings.midPointSnap);
                        settingsChanged |= ImGui.checkbox(PlotI18n.tr("snap.plot.circle_center"), settings.centerPointSnap);
                        settingsChanged |= ImGui.checkbox(PlotI18n.tr("snap.plot.center_point_geom"), settings.centroidSnap);
                        settingsChanged |= ImGui.checkbox(PlotI18n.tr("snap.plot.vertex"), settings.vertexSnap);
                        settingsChanged |= ImGui.checkbox(PlotI18n.tr("snap.plot.quadrant"), settings.quadrantSnap);
                        settingsChanged |= ImGui.checkbox(PlotI18n.tr("snap.plot.grid_point"), settings.gridPointSnap);
                        settingsChanged |= ImGui.checkbox(PlotI18n.tr("snap.plot.perpendicular"), settings.perpendicularSnap);
                        settingsChanged |= ImGui.checkbox(PlotI18n.tr("snap.plot.intersection"), settings.intersectionSnap);
                        settingsChanged |= ImGui.checkbox(PlotI18n.tr("snap.plot.nearest_point"), settings.nearestPointSnap);
                        settingsChanged |= ImGui.checkbox(PlotI18n.tr("snap.plot.control_point"), settings.controlPointSnap);
                        settingsChanged |= ImGui.checkbox(PlotI18n.tr("snap.plot.tangent_point"), settings.tangentPointSnap);

                        ImGui.unindent(10);
                    }

                    if (ImGui.collapsingHeader(PlotI18n.tr("snap.plot.geom_relations"))) {
                        ImGui.indent(10);
                        settingsChanged |= ImGui.checkbox(PlotI18n.tr("snap.plot.horizontal"), settings.horizontalSnap);
                        settingsChanged |= ImGui.checkbox(PlotI18n.tr("snap.plot.vertical"), settings.verticalSnap);
                        settingsChanged |= ImGui.checkbox(PlotI18n.tr("snap.plot.parallel"), settings.parallelSnap);
                        settingsChanged |= ImGui.checkbox(PlotI18n.tr("snap.plot.extension"), settings.extensionSnap);
                        ImGui.unindent(10);
                    }

                    if (ImGui.collapsingHeader(PlotI18n.tr("snap.plot.constraint_settings"))) {
                        ImGui.indent(10);

                        if (DialogLayoutHelper.beginForm("##snap_settings_form")) {
                            DialogLayoutHelper.formRowLabel(PlotI18n.tr("snap.plot.snap_radius"));

                            float[] snapRadius = settings.isPixelMode()
                                    ? new float[]{settings.getSnapRadiusInPixels()}
                                    : new float[]{settings.getSnapRadiusInMM()};

                            float minRadius = settings.isPixelMode() ? 1.0f : 0.2f;
                            float maxRadius = settings.isPixelMode() ? 50.0f : 15.0f;
                            String format = settings.isPixelMode() ? "%.0f px" : "%.1f mm";

                            if (ImGui.sliderFloat("##snap_radius", snapRadius, minRadius, maxRadius, format)) {
                                settings.setSnapRadius(snapRadius[0]);
                                settingsChanged = true;
                            }
                            if (ImGui.isItemHovered()) {
                                ImGui.setTooltip(PlotI18n.tr("snap.plot.unit_toggle_tooltip",
                                        settings.isPixelMode()
                                                ? PlotI18n.tr("snap.plot.unit_pixel")
                                                : PlotI18n.tr("snap.plot.unit_mm")));
                            }
                            if (ImGui.isItemActive() && ImGui.getIO().getKeyAlt()) {
                                settings.toggleUnitMode();
                                settingsChanged = true;
                            }

                            DialogLayoutHelper.formRowLabel(PlotI18n.tr("snap.plot.marker_size"));
                            float[] markerSize = new float[]{settings.getMarkerSize()};
                            if (ImGui.sliderFloat("##marker_size", markerSize, 2.0f, 10.0f, "%.1f px")) {
                                settings.setMarkerSize(markerSize[0]);
                                settingsChanged = true;
                            }

                            DialogLayoutHelper.formRowLabel(PlotI18n.tr("snap.plot.snap_level"));
                            String[] levels = {
                                    PlotI18n.tr("snap.plot.level_global"),
                                    PlotI18n.tr("snap.plot.level_tool"),
                                    PlotI18n.tr("snap.plot.level_layer")
                            };
                            settingsChanged |= ImGui.combo("##snap_level", settings.snapLevel, levels);

                            DialogLayoutHelper.formRowLabel(PlotI18n.tr("snap.plot.priority_strategy"));
                            String[] priorities = {
                                    PlotI18n.tr("snap.plot.priority_type"),
                                    PlotI18n.tr("snap.plot.priority_distance")
                            };
                            settingsChanged |= ImGui.combo("##snap_priority", settings.snapPriority, priorities);

                            DialogLayoutHelper.endForm();
                        }

                        DialogLayoutHelper.rowGap();
                        settingsChanged |= ImGui.checkbox(PlotI18n.tr("snap.plot.exclude_hidden_layers"), settings.excludeHiddenLayers);
                        settingsChanged |= ImGui.checkbox(PlotI18n.tr("snap.plot.temp_disable_shift"), settings.tempDisableWithShift);
                        settingsChanged |= ImGui.checkbox(PlotI18n.tr("snap.plot.show_markers_preview"), settings.showSnapMarkers);
                        if (settings.showSnapMarkers.get()) {
                            settingsChanged |= ImGui.checkbox(PlotI18n.tr("snap.plot.enable_marker_pulse"), settings.enableMarkerPulse);
                        }

                        ImGui.unindent(10);
                    }

                    if (settingsChanged) {
                        applySettings();
                    }

                    if (DialogLayoutHelper.isCancelShortcutPressed()) {
                        showSettings = false;
                    }

                    renderButtons();
                }
            } finally {
                ImGui.end();
            }
        } finally {
            DialogStyleManager.popDialogStyle(styleScope);
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
            lastResolvedSnapType = SnapPriorityEvaluator.SnapType.NONE;
            lastResolvedSnapSourceShape = null;
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
            lastResolvedSnapType = calculator.getSnapType();
            lastResolvedSnapSourceShape = calculator.getLastSnapSourceShape();

            // 如果启用了吸附标记预览，显示吸附点
            if (settings.showSnapMarkers.get() && !point.equals(snapPoint)) {
                renderSnapMarker(snapPoint, calculator.getSnapType());
            }

            return snapPoint;
        } catch (Exception e) {
            lastResolvedSnapType = SnapPriorityEvaluator.SnapType.NONE;
            lastResolvedSnapSourceShape = null;
            LOGGER.error("计算吸附点时发生错误", e);
            return point;
        }
    }

    public SnapPriorityEvaluator.SnapType getLastResolvedSnapType() {
        return lastResolvedSnapType;
    }

    public Shape getLastResolvedSnapSourceShape() {
        return lastResolvedSnapSourceShape;
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
            com.plot.ui.canvas.Canvas canvas = appState.getCanvas();
            if (canvas != null) {
                com.plot.ui.canvas.CanvasCamera camera = canvas.getCamera();
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

    private void renderButtons() {
        ImGui.separator();
        DialogLayoutHelper.FooterResult action =
                DialogLayoutHelper.footerConfirmCancelCentered(
                        PlotI18n.tr("button.plot.reset"), PlotI18n.tr("button.plot.confirm"),
                        DialogStyleManager.getContentWidth());

        if (action.confirmClicked()) {
            showSettings = false;
        }
        if (action.cancelClicked()) {
            resetToDefaults();
        }
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