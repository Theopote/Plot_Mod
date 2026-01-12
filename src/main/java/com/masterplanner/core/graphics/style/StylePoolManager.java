package com.masterplanner.core.graphics.style;

import com.masterplanner.api.graphics.IShapeStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 样式池管理器
 * 使用对象池模式优化样式克隆性能，减少频繁的对象创建和垃圾回收
 */
public final class StylePoolManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(StylePoolManager.class);
    
    // 单例实例
    private static volatile StylePoolManager instance;
    private static final Object LOCK = new Object();
    
    // 样式池配置
    private static final int MAX_POOL_SIZE = 1000;           // 最大池大小
    private static final int CLEANUP_THRESHOLD = 1200;       // 清理阈值
    private static final long CLEANUP_INTERVAL = 300000;     // 清理间隔（5分钟）
    private static final long STYLE_TTL = 600000;            // 样式生存时间（10分钟）
    
    // 样式池存储
    private final Map<String, PooledStyleEntry> stylePool;
    private final ReentrantLock cleanupLock;
    
    // 性能统计
    private final AtomicLong hitCount = new AtomicLong(0);
    private final AtomicLong missCount = new AtomicLong(0);
    private final AtomicLong creationCount = new AtomicLong(0);
    
    // 清理管理
    private volatile long lastCleanupTime = System.currentTimeMillis();
    
    /**
     * 私有构造函数
     */
    private StylePoolManager() {
        this.stylePool = new ConcurrentHashMap<>();
        this.cleanupLock = new ReentrantLock();
        LOGGER.debug("StylePoolManager 初始化完成，最大池大小: {}", MAX_POOL_SIZE);
    }
    
    /**
     * 获取单例实例
     * @return StylePoolManager实例
     */
    public static StylePoolManager getInstance() {
        if (instance == null) {
            synchronized (LOCK) {
                if (instance == null) {
                    instance = new StylePoolManager();
                }
            }
        }
        return instance;
    }
    
    /**
     * 从池中获取样式（主要入口方法）
     * @param originalStyle 原始样式
     * @return 池化的样式副本
     */
    public ShapeStyle getPooledStyle(IShapeStyle originalStyle) {
        if (originalStyle == null) {
            missCount.incrementAndGet();
            return DefaultStyleConfig.createDefaultShapeStyle();
        }
        
        // 触发定期清理
        checkAndPerformCleanup();
        
        // 生成样式键
        String styleKey = generateStyleKey(originalStyle);
        
        // 尝试从池中获取
        PooledStyleEntry entry = stylePool.get(styleKey);
        if (entry != null && !entry.isExpired()) {
            entry.updateAccessTime();
            hitCount.incrementAndGet();
            LOGGER.trace("样式池命中: {}", styleKey);
            return (ShapeStyle) entry.style.clone();
        }
        
        // 池中不存在或已过期，创建新样式
        missCount.incrementAndGet();
        return createAndPoolStyle(originalStyle, styleKey);
    }
    
    /**
     * 生成样式键
     * @param style 样式对象
     * @return 样式键
     */
    private String generateStyleKey(IShapeStyle style) {
        if (style == null) {
            return "null";
        }
        
        // 使用关键属性生成哈希键
        return String.format("S%d_F%d_W%.2f",
            style.getStrokeColor(),
            style.getFillColor(),
            style.getStrokeWidth()
        );
    }
    
    /**
     * 创建并池化样式
     * @param originalStyle 原始样式
     * @param styleKey 样式键
     * @return 新创建的样式
     */
    private ShapeStyle createAndPoolStyle(IShapeStyle originalStyle, String styleKey) {
        try {
            ShapeStyle newStyle;
            
            if (originalStyle instanceof ShapeStyle) {
                newStyle = (ShapeStyle) originalStyle.clone();
            } else {
                // 转换其他类型的样式
                newStyle = convertToShapeStyle(originalStyle);
            }
            
            // 检查池大小，必要时清理
            if (stylePool.size() >= MAX_POOL_SIZE) {
                performPartialCleanup();
            }
            
            // 添加到池中（如果池未满）
            if (stylePool.size() < CLEANUP_THRESHOLD) {
                PooledStyleEntry entry = new PooledStyleEntry(newStyle);
                stylePool.put(styleKey, entry);
                LOGGER.trace("样式已添加到池: {} (池大小: {})", styleKey, stylePool.size());
            }
            
            creationCount.incrementAndGet();
            return newStyle;
            
        } catch (Exception e) {
            LOGGER.warn("创建池化样式失败: {}, 使用默认样式", e.getMessage());
            return DefaultStyleConfig.createFallbackShapeStyle();
        }
    }
    
    /**
     * 转换样式到ShapeStyle
     * @param style 原始样式
     * @return ShapeStyle实例
     */
    private ShapeStyle convertToShapeStyle(IShapeStyle style) {
        ShapeStyle shapeStyle = new ShapeStyle();
        shapeStyle.setStrokeColor(style.getStrokeColor());
        shapeStyle.setStrokeWidth(style.getStrokeWidth());
        shapeStyle.setFillColor(style.getFillColor());
        return shapeStyle;
    }
    
    /**
     * 检查并执行定期清理
     */
    private void checkAndPerformCleanup() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCleanupTime > CLEANUP_INTERVAL) {
            if (cleanupLock.tryLock()) {
                try {
                    // 双重检查
                    if (currentTime - lastCleanupTime > CLEANUP_INTERVAL) {
                        performFullCleanup();
                        lastCleanupTime = currentTime;
                    }
                } finally {
                    cleanupLock.unlock();
                }
            }
        }
    }
    
    /**
     * 执行部分清理（当池接近满时）
     */
    private void performPartialCleanup() {
        if (!cleanupLock.tryLock()) {
            return; // 其他线程正在清理
        }
        
        try {
            long currentTime = System.currentTimeMillis();
            int removedCount = 0;
            int targetRemove = stylePool.size() / 4; // 移除25%
            
            for (Map.Entry<String, PooledStyleEntry> entry : stylePool.entrySet()) {
                if (removedCount >= targetRemove) {
                    break;
                }
                
                PooledStyleEntry poolEntry = entry.getValue();
                if (poolEntry.isExpired() || 
                    (currentTime - poolEntry.lastAccessTime) > STYLE_TTL / 2) {
                    stylePool.remove(entry.getKey());
                    removedCount++;
                }
            }
            
            LOGGER.debug("部分清理完成，移除 {} 个过期样式，池大小: {}", removedCount, stylePool.size());
            
        } finally {
            cleanupLock.unlock();
        }
    }
    
    /**
     * 执行完整清理
     */
    private void performFullCleanup() {
        long currentTime = System.currentTimeMillis();
        int removedCount = 0;
        
        stylePool.entrySet().removeIf(entry -> {
            boolean shouldRemove = entry.getValue().isExpired();
            return shouldRemove;
        });
        
        LOGGER.debug("完整清理完成，移除 {} 个过期样式，池大小: {}", removedCount, stylePool.size());
    }
    
    /**
     * 清空样式池
     */
    public void clearPool() {
        cleanupLock.lock();
        try {
            int oldSize = stylePool.size();
            stylePool.clear();
            LOGGER.info("样式池已清空，原大小: {}", oldSize);
        } finally {
            cleanupLock.unlock();
        }
    }
    
    /**
     * 获取池状态信息
     * @return 池状态字符串
     */
    public String getPoolStatus() {
        long hits = hitCount.get();
        long misses = missCount.get();
        long total = hits + misses;
        double hitRate = total > 0 ? (double) hits / total * 100 : 0;
        
        return String.format(
            "样式池状态 - 大小: %d/%d, 命中率: %.2f%% (%d/%d), 创建: %d",
            stylePool.size(), MAX_POOL_SIZE, hitRate, hits, total, creationCount.get()
        );
    }
    
    /**
     * 获取详细的性能统计
     * @return 性能统计对象
     */
    public PoolStatistics getStatistics() {
        return new PoolStatistics(
            stylePool.size(),
            hitCount.get(),
            missCount.get(),
            creationCount.get()
        );
    }
    
    /**
     * 重置统计计数器
     */
    public void resetStatistics() {
        hitCount.set(0);
        missCount.set(0);
        creationCount.set(0);
        LOGGER.debug("样式池统计计数器已重置");
    }
    
    /**
     * 池化样式条目
     */
    private static class PooledStyleEntry {
        final ShapeStyle style;
        final long creationTime;
        volatile long lastAccessTime;
        
        PooledStyleEntry(ShapeStyle style) {
            this.style = style;
            this.creationTime = System.currentTimeMillis();
            this.lastAccessTime = creationTime;
        }
        
        void updateAccessTime() {
            this.lastAccessTime = System.currentTimeMillis();
        }
        
        boolean isExpired() {
            return (System.currentTimeMillis() - creationTime) > STYLE_TTL;
        }
    }
    
    /**
     * 池统计信息
     */
    public static class PoolStatistics {
        public final int poolSize;
        public final long hitCount;
        public final long missCount;
        public final long creationCount;
        public final double hitRate;
        
        PoolStatistics(int poolSize, long hitCount, long missCount, long creationCount) {
            this.poolSize = poolSize;
            this.hitCount = hitCount;
            this.missCount = missCount;
            this.creationCount = creationCount;
            
            long total = hitCount + missCount;
            this.hitRate = total > 0 ? (double) hitCount / total * 100 : 0.0;
        }
        
        @Override
        public String toString() {
            return String.format(
                "PoolStatistics{poolSize=%d, hitRate=%.2f%%, hits=%d, misses=%d, created=%d}",
                poolSize, hitRate, hitCount, missCount, creationCount
            );
        }
    }
}
