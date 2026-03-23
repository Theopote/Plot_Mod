package com.plot.core.spatial;

import com.plot.api.geometry.IBoundingBox;
import com.plot.api.geometry.Vec2d;
import com.plot.core.model.Shape;

import java.util.List;

/**
 * 空间索引接口
 * 
 * <p>提供高效的空间查询功能，用于加速图形查找和空间计算：</p>
 * <ul>
 *   <li>插入图形：将图形添加到空间索引中</li>
 *   <li>删除图形：从空间索引中移除图形</li>
 *   <li>更新图形：更新图形在空间索引中的位置</li>
 *   <li>范围查询：查询指定区域内的图形</li>
 *   <li>射线查询：查询与射线相交的图形</li>
 * </ul>
 * 
 * @author Plot Team
 * @version 1.0 - 空间索引接口
 */
public interface SpatialIndex {
    
    /**
     * 插入图形到空间索引
     * 
     * @param shape 要插入的图形
     */
    void insert(Shape shape);
    
    /**
     * 从空间索引中删除图形
     * 
     * @param shape 要删除的图形
     */
    void remove(Shape shape);
    
    /**
     * 更新图形在空间索引中的位置
     * 
     * @param shape 要更新的图形
     */
    void update(Shape shape);
    
    /**
     * 查询指定区域内的图形
     * 
     * @param bounds 查询区域
     * @return 区域内的图形列表
     */
    List<Shape> query(IBoundingBox bounds);
    
    /**
     * 查询与射线相交的图形
     * 
     * @param startPoint 射线起点
     * @param direction 射线方向
     * @param maxDistance 最大距离
     * @return 与射线相交的图形列表
     */
    List<Shape> queryRay(Vec2d startPoint, Vec2d direction, double maxDistance);
    
    /**
     * 查询指定点附近的图形
     * 
     * @param point 查询点
     * @param tolerance 容差
     * @return 点附近的图形列表
     */
    List<Shape> queryNear(Vec2d point, double tolerance);
    
    /**
     * 清空空间索引
     */
    void clear();
    
    /**
     * 获取空间索引中的图形总数
     * 
     * @return 图形总数
     */
    int size();
    
    /**
     * 检查空间索引是否为空
     * 
     * @return true如果为空，false否则
     */
    boolean isEmpty();
    
    /**
     * 获取空间索引的统计信息
     * 
     * @return 统计信息
     */
    SpatialIndexStats getStats();
} 