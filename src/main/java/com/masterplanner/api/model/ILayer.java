package com.masterplanner.api.model;

import java.util.List;
import java.util.Set;

import com.masterplanner.core.model.Shape;
import com.masterplanner.core.graphics.style.LineStyle;
import java.awt.Color;

/**
 * 图层接口，定义了图层的基本操作
 */
public interface ILayer {
    /**
     * 获取图层ID
     */
    String getId();

    /**
     * 获取图层名称
     * @return 图层名称
     */
    String getName();

    /**
     * 设置图层名称
     * @param name 新的图层名称
     */
    void setName(String name);

    /**
     * 获取图层是否可见
     * @return 是否可见
     */
    boolean isVisible();

    /**
     * 设置图层可见性
     * @param visible 是否可见
     */
    void setVisible(boolean visible);

    /**
     * 获取图层是否锁定
     * @return 是否锁定
     */
    boolean isLocked();

    /**
     * 设置图层锁定状态
     * @param locked 是否锁定
     */
    void setLocked(boolean locked);

    /**
     * 获取图层透明度
     * @return 透明度值（0.0-1.0）
     */
    double getOpacity();

    /**
     * 设置图层透明度
     * @param opacity 新的透明度值（0.0-1.0）
     */
    void setOpacity(double opacity);

    /**
     * 获取所有形状ID
     */
    Set<String> getShapeIds();

    /**
     * 获取指定ID的形状
     */
    Shape getShape(String id);

    /**
     * 添加形状
     */
    void addShape(Shape shape);

    /**
     * 从图层中移除形状
     * @param shape 要移除的形状
     * @return 是否成功移除
     */
    boolean removeShape(Shape shape);

    /**
     * 检查是否包含指定ID的形状
     */
    boolean hasShape(String id);

    /**
     * 获取所有形状
     * @return 形状列表
     */
    List<Shape> getShapes();

    /**
     * 清空图层
     */
    void clear();

    /**
     * 获取图层顺序
     * @return Z序值
     */
    int getZOrder();

    /**
     * 设置图层顺序
     * @param zOrder 新的Z序值
     */
    void setZOrder(int zOrder);

    /**
     * 设置图层线条样式
     */
    void setLineStyle(LineStyle style);

    /**
     * 获取图层线条样式
     */
    LineStyle getLineStyle();

    /**
     * 设置图层颜色
     */
    void setColor(Color color);

    /**
     * 获取图层颜色
     */
    Color getColor();
    
    /**
     * 获取图层中的所有元素
     * @return 元素列表
     */
    List<IElement> getElements();
    
    /**
     * 添加元素到图层
     * @param element 要添加的元素
     */
    void addElement(IElement element);
    
    /**
     * 从图层中移除元素
     * @param element 要移除的元素
     * @return 是否成功移除
     */
    boolean removeElement(IElement element);
}
