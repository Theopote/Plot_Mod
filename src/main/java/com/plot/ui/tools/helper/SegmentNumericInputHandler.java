package com.plot.ui.tools.helper;

import com.plot.api.geometry.Vec2d;
import com.plot.ui.tools.impl.drawing.strategy.IInteractionStrategy.InteractionResult;

import java.util.function.Supplier;

/**
 * 绘制过程中「从锚点沿鼠标方向输入距离」的键盘处理。
 */
public final class SegmentNumericInputHandler {

    private final NumericInputBuffer buffer = new NumericInputBuffer();

    public NumericInputBuffer buffer() {
        return buffer;
    }

    public boolean hasInput() {
        return !buffer.isEmpty();
    }

    public String getText() {
        return buffer.getText();
    }

    public void clear() {
        buffer.clear();
    }

    public InteractionResult onKeyDown(int keyCode,
                                       boolean active,
                                       Runnable refreshPreview,
                                       Supplier<InteractionResult> onConfirm) {
        if (!active) {
            return InteractionResult.IGNORED;
        }
        if (NumericInputKeys.isEscape(keyCode) && NumericInputSupport.clearOnEscape(buffer)) {
            if (refreshPreview != null) {
                refreshPreview.run();
            }
            return InteractionResult.CONTINUE;
        }
        if (NumericInputKeys.isBackspace(keyCode) && NumericInputSupport.applyBackspace(buffer)) {
            if (refreshPreview != null) {
                refreshPreview.run();
            }
            return InteractionResult.CONTINUE;
        }
        if (NumericInputKeys.isEnter(keyCode) && hasInput()) {
            if (onConfirm != null) {
                InteractionResult result = onConfirm.get();
                return result != null ? result : InteractionResult.CONTINUE;
            }
            return InteractionResult.CONTINUE;
        }
        return InteractionResult.IGNORED;
    }

    public InteractionResult onKeyTyped(char character, boolean active, Runnable refreshPreview) {
        if (!active || !NumericInputSupport.applyTyped(buffer, character)) {
            return InteractionResult.IGNORED;
        }
        if (refreshPreview != null) {
            refreshPreview.run();
        }
        return InteractionResult.CONTINUE;
    }

    public Vec2d pointAtDistance(Vec2d anchor, Vec2d toward, double blocks) {
        return NumericInputSupport.pointAtBlockDistance(anchor, toward, blocks);
    }

    public Double parsePositive() {
        return NumericInputSupport.parsePositive(buffer);
    }
}
