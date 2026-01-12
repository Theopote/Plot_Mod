package com.masterplanner.api.render;

import com.masterplanner.ui.canvas.CanvasCamera;
import imgui.ImDrawList;

/**
 * 渲染访问者接口
 * 
 * <p>使用访问者模式分离渲染逻辑，提高代码的可扩展性和可维护性。
 * 每种图形类型都有对应的渲染方法，遵循开闭原则。</p>
 * 
 * @author MasterPlanner Team
 * @version 1.0
 */
public interface IRenderVisitor {
    
    /**
     * 渲染线段
     * 
     * @param shape 线段图形
     * @param drawList ImGui绘制列表
     * @param camera 相机对象
     */
    void render(com.masterplanner.core.geometry.shapes.LineShape shape, ImDrawList drawList, CanvasCamera camera);
    
    /**
     * 渲染矩形
     * 
     * @param shape 矩形图形
     * @param drawList ImGui绘制列表
     * @param camera 相机对象
     */
    void render(com.masterplanner.core.geometry.shapes.RectangleShape shape, ImDrawList drawList, CanvasCamera camera);
    
    /**
     * 渲染圆形
     * 
     * @param shape 圆形图形
     * @param drawList ImGui绘制列表
     * @param camera 相机对象
     */
    void render(com.masterplanner.core.geometry.shapes.CircleShape shape, ImDrawList drawList, CanvasCamera camera);
    
    /**
     * 渲染椭圆
     * 
     * @param shape 椭圆图形
     * @param drawList ImGui绘制列表
     * @param camera 相机对象
     */
    void render(com.masterplanner.core.geometry.shapes.EllipseShape shape, ImDrawList drawList, CanvasCamera camera);
    
    /**
     * 渲染多边形
     * 
     * @param shape 多边形图形
     * @param drawList ImGui绘制列表
     * @param camera 相机对象
     */
    void render(com.masterplanner.core.geometry.shapes.Polygon shape, ImDrawList drawList, CanvasCamera camera);
    
    /**
     * 渲染弧线
     * 
     * @param shape 弧线图形
     * @param drawList ImGui绘制列表
     * @param camera 相机对象
     */
    void render(com.masterplanner.core.geometry.shapes.ArcShape shape, ImDrawList drawList, CanvasCamera camera);
    
    /**
     * 渲染椭圆弧
     * 
     * @param shape 椭圆弧图形
     * @param drawList ImGui绘制列表
     * @param camera 相机对象
     */
    void render(com.masterplanner.core.geometry.shapes.EllipticalArcShape shape, ImDrawList drawList, CanvasCamera camera);
    
    /**
     * 渲染贝塞尔曲线
     * 
     * @param shape 贝塞尔曲线图形
     * @param drawList ImGui绘制列表
     * @param camera 相机对象
     */
    void render(com.masterplanner.core.geometry.shapes.BezierCurveShape shape, ImDrawList drawList, CanvasCamera camera);
    
    /**
     * 渲染多段线
     * 
     * @param shape 多段线图形
     * @param drawList ImGui绘制列表
     * @param camera 相机对象
     */
    void render(com.masterplanner.core.geometry.shapes.PolylineShape shape, ImDrawList drawList, CanvasCamera camera);
    
    /**
     * 渲染文本
     * 
     * @param shape 文本图形
     * @param drawList ImGui绘制列表
     * @param camera 相机对象
     */
    void render(com.masterplanner.core.geometry.shapes.TextShape shape, ImDrawList drawList, CanvasCamera camera);
    
    /**
     * 渲染正弦曲线
     * 
     * @param shape 正弦曲线图形
     * @param drawList ImGui绘制列表
     * @param camera 相机对象
     */
    void render(com.masterplanner.core.geometry.shapes.SineCurveShape shape, ImDrawList drawList, CanvasCamera camera);
    
    /**
     * 渲染自由绘制路径
     * 
     * @param shape 自由绘制路径图形
     * @param drawList ImGui绘制列表
     * @param camera 相机对象
     */
    void render(com.masterplanner.core.geometry.shapes.FreeDrawPath shape, ImDrawList drawList, CanvasCamera camera);
    
    /**
     * 渲染螺旋线
     * 
     * @param shape 螺旋线图形
     * @param drawList ImGui绘制列表
     * @param camera 相机对象
     */
    void render(com.masterplanner.core.geometry.shapes.SpiralShape shape, ImDrawList drawList, CanvasCamera camera);
    
    /**
     * 渲染悬链线
     * 
     * @param shape 悬链线图形
     * @param drawList ImGui绘制列表
     * @param camera 相机对象
     */
    void render(com.masterplanner.core.geometry.shapes.CableShape shape, ImDrawList drawList, CanvasCamera camera);
    
    /**
     * 渲染通用图形（默认实现）
     * 
     * @param shape 通用图形
     * @param drawList ImGui绘制列表
     * @param camera 相机对象
     */
    void render(com.masterplanner.core.model.Shape shape, ImDrawList drawList, CanvasCamera camera);
}
