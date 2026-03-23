package com.plot.api.model;

import com.plot.api.geometry.Vec2d;
import com.plot.api.graphics.ITextStyle;
import com.plot.api.graphics.IShapeStyle;
import com.plot.api.model.ILayer;
import com.plot.core.model.Shape;
import java.util.List;

/**
 * 画布接口，定义了画布的基本操作
 */
public interface ICanvas {
    /**
     * 获取画布中的所有图层
     * @return 图层列表
     */
    List<ILayer> getLayers();

    /**
     * 添加新图层
     * @param layer 要添加的图层
     */
    void addLayer(ILayer layer);

    /**
     * 移除图层
     * @param layer 要移除的图层
     */
    void removeLayer(ILayer layer);

    /**
     * 获取当前活动图层
     * @return 当前活动图层
     */
    ILayer getCurrentLayer();

    /**
     * 设置当前活动图层
     * @param layer 要设置为活动的图层
     */
    void setCurrentLayer(ILayer layer);

    /**
     * 获取画布原点（世界坐标系中的位置）
     * @return 画布原点坐标
     */
    Vec2d getOrigin();

    /**
     * 设置画布原点
     * @param origin 新的原点坐标
     */
    void setOrigin(Vec2d origin);

    /**
     * 获取画布缩放比例
     * @return 缩放比例
     */
    double getScale();

    /**
     * 设置画布缩放比例
     * @param scale 新的缩放比例
     */
    void setScale(double scale);

    /**
     * 获取画布旋转角度（弧度）
     * @return 旋转角度
     */
    double getRotation();

    /**
     * 设置画布旋转角度
     * @param rotation 新的旋转角度（弧度）
     */
    void setRotation(double rotation);

    /**
     * 世界坐标转换为画布坐标
     * @param worldPoint 世界坐标点
     * @return 画布坐标点
     */
    Vec2d worldToCanvas(Vec2d worldPoint);

    /**
     * 画布坐标转换为世界坐标
     * @param canvasPoint 画布坐标点
     * @return 世界坐标点
     */
    Vec2d canvasToWorld(Vec2d canvasPoint);

    /**
     * 获取当前文本样式
     */
    ITextStyle getCurrentTextStyle();
    
    /**
     * 设置当前文本样式
     */
    void setCurrentTextStyle(ITextStyle style);
    
    /**
     * 获取当前形状样式
     */
    IShapeStyle getCurrentShapeStyle();
    
    /**
     * 设置当前形状样式
     */
    void setCurrentShapeStyle(IShapeStyle style);
    
    /**
     * 设置光标类型
     */
    void setCursor(String cursorType);
    
    /**
     * 获取光标类型
     */
    String getCursor();
    
    /**
     * 添加形状到当前图层
     */
    void addShape(Shape shape);
    
    /**
     * 从当前图层移除形状
     */
    void removeShape(Shape shape);
    
    /**
     * 清空所有图层
     */
    void clear();
    
    /**
     * 重绘画布
     */
    void refresh();

    /**
     * 获取画布上的所有图形
     * @return 图形列表
     */
    List<Shape> getShapes();

    /**
     * 将屏幕坐标转换为世界坐标
     * @param screenPos 屏幕坐标
     * @return 世界坐标
     */
    Vec2d screenToWorld(Vec2d screenPos);

    /**
     * 将世界坐标转换为屏幕坐标
     * @param worldPos 世界坐标
     * @return 屏幕坐标
     */
    Vec2d worldToScreen(Vec2d worldPos);
}
