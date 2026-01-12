package com.masterplanner.core.shortcut;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 键盘事件到快捷键字符串的转换器
 * 负责将原始的键盘事件（keyCode + modifiers）转换为快捷键字符串格式
 */
public class KeyboardShortcutConverter {
    private static final Logger LOGGER = LoggerFactory.getLogger(KeyboardShortcutConverter.class);
    
    // GLFW 修饰键常量
    private static final int GLFW_MOD_SHIFT = 0x0001;
    private static final int GLFW_MOD_CONTROL = 0x0002;
    private static final int GLFW_MOD_ALT = 0x0004;
    private static final int GLFW_MOD_SUPER = 0x0008;
    
    /**
     * 将键盘事件转换为快捷键字符串
     * @param keyCode 按键代码
     * @param modifiers 修饰键
     * @return 快捷键字符串，如 "ctrl+z", "escape", "delete" 等
     */
    public static String convertToShortcutString(int keyCode, int modifiers) {
        StringBuilder shortcut = new StringBuilder();
        
        // 添加修饰键
        boolean hasModifiers = false;
        
        if ((modifiers & GLFW_MOD_CONTROL) != 0) {
            shortcut.append("ctrl");
            hasModifiers = true;
        }
        
        if ((modifiers & GLFW_MOD_SHIFT) != 0) {
            if (hasModifiers) shortcut.append("+");
            shortcut.append("shift");
            hasModifiers = true;
        }
        
        if ((modifiers & GLFW_MOD_ALT) != 0) {
            if (hasModifiers) shortcut.append("+");
            shortcut.append("alt");
            hasModifiers = true;
        }
        
        if ((modifiers & GLFW_MOD_SUPER) != 0) {
            if (hasModifiers) shortcut.append("+");
            shortcut.append("super");
            hasModifiers = true;
        }
        
        // 添加主要按键
        String keyName = getKeyName(keyCode);
        if (keyName != null) {
            if (hasModifiers) shortcut.append("+");
            shortcut.append(keyName);
            
            String result = shortcut.toString().toLowerCase();
            LOGGER.debug("转换键盘事件: keyCode={}, modifiers={} -> '{}'", keyCode, modifiers, result);
            return result;
        }
        
        LOGGER.debug("无法转换键盘事件: keyCode={}, modifiers={}", keyCode, modifiers);
        return null;
    }
    
    /**
     * 根据 keyCode 获取按键名称
     * @param keyCode 按键代码
     * @return 按键名称，如果无法识别则返回 null
     */
    private static String getKeyName(int keyCode) {
        // 字母键 A-Z (65-90)
        if (keyCode >= 65 && keyCode <= 90) {
            return String.valueOf((char) keyCode).toLowerCase();
        }
        
        // 数字键 0-9 (48-57)
        if (keyCode >= 48 && keyCode <= 57) {
            return String.valueOf((char) keyCode);
        }
        
        // 特殊键
        return switch (keyCode) {
            // 功能键
            case 256 -> "escape";
            case 257 -> "enter";
            case 258 -> "tab";
            case 259 -> "backspace";
            case 260 -> "insert";
            case 261 -> "delete";
            case 262 -> "right";
            case 263 -> "left";
            case 264 -> "down";
            case 265 -> "up";
            case 266 -> "page_up";
            case 267 -> "page_down";
            case 268 -> "home";
            case 269 -> "end";
            case 280 -> "caps_lock";
            case 281 -> "scroll_lock";
            case 282 -> "num_lock";
            case 283 -> "print_screen";
            case 284 -> "pause";

            // F1-F12
            case 290 -> "f1";
            case 291 -> "f2";
            case 292 -> "f3";
            case 293 -> "f4";
            case 294 -> "f5";
            case 295 -> "f6";
            case 296 -> "f7";
            case 297 -> "f8";
            case 298 -> "f9";
            case 299 -> "f10";
            case 300 -> "f11";
            case 301 -> "f12";

            // 小键盘
            case 320 -> "kp_0";
            case 321 -> "kp_1";
            case 322 -> "kp_2";
            case 323 -> "kp_3";
            case 324 -> "kp_4";
            case 325 -> "kp_5";
            case 326 -> "kp_6";
            case 327 -> "kp_7";
            case 328 -> "kp_8";
            case 329 -> "kp_9";
            case 330 -> "kp_decimal";
            case 331 -> "kp_divide";
            case 332 -> "kp_multiply";
            case 333 -> "kp_subtract";
            case 334 -> "kp_add";
            case 335 -> "kp_enter";
            case 336 -> "kp_equal";

            // 修饰键 (这些通常不单独作为快捷键)
            case 340 -> "left_shift";
            case 341 -> "left_control";
            case 342 -> "left_alt";
            case 343 -> "left_super";
            case 344 -> "right_shift";
            case 345 -> "right_control";
            case 346 -> "right_alt";
            case 347 -> "right_super";

            // 标点符号和特殊字符
            case 32 -> "space";
            case 39 -> "apostrophe";
            case 44 -> "comma";
            case 45 -> "minus";
            case 46 -> "period";
            case 47 -> "slash";
            case 59 -> "semicolon";
            case 61 -> "equal";
            case 91 -> "left_bracket";
            case 92 -> "backslash";
            case 93 -> "right_bracket";
            case 96 -> "grave_accent";
            default -> null;
        };
    }
    
    /**
     * 检查是否是有效的快捷键组合
     * @param keyCode 按键代码
     * @param modifiers 修饰键
     * @return 是否是有效的快捷键组合
     */
    public static boolean isValidShortcut(int keyCode, int modifiers) {
        String keyName = getKeyName(keyCode);
        
        // 必须有有效的按键名称
        if (keyName == null) {
            return false;
        }
        
        // 修饰键本身不能作为快捷键
        if (keyCode >= 340 && keyCode <= 347) {
            return false;
        }
        
        // 一些特殊键可以单独作为快捷键（不需要修饰键）
        return switch (keyCode) { // Escape
            // Delete
            // Backspace
            // Enter
            // Tab
            case 256, 261, 259, 257, 258, 262, 263, 264, 265 -> // 箭头键
                    true;
            default ->

                // 其他键通常需要修饰键
                    modifiers != 0;
        };

    }
}