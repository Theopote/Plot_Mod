package com.plot.ui.dialog.BlockConfigDialog;

import com.plot.ui.component.BlockIconRenderer;
import com.plot.utils.PlotI18n;
import net.minecraft.block.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 方块预加载管理器
 * 负责方块图标的预加载管理，包括预加载任务调度、状态跟踪和与BlockIconRenderer的交互
 */
public class BlockPreloadManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/BlockPreloadManager");
    
    // 预加载相关常量
    private static final int DEFAULT_PRELOAD_LIMIT = 50;
    private static final float PRELOAD_PERCENTAGE = 0.3f; // 预加载分类中方块总数的百分比

    // 预加载状态
    private final Map<BlockCategoryManager.BlockCategory, PreloadStatus> preloadStatus = new EnumMap<>(BlockCategoryManager.BlockCategory.class);

    // 分类管理器引用
    private final BlockCategoryManager categoryManager;
    
    /**
     * 预加载状态类
     * 跟踪方块纹理预加载的进度
     */
    public static class PreloadStatus {
        private final CompletableFuture<Void> future;
        private final AtomicInteger loadedCount = new AtomicInteger(0);

        public PreloadStatus(CompletableFuture<Void> future, int totalCount, int preloadLimit) {
            this.future = future;
        }
        
        public boolean isComplete() {
            return future.isDone();
        }
        
        public int getLoadedCount() {
            return loadedCount.get();
        }

    }
    
    /**
     * 构造函数
     * @param categoryManager 方块分类管理器
     */
    public BlockPreloadManager(BlockCategoryManager categoryManager) {
        this.categoryManager = categoryManager;
        
        try {
            // 初始化预加载状态
            for (BlockCategoryManager.BlockCategory category : BlockCategoryManager.BlockCategory.values()) {
                Map<BlockCategoryManager.BlockCategory, List<Block>> categorizedBlocks = categoryManager.getCategorizedBlocks();
                List<Block> blocks = categorizedBlocks.getOrDefault(category, Collections.emptyList());
                preloadStatus.put(category, new PreloadStatus(
                    CompletableFuture.completedFuture(null),
                    blocks.size(),
                    0
                ));
            }
            
            // [REMOVED] 移除这里的BlockIconRenderer.initialize()调用
            // 让CompactBlockConfigDialog在open()方法中统一初始化，避免重复初始化导致的问题
            LOGGER.debug("BlockPreloadManager 构造函数完成，预加载状态已初始化");
            
        } catch (Exception e) {
            LOGGER.error("BlockPreloadManager 构造函数失败: {}", e.getMessage(), e);
            throw new RuntimeException(PlotI18n.error("error.plot.init.block_preload_failed"), e);
        }
    }
    
    /**
     * 预加载指定分类的方块图标 - 简化版本
     * 直接将预加载请求提交给 BlockIconRenderer 的队列
     * @param category 要预加载的方块分类
     */
    public void preloadCategoryIcons(BlockCategoryManager.BlockCategory category) {
        // 获取当前分类的方块
        List<Block> blocks = categoryManager.getCategorizedBlocks().get(category);
        int totalBlocks = blocks.size();
        
        // 如果已经在预加载中或已完成，直接返回
        if (preloadStatus.containsKey(category) && 
            (!preloadStatus.get(category).isComplete() || 
             preloadStatus.get(category).getLoadedCount() > 0)) {
            return;
        }
        
        // 针对杂项分类特殊处理：严格限制预加载数量
        int preloadLimit;
        if (category == BlockCategoryManager.BlockCategory.MISCELLANEOUS) {
            // 杂项分类只预加载前20个方块，避免性能问题
            preloadLimit = Math.min(20, totalBlocks);
            LOGGER.debug("杂项分类特殊处理：限制预加载数量为 {}", preloadLimit);
        } else {
            // 其他分类正常计算预加载数量
            preloadLimit = Math.min(
                DEFAULT_PRELOAD_LIMIT,
                Math.max(10, (int)(totalBlocks * PRELOAD_PERCENTAGE))
            );
        }
        
        // 直接将预加载请求提交给 BlockIconRenderer 的队列
        List<Block> blocksToPreload = blocks.subList(0, Math.min(preloadLimit, blocks.size()));
        
        LOGGER.debug("开始预加载分类 {} 的方块图标，数量: {}", 
            category.getDisplayName(), blocksToPreload.size());

        BlockIconRenderer.getInstance().preload(blocksToPreload);

        // 由于 BlockIconRenderer 自己管理队列，PreloadManager 不再需要复杂的 CompletableFuture 和状态跟踪
        // 我们可以简化 PreloadStatus，或者完全移除它，因为加载状态现在由 BlockIconRenderer 内部处理
        // 为了保持UI上的进度条，我们可以保留一个简化的状态
        PreloadStatus status = new PreloadStatus(CompletableFuture.completedFuture(null), totalBlocks, preloadLimit);
        // 这里可以模拟一个假的加载完成，因为任务已经移交
        // 更好的方式是改造BlockIconRenderer以提供队列状态
        preloadStatus.put(category, status);
    }
} 