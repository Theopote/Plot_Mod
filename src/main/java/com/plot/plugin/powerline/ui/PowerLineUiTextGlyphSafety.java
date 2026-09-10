package com.plot.plugin.powerline.ui;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PowerLine 玩家可见文本的字形安全校验。
 * <p>
 * 图标应通过 {@link PowerLineStatusIcon} 等 ImDrawList 绘制，而非依赖字体 glyph。
 * 常规标点（如 {@value #INLINE_SEPARATOR}）仍允许出现在文案中。
 */
public final class PowerLineUiTextGlyphSafety {
    /** Build 摘要等行内分隔符（中点，字体支持度通常良好）。 */
    public static final String INLINE_SEPARATOR = " \u00B7 ";

    private static final Pattern JAVA_STRING_LITERAL = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");

    private PowerLineUiTextGlyphSafety() {
    }

    public static boolean isForbiddenPlayerTextChar(char ch) {
        if (Character.isSurrogate(ch)) {
            return true;
        }
        int codePoint = ch;
        if (codePoint >= 0x2600 && codePoint <= 0x26FF) {
            return true;
        }
        if (codePoint >= 0x2700 && codePoint <= 0x27BF) {
            return true;
        }
        if (codePoint >= 0x1F300 && codePoint <= 0x1FAFF) {
            return true;
        }
        return false;
    }

    public static boolean isForbiddenPlayerText(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isHighSurrogate(ch) && i + 1 < text.length()) {
                int codePoint = Character.toCodePoint(ch, text.charAt(i + 1));
                if (codePoint >= 0x1F300 && codePoint <= 0x1FAFF) {
                    return true;
                }
                i++;
                continue;
            }
            if (isForbiddenPlayerTextChar(ch)) {
                return true;
            }
        }
        return false;
    }

    public static List<String> forbiddenGlyphs(String text) {
        Set<String> glyphs = new LinkedHashSet<>();
        if (text == null) {
            return List.of();
        }
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isHighSurrogate(ch) && i + 1 < text.length()) {
                int codePoint = Character.toCodePoint(ch, text.charAt(i + 1));
                if (codePoint >= 0x1F300 && codePoint <= 0x1FAFF) {
                    glyphs.add(new String(Character.toChars(codePoint)));
                }
                i++;
                continue;
            }
            if (isForbiddenPlayerTextChar(ch)) {
                glyphs.add(String.valueOf(ch));
            }
        }
        return new ArrayList<>(glyphs);
    }

    public static List<String> extractJavaStringLiterals(String source) {
        List<String> literals = new ArrayList<>();
        if (source == null || source.isEmpty()) {
            return literals;
        }
        Matcher matcher = JAVA_STRING_LITERAL.matcher(source);
        while (matcher.find()) {
            literals.add(unescapeJavaString(matcher.group(1)));
        }
        return literals;
    }

    public static boolean isIgnorableJavaSourceLine(String line) {
        if (line == null) {
            return true;
        }
        String trimmed = line.trim();
        return trimmed.isEmpty()
            || trimmed.startsWith("//")
            || trimmed.startsWith("*")
            || trimmed.startsWith("/*")
            || trimmed.startsWith("@")
            || line.contains("LOGGER.");
    }

    private static String unescapeJavaString(String literal) {
        StringBuilder out = new StringBuilder(literal.length());
        for (int i = 0; i < literal.length(); i++) {
            char ch = literal.charAt(i);
            if (ch == '\\' && i + 1 < literal.length()) {
                char next = literal.charAt(i + 1);
                switch (next) {
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case '\\', '"', '\'' -> out.append(next);
                    case 'u' -> {
                        if (i + 5 < literal.length()) {
                            int code = Integer.parseInt(literal.substring(i + 2, i + 6), 16);
                            out.append((char) code);
                            i += 5;
                        }
                    }
                    default -> out.append(next);
                }
                i++;
                continue;
            }
            out.append(ch);
        }
        return out.toString();
    }
}
