package com.plot.api.geometry;

/**
 * 表示形状的边界框接口
 */
public interface IBoundingBox {
    /**
     * 获取边界框的最小点(左下角)
     * @return 最小点坐标
     */
    Vec2d getMin();
    
    /**
     * 获取边界框的最大点(右上角)
     * @return 最大点坐标
     */
    Vec2d getMax();
    
    /**
     * 获取边界框的宽度
     * @return 宽度
     */
    double getWidth();
    
    /**
     * 获取边界框的高度
     * @return 高度
     */
    double getHeight();
    
    /**
     * 获取边界框的中心点
     * @return 中心点坐标
     */
    Vec2d getCenter();
    
    /**
     * 判断点是否在边界框内
     * @param point 要判断的点
     * @return 是否在边界框内
     */
    boolean contains(Vec2d point);
    
    /**
     * 判断是否与另一个边界框相交
     * @param other 另一个边界框
     * @return 是否相交
     */
    boolean intersects(IBoundingBox other);
    
    /**
     * 扩展边界框
     * @param margin 扩展的边距
     * @return 扩展后的边界框
     */
    IBoundingBox expand(double margin);
    
    /**
     * 获取点到边界框的最短距离
     * @param point 要计算的点
     * @return 最短距离
     */
    double distanceTo(Vec2d point);
} 