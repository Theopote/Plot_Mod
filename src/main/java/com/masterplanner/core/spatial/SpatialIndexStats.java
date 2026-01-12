package com.masterplanner.core.spatial;

/**
 * 空间索引统计信息
 * 
 * <p>提供空间索引的性能和状态统计信息：</p>
 * <ul>
 *   <li>节点数量：索引中的节点总数</li>
 *   <li>图形数量：索引中的图形总数</li>
 *   <li>查询次数：累计查询次数</li>
 *   <li>平均查询时间：平均每次查询的耗时</li>
 *   <li>内存使用：索引占用的内存大小</li>
 * </ul>
 * 
 * @author MasterPlanner Team
 * @version 1.0 - 空间索引统计信息
 */
public class SpatialIndexStats {
    private final int nodeCount;
    private final int shapeCount;
    private final long queryCount;
    private final double averageQueryTime;
    private final long memoryUsage;
    private final long lastUpdateTime;
    
    /**
     * 构造函数
     * 
     * @param nodeCount 节点数量
     * @param shapeCount 图形数量
     * @param queryCount 查询次数
     * @param averageQueryTime 平均查询时间（毫秒）
     * @param memoryUsage 内存使用（字节）
     */
    public SpatialIndexStats(int nodeCount, int shapeCount, long queryCount, 
                           double averageQueryTime, long memoryUsage) {
        this.nodeCount = nodeCount;
        this.shapeCount = shapeCount;
        this.queryCount = queryCount;
        this.averageQueryTime = averageQueryTime;
        this.memoryUsage = memoryUsage;
        this.lastUpdateTime = System.currentTimeMillis();
    }
    
    /**
     * 获取节点数量
     * 
     * @return 节点数量
     */
    public int getNodeCount() {
        return nodeCount;
    }
    
    /**
     * 获取图形数量
     * 
     * @return 图形数量
     */
    public int getShapeCount() {
        return shapeCount;
    }
    
    /**
     * 获取查询次数
     * 
     * @return 查询次数
     */
    public long getQueryCount() {
        return queryCount;
    }
    
    /**
     * 获取平均查询时间
     * 
     * @return 平均查询时间（毫秒）
     */
    public double getAverageQueryTime() {
        return averageQueryTime;
    }
    
    /**
     * 获取内存使用
     * 
     * @return 内存使用（字节）
     */
    public long getMemoryUsage() {
        return memoryUsage;
    }
    
    /**
     * 获取最后更新时间
     * 
     * @return 最后更新时间（毫秒）
     */
    public long getLastUpdateTime() {
        return lastUpdateTime;
    }
    
    /**
     * 获取内存使用的人类可读格式
     * 
     * @return 格式化的内存使用字符串
     */
    public String getFormattedMemoryUsage() {
        if (memoryUsage < 1024) {
            return memoryUsage + " B";
        } else if (memoryUsage < 1024 * 1024) {
            return String.format("%.1f KB", memoryUsage / 1024.0);
        } else {
            return String.format("%.1f MB", memoryUsage / (1024.0 * 1024.0));
        }
    }
    
    @Override
    public String toString() {
        return String.format("SpatialIndexStats{nodeCount=%d, shapeCount=%d, queryCount=%d, " +
                           "avgQueryTime=%.2fms, memoryUsage=%s}",
            nodeCount, shapeCount, queryCount, averageQueryTime, getFormattedMemoryUsage());
    }
} 