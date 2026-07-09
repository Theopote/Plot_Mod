package com.plot.ui.tools.impl.modify.strategy;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.commands.ModifyCommand;
import com.plot.core.model.Shape;
import com.plot.ui.tools.impl.modify.constants.AlignConstants;
import com.plot.ui.tools.impl.modify.helper.AlignHandler;
import com.plot.ui.tools.impl.modify.dto.ModifyParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.plot.utils.ExceptionDebug;

import java.util.*;
import java.util.List;
import java.util.Collections;
import com.plot.utils.PlotI18n;

/**
 * 对齐策略实现 - 重构版本
 * 
 * <p>专注于对齐交互逻辑和状态管理：</p>
 * <ul>
 *   <li>处理用户输入（鼠标、键盘事件）</li>
 *   <li>管理对齐流程状态</li>
 *   <li>委托计算逻辑给AlignHandler</li>
 *   <li>准备渲染所需的数据</li>
 * </ul>
 * 
 * @author Plot Team
 * @version 2.0 - 重构版本
 */
public class AlignStrategy implements IModifyStrategy {
    private static final Logger LOGGER = LoggerFactory.getLogger(AlignStrategy.class);
    
    // 委托给AlignHandler进行对齐计算
    private final AlignHandler alignHandler;
    
    /**
     * 对齐模式枚举
     */
    public enum AlignMode {
        LEFT("align.plot.mode.left", "align.plot.mode.left.desc"),
        RIGHT("align.plot.mode.right", "align.plot.mode.right.desc"),
        CENTER("align.plot.mode.center", "align.plot.mode.center.desc"),
        TOP("align.plot.mode.top", "align.plot.mode.top.desc"),
        BOTTOM("align.plot.mode.bottom", "align.plot.mode.bottom.desc"),
        MIDDLE("align.plot.mode.middle", "align.plot.mode.middle.desc"),
        DISTRIBUTE_H("align.plot.mode.distribute_h", "align.plot.mode.distribute_h.desc"),
        DISTRIBUTE_V("align.plot.mode.distribute_v", "align.plot.mode.distribute_v.desc");

        private final String nameKey;
        private final String descKey;

        AlignMode(String nameKey, String descKey) {
            this.nameKey = nameKey;
            this.descKey = descKey;
        }

        public String getDisplayName() { return PlotI18n.modeLabel(nameKey); }
        public String getDescription() { return PlotI18n.modeLabel(descKey); }
    }

    /**
     * 参考模式枚举
     */
    public enum ReferenceMode {
        SELECTION_BOUNDS("align.plot.ref.selection_bounds", "align.plot.ref.selection_bounds.desc"),
        FIRST_SELECTED("align.plot.ref.first_selected", "align.plot.ref.first_selected.desc"),
        LAST_SELECTED("align.plot.ref.last_selected", "align.plot.ref.last_selected.desc"),
        LARGEST("align.plot.ref.largest", "align.plot.ref.largest.desc");

        private final String nameKey;
        private final String descKey;

        ReferenceMode(String nameKey, String descKey) {
            this.nameKey = nameKey;
            this.descKey = descKey;
        }

        public String getDisplayName() { return PlotI18n.modeLabel(nameKey); }
        public String getDescription() { return PlotI18n.modeLabel(descKey); }
    }
    
    // 旧模式（保留字段但不再用于交互式点对点对齐）
    private AlignMode currentMode = AlignMode.LEFT;
    private ReferenceMode referenceMode = ReferenceMode.SELECTION_BOUNDS;

    // 新的交互式对齐状态机（CAD风格：S1 -> T1 -> S2 -> T2）
    private enum AlignState { IDLE, AWAIT_SOURCE1, AWAIT_TARGET1, AWAIT_SOURCE2, AWAIT_TARGET2 }
    private AlignState alignState = AlignState.IDLE;
    private Vec2d sourcePoint1;
    private Vec2d targetPoint1;
    private Vec2d sourcePoint2;
    private Vec2d targetPoint2;
    private boolean scaleEnabled = false; // 默认不开启缩放
    
    // 对齐数据 - 存储要移动的图形（必须在工具激活前选中）
    private final List<Shape> shapesToMove = new ArrayList<>();
    private List<Shape> previewShapes;
    // 预览辅助线：S1->T1, S2->T2
    private List<com.plot.ui.tools.impl.modify.helper.AlignmentGuide> previewGuides = new ArrayList<>();
    
    // 命令状态
    private ModifyCommand pendingCommand;
    
    /**
     * 默认构造函数
     */
    public AlignStrategy() {
        // 委托给AlignHandler进行对齐计算
        this.alignHandler = AlignHandler.getInstance();
        LOGGER.debug("AlignStrategy 已创建，模式: {}", currentMode.getDisplayName());
    }
    
    /**
     * 设置是否允许缩放
     */
    public void setScaleEnabled(boolean enabled) {
        this.scaleEnabled = enabled;
        LOGGER.debug("对齐缩放设置已更新: {}", enabled);
    }

    @Override
    public ModifyResult onMouseDown(Vec2d pos, int button, ModifyToolContext context) {
        if (button != AlignConstants.MOUSE_LEFT) {
            if (button == AlignConstants.MOUSE_RIGHT) {
                // 右键取消当前操作
                reset();
                context.setStatusMessage("status.plot.align.cancelled");
                return ModifyResult.CANCEL;
            }
            return ModifyResult.IGNORED;
        }

        try {
            // 吸附点
            Vec2d snapped = context.getSnapHandler().getSnappedWorldPoint(pos, context.getCamera());

            switch (alignState) {
                case IDLE -> {
                    // 第一步：检查是否有选中的图形，并锁定要移动的图形
                    List<Shape> shapes = context.getSelectedShapes();
                    if (shapes == null || shapes.isEmpty()) {
                        context.setStatusMessage("status.plot.move.initial_select");
                        return ModifyResult.NEED_SELECTION;
                    }
                    
                    // 锁定要移动的图形集合
                    shapesToMove.clear();
                    shapesToMove.addAll(shapes);
                    
                    // 在选中的图形上选择第一个源点
                    sourcePoint1 = projectToSelection(snapped, shapesToMove);
                    alignState = AlignState.AWAIT_TARGET1;
                    context.setStatusMessage("status.plot.align.source1_short");
                    updatePreviewGuides();
                    return ModifyResult.CONTINUE;
                }
                case AWAIT_TARGET1 -> {
                    // 第二步：选择第一个目标点（任意位置）
                    targetPoint1 = snapped;
                    alignState = AlignState.AWAIT_SOURCE2;
                    context.setStatusMessage("status.plot.align.target1_source2");
                    updatePreviewGuides();
                    return ModifyResult.CONTINUE;
                }
                case AWAIT_SOURCE2 -> {
                    // 第三步：在要移动的图形上选择第二个源点
                    sourcePoint2 = projectToSelection(snapped, shapesToMove);
                    alignState = AlignState.AWAIT_TARGET2;
                    context.setStatusMessage("status.plot.align.source2_short");
                    updatePreviewGuides();
                    return ModifyResult.CONTINUE;
                }
                case AWAIT_TARGET2 -> {
                    // 第四步：选择第二个目标点，完成对齐
                    targetPoint2 = snapped;
                    return performPointPairAlign(shapesToMove, context);
                }
                default -> {
                    return ModifyResult.IGNORED;
                }
            }
        } catch (Exception e) {
            LOGGER.error("对齐策略鼠标按下处理失败: {}", e.getMessage(), e);
            reset();
            return ModifyResult.CANCEL;
        }
    }
    
    @Override
    public ModifyResult onMouseMove(Vec2d pos, ModifyToolContext context) {
        // 在等待目标点2时，实时预览对齐结果
        if (alignState == AlignState.AWAIT_TARGET2 && sourcePoint1 != null && targetPoint1 != null && sourcePoint2 != null) {
            try {
                Vec2d previewTarget2 = context.getSnapHandler().getSnappedWorldPoint(pos, context.getCamera());
                if (shapesToMove.isEmpty()) return ModifyResult.CONTINUE;

                // 生成预览
                List<Shape> modified = transformShapes(shapesToMove, sourcePoint1, targetPoint1, sourcePoint2, previewTarget2, scaleEnabled);
                createPreviewShapesFromModified(modified);
                // 更新预览辅助线
                updatePreviewGuidesWithDynamicTarget2(previewTarget2);
                context.setPreviewEnabled(true);
                return ModifyResult.CONTINUE;
            } catch (Exception e) {
                LOGGER.debug("对齐预览失败: {}", e.getMessage());
            }
        }
        return ModifyResult.CONTINUE;
    }
    
    @Override
    public ModifyResult onMouseUp(Vec2d pos, int button, ModifyToolContext context) {
        // 对齐工具通常不需要鼠标释放处理
        return ModifyResult.IGNORED;
    }
    
    @Override
    public ModifyResult onKeyDown(int keyCode, ModifyToolContext context) {
        if (keyCode == AlignConstants.ESC_KEY) {
            reset();
            context.setStatusMessage("status.plot.align.cancelled");
            return ModifyResult.CANCEL;
        }
        return ModifyResult.IGNORED;
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

    // ====== 新增：点对点对齐实现 ======
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
        } catch (Exception e) { ExceptionDebug.log("AlignStrategy: build alignment preview guides", e); }
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
        } catch (Exception e) { ExceptionDebug.log("AlignStrategy: preview guide for dynamic target 2", e); }
    }

    /**
     * 获取当前的预览辅助线
     */
    public List<com.plot.ui.tools.impl.modify.helper.AlignmentGuide> getPreviewGuides() {
        return previewGuides != null ? Collections.unmodifiableList(previewGuides) : List.of();
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
            } catch (Exception e) { ExceptionDebug.log("AlignStrategy: project point onto shape", e); }
        }
        return best != null ? best : point;
    }
    
    // 这些方法现在由AlignHandler处理，不再需要在这里实现
    
    @Override
    public ModifyCommand getModifyCommand() {
        return pendingCommand;
    }
    
    @Override
    public void reset() {
        shapesToMove.clear();
        pendingCommand = null;
        previewShapes = null;
        alignState = AlignState.IDLE;
        sourcePoint1 = null;
        targetPoint1 = null;
        sourcePoint2 = null;
        targetPoint2 = null;
        if (previewGuides != null) previewGuides.clear();
        LOGGER.debug("对齐策略已重置");
    }
    
    @Override
    public String getStrategyName() {
        return PlotI18n.modeLabel("strategy.plot.name.align");
    }

    @Override
    public String getStrategyDescription() {
        return PlotI18n.modeLabel("strategy.plot.desc.align");
    }
    
    @Override
    public boolean requiresSelection() {
        return true; // 对齐工具需要预先选择图形
    }
    
    @Override
    public int getMinimumSelectionCount() {
        return switch (currentMode) {
            case DISTRIBUTE_H, DISTRIBUTE_V -> 3; // 分布至少需要3个图形
            default -> 2; // 其他对齐模式至少需要2个图形
        };
    }
    
    @Override
    public int getMaximumSelectionCount() {
        return -1; // 无限制
    }
    
    // ====== 配置方法 ======
    
    /**
     * 设置对齐模式
     */
    public void setAlignMode(AlignMode mode) {
        if (this.currentMode != mode) {
            this.currentMode = mode;
            LOGGER.debug("对齐模式已切换为: {}", mode.getDisplayName());
        }
    }
    
    /**
     * 获取当前模式
     */
    public AlignMode getCurrentMode() {
        return currentMode;
    }
    
    /**
     * 设置参考模式
     */
    public void setReferenceMode(ReferenceMode mode) {
        if (this.referenceMode != mode) {
            this.referenceMode = mode;
            LOGGER.debug("参考模式已切换为: {}", mode.getDisplayName());
        }
    }

    /**
     * 获取选中的图形列表
     */
    public List<Shape> getSelectedShapes() {
        return Collections.unmodifiableList(shapesToMove);
    }

    /**
     * 获取预览图形列表
     */
    public List<Shape> getPreviewShapes() {
        return previewShapes != null ? Collections.unmodifiableList(previewShapes) : null;
    }
    
    /**
     * 设置预览图形列表
     */
    public void setPreviewShapes(List<Shape> previewShapes) {
        this.previewShapes = previewShapes != null ? new ArrayList<>(previewShapes) : null;
    }
    
    // ====== 渲染数据获取方法 ======
    
    /**
     * 获取对齐辅助线
     * 
     * @return 对齐辅助线列表
     */
    public List<com.plot.ui.tools.impl.modify.helper.AlignmentGuide> getAlignmentGuides() {
        // 在四点对齐交互进行中，不显示标准对齐辅助线（避免首次点选出现多余绿色线）
        if (alignState != AlignState.IDLE) {
            return new ArrayList<>();
        }

        if (shapesToMove.isEmpty()) {
            return new ArrayList<>();
        }
        
        return alignHandler.calculateAlignmentGuides(
            shapesToMove, 
            currentMode.name(), 
            referenceMode.name()
        );
    }
    
    /**
     * 获取参考点信息
     * 
     * @return 参考点信息
     */
    public com.plot.ui.tools.impl.modify.helper.ReferencePointInfo getReferencePointInfo() {
        if (shapesToMove.isEmpty()) {
            return null;
        }
        
        return alignHandler.calculateReferencePointInfo(shapesToMove, referenceMode.name());
    }
}