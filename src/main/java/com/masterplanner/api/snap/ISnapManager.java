package com.masterplanner.api.snap;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.api.resource.IDisposable;
import com.masterplanner.core.model.Shape;
import java.util.List;

/**
 * 吸附管理器接口
 * 定义吸附功能的核心接口，降低与具体实现类的耦合
 * 
 * 继承 IDisposable 接口，支持类型安全的资源清理
 * 
 * 主要职责：
 * - 点吸附功能
 * - 吸附配置管理
 * - 吸附目标管理
 * - 资源清理管理
 */
public interface ISnapManager extends IDisposable {
    
    /**
     * 执行点吸附
     * @param point 原始点坐标
     * @param snapTargets 吸附目标列表
     * @return 吸附后的点坐标
     */
    Vec2d snapPoint(Vec2d point, List<Shape> snapTargets);
    
    /**
     * 执行点吸附（带起始点参考）
     * @param point 原始点坐标
     * @param startPoint 起始点坐标，用于某些吸附算法
     * @param snapTargets 吸附目标列表
     * @return 吸附后的点坐标
     */
    Vec2d snapPoint(Vec2d point, Vec2d startPoint, List<Shape> snapTargets);
    
    /**
     * 检查是否启用吸附功能
     * @return 是否启用
     */
    boolean isSnapEnabled();
    
    /**
     * 设置吸附功能开关
     * @param enabled 是否启用
     */
    void setSnapEnabled(boolean enabled);
    
    /**
     * 获取吸附距离阈值
     * @return 吸附距离（像素）
     */
    double getSnapDistance();
    
    /**
     * 设置吸附距离阈值
     * @param distance 吸附距离（像素）
     */
    void setSnapDistance(double distance);
    
    /**
     * 检查是否启用网格吸附
     * @return 是否启用网格吸附
     */
    boolean isGridSnapEnabled();
    
    /**
     * 检查是否启用对象吸附
     * @return 是否启用对象吸附
     */
    boolean isObjectSnapEnabled();
    
    /**
     * 获取吸附管理器的配置信息
     * @return 配置信息字符串
     */
    String getConfigInfo();
    
    /**
     * 重置吸附管理器到默认状态
     */
    void reset();
} 