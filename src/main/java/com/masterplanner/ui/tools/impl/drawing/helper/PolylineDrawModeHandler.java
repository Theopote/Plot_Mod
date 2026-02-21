package com.masterplanner.ui.tools.impl.drawing.helper;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.core.geometry.shapes.PolylineShape;
import com.masterplanner.core.graphics.style.ShapeStyle;
import com.masterplanner.core.model.Shape;
import com.masterplanner.ui.tools.impl.drawing.strategy.IInteractionStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import imgui.ImGui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

// 导入常量以便使用
import static com.masterplanner.ui.tools.impl.drawing.PolylineTool.Constants.*;

/**
 * 折线绘制模式处理器
 * <p>
 * 负责处理折线模式下的所有交互逻辑，包括：
 * - 点击添加顶点
 * - 双击完成绘制
 * - 预览渲染
 * - 最终图形生成
 */
public class PolylineDrawModeHandler extends AbstractModeHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(PolylineDrawModeHandler.class);
    
    // 折线特有状态
    private final List<Vec2d> points = new ArrayList<>();
    
    public PolylineDrawModeHandler(StyleHandler styleHandler) {
        super(styleHandler);
    }
    
    @Override
    public IInteractionStrategy.InteractionResult onMouseDown(Vec2d pos, int button, DrawingToolContext context) {
        LOGGER.debug("PolylineDrawModeHandler.onMouseDown: 位置={}, 按钮={}", pos, button);
        
        if (button != 0) { // 非左键
            if (button == 1) { // 右键
                if (isDrawing && points.size() >= 2) {
                    // 右键完成绘制
                    LOGGER.debug("折线模式右键完成绘制，当前点数：{}", points.size());
                    return IInteractionStrategy.InteractionResult.COMPLETE;
                } else {
                    // 点数不足，取消绘制
                    LOGGER.debug("折线模式右键取消绘制，点数不足");
                    reset();
                    context.resetDrawing("右键取消");
                    return IInteractionStrategy.InteractionResult.CANCEL;
                }
            }
            LOGGER.debug("忽略非左键按钮: {}", button);
            return IInteractionStrategy.InteractionResult.IGNORED;
        }
        
        // 处理左键点击
        return handleLeftMouseDown(pos, context);
    }
    
    @Override
    protected IInteractionStrategy.InteractionResult handleLeftMouseDown(Vec2d pos, DrawingToolContext context) {
        Vec2d worldPoint = getWorldPoint(pos, context);
        
        LOGGER.debug("折线模式鼠标按下: 位置={}", worldPoint);
        
        if (!isDrawing) {
            // 开始新的折线
            points.clear();
            points.add(worldPoint);
            isDrawing = true;
            context.updateStatusMessage("点击添加下一个顶点，右键或Esc键结束绘制");
            LOGGER.debug("折线模式：开始新的折线");
            
            // 通知工具更新预览
            notifyPreviewUpdate(context);
        } else {
            // 添加新点（Shift 下与预览一致的正交点）
            boolean shiftDown = ImGui.getIO().getKeyShift();
            if (shiftDown && !points.isEmpty()) {
                Vec2d last = points.getLast();
                if (currentMousePoint != null) {
                    // 与预览一致
                    worldPoint = currentMousePoint;
                } else {
                    // 回退：现场计算正交锁定
                    Vec2d delta = new Vec2d(worldPoint.x - last.x, worldPoint.y - last.y);
                    if (Math.abs(delta.x) >= Math.abs(delta.y)) {
                        worldPoint = new Vec2d(worldPoint.x, last.y);
                    } else {
                        worldPoint = new Vec2d(last.x, worldPoint.y);
                    }
                }
            }
            points.add(worldPoint);
            LOGGER.debug("折线模式：添加新顶点，当前点数：{}", points.size());
            context.updateStatusMessage("点击添加下一个顶点，右键或Esc键结束绘制");
            
            // 通知工具更新预览
            notifyPreviewUpdate(context);
        }
        
        return IInteractionStrategy.InteractionResult.CONTINUE;
    }
    
    // onMouseMove 和 onMouseUp 使用基类的默认实现
    
    @Override
    public IInteractionStrategy.InteractionResult onMouseMove(Vec2d pos, DrawingToolContext context) {
        // 基础：获取吸附后的世界坐标
        Vec2d snapped = context.getSnapHandler().getSnappedWorldPoint(pos, context.getCamera());

        // 正交约束：按住 Shift 时，将预览点约束到与上一个点水平/垂直
        boolean shiftDown = ImGui.getIO().getKeyShift();
        if (isDrawing && shiftDown && !points.isEmpty()) {
            Vec2d last = points.getLast();
            Vec2d delta = new Vec2d(snapped.x - last.x, snapped.y - last.y);
            if (Math.abs(delta.x) >= Math.abs(delta.y)) {
                // 更接近水平 → 锁定 Y
                currentMousePoint = new Vec2d(snapped.x, last.y);
            } else {
                // 更接近垂直 → 锁定 X
                currentMousePoint = new Vec2d(last.x, snapped.y);
            }
        } else {
            currentMousePoint = snapped;
        }

        if (isDrawing) {
            notifyPreviewUpdate(context);
            return IInteractionStrategy.InteractionResult.CONTINUE;
        }
        return IInteractionStrategy.InteractionResult.IGNORED;
    }
    
    @Override
    public void renderPreview(DrawingAdapter adapter) {
        if (points.isEmpty()) return;
        
        // 获取当前图层的颜色
        Color layerColor = getLayerColor();
        
        // 绘制已确定的线段（使用图层颜色）
        for (int i = 0; i < points.size() - 1; i++) {
            adapter.drawLine(points.get(i), points.get(i + 1), layerColor, Rendering.PREVIEW_LINE_THICKNESS);
        }
        
        // 绘制到当前鼠标位置的预览线（使用半透明的图层颜色）
        if (currentMousePoint != null && !points.isEmpty()) {
            Vec2d lastPoint = points.getLast();
            Color previewColor = new Color(layerColor.getRed(), layerColor.getGreen(), layerColor.getBlue(), 128);
            adapter.drawLine(lastPoint, currentMousePoint, previewColor, Rendering.PREVIEW_LINE_THICKNESS);
        }
        
        // 绘制顶点（使用图层颜色）
        for (Vec2d point : points) {
            adapter.drawCircle(point, Rendering.POINT_SIZE, layerColor, true);
        }
        
        // 绘制当前鼠标位置的预览点（使用半透明的图层颜色）
        if (currentMousePoint != null) {
            Color previewColor = new Color(layerColor.getRed(), layerColor.getGreen(), layerColor.getBlue(), 128);
            adapter.drawCircle(currentMousePoint, Rendering.POINT_SIZE, previewColor, true);
        }
    }
    
    @Override
    public Shape getFinalShape() {
        // 修复：只使用用户点击确定的points，不包含鼠标悬停位置
        if (points.size() >= 2) {
            // 为安全起见，传入一个副本给构造函数
            PolylineShape polyline = new PolylineShape(new ArrayList<>(points), false);
            ShapeStyle style = (ShapeStyle) styleHandler.getFinalStyle();
            if (style != null) {
                polyline.setStyle(style);
            }
            LOGGER.debug("折线模式创建图形: 点数={}", points.size());
            return polyline;
        }
        return null;
    }
    
    @Override
    public void reset() {
        points.clear();
        super.reset(); // 调用父类的 reset 方法
    }
    
    @Override
    public String getStatusMessage() {
        if (!isDrawing) {
            return "点击开始绘制折线";
        } else if (points.size() == 1) {
            return "点击添加下一个顶点，右键或Enter键完成绘制，Esc键取消";
        } else {
            return String.format("已添加 %d 个顶点，右键或Enter键完成绘制，Esc键取消", points.size());
        }
    }
    
    @Override
    public IInteractionStrategy.InteractionResult onKeyDown(int key, DrawingToolContext context) {
        if (key == 27) { // ESC键：总是取消绘制，遵循通用UI惯例
            LOGGER.debug("折线模式ESC键取消绘制");
            reset();
            context.resetDrawing("ESC键取消");
            return IInteractionStrategy.InteractionResult.CANCEL;
        } else if (key == 13 && isDrawing && points.size() >= 2) { // Enter键：完成绘制
            LOGGER.debug("折线模式Enter键完成绘制，当前点数：{}", points.size());
            return IInteractionStrategy.InteractionResult.COMPLETE;
        }
        return super.onKeyDown(key, context); // 处理其他键
    }

    /**
     * 获取当前点列表的只读副本
     */
    public List<Vec2d> getPoints() {
        return new ArrayList<>(points);
    }
    
    /**
     * 获取当前图层的颜色
     * 优化：移除不必要的try-catch，直接进行类型检查，正确处理透明度
     */
    private Color getLayerColor() {
        Object styleObj = styleHandler.getFinalStyle();
        if (styleObj instanceof ShapeStyle shapeStyle) {
            // 优先使用描边颜色
            int strokeColor = shapeStyle.getStrokeColor();
            if (strokeColor != 0) {
                return new Color(strokeColor, true); // true 表示包含 alpha 通道
            }
            // 其次使用填充颜色
            int fillColor = shapeStyle.getFillColor();
            if (fillColor != 0) {
                return new Color(fillColor, true); // true 表示包含 alpha 通道
            }
        }
        
        // 回退到默认颜色
        return Colors.LINE;
    }
}