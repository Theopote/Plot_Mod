package com.plot.ui.tools.helper;

import com.plot.core.tool.BaseTool;
import com.plot.ui.tools.impl.drawing.DrawingTool;

/**
 * 绘制/编辑工具数值输入时，决定是否将键盘事件从 ImGui 转交给工具。
 */
public final class ToolNumericKeyboardPolicy {

    private ToolNumericKeyboardPolicy() {
    }

    public static boolean isNumericCharacter(char character) {
        return Character.isDigit(character)
            || character == '.'
            || character == ','
            || character == ';'
            || character == '\uFF0C'
            || character == '+'
            || character == '-';
    }

    public static boolean isNumericControlKey(int keyCode) {
        return NumericInputKeys.isEnter(keyCode)
            || NumericInputKeys.isBackspace(keyCode)
            || NumericInputKeys.isEscape(keyCode);
    }

    public static boolean toolAcceptsNumericKeyboard(BaseTool tool) {
        return tool instanceof DrawingTool drawingTool && drawingTool.isActivelyDrawing();
    }

    public static boolean shouldBypassImGui(BaseTool tool, int keyCode, char character) {
        if (!toolAcceptsNumericKeyboard(tool)) {
            return false;
        }
        if (character != '\0' && isNumericCharacter(character)) {
            return true;
        }
        return isNumericControlKey(keyCode);
    }
}
