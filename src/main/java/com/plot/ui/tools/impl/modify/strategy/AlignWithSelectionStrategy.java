package com.plot.ui.tools.impl.modify.strategy;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.commands.ModifyCommand;
import com.plot.core.graphics.DrawContext;
import com.plot.core.model.Shape;
import com.plot.ui.canvas.CanvasCamera;
import com.plot.ui.theme.ThemeManager;
import com.plot.ui.tools.impl.modify.helper.AlignHandler;
import com.plot.ui.tools.impl.modify.dto.ModifyParameters;
import imgui.ImDrawList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.plot.utils.ExceptionDebug;

import java.util.List;
import java.util.ArrayList;
import com.plot.utils.PlotI18n;

/**
 * 对齐工具与选择功能结合策略
 *
 * <p>这个策略结合了选择工具和对齐工具的功能：</p>
 * <ul>
 *   <li><strong>选择模式</strong>：左键点选/框选图形，右键完成选择</li>
 *   <li><strong>对齐模式</strong>：右键后进入四点对齐模式（S1 -> T1 -> S2 -> T2）</li>
 *   <li><strong>智能切换</strong>：根据右键操作在选择和对齐间切换</li>
 * </ul>
 *
 * @author Plot Team
 * @version 1.0 - 对齐选择结合策略
 */
public class AlignWithSelectionStrategy extends BaseSelectionStrategy implements IModifyStrategy {
    private static final Logger LOGGER = LoggerFactory.getLogger(AlignWithSelectionStrategy.class);

    // 策略模式枚举
    public enum StrategyMode {
        SELECTION("strategy.plot.mode.selection", "strategy.plot.mode.selection.desc"),
        ALIGN("strategy.plot.mode.align", "strategy.plot.mode.align.desc");

        private final String nameKey;
        private final String descKey;

        StrategyMode(String nameKey, String descKey) {
            this.nameKey = nameKey;
            this.descKey = descKey;
        }

        public String getDisplayName() { return PlotI18n.modeLabel(nameKey); }
        public String getDescription() { return PlotI18n.modeLabel(descKey); }
    }

    // 对齐状态枚举（继承自原始AlignStrategy）
    public enum AlignState {
        IDLE("mode.plot.common.idle", "mode.plot.align.state.idle.desc"),
        AWAIT_SOURCE1("mode.plot.align.state.await_source1", "mode.plot.align.state.await_source1.desc"),
        AWAIT_TARGET1("mode.plot.align.state.await_target1", "mode.plot.align.state.await_target1.desc"),
        AWAIT_SOURCE2("mode.plot.align.state.await_source2", "mode.plot.align.state.await_source2.desc"),
        AWAIT_TARGET2("mode.plot.align.state.await_target2", "mode.plot.align.state.await_target2.desc");

        private final String nameKey;
        private final String descKey;

        AlignState(String nameKey, String descKey) {
            this.nameKey = nameKey;
            this.descKey = descKey;
        }

        public String getDisplayName() { return PlotI18n.modeLabel(nameKey); }
        public String getDescription() { return PlotI18n.modeLabel(descKey); }
    }

    // 常量
    private static final int ESC_KEY = 27;

    // 策略状态
    private StrategyMode currentMode = StrategyMode.SELECTION;
    private AlignState alignState = AlignState.IDLE;

    // 对齐坐标状态（四点对齐）
    private Vec2d sourcePoint1;
    private Vec2d targetPoint1;
    private Vec2d sourcePoint2;
    private Vec2d targetPoint2;
    private boolean scaleEnabled = false; // 默认不开启缩放

    // 对齐处理器和参数
    private AlignHandler alignHandler;
    private ModifyParameters alignParameters;
    private List<Shape> previewShapes;
    private ModifyCommand pendingCommand;

    // 预览辅助线：S1->T1, S2->T2
    private List<com.plot.ui.tools.impl.modify.helper.AlignmentGuide> previewGuides = new ArrayList<>();

    /**
     * 默认构造函数
     */
    public AlignWithSelectionStrategy() {
        this.alignParameters = new ModifyParameters();
        this.alignHandler = AlignHandler.getInstance();
    }

    @Override
    public ModifyResult onMouseDown(Vec2d pos, int button, ModifyToolContext context) {
        if (button == MOUSE_RIGHT) {
            return handleRightMouseDown(pos, context);
        } else if (button == MOUSE_LEFT) {
            return handleLeftMouseDown(pos, context);
        }
        return ModifyResult.IGNORED;
    }

    /**
     * 处理右键按下
     * 用于在选择模式和对齐模式之间切换
     */
    private ModifyResult handleRightMouseDown(Vec2d pos, ModifyToolContext context) {
        if (currentMode == StrategyMode.SELECTION) {
            // 在选择模式下右键：完成选择，切换到对齐模式
            if (!selectedShapeIds.isEmpty()) {
                currentMode = StrategyMode.ALIGN;
                selectedShapes = getSelectedShapesFromIds(context);
                alignState = AlignState.AWAIT_SOURCE1;
                context.setStatusMessage(PlotI18n.status("status.plot.align.initial_source1", selectedShapeIds.size()));
                LOGGER.info("切换到对齐模式，已选择 {} 个图形", selectedShapeIds.size());
                return ModifyResult.CONTINUE;
            } else {
                context.setStatusMessage("status.plot.align.initial_select");
                return ModifyResult.NEED_SELECTION;
            }
        } else {
            // 在对齐模式下右键：取消对齐，返回选择模式
            resetAlignState();
            currentMode = StrategyMode.SELECTION;
            context.setStatusMessage("status.plot.align.cancelled");
            LOGGER.info("从对齐模式返回选择模式");
            return ModifyResult.CANCEL;
        }
    }

    /**
     * 处理左键按下
     * 根据当前模式执行不同操作
     */
    private ModifyResult handleLeftMouseDown(Vec2d pos, ModifyToolContext context) {
        if (currentMode == StrategyMode.SELECTION) {
            return handleSelectionMouseDown(pos, context);
        } else {
            return handleAlignMouseDown(pos, context);
        }
    }

    /**
     * 处理对齐模式下的左键按下
     */
    private ModifyResult handleAlignMouseDown(Vec2d pos, ModifyToolContext context) {
        if (selectedShapes == null || selectedShapes.isEmpty()) {
            context.setStatusMessage("status.plot.align.no_selection");
            return ModifyResult.NEED_SELECTION;
        }

        try {
            // 吸附点
            Vec2d snapped = context.getSnapHandler().getSnappedWorldPoint(pos, context.getCamera());

            switch (alignState) {
                case AWAIT_SOURCE1 -> {
                    // 第一步：在选中的图形上选择第一个源点
                    sourcePoint1 = projectToSelection(snapped, selectedShapes);
                    alignState = AlignState.AWAIT_TARGET1;
                    context.setStatusMessage("status.plot.align.source1");
                    updatePreviewGuides();
                    return ModifyResult.CONTINUE;
                }
                case AWAIT_TARGET1 -> {
                    // 第二步：选择第一个目标点（任意位置，可以是网格点、空白处等）
                    targetPoint1 = snapped;
                    alignState = AlignState.AWAIT_SOURCE2;
                    context.setStatusMessage("status.plot.align.target1_source2");
                    updatePreviewGuides();
                    return ModifyResult.CONTINUE;
                }
                case AWAIT_SOURCE2 -> {
                    // 第三步：在要移动的图形上选择第二个源点
                    sourcePoint2 = projectToSelection(snapped, selectedShapes);
                    alignState = AlignState.AWAIT_TARGET2;
                    context.setStatusMessage("status.plot.align.source2_target2");
                    updatePreviewGuides();
                    return ModifyResult.CONTINUE;
                }
                case AWAIT_TARGET2 -> {
                    // 第四步：选择第二个目标点（任意位置，可以是网格点、空白处等），完成对齐
                    targetPoint2 = snapped;
                    return performPointPairAlign(selectedShapes, context);
                }
                default -> {
                    return ModifyResult.IGNORED;
                }
            }
        } catch (Exception e) {
            LOGGER.error("对齐策略鼠标按下处理失败: {}", e.getMessage(), e);
            resetAlignState();
            return ModifyResult.CANCEL;
        }
    }

    @Override
    public ModifyResult onMouseMove(Vec2d pos, ModifyToolContext context) {
        if (currentMode == StrategyMode.SELECTION) {
            return handleSelectionMouseMove(pos, context);
        } else {
            return handleAlignMouseMove(pos, context);
        }
    }

    /**
     * 处理对齐模式下的鼠标移动
     */
    private ModifyResult handleAlignMouseMove(Vec2d pos, ModifyToolContext context) {
        try {
            Vec2d snapped = context.getSnapHandler().getSnappedWorldPoint(pos, context.getCamera());
            
            // 在等待目标点1时，显示从源点1到当前鼠标位置的预览线
            if (alignState == AlignState.AWAIT_TARGET1 && sourcePoint1 != null) {
                updatePreviewGuidesWithDynamicTarget1(snapped);
                context.setPreviewEnabled(true);
                return ModifyResult.CONTINUE;
            }
            
            // 在等待源点2时，显示从源点1到目标点1的固定线，以及从当前鼠标位置到目标点1的预览线
            if (alignState == AlignState.AWAIT_SOURCE2 && sourcePoint1 != null && targetPoint1 != null) {
                updatePreviewGuidesWithDynamicSource2(snapped);
                context.setPreviewEnabled(true);
                return ModifyResult.CONTINUE;
            }
            
            // 在等待目标点2时，实时预览对齐结果
            if (alignState == AlignState.AWAIT_TARGET2 && sourcePoint1 != null && targetPoint1 != null && sourcePoint2 != null) {
                if (selectedShapes.isEmpty()) return ModifyResult.CONTINUE;

                // 生成预览
                List<Shape> modified = transformShapes(selectedShapes, sourcePoint1, targetPoint1, sourcePoint2, snapped, scaleEnabled);
                createPreviewShapesFromModified(modified);
                // 更新预览辅助线
                updatePreviewGuidesWithDynamicTarget2(snapped);
                context.setPreviewEnabled(true);
                return ModifyResult.CONTINUE;
            }
        } catch (Exception e) {
            LOGGER.debug("对齐预览失败: {}", e.getMessage());
        }
        return ModifyResult.CONTINUE;
    }

    @Override
    public ModifyResult onMouseUp(Vec2d pos, int button, ModifyToolContext context) {
        // 参考SelectionStrategy的条件检查
        if (button != MOUSE_LEFT || !isSelecting) {
            return ModifyResult.IGNORED;
        }

        if (currentMode == StrategyMode.SELECTION) {
            return handleSelectionMouseUp(pos, context);
        } else {
            // 对齐模式下不处理鼠标释放
            return ModifyResult.IGNORED;
        }
    }

    @Override
    public ModifyResult onKeyDown(int keyCode, ModifyToolContext context) {
        if (keyCode == ESC_KEY) {
            reset();
            context.setStatusMessage("status.plot.common.operation_cancelled");
            return ModifyResult.CANCEL;
        }
        return ModifyResult.IGNORED;
    }

    @Override
    public ModifyResult onKeyUp(int keyCode, ModifyToolContext context) {
        return IModifyStrategy.super.onKeyUp(keyCode, context);
    }

    /**
     * 执行四点对齐操作
     */
    private ModifyResult performPointPairAlign(List<Shape> shapes, ModifyToolContext context) {
        try {
            List<Shape> modified = transformShapes(shapes, sourcePoint1, targetPoint1, sourcePoint2, targetPoint2, scaleEnabled);
            createPreviewShapesFromModified(modified);

            ModifyParameters parameters = new ModifyParameters();
            parameters.setString("mode", "POINT_PAIR");
            parameters.setBoolean("scaleEnabled", scaleEnabled);

            pendingCommand = alignHandler.createModifyCommand(shapes, modified, parameters);
            if (pendingCommand != null) {
                // 完成
                context.setStatusMessage("status.plot.align.complete");
                // 即时清空预览辅助线，避免保留在屏幕上
                if (previewGuides != null) previewGuides.clear();
                previewShapes = null;
                // 重置状态机
                alignState = AlignState.AWAIT_SOURCE1;
                sourcePoint1 = targetPoint1 = sourcePoint2 = targetPoint2 = null;
                return ModifyResult.COMPLETE;
            }
            context.setStatusMessage("status.plot.align.command_failed");
            return ModifyResult.CANCEL;
        } catch (Exception e) {
            LOGGER.error("点对点对齐失败: {}", e.getMessage(), e);
            context.setStatusMessage(PlotI18n.status("status.plot.common.failed", PlotI18n.tr("tool.plot.align"), e.getMessage()));
            return ModifyResult.CANCEL;
        }
    }

    /**
     * 从修改后的图形创建预览图形
     */
    private void createPreviewShapesFromModified(List<Shape> modifiedShapes) {
        if (modifiedShapes == null || modifiedShapes.isEmpty()) {
            previewShapes = null;
            return;
        }
        
        previewShapes = new ArrayList<>();
        
        for (Shape shape : modifiedShapes) {
            try {
                // 设置预览样式
                shape.setStyle(com.plot.core.graphics.style.ShapeStyle.PREVIEW);
                
                // 修复：清除预览图形的选中状态，确保使用图层颜色而不是选中颜色
                shape.setSelected(false);
                shape.setHighlighted(false);
                
                previewShapes.add(shape);
            } catch (Exception e) {
                LOGGER.warn("创建预览图形失败: {}", e.getMessage());
                // 如果设置样式失败，添加原图形
                previewShapes.add(shape);
            }
        }
    }

    /**
     * 变换图形（继承自原始AlignStrategy）
     */
    private List<Shape> transformShapes(List<Shape> shapes, Vec2d s1, Vec2d t1, Vec2d s2, Vec2d t2, boolean allowScale) {
        List<Shape> result = new ArrayList<>();
        if (s1 == null || t1 == null || s2 == null || t2 == null) return result;

        Vec2d vs = s2.subtract(s1);
        Vec2d vt = t2.subtract(t1);

        double lenS = Math.max(1e-9, Math.hypot(vs.x, vs.y));
        double lenT = Math.max(1e-9, Math.hypot(vt.x, vt.y));

        double angleS = Math.atan2(vs.y, vs.x);
        double angleT = Math.atan2(vt.y, vt.x);
        double theta = angleT - angleS;
        double scale = allowScale ? (lenT / lenS) : 1.0;

        for (Shape shape : shapes) {
            try {
                Shape clone = shape.clone();
                // 先围绕 s1 进行旋转与缩放，再平移到 t1
                clone.rotate(theta, s1);
                clone.scale(new Vec2d(scale, scale), s1);
                Vec2d translation = t1.subtract(s1);
                clone.translate(translation);
                result.add(clone);
            } catch (Exception e) {
                LOGGER.warn("转换图形失败: {}", e.getMessage());
                result.add(shape);
            }
        }
        return result;
    }

    /**
     * 更新静态/已选点产生的预览辅助线
     */
    private void updatePreviewGuides() {
        previewGuides.clear();
        try {
            if (sourcePoint1 != null && targetPoint1 != null) {
                previewGuides.add(new com.plot.ui.tools.impl.modify.helper.AlignmentGuide(
                        sourcePoint1, targetPoint1, "PAIR_1"));
            }
            if (sourcePoint2 != null && targetPoint2 != null) {
                previewGuides.add(new com.plot.ui.tools.impl.modify.helper.AlignmentGuide(
                        sourcePoint2, targetPoint2, "PAIR_2"));
            }
        } catch (Exception e) { ExceptionDebug.log("AlignWithSelectionStrategy: build alignment preview guides", e); }
    }

    /**
     * 在等待目标点1时，显示从源点1到当前鼠标位置的预览线
     */
    private void updatePreviewGuidesWithDynamicTarget1(Vec2d dynamicTarget1) {
        previewGuides.clear();
        try {
            if (sourcePoint1 != null && dynamicTarget1 != null) {
                previewGuides.add(new com.plot.ui.tools.impl.modify.helper.AlignmentGuide(
                        sourcePoint1, dynamicTarget1, "PAIR_1_PREVIEW"));
            }
        } catch (Exception e) { ExceptionDebug.log("AlignWithSelectionStrategy: preview guide for dynamic target 1", e); }
    }

    /**
     * 在等待源点2时，显示从源点1到目标点1的固定线，以及从当前鼠标位置到目标点1的预览线
     */
    private void updatePreviewGuidesWithDynamicSource2(Vec2d dynamicSource2) {
        previewGuides.clear();
        try {
            if (sourcePoint1 != null && targetPoint1 != null) {
                previewGuides.add(new com.plot.ui.tools.impl.modify.helper.AlignmentGuide(
                        sourcePoint1, targetPoint1, "PAIR_1"));
            }
        } catch (Exception e) { ExceptionDebug.log("AlignWithSelectionStrategy: preview guide while selecting source point 2", e); }
    }

    /**
     * 在移动选择第四点时，第二条辅助线用动态目标点
     */
    private void updatePreviewGuidesWithDynamicTarget2(Vec2d dynamicTarget2) {
        previewGuides.clear();
        try {
            if (sourcePoint1 != null && targetPoint1 != null) {
                previewGuides.add(new com.plot.ui.tools.impl.modify.helper.AlignmentGuide(
                        sourcePoint1, targetPoint1, "PAIR_1"));
            }
            if (sourcePoint2 != null && dynamicTarget2 != null) {
                previewGuides.add(new com.plot.ui.tools.impl.modify.helper.AlignmentGuide(
                        sourcePoint2, dynamicTarget2, "PAIR_2_PREVIEW"));
            }
        } catch (Exception e) { ExceptionDebug.log("AlignWithSelectionStrategy: preview guide for dynamic target 2", e); }
    }

    /**
     * 将任意点投影到所选图形集合的最近点，保证源点落在选中图形上或其几何附近
     */
    private Vec2d projectToSelection(Vec2d point, List<Shape> shapes) {
        if (shapes == null || shapes.isEmpty() || point == null) return point;
        Vec2d best = null;
        double bestDist = Double.POSITIVE_INFINITY;
        for (Shape s : shapes) {
            try {
                Vec2d cp = s.getClosestPoint(point);
                if (cp != null) {
                    double d = cp.distance(point);
                    if (d < bestDist) {
                        bestDist = d;
                        best = cp;
                    }
                }
            } catch (Exception e) { ExceptionDebug.log("AlignWithSelectionStrategy: project point onto shape", e); }
        }
        return best != null ? best : point;
    }

    /**
     * 重置对齐状态
     */
    private void resetAlignState() {
        alignState = AlignState.IDLE;
        sourcePoint1 = null;
        targetPoint1 = null;
        sourcePoint2 = null;
        targetPoint2 = null;
        previewShapes = null;
        pendingCommand = null;
        if (previewGuides != null) previewGuides.clear();
    }

    @Override
    public ModifyCommand getModifyCommand() {
        return pendingCommand;
    }

    @Override
    public void reset() {
        LOGGER.debug("AlignWithSelectionStrategy 重置");

        // 重置选择状态
        resetSelectionState();

        // 重置对齐状态
        resetAlignState();

        // 返回选择模式
        currentMode = StrategyMode.SELECTION;
        
        LOGGER.debug("AlignWithSelectionStrategy 重置完成，当前模式: {}", currentMode);
    }

    @Override
    public String getStrategyName() {
        return PlotI18n.modeLabel("strategy.plot.name.align_with_selection");
    }

    @Override
    public String getStrategyDescription() {
        return currentMode.getDescription();
    }

    @Override
    public boolean requiresSelection() {
        return false; // 这个策略自己处理选择
    }

    @Override
    public int getMinimumSelectionCount() {
        return 0;
    }

    @Override
    public int getMaximumSelectionCount() {
        return IModifyStrategy.super.getMaximumSelectionCount();
    }

    /**
     * 获取当前模式
     */
    public StrategyMode getCurrentMode() {
        return currentMode;
    }

    /**
     * 获取预览图形
     */
    public List<Shape> getPreviewShapes() {
        return previewShapes;
    }

    /**
     * 设置是否允许缩放
     */
    public void setScaleEnabled(boolean enabled) {
        this.scaleEnabled = enabled;
        LOGGER.debug("对齐缩放设置已更新: {}", enabled);
    }


    /**
     * 重写选择状态消息，添加对齐相关信息
     */
    @Override
    protected ModifyResult handleSelectionMouseUp(Vec2d pos, ModifyToolContext context) {
        ModifyResult result = super.handleSelectionMouseUp(pos, context);
        
        // 更新状态消息以包含对齐信息
        if (result == ModifyResult.COMPLETE) {
            int count = getSelectedCount();
            if (count > 0) {
                context.setStatusMessage(PlotI18n.status("status.plot.align.four_point", count));
            } else {
                context.setStatusMessage("status.plot.align.select_shapes");
            }
        }
        
        return result;
    }

    /**
     * 渲染预览
     */
    public void renderPreview(DrawContext context) {
        if (currentMode == StrategyMode.SELECTION) {
            renderSelectionPreview(context);
        } else {
            renderAlignPreview(context);
        }
    }

    /**
     * 渲染预览（ImGui版本）
     */
    public void renderPreview(ImDrawList drawList, CanvasCamera camera) {
        if (currentMode == StrategyMode.SELECTION) {
            renderSelectionPreviewImGui(drawList, camera);
        } else {
            renderAlignPreviewImGui(drawList, camera);
        }
    }

    /**
     * 渲染对齐预览
     */
    private void renderAlignPreview(DrawContext context) {
        // 渲染预览图形
        if (previewShapes != null) {
            for (Shape shape : previewShapes) {
                shape.render(context);
            }
        }

        // 渲染预览辅助线
        for (com.plot.ui.tools.impl.modify.helper.AlignmentGuide guide : previewGuides) {
            context.drawDashedLine(guide.getStart(), guide.getEnd(), 
                new java.awt.Color(0, 255, 0, 180)); // 绿色虚线
        }
    }

    /**
     * 渲染对齐预览（ImGui版本）
     */
    private void renderAlignPreviewImGui(ImDrawList drawList, CanvasCamera camera) {
        try {
            var theme = ThemeManager.getInstance().getCurrentTheme();
            // 渲染预览辅助线
            for (com.plot.ui.tools.impl.modify.helper.AlignmentGuide guide : previewGuides) {
                Vec2d screenStart = camera.worldToScreen(guide.getStart());
                Vec2d screenEnd = camera.worldToScreen(guide.getEnd());
                
                drawList.addLine(
                    (float) screenStart.x, (float) screenStart.y,
                    (float) screenEnd.x, (float) screenEnd.y,
                    theme.successText, 2.0f
                );
            }
        } catch (Exception e) {
            LOGGER.warn("渲染对齐预览时出错: {}", e.getMessage());
        }
    }
}
