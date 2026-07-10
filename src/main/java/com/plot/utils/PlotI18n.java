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

    /** User-facing error message (keys use {@code error.plot.*}). */
    public static String error(String key, Object... args) {
        return tr(key, args);
    }

    public static String localizeStatus(String message) {
        return localizeMessage(message);
    }

    /** Localize a user-facing message when it uses a known translation key prefix. */
    public static String localizeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        if (message.startsWith("status.plot.")
                || message.startsWith("error.plot.")
                || message.startsWith("dialog.plot.")
                || message.startsWith("layer.plot.")
                || message.startsWith("toolbar.plot.")) {
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

    /** Mode / enum display label (keys use {@code mode.plot.*}). */
    public static String modeLabel(String key) {
        return tr(key);
    }

    /** Mode description or status key (supports {@code mode.plot.*} and {@code status.plot.*}). */
    public static String modeOrStatus(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        if (key.startsWith("status.plot.")) {
            return localizeStatus(key);
        }
        return modeLabel(key);
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

    public static String defaultProjectName() {
        return tr("project.plot.unnamed");
    }

    public static String defaultCanvasName() {
        return tr("project.plot.default_canvas");
    }

    public static String defaultLayerName() {
        return tr("layer.plot.initial_name");
    }

    /** Resolve a stored layer name when it was saved as a translation key. */
    public static String layerDisplayName(String storedName) {
        if (storedName == null || storedName.isBlank()) {
            return "";
        }
        if (storedName.startsWith("layer.plot.")) {
            String translated = tr(storedName);
            if (!translated.equals(storedName)) {
                return translated;
            }
        }
        return storedName;
    }

    public static String fallbackLayerName() {
        return tr("layer.plot.fallback_name");
    }

    public static String layerPropertyLabel(String propertyName) {
        if (propertyName == null || propertyName.isBlank()) {
            return "";
        }
        String key = "layer.plot.property." + propertyName;
        String translated = tr(key);
        return translated.equals(key) ? propertyName : translated;
    }

    public static String layerContentChangeLabel(String changeType) {
        if (changeType == null || changeType.isBlank()) {
            return "";
        }
        String key = "layer.plot.event.content." + changeType;
        String translated = tr(key);
        return translated.equals(key) ? changeType : translated;
    }

    public static String unsupportedShapeOperation(String operation) {
        if (operation == null || operation.isBlank()) {
            return tr("error.plot.shape.unsupported.generic");
        }
        String key = "error.plot.shape.unsupported." + operation;
        String translated = tr(key);
        return translated.equals(key) ? tr("error.plot.shape.unsupported.generic") : translated;
    }
}
