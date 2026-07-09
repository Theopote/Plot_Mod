package com.plot.ui.tools.impl.modify.strategy;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.commands.CopyOffsetCommand;
import com.plot.core.command.commands.ModifyCommand;
import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.ui.tools.impl.modify.helper.OffsetHandler;
import com.plot.ui.tools.impl.modify.dto.ModifyParameters;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.core.geometry.shapes.ArcShape;
import com.plot.core.geometry.shapes.CircleShape;
import com.plot.core.geometry.shapes.EllipseShape;
import com.plot.core.geometry.shapes.FreeDrawPath;
import com.plot.core.geometry.shapes.CableShape;
import com.plot.core.geometry.shapes.SineCurveShape;
import com.plot.core.geometry.shapes.SpiralShape;
import com.plot.core.geometry.shapes.RectangleShape;
import com.plot.core.geometry.shapes.Polygon;
import com.plot.utils.ExceptionDebug;
// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;

import java.util.List;
import com.plot.utils.PlotI18n;

/**
 * 简化版偏移策略：
 * - 仅支持线类图形(LineShape)
 * - 工作流：点击图形并记录参考点 → 点击目标点 → 复制偏移图形
 * - 保留原图形，仅添加新线
 */
public class SimpleOffsetStrategy implements IModifyStrategy {
    // private static final Logger LOGGER = LoggerFactory.getLogger(SimpleOffsetStrategy.class);

    private enum State { IDLE, FIRST_POINT_SET }

    private State state = State.IDLE;
    private Shape targetShape;
    private Vec2d firstPoint;
    private ModifyCommand pendingCommand;
    private static final double HIT_TOLERANCE = 10.0;
    private static final double OUTLINE_PICK_TOLERANCE = 8.0;
    private boolean multipleMode = false;

    @Override
    public ModifyResult onMouseDown(Vec2d pos, int button, ModifyToolContext context) {
        if (button != 0) return ModifyResult.IGNORED; // 左键

        Vec2d snapped = context.getSnapHandler().getSnappedWorldPoint(pos, context.getCamera());

        if (state == State.IDLE) {
            // 命中线类图形（支持容差）；若未直接命中则在活动图层中寻找最近的线类图形
            Shape shape = context.findShapeAt(snapped, HIT_TOLERANCE);
            if (!isLineLike(shape)) {
                shape = findNearestLineLikeShape(snapped, context);
            }
            // 对闭合轮廓（矩形/圆/多边形等）：允许点击在内部或靠近轮廓；若两者都不满足则不选中
            if (isClosedShape(shape)) {
                boolean nearOutline = isNearOutline(shape, snapped);
                boolean inside = isInsideClosedShapeIgnoringStyle(shape, snapped);
                if (!nearOutline && !inside) {
                    context.setStatusMessage("status.plot.offset.click_inside");
                    return ModifyResult.CONTINUE;
                }
            }
            if (!isLineLike(shape)) {
                context.setStatusMessage("status.plot.offset.no_linear");
                return ModifyResult.CONTINUE;
            }
            targetShape = shape;

            // 自动选中被点击的图形，并应用与选择工具一致的视觉反馈
            try {
                context.clearSelection();
                targetShape.setHighlighted(false);
                targetShape.setSelected(true);
                context.addSelectedShape(targetShape);
            } catch (Exception e) {
                ExceptionDebug.log("SimpleOffsetStrategy: select clicked shape", e);
            }
            // 记录第一参考点
            firstPoint = snapped;
            state = State.FIRST_POINT_SET;
            context.setStatusMessage("status.plot.offset.reference_set");
            return ModifyResult.CONTINUE;
        }

        if (state == State.FIRST_POINT_SET && targetShape != null) {
            // CAD式偏移：计算到原图形的符号距离作为偏移距离，按法线整体偏移
            if (firstPoint == null) {
                reset();
                return ModifyResult.CANCEL;
            }

            // 计算符号距离（优先基于切线和最近点，保证方向正确；特殊形状如椭圆使用精确欧氏距离并带符号）
            double signedDistance = computeSignedOffsetDistance(targetShape, snapped);

            // 用 OffsetHandler 计算偏移后的图形（保持原始图形，添加副本）
            AppState appState = (AppState) context.getAppState();
            OffsetHandler handler = new OffsetHandler(appState);
            ModifyParameters params = new ModifyParameters();
            params.setDouble("offsetDistance", signedDistance);

            List<Shape> originals = List.of(targetShape);
            List<Shape> modified = handler.calculateModifiedShapes(originals, params);
            if (modified == null || modified.isEmpty() || containsOriginalReference(originals, modified)) {
                context.setStatusMessage("status.plot.offset.generate_failed");
                reset();
                return ModifyResult.CANCEL;
            }

            pendingCommand = new CopyOffsetCommand(originals, modified, appState);
            context.executeModifyCommand(pendingCommand);

            // 保持新生成的偏移副本被选中，以便后续连续操作；原图形取消选中
            try {
                context.clearSelection();
                for (Shape s : modified) {
                    s.setSelected(true);
                    s.setHighlighted(false);
                    context.addSelectedShape(s);
                }
            } catch (Exception e) {
                ExceptionDebug.log("SimpleOffsetStrategy: select offset result shapes", e);
            }
            if (multipleMode) {
                reset();
                context.setStatusMessage(PlotI18n.status("status.plot.offset.complete_continue", Math.abs(signedDistance)));
                return ModifyResult.CONTINUE;
            }

            reset();
            return ModifyResult.COMPLETE;
        }

        return ModifyResult.IGNORED;
    }

    @Override
    public ModifyResult onMouseMove(Vec2d pos, ModifyToolContext context) {
        // 简化版不渲染预览
        return ModifyResult.CONTINUE;
    }

    @Override
    public ModifyResult onMouseUp(Vec2d pos, int button, ModifyToolContext context) {
        return ModifyResult.IGNORED;
    }

    @Override
    public ModifyCommand getModifyCommand() {
        return pendingCommand;
    }

    @Override
    public void reset() {
        state = State.IDLE;
        targetShape = null;
        pendingCommand = null;
    }

    @Override
    public String getStrategyName() {
        return PlotI18n.modeLabel("strategy.plot.name.simple_offset");
    }

    @Override
    public String getStrategyDescription() {
        return PlotI18n.modeLabel("strategy.plot.desc.simple_offset");
    }

    @Override
    public boolean requiresSelection() {
        return false;
    }

    @Override
    public int getMinimumSelectionCount() { return 0; }

    @Override
    public int getMaximumSelectionCount() { return 1; }

    private boolean isLineLike(Shape shape) {
        return shape instanceof LineShape
            || shape instanceof PolylineShape
            || shape instanceof ArcShape
            || shape instanceof CircleShape
            || shape instanceof EllipseShape
            || shape instanceof FreeDrawPath
                || shape instanceof CableShape
            || shape instanceof SineCurveShape
            || shape instanceof SpiralShape
            || shape instanceof RectangleShape
            || shape instanceof Polygon;
    }

    private double computeSignedOffsetDistance(Shape shape, Vec2d clickPoint) {
        try {
            // 椭圆：使用边界距离+内外判定，避免半轴不等导致的切线法线误判
            if (shape instanceof EllipseShape ellipse) {
                double dist = ellipse.getDistanceToPoint(clickPoint);
                boolean inside = ellipse.isPointInside(clickPoint);
                return (inside ? -1.0 : 1.0) * dist;
            }

            // 封闭轮廓：直接使用有符号距离，避免重复计算内外判定
            if (isClosedShape(shape)) {
                return safeSignedDistanceTo(shape, clickPoint);
            }

            // 通用：最近点 + 切线 -> 法线方向决定正负，距离为欧氏距离
            Vec2d closest = shape.getClosestPoint(clickPoint);
            Vec2d tangent = shape.getTangentAt(closest);
            Vec2d v = clickPoint.subtract(closest);
            double distance = v.length();
            if (distance < 1e-8) {
                // 退化：回退到形状提供的signedDistance
                return shape.getSignedDistance(clickPoint);
            }
            if (tangent == null || Math.abs(tangent.x) + Math.abs(tangent.y) < 1e-8) {
                return shape.getSignedDistance(clickPoint);
            }
            // 2D 叉积 z 分量决定侧向
            double crossZ = tangent.x * v.y - tangent.y * v.x;
            double sign = Math.signum(crossZ);
            if (sign == 0) sign = 1.0; // 与切线共线时默认外侧
            return sign * distance;
        } catch (Exception e) {
            ExceptionDebug.log("SimpleOffsetStrategy: compute signed offset distance", e);
            // 最后回退：使用原有的signedDistance（部分形状可能返回无符号）
            return shape.getSignedDistance(clickPoint);
        }
    }

    private boolean isNearOutline(Shape shape, Vec2d point) {
        try {
            if (shape instanceof RectangleShape rect) {
                // 使用矩形的点序列构造边
                List<Vec2d> pts = rect.getPoints();
                for (int i = 0; i < pts.size() - 1; i++) {
                    LineShape edge = new LineShape(pts.get(i), pts.get(i + 1));
                    if (edge.containsPoint(point, SimpleOffsetStrategy.OUTLINE_PICK_TOLERANCE)) return true;
                }
                return false;
            }
            if (shape instanceof CircleShape circle) {
                double d = Math.abs(point.distance(circle.getCenter()) - circle.getRadius());
                return d <= SimpleOffsetStrategy.OUTLINE_PICK_TOLERANCE;
            }
            if (shape instanceof PolylineShape pl) {
                return pl.containsPoint(point, SimpleOffsetStrategy.OUTLINE_PICK_TOLERANCE);
            }
            if (shape instanceof Polygon pg) {
                // 近似：将多边形转折线检测
                PolylineShape edgePoly = new PolylineShape(pg.getPoints(), true);
                return edgePoly.containsPoint(point, SimpleOffsetStrategy.OUTLINE_PICK_TOLERANCE);
            }
            if (shape instanceof ArcShape arc) {
                // 点到弧的采样线段距离
                List<Vec2d> pts = arc.getPoints();
                for (int i = 0; i < pts.size() - 1; i++) {
                    LineShape seg = new LineShape(pts.get(i), pts.get(i + 1));
                    if (seg.containsPoint(point, SimpleOffsetStrategy.OUTLINE_PICK_TOLERANCE)) return true;
                }
                return false;
            }
            return shape.containsPoint(point, SimpleOffsetStrategy.OUTLINE_PICK_TOLERANCE);
        } catch (Exception e) {
            ExceptionDebug.log("SimpleOffsetStrategy: check near outline", e);
            return false;
        }
    }

    private boolean isInsideClosedShapeIgnoringStyle(Shape shape, Vec2d point) {
        try {
            if (shape instanceof RectangleShape rect) {
                // 使用矩形当前世界坐标的顶点进行点在多边形内测试，避免忽略 transform 带来的误差
                List<Vec2d> pts = rect.getPoints();
                if (pts.size() >= 4) {
                    // getPoints() 是闭合的，去掉最后一个重复点
                    if (pts.getFirst().equals(pts.getLast())) {
                        pts = new java.util.ArrayList<>(pts.subList(0, pts.size() - 1));
                    }
                    return pointInPolygon(pts, point);
                }
                return false;
            }
            if (shape instanceof CircleShape circle) {
                return point.distance(circle.getCenter()) <= circle.getRadius();
            }
            if (shape instanceof EllipseShape ellipse) {
                return ellipse.isPointInside(point);
            }
            if (shape instanceof Polygon pg) {
                // 射线法：复用 Polygon.contains 但不依赖样式
                return pg.contains(point);
            }
            if (shape instanceof PolylineShape pl) {
                return pl.isClosed() && new Polygon(pl.getPoints(), true).contains(point);
            }
            return shape.contains(point);
        } catch (Exception e) {
            ExceptionDebug.log("SimpleOffsetStrategy: check inside closed shape", e);
            return false;
        }
    }

    // 射线法判断点是否在多边形内（顶点为世界坐标）
    private boolean pointInPolygon(List<Vec2d> vertices, Vec2d p) {
        boolean inside = false;
        int n = vertices.size();
        if (n < 3) return false;
        int j = n - 1;
        for (int i = 0; i < n; i++) {
            Vec2d vi = vertices.get(i);
            Vec2d vj = vertices.get(j);
            boolean intersect = ((vi.y > p.y) != (vj.y > p.y)) &&
                    (p.x < (vj.x - vi.x) * (p.y - vi.y) / (vj.y - vi.y + 1e-20) + vi.x);
            if (intersect) inside = !inside;
            j = i;
        }
        return inside;
    }

    private boolean isClosedShape(Shape shape) {
        if (shape instanceof CircleShape) return true;
        if (shape instanceof EllipseShape) return true;
        if (shape instanceof RectangleShape) return true;
        if (shape instanceof Polygon) return true;
        if (shape instanceof PolylineShape poly) {
            try {
                return poly.isClosed();
            } catch (Exception e) {
                ExceptionDebug.log("SimpleOffsetStrategy: check polyline closed", e);
                return false;
            }
        }
        return false;
    }

    private double safeDistanceTo(Shape shape, Vec2d point) {
        try {
            return shape.getDistanceToPoint(point);
        } catch (Exception e) {
            try {
                double d = shape.getSignedDistance(point);
                return Math.abs(d);
            } catch (Exception ex) {
                return point.distance(shape.getClosestPoint(point));
            }
        }
    }
    
    /**
     * 获取有符号距离，用于偏移计算
     */
    private double safeSignedDistanceTo(Shape shape, Vec2d point) {
        try {
            return shape.getSignedDistance(point);
        } catch (Exception e) {
            try {
                return shape.getDistanceToPoint(point);
            } catch (Exception ex) {
                return point.distance(shape.getClosestPoint(point));
            }
        }
    }

    private Shape findNearestLineLikeShape(Vec2d point, ModifyToolContext context) {
        try {
            List<Shape> shapes = context.getAllShapesInActiveLayer();
            Shape nearest = null;
            double best = Double.POSITIVE_INFINITY;
            for (Shape s : shapes) {
                if (!isLineLike(s)) continue;
                double d = safeDistanceTo(s, point);
                if (d < best) {
                    best = d;
                    nearest = s;
                }
            }
            if (best <= SimpleOffsetStrategy.HIT_TOLERANCE) return nearest;
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    public void setMultipleMode(boolean multipleMode) {
        this.multipleMode = multipleMode;
    }

    private boolean containsOriginalReference(List<Shape> originals, List<Shape> modified) {
        if (originals == null || modified == null) return false;
        for (Shape m : modified) {
            for (Shape o : originals) {
                if (m == o) {
                    return true;
                }
            }
        }
        return false;
    }
}