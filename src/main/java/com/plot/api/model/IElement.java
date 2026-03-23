package com.plot.api.model;

import com.google.gson.JsonObject;
import com.plot.api.geometry.Vec2d;

/**
 * 元素接口
 * 定义了所有可以添加到图层的元素的通用行为
 */
public interface IElement {
    /**
     * 获取元素ID
     * @return 元素唯一标识符
     */
    String getId();
    
    /**
     * 获取元素类型
     * @return 元素类型
     */
    String getType();
    
    /**
     * 获取元素位置
     * @return 元素位置
     */
    Vec2d getPosition();
    
    /**
     * 设置元素位置
     * @param position 新位置
     */
    void setPosition(Vec2d position);
    
    /**
     * 序列化元素为JSON对象
     * @return 包含元素数据的JSON对象
     */
    JsonObject serialize();
    
    /**
     * 克隆元素
     * @return 元素的副本
     */
    IElement clone();
    
    /**
     * 获取元素名称
     * @return 元素名称
     */
    String getName();
    
    /**
     * 设置元素名称
     * @param name 新名称
     */
    void setName(String name);
    
    /**
     * 检查元素是否可见
     * @return 如果可见返回true，否则返回false
     */
    boolean isVisible();
    
    /**
     * 设置元素可见性
     * @param visible 可见性
     */
    void setVisible(boolean visible);
} 