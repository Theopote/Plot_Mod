package com.plot.ui.tools.helper;

import com.plot.api.geometry.Vec2d;
import com.plot.ui.tools.impl.drawing.strategy.IInteractionStrategy.InteractionResult;

import java.util.function.Supplier;

/**
 * 支持单距离或「宽,高」双数值的键盘输入处理。
 */
public final class PairNumericInputHandler {

    private final PairNumericInputBuffer buffer = new PairNumericInputBuffer();

    public PairNumericInputBuffer buffer() {
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
        if (NumericInputKeys.isEscape(keyCode) && !buffer.isEmpty()) {
            buffer.clear();
            if (refreshPreview != null) {
                refreshPreview.run();
            }
            return InteractionResult.CONTINUE;
        }
        if (NumericInputKeys.isBackspace(keyCode) && buffer.backspace()) {
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
        if (!active || !buffer.append(character)) {
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

    public Double parseSinglePositive() {
        return buffer.tryParseSingle();
    }

    public double[] parsePairPositive() {
        return buffer.tryParsePair();
    }

    public boolean hasCompleteInput() {
        return buffer.tryParsePair() != null || buffer.tryParseSingle() != null;
    }

    public boolean hasSeparator() {
        return buffer.hasSeparator();
    }
}
