package com.plot.core.geometry.visitor;

import com.plot.api.render.ViewTransform;
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
import com.plot.core.geometry.shapes.TextShape;
import com.plot.core.model.Shape;
import imgui.ImDrawList;

/**
 * 渲染访问者接口。
 *
 * <p>使用访问者模式分离渲染逻辑；依赖具体 Shape 类型与 ImGui，
 * 因此位于 core，而非 api。</p>
 */
public interface IRenderVisitor {

    void render(LineShape shape, ImDrawList drawList, ViewTransform camera);

    void render(RectangleShape shape, ImDrawList drawList, ViewTransform camera);

    void render(CircleShape shape, ImDrawList drawList, ViewTransform camera);

    void render(EllipseShape shape, ImDrawList drawList, ViewTransform camera);

    void render(Polygon shape, ImDrawList drawList, ViewTransform camera);

    void render(ArcShape shape, ImDrawList drawList, ViewTransform camera);

    void render(EllipticalArcShape shape, ImDrawList drawList, ViewTransform camera);

    void render(BezierCurveShape shape, ImDrawList drawList, ViewTransform camera);

    void render(PolylineShape shape, ImDrawList drawList, ViewTransform camera);

    void render(TextShape shape, ImDrawList drawList, ViewTransform camera);

    void render(SineCurveShape shape, ImDrawList drawList, ViewTransform camera);

    void render(FreeDrawPath shape, ImDrawList drawList, ViewTransform camera);

    void render(SpiralShape shape, ImDrawList drawList, ViewTransform camera);

    void render(CableShape shape, ImDrawList drawList, ViewTransform camera);

    void render(Shape shape, ImDrawList drawList, ViewTransform camera);
}
