package com.plot.utils;

import com.plot.core.graphics.style.LineStyle;
import net.minecraft.text.Text;

/**
 * Plot 模组国际化工具，基于 Minecraft 语言文件（assets/plot/lang）。
 */
public final class PlotI18n {

    private PlotI18n() {
    }

    public static String tr(String key, Object... args) {
        return Text.translatable(key, args).getString();
    }

    public static String localizeStatus(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        if (message.startsWith("status.plot.")) {
            return tr(message);
        }
        return message;
    }

    /** 状态栏 / 工具 Usage 专用翻译（键必须以 {@code status.plot.} 开头）。 */
    public static String status(String key, Object... args) {
        return tr(key, args);
    }

    public static String operationName(String operationKey) {
        if (operationKey == null || operationKey.isBlank()) {
            return "";
        }
        if (operationKey.startsWith("history.plot.op.")) {
            return tr(operationKey);
        }
        return operationKey;
    }

    public static String lineTypeLabel(LineStyle.LineType type) {
        if (type == null) {
            return "";
        }
        return tr("line.plot." + type.name().toLowerCase());
    }

    public static String shapeTypeLabel(String className) {
        if (className == null || className.isBlank()) {
            return tr("history.plot.shape.generic");
        }
        String key = "shape.plot." + shapeTypeKey(className);
        String translated = tr(key);
        return translated.equals(key) ? className : translated;
    }

    private static String shapeTypeKey(String className) {
        return switch (className) {
            case "LineShape" -> "line";
            case "CircleShape" -> "circle";
            case "ArcShape" -> "arc";
            case "RectangleShape" -> "rectangle";
            case "EllipseShape" -> "ellipse";
            case "EllipticalArcShape" -> "elliptical_arc";
            case "PolylineShape" -> "polyline";
            case "BezierCurveShape" -> "bezier";
            case "SineCurveShape" -> "sine";
            case "SpiralShape" -> "spiral";
            case "TextShape" -> "text";
            case "AnnotationShape" -> "annotation";
            case "CableShape" -> "cable";
            case "Polygon" -> "polygon";
            case "FreeDrawPath" -> "free_draw";
            default -> className.endsWith("Shape")
                    ? className.substring(0, className.length() - 5).toLowerCase()
                    : className.toLowerCase();
        };
    }

    public static String toolLabel(String toolId) {
        if (toolId == null || toolId.isBlank()) {
            return tr("tool.plot.unknown");
        }
        String normalized = toolId.toLowerCase().trim();
        if ("freedraw".equals(normalized)) {
            return tr("tool.plot.freehand");
        }
        String key = "tool.plot." + normalized;
        String translated = tr(key);
        return translated.equals(key) ? toolId : translated;
    }

    public static String toolDescription(String toolId) {
        return tr(toolDescriptionKey(toolId));
    }

    public static String toolUsageHint(String toolId) {
        String key = toolUsageHintKey(toolId);
        String translated = tr(key);
        return translated.equals(key) ? "" : translated;
    }

    private static String toolDescriptionKey(String toolId) {
        return "tool.plot." + normalizeToolId(toolId) + ".desc";
    }

    private static String toolUsageHintKey(String toolId) {
        return "tool.plot." + normalizeToolId(toolId) + ".hint";
    }

    private static String normalizeToolId(String toolId) {
        if (toolId == null || toolId.isBlank()) {
            return "unknown";
        }
        return toolId.toLowerCase().trim();
    }
}
