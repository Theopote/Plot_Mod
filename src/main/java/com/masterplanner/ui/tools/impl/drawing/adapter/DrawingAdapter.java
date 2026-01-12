package com.masterplanner.ui.tools.impl.drawing.adapter;

import com.masterplanner.api.geometry.Vec2d;
import java.awt.Color;
import java.util.List;

/**
 * 绘制适配器接口
 * 
 * 用于抽象不同绘制后端（DrawContext 和 ImDrawList）的绘制行为，
 * 避免在工具类中重复实现渲染逻辑。
 * 
 * 支持的绘制操作：
 * - 线条绘制（直线、圆弧、曲线）
 * - 图形绘制（圆形、椭圆、矩形、多边形）
 * - 文本绘制
 * - 控制点和辅助几何体绘制
 * 
 * 使用示例：
 * <pre>
 * public void renderPreview(DrawContext context) {
 *     DrawingAdapter adapter = new DrawContextAdapter(context);
 *     renderPreviewWithAdapter(adapter);
 * }
 * 
 * public void renderPreview(ImDrawList drawList, CanvasCamera camera) {
 *     DrawingAdapter adapter = new ImGuiAdapter(drawList, camera);
 *     renderPreviewWithAdapter(adapter);
 * }
 * 
 * private void renderPreviewWithAdapter(DrawingAdapter adapter) {
 *     // 统一的渲染逻辑
 *     adapter.drawLine(startPoint, endPoint, Color.WHITE, 2.0f);
 *     adapter.drawCircle(centerPoint, radius, Color.BLUE, false);
 * }
 * </pre>
 */
public interface DrawingAdapter {
    
    // =============== 基础绘制操作 ===============
    
    /**
     * 绘制直线
     * @param start 起点（世界坐标）
     * @param end 终点（世界坐标）
     * @param color 颜色
     * @param thickness 线条粗细
     */
    void drawLine(Vec2d start, Vec2d end, Color color, float thickness);
    
    /**
     * 绘制多条连接的线段
     * @param points 点列表（世界坐标）
     * @param color 颜色
     * @param thickness 线条粗细
     * @param closed 是否闭合（连接首尾点）
     */
    void drawPolyline(List<Vec2d> points, Color color, float thickness, boolean closed);
    
    /**
     * 绘制圆形
     * @param center 圆心（世界坐标）
     * @param radius 半径（世界坐标单位）
     * @param color 颜色
     * @param filled 是否填充
     */
    void drawCircle(Vec2d center, double radius, Color color, boolean filled);
    
    /**
     * 绘制圆形（带线条粗细）
     * @param center 圆心（世界坐标）
     * @param radius 半径（世界坐标单位）
     * @param color 颜色
     * @param thickness 线条粗细（仅当 filled=false 时有效）
     * @param filled 是否填充
     */
    void drawCircle(Vec2d center, double radius, Color color, float thickness, boolean filled);
    
    /**
     * 绘制椭圆
     * @param center 中心点（世界坐标）
     * @param radiusX X轴半径（世界坐标单位）
     * @param radiusY Y轴半径（世界坐标单位）
     * @param rotation 旋转角度（弧度）
     * @param color 颜色
     * @param thickness 线条粗细
     * @param filled 是否填充
     * @param segments 分段数（用于近似椭圆）
     */
    void drawEllipse(Vec2d center, double radiusX, double radiusY, double rotation, 
                    Color color, float thickness, boolean filled, int segments);
    
    /**
     * 绘制矩形
     * @param min 最小点（世界坐标）
     * @param max 最大点（世界坐标）
     * @param color 颜色
     * @param thickness 线条粗细
     * @param filled 是否填充
     */
    void drawRectangle(Vec2d min, Vec2d max, Color color, float thickness, boolean filled);
    
    /**
     * 绘制多边形
     * @param points 顶点列表（世界坐标）
     * @param color 颜色
     * @param thickness 线条粗细
     * @param filled 是否填充
     */
    void drawPolygon(List<Vec2d> points, Color color, float thickness, boolean filled);
    
    // =============== 圆弧绘制操作 ===============
    
    /**
     * 绘制圆弧
     * @param center 圆心（世界坐标）
     * @param radius 半径（世界坐标单位）
     * @param startAngle 起始角度（弧度）
     * @param endAngle 结束角度（弧度）
     * @param color 颜色
     * @param thickness 线条粗细
     * @param segments 分段数
     * @param clockwise 是否顺时针
     */
    void drawArc(Vec2d center, double radius, double startAngle, double endAngle,
                Color color, float thickness, int segments, boolean clockwise);
    
    /**
     * 绘制圆弧扇形（带填充）
     * @param center 圆心（世界坐标）
     * @param radius 半径（世界坐标单位）
     * @param startAngle 起始角度（弧度）
     * @param endAngle 结束角度（弧度）
     * @param color 颜色
     * @param thickness 边框粗细
     * @param fillColor 填充颜色（null表示不填充）
     * @param segments 分段数
     * @param clockwise 是否顺时针
     */
    void drawArcSector(Vec2d center, double radius, double startAngle, double endAngle,
                      Color color, float thickness, Color fillColor, int segments, boolean clockwise);
    
    // =============== 文本绘制操作 ===============
    
    /**
     * 绘制文本
     * @param position 文本位置（世界坐标）
     * @param text 文本内容
     * @param color 颜色
     */
    void drawText(Vec2d position, String text, Color color);
    
    /**
     * 绘制文本（带字体大小）
     * @param position 文本位置（世界坐标）
     * @param text 文本内容
     * @param color 颜色
     * @param fontSize 字体大小
     */
    void drawText(Vec2d position, String text, Color color, float fontSize);
    
    // =============== 辅助绘制操作 ===============
    
    /**
     * 绘制控制点（小圆点）
     * @param position 位置（世界坐标）
     * @param color 颜色
     * @param size 大小
     * @param filled 是否填充
     */
    void drawControlPoint(Vec2d position, Color color, float size, boolean filled);
    
    /**
     * 绘制虚线
     * @param start 起点（世界坐标）
     * @param end 终点（世界坐标）
     * @param color 颜色
     * @param thickness 线条粗细
     * @param dashLength 虚线段长度
     * @param gapLength 虚线间隔长度
     */
    void drawDashedLine(Vec2d start, Vec2d end, Color color, float thickness, 
                       float dashLength, float gapLength);
    
    /**
     * 绘制箭头
     * @param start 起点（世界坐标）
     * @param end 终点（世界坐标）
     * @param color 颜色
     * @param thickness 线条粗细
     * @param arrowSize 箭头大小
     */
    void drawArrow(Vec2d start, Vec2d end, Color color, float thickness, float arrowSize);
    
    // =============== 高级绘制操作 ===============
    
    /**
     * 绘制贝塞尔曲线
     * @param points 控制点列表（世界坐标）
     * @param color 颜色
     * @param thickness 线条粗细
     * @param segments 分段数
     */
    void drawBezierCurve(List<Vec2d> points, Color color, float thickness, int segments);
    
    /**
     * 绘制样条曲线
     * @param points 控制点列表（世界坐标）
     * @param color 颜色
     * @param thickness 线条粗细
     * @param tension 张力参数
     * @param segments 分段数
     */
    void drawSpline(List<Vec2d> points, Color color, float thickness, float tension, int segments);
    
    // =============== 工具方法 ===============
    
    /**
     * 获取颜色的 ImGui 整数表示
     * @param color AWT 颜色
     * @return ImGui 颜色整数
     */
    default int getImGuiColor(Color color) {
        return getImGuiColor(color, 1.0f);
    }
    
    /**
     * 获取颜色的 ImGui 整数表示（带透明度）
     * @param color AWT 颜色
     * @param alpha 透明度 (0.0-1.0)
     * @return ImGui 颜色整数
     */
    default int getImGuiColor(Color color, float alpha) {
        return ((int)(alpha * 255) << 24) |
               (color.getRed() << 16) |
               (color.getGreen() << 8) |
               color.getBlue();
    }
    
    /**
     * 创建预览颜色（带透明度）
     * @param baseColor 基础颜色
     * @param alpha 透明度 (0.0-1.0)
     * @return 预览颜色
     */
    default Color createPreviewColor(Color baseColor, float alpha) {
        return new Color(
            baseColor.getRed(),
            baseColor.getGreen(),
            baseColor.getBlue(),
            (int)(alpha * 255)
        );
    }
    
    // =============== 常用预设颜色 ===============
    
    Color PREVIEW_COLOR = new Color(255, 255, 255, 204); // 白色，80% 透明度
    Color CONTROL_POINT_COLOR = new Color(255, 255, 0, 204); // 黄色，80% 透明度
    Color AUXILIARY_LINE_COLOR = new Color(128, 128, 128, 153); // 灰色，60% 透明度
    Color ERROR_COLOR = new Color(255, 0, 0, 204); // 红色，80% 透明度
    Color SUCCESS_COLOR = new Color(0, 255, 0, 204); // 绿色，80% 透明度
    
    // =============== 常用预设尺寸 ===============
    
    float DEFAULT_LINE_THICKNESS = 2.0f;
    float THIN_LINE_THICKNESS = 1.0f;
    float THICK_LINE_THICKNESS = 3.0f;
    float CONTROL_POINT_SIZE = 4.0f;
    float LARGE_CONTROL_POINT_SIZE = 6.0f;
    float DEFAULT_DASH_LENGTH = 5.0f;
    float DEFAULT_GAP_LENGTH = 3.0f;
    float DEFAULT_ARROW_SIZE = 8.0f;
} 