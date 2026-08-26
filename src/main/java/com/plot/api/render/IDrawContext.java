package com.plot.api.render;

import com.plot.api.geometry.Vec2d;

import java.awt.Color;

/**
 * API 层绘图上下文抽象，避免工具/渲染接口直接依赖 core 的 DrawContext 实现。
 */
public interface IDrawContext {
    ViewTransform getCamera();

    void setLineWidth(float width);

    float getLineWidth();

    void setOpacity(float opacity);

    float getOpacity();

    void drawLine(Vec2d start, Vec2d end);

    void drawLine(Vec2d start, Vec2d end, Color color);

    void drawDashedLine(Vec2d start, Vec2d end, Color color);

    void drawRect(Vec2d topLeft, Vec2d bottomRight, Color color);

    void fillRect(Vec2d topLeft, Vec2d bottomRight, Color color);

    void drawCircle(Vec2d center, float radius);

    void drawCircle(Vec2d center, float radius, Color color);

    void drawCircle(Vec2d center, double radius, Color color);

    void drawCircleFilled(Vec2d center, float radius, Color color);

    void fillCircle(Vec2d center, float radius, Color color);

    void fillCircle(Vec2d center, double radius, Color color);

    void drawArc(Vec2d center, double radius, double startAngle, double endAngle, Color color);

    void drawText(String text, Vec2d position, Color color);

    Vec2d worldToScreen(Vec2d worldPoint);
}
