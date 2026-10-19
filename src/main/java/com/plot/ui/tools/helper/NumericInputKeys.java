package com.plot.ui.tools.helper;

/**
 * 工具数值输入共用的键码（AWT 与 GLFW 兼容）。
 */
public final class NumericInputKeys {

    public static final int ESC_KEY_AWT = 27;
    public static final int ESC_KEY_GLFW = 256;
    public static final int ENTER_KEY_GLFW = 257;
    public static final int KP_ENTER_KEY_GLFW = 335;
    public static final int BACKSPACE_KEY_GLFW = 259;

    private NumericInputKeys() {
    }

    public static boolean isEnter(int keyCode) {
        return keyCode == ENTER_KEY_GLFW || keyCode == KP_ENTER_KEY_GLFW;
    }

    public static boolean isEscape(int keyCode) {
        return keyCode == ESC_KEY_AWT || keyCode == ESC_KEY_GLFW;
    }

    public static boolean isBackspace(int keyCode) {
        return keyCode == BACKSPACE_KEY_GLFW;
    }
}
