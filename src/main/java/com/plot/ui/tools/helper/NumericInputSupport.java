package com.plot.ui.tools.helper;

import com.plot.api.geometry.Vec2d;
import com.plot.ui.tools.impl.drawing.helper.DrawMeasureHelper;

/**
 * 距离/数值键盘输入的共用逻辑，供绘制与编辑策略复用。
 */
public final class NumericInputSupport {

    private NumericInputSupport() {
    }

    public static Vec2d pointAtBlockDistance(Vec2d anchor, Vec2d toward, double blocks) {
        return DrawMeasureHelper.pointAtBlockDistance(anchor, toward, blocks);
    }

    public static boolean clearOnEscape(NumericInputBuffer buffer) {
        if (buffer.isEmpty()) {
            return false;
        }
        buffer.clear();
        return true;
    }

    public static boolean applyBackspace(NumericInputBuffer buffer) {
        return buffer.backspace();
    }

    public static boolean applyTyped(NumericInputBuffer buffer, char character) {
        return buffer.append(character);
    }

    public static boolean hasInput(NumericInputBuffer buffer) {
        return !buffer.isEmpty();
    }

    public static Double parsePositive(NumericInputBuffer buffer) {
        Double value = buffer.tryParse();
        if (value == null || value <= 0.0) {
            return null;
        }
        return value;
    }

    /**
     * 由第一角点、鼠标象限与宽/高（方块）计算对角点。
     */
    public static Vec2d cornerFromBlockWidthHeight(Vec2d anchor, Vec2d towardMouse,
                                                   double blockWidth, double blockHeight) {
        if (anchor == null || blockWidth <= 0.0 || blockHeight <= 0.0) {
            return towardMouse;
        }
        double signX = towardMouse != null && towardMouse.x < anchor.x ? -1.0 : 1.0;
        double signY = towardMouse != null && towardMouse.y < anchor.y ? -1.0 : 1.0;
        Vec2d xToward = new Vec2d(anchor.x + signX * 1000.0, anchor.y);
        Vec2d yToward = new Vec2d(anchor.x, anchor.y + signY * 1000.0);
        Vec2d xPoint = pointAtBlockDistance(anchor, xToward, blockWidth);
        Vec2d yPoint = pointAtBlockDistance(anchor, yToward, blockHeight);
        return new Vec2d(xPoint.x, yPoint.y);
    }

    /**
     * 沿轴参考方向的垂直线，距中心 {@code blocks} 处的点（符号由鼠标侧决定）。
     */
    public static Vec2d pointAtPerpendicularDistance(Vec2d center, Vec2d axisPoint,
                                                     Vec2d towardMouse, double blocks) {
        if (center == null || axisPoint == null || blocks <= 0.0) {
            return towardMouse;
        }
        Vec2d axisDelta = axisPoint.subtract(center);
        double axisLen = axisDelta.length();
        if (axisLen < 1e-9) {
            return pointAtBlockDistance(center, towardMouse, blocks);
        }
        Vec2d axisDir = axisDelta.multiply(1.0 / axisLen);
        Vec2d perpDir = new Vec2d(-axisDir.y, axisDir.x);
        double sign = 1.0;
        if (towardMouse != null) {
            Vec2d toMouse = towardMouse.subtract(center);
            if (toMouse.dot(perpDir) < 0.0) {
                sign = -1.0;
            }
        }
        return center.add(perpDir.multiply(sign * blocks));
    }
}
