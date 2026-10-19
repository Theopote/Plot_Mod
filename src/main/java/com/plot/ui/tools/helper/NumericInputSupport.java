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
}
