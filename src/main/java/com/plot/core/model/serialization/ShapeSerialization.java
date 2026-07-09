package com.plot.core.model.serialization;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.AnnotationShape;
import com.plot.core.geometry.shapes.ArcShape;
import com.plot.core.geometry.shapes.BezierCurveShape;
import com.plot.core.geometry.shapes.CableShape;
import com.plot.core.geometry.shapes.CircleShape;
import com.plot.core.geometry.shapes.EllipseShape;
import com.plot.core.geometry.shapes.EllipticalArcShape;
import com.plot.core.geometry.shapes.FreeDrawPath;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.geometry.shapes.Polygon;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.core.geometry.shapes.RectangleShape;
import com.plot.core.geometry.shapes.SineCurveShape;
import com.plot.core.geometry.shapes.SpiralShape;
import com.plot.core.geometry.shapes.SpiralType;
import com.plot.core.geometry.shapes.TextShape;
import com.plot.core.model.Shape;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * 图形序列化/反序列化工具。
 * 项目存档通过各图形的 {@link Shape#serialize()} / {@link Shape#deserialize(String)} 完成往返。
 */
public final class ShapeSerialization {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShapeSerialization.class);
    private static final Vec2d ORIGIN = new Vec2d(0, 0);

    private static final Map<String, Function<String, Shape>> DESERIALIZERS = new HashMap<>();

    static {
        register("LineShape", data -> deserializeInto(new LineShape(ORIGIN, ORIGIN), data));
        register("CircleShape", data -> deserializeInto(new CircleShape(ORIGIN, 1), data));
        register("RectangleShape", data -> deserializeInto(new RectangleShape(ORIGIN, 1, 1, 0, 0), data));
        register("EllipseShape", data -> deserializeInto(new EllipseShape(ORIGIN, 1, 1, 0), data));
        register("ArcShape", data -> deserializeInto(new ArcShape(ORIGIN, 1, 0, Math.PI), data));
        register("EllipticalArcShape", data -> deserializeInto(new EllipticalArcShape(ORIGIN, 1, 1, 0, 0, Math.PI), data));
        register("PolylineShape", data -> deserializeInto(new PolylineShape(Collections.emptyList(), false), data));
        register("Polygon", data -> deserializeInto(new Polygon(Collections.emptyList()), data));
        register("FreeDrawPath", data -> deserializeInto(new FreeDrawPath(Collections.emptyList()), data));
        register("BezierCurveShape", data -> deserializeInto(
                new BezierCurveShape(Collections.emptyList(), Collections.emptyList(), false), data));
        register("SpiralShape", data -> deserializeInto(
                new SpiralShape(ORIGIN, 1, 1, 1, SpiralType.LINEAR), data));
        register("SineCurveShape", data -> deserializeInto(
                new SineCurveShape(ORIGIN, ORIGIN, 1, 1, 0), data));
        register("TextShape", data -> deserializeInto(new TextShape(ORIGIN, ""), data));
        register("CableShape", data -> deserializeInto(new CableShape(ORIGIN, ORIGIN, 0, 8), data));
        register("AnnotationShape", data -> deserializeAnnotation(data));
    }

    private ShapeSerialization() {
    }

    private static void register(String type, Function<String, Shape> deserializer) {
        DESERIALIZERS.put(type, deserializer);
    }

    public static String getTypeName(Shape shape) {
        return shape.getClass().getSimpleName();
    }

    public static Shape deserialize(String type, String data) {
        if (data == null || data.isBlank()) {
            return null;
        }

        String resolvedType = type;
        if (resolvedType == null || resolvedType.isBlank()) {
            resolvedType = inferTypeFromData(data);
        }
        if (resolvedType == null) {
            LOGGER.warn("无法识别图形类型，跳过反序列化: {}", abbreviate(data));
            return null;
        }

        Function<String, Shape> deserializer = DESERIALIZERS.get(resolvedType);
        if (deserializer == null) {
            LOGGER.warn("不支持的图形类型: {}", resolvedType);
            return null;
        }

        try {
            return deserializer.apply(data);
        } catch (Exception e) {
            LOGGER.warn("反序列化图形失败 [{}]: {}", resolvedType, e.getMessage());
            return null;
        }
    }

    private static Shape deserializeInto(Shape shape, String data) {
        shape.deserialize(data);
        return shape;
    }

    private static Shape deserializeAnnotation(String data) {
        try {
            return deserializeInto(
                    AnnotationShape.createDistanceAnnotation(ORIGIN, ORIGIN, ""),
                    data);
        } catch (UnsupportedOperationException ex) {
            LOGGER.warn("标注图形暂不支持反序列化，已跳过");
            return null;
        }
    }

    static String inferTypeFromData(String data) {
        String trimmed = data.trim();
        if (trimmed.startsWith("{")) {
            if (trimmed.contains("\"type\":\"CableShape\"")) {
                return "CableShape";
            }
            if (trimmed.contains("\"type\":\"FreeDrawPath\"")) {
                return "FreeDrawPath";
            }
            if (trimmed.contains("\"type\":\"Polygon\"")) {
                return "Polygon";
            }
            if (trimmed.contains("\"startX\"") && trimmed.contains("\"wavelength\"")) {
                return "SineCurveShape";
            }
            if (trimmed.contains("\"positionX\"") || trimmed.contains("\"fontFamily\"")) {
                return "TextShape";
            }
            if (trimmed.contains("\"center\"") && trimmed.contains("\"turns\"")) {
                return "SpiralShape";
            }
            if (trimmed.contains("\"type\":\"DISTANCE\"") || trimmed.contains("\"type\":\"ANGLE\"")) {
                return "AnnotationShape";
            }
            return null;
        }

        if (trimmed.startsWith("LINE ")) {
            return "LineShape";
        }
        if (trimmed.startsWith("CIRCLE ")) {
            return "CircleShape";
        }
        if (trimmed.startsWith("RECTANGLE ")) {
            return "RectangleShape";
        }
        if (trimmed.startsWith("ELLIPSE ")) {
            return "EllipseShape";
        }
        if (trimmed.startsWith("ARC ")) {
            return "ArcShape";
        }
        if (trimmed.startsWith("EllipticalArc:")) {
            return "EllipticalArcShape";
        }
        if (trimmed.startsWith("POLYLINE ") || trimmed.startsWith("POLYGON ")) {
            return "PolylineShape";
        }
        if (trimmed.startsWith("BEZIER")) {
            return "BezierCurveShape";
        }
        if (trimmed.startsWith("spiral:")) {
            return "SpiralShape";
        }
        return null;
    }

    private static String abbreviate(String data) {
        if (data.length() <= 80) {
            return data;
        }
        return data.substring(0, 80) + "...";
    }
}
