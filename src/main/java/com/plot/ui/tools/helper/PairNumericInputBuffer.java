package com.plot.ui.tools.helper;

/**
 * 宽×高类双数值输入缓冲。逗号/分号分隔两个正数，小数点仅用 {@code .}。
 * 无分隔符时行为与单值距离输入相同。
 */
public final class PairNumericInputBuffer {

    private final StringBuilder buffer = new StringBuilder();
    private int separatorIndex = -1;

    public void clear() {
        buffer.setLength(0);
        separatorIndex = -1;
    }

    public boolean isEmpty() {
        return buffer.isEmpty();
    }

    public String getText() {
        return buffer.toString();
    }

    public boolean hasSeparator() {
        return separatorIndex >= 0;
    }

    public boolean append(char character) {
        if (isPairSeparator(character)) {
            if (separatorIndex >= 0 || buffer.isEmpty()) {
                return false;
            }
            separatorIndex = buffer.length();
            buffer.append(',');
            return true;
        }
        if (Character.isDigit(character)) {
            buffer.append(character);
            return true;
        }
        if (character == '.') {
            String segment = currentSegmentText();
            if (segment.contains(".")) {
                return false;
            }
            buffer.append('.');
            return true;
        }
        if (character == '-' || character == '+') {
            if (!currentSegmentText().isEmpty()) {
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
        if (separatorIndex >= buffer.length()) {
            separatorIndex = -1;
        }
        return true;
    }

    public Double tryParseSingle() {
        if (separatorIndex >= 0) {
            return null;
        }
        return parsePositiveSegment(buffer.toString());
    }

    public double[] tryParsePair() {
        if (separatorIndex < 0) {
            return null;
        }
        Double first = parsePositiveSegment(buffer.substring(0, separatorIndex));
        Double second = parsePositiveSegment(buffer.substring(separatorIndex + 1));
        if (first == null || second == null) {
            return null;
        }
        return new double[] {first, second};
    }

    private String currentSegmentText() {
        int start = separatorIndex >= 0 ? separatorIndex + 1 : 0;
        return buffer.substring(start);
    }

    private static boolean isPairSeparator(char character) {
        return character == ',' || character == ';' || character == '\uFF0C';
    }

    private static Double parsePositiveSegment(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        String normalized = text.trim().replace(',', '.');
        if ("-".equals(normalized) || "+".equals(normalized) || ".".equals(normalized)
            || "-.".equals(normalized) || "+.".equals(normalized)) {
            return null;
        }
        try {
            double value = Double.parseDouble(normalized);
            return value > 0.0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
