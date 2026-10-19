package com.plot.ui.tools.helper;

/**
 * 工具操作时的键盘数值输入缓冲（距离、角度、比例等）。
 */
public final class NumericInputBuffer {

    private final StringBuilder buffer = new StringBuilder();

    public void clear() {
        buffer.setLength(0);
    }

    public boolean isEmpty() {
        return buffer.isEmpty();
    }

    public String getText() {
        return buffer.toString();
    }

    public boolean append(char character) {
        if (Character.isDigit(character)) {
            buffer.append(character);
            return true;
        }
        if (character == '.' || character == ',') {
            String current = buffer.toString().replace(',', '.');
            if (current.contains(".")) {
                return false;
            }
            buffer.append('.');
            return true;
        }
        if (character == '-' || character == '+') {
            if (!buffer.isEmpty()) {
                return false;
            }
            buffer.append(character);
            return true;
        }
        return false;
    }

    public boolean backspace() {
        if (buffer.isEmpty()) {
            return false;
        }
        buffer.deleteCharAt(buffer.length() - 1);
        return true;
    }

    /**
     * @return 可解析的数值；不完整输入（如 "-" 或 "12."）返回 null
     */
    public Double tryParse() {
        if (buffer.isEmpty()) {
            return null;
        }
        String text = buffer.toString().trim().replace(',', '.');
        if (text.isEmpty() || "-".equals(text) || "+".equals(text) || ".".equals(text)
            || "-.".equals(text) || "+.".equals(text)) {
            return null;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
