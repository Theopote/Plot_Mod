package com.plot.api.render;

import com.plot.api.geometry.Vec2d;
import com.plot.core.graphics.DrawContext;
import java.awt.Color;

/**
 * 渲染器接口，定义基本的渲染操作
 */
public interface IRenderer {
    /**
     * 开始渲染
     * @param context 绘制上下文
     */
    void begin(DrawContext context);

    /**
     * 结束渲染
     */
    void end();

    /**
     * 设置线条颜色
     * @param color 颜色
     */
    void setColor(Color color);

    /**
     * 设置线条宽度
     * @param width 宽度
     */
    void setLineWidth(float width);

    /**
     * 设置填充颜色
     * @param color 颜色
     */
    void setFillColor(Color color);

    /**
     * 设置透明度
     * @param alpha 透明度 (0.0-1.0)
     */
    void setAlpha(float alpha);

    /**
     * 绘制线段
     * @param start 起点
     * @param end 终点
     */
    void drawLine(Vec2d start, Vec2d end);

    /**
     * 绘制矩形
     * @param position 位置
     * @param width 宽度
     * @param height 高度
     * @param filled 是否填充
     */
    void drawRect(Vec2d position, double width, double height, boolean filled);

    /**
     * 绘制圆形
     * @param center 中心点
     * @param radius 半径
     * @param filled 是否填充
     */
    void drawCircle(Vec2d center, double radius, boolean filled);

    /**
     * 绘制椭圆
     * @param center 中心点
     * @param radiusX X轴半径
     * @param radiusY Y轴半径
     * @param filled 是否填充
     */
    void drawEllipse(Vec2d center, double radiusX, double radiusY, boolean filled);

    /**
     * 绘制多边形
     * @param points 顶点数组
     * @param filled 是否填充
     */
    void drawPolygon(Vec2d[] points, boolean filled);

    /**
     * 绘制贝塞尔曲线
     * @param points 控制点数组
     */
    void drawBezier(Vec2d[] points);

    /**
     * 绘制文本
     * @param text 文本内容
     * @param position 位置
     * @param scale 缩放
     */
    void drawText(String text, Vec2d position, float scale);

    /**
     * 绘制图像
     * @param resourceLocation 资源位置
     * @param position 位置
     * @param width 宽度
     * @param height 高度
     */
    void drawImage(String resourceLocation, Vec2d position, int width, int height);

    /**
     * 应用变换
     * @param transform 变换矩阵
     */
    void applyTransform(ITransform transform);

    /**
     * 保存当前状态
     */
    void pushState();

    /**
     * 恢复上一个状态
     */
    void popState();

    /**
     * 清除画布
     * @param color 背景颜色
     */
    void clear(Color color);
}
