package com.masterplanner.ui.tools.impl.modify.helper;

import com.masterplanner.core.model.Shape;
import com.masterplanner.core.geometry.shapes.RectangleShape;
import com.masterplanner.core.geometry.shapes.CircleShape;
import com.masterplanner.core.geometry.shapes.LineShape;
import com.masterplanner.core.geometry.shapes.EllipseShape;
import com.masterplanner.core.geometry.shapes.ArcShape;
import com.masterplanner.core.geometry.shapes.Polygon;
import com.masterplanner.core.geometry.shapes.PolylineShape;
import com.masterplanner.core.geometry.shapes.TextShape;
import com.masterplanner.core.geometry.shapes.FreeDrawPath;
import com.masterplanner.core.geometry.shapes.BezierCurveShape;
import com.masterplanner.core.geometry.shapes.CableShape;

/**
 * 形状访问者接口
 * 
 * <p>用于处理不同形状的特定操作，如偏移、旋转、缩放等。
 * 采用访问者模式，让每种形状对象"接受"一个处理器，
 * 由图形自身提供最核心的操作算法。</p>
 * 
 * <p><strong>设计优势：</strong></p>
 * <ul>
 *   <li><strong>高扩展性</strong>：新增图形类型时只需实现accept方法和对应的visit方法</li>
 *   <li><strong>职责清晰</strong>：每种形状的操作逻辑分散到各自的visit方法中</li>
 *   <li><strong>开闭原则</strong>：对扩展开放，对修改关闭</li>
 *   <li><strong>类型安全</strong>：编译时类型检查，避免运行时类型转换错误</li>
 * </ul>
 * 
 * @author MasterPlanner Team
 * @version 1.0 - 形状访问者接口
 */
public interface IShapeVisitor {
    
    /**
     * 访问矩形形状
     * @param rect 矩形形状
     * @return 处理后的形状
     */
    Shape visit(RectangleShape rect);
    
    /**
     * 访问圆形形状
     * @param circle 圆形形状
     * @return 处理后的形状
     */
    Shape visit(CircleShape circle);
    
    /**
     * 访问直线形状
     * @param line 直线形状
     * @return 处理后的形状
     */
    Shape visit(LineShape line);
    
    /**
     * 访问椭圆形状
     * @param ellipse 椭圆形状
     * @return 处理后的形状
     */
    Shape visit(EllipseShape ellipse);
    
    /**
     * 访问圆弧形状
     * @param arc 圆弧形状
     * @return 处理后的形状
     */
    Shape visit(ArcShape arc);
    
    /**
     * 访问多边形形状
     * @param polygon 多边形形状
     * @return 处理后的形状
     */
    Shape visit(Polygon polygon);
    
    /**
     * 访问多段线形状
     * @param polyline 多段线形状
     * @return 处理后的形状
     */
    Shape visit(PolylineShape polyline);
    
    /**
     * 访问文本形状
     * @param text 文本形状
     * @return 处理后的形状
     */
    Shape visit(TextShape text);
    
    /**
     * 访问自由绘制路径形状
     * @param path 自由绘制路径形状
     * @return 处理后的形状
     */
    Shape visit(FreeDrawPath path);
    
    /**
     * 访问贝塞尔曲线形状（样条曲线）
     * @param bezier 贝塞尔曲线形状
     * @return 处理后的形状
     */
    Shape visit(BezierCurveShape bezier);
    
    /**
     * 访问悬链线形状
     * @param cable 悬链线形状
     * @return 处理后的形状
     */
    Shape visit(CableShape cable);
    
    /**
     * 访问通用形状（默认处理）
     * @param shape 通用形状
     * @return 处理后的形状
     */
    Shape visit(Shape shape);
} 