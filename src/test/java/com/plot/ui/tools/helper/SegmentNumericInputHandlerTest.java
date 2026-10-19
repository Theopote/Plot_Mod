package com.plot.ui.tools.helper;

import com.plot.ui.tools.impl.drawing.strategy.IInteractionStrategy.InteractionResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SegmentNumericInputHandlerTest {

    @Test
    void enterForwardsConfirmResult() {
        SegmentNumericInputHandler handler = new SegmentNumericInputHandler();
        handler.buffer().append('5');
        InteractionResult result = handler.onKeyDown(
            NumericInputKeys.ENTER_KEY_GLFW,
            true,
            null,
            () -> InteractionResult.COMPLETE
        );
        assertEquals(InteractionResult.COMPLETE, result);
    }
}
