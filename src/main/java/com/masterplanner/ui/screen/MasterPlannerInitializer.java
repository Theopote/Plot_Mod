package com.masterplanner.ui.screen;

import com.masterplanner.ui.imgui.ImGuiRenderer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MasterPlanner 初始化器 - 线程安全版本
 * 
 * 主要优化：
 * 1. 严格的线程检查和调度机制
 * 2. 防止多重初始化和竞态条件
 * 3. 完善的错误处理和恢复机制
 * 4. 智能的初始化重试逻辑
 */
public class MasterPlannerInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("MasterPlanner/MasterPlannerInitializer");
    
    // 初始化状态管理
    private static volatile boolean initialized = false;
    private static volatile boolean initializing = false;
    private static final Object INIT_LOCK = new Object();
    
    // 初始化重试机制
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 100;
    private static int retryCount = 0;
    
    // 线程检查
    private static volatile Thread initializationThread = null;

    /**
     * 主初始化方法 - 线程安全版本
     */
    public static void initialize() {
        LOGGER.debug("MasterPlannerInitializer.initialize() 被调用 (initialized: {}, initializing: {})", 
            initialized, initializing);
            
        // 快速检查：如果已经初始化完成，直接返回
        if (initialized) {
            LOGGER.debug("已初始化完成，跳过重复初始化");
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            LOGGER.error("无法初始化：MinecraftClient为null");
            return;
        }

        // 线程检查和调度
        if (!client.isOnThread()) {
            LOGGER.info("当前不在主线程 [{}]，调度到主线程执行", Thread.currentThread().getName());
            scheduleMainThreadInitialization(client);
            return;
        }

        // 在主线程中执行实际的初始化
        performInitialization(client);
    }
    
    /**
     * 调度到主线程执行初始化
     */
    private static void scheduleMainThreadInitialization(MinecraftClient client) {
        synchronized (INIT_LOCK) {
            // 防止多重调度
            if (initializing || initialized) {
                LOGGER.debug("初始化已在进行中或已完成，跳过调度");
                return;
            }
            
            initializing = true;
            initializationThread = Thread.currentThread();
        }
        
        try {
            // 调度到主线程
            client.execute(() -> {
                try {
                    LOGGER.debug("在主线程 [{}] 中执行延迟初始化", Thread.currentThread().getName());
                    performInitialization(client);
                } catch (Exception e) {
                    LOGGER.error("主线程初始化执行失败", e);
                    resetInitializationState();
                    
                    // 考虑重试
                    scheduleRetryIfNeeded(client);
                }
            });
        } catch (Exception e) {
            LOGGER.error("调度到主线程失败", e);
            resetInitializationState();
        }
    }
    
    /**
     * 执行实际的初始化逻辑
     */
    private static void performInitialization(MinecraftClient client) {
        synchronized (INIT_LOCK) {
            // 双重检查：防止竞态条件
            if (initialized) {
                LOGGER.debug("在同步块中检测到已初始化，跳过执行");
                return;
            }
            
            if (initializing && !Thread.currentThread().equals(initializationThread)) {
                LOGGER.debug("其他线程正在初始化，当前线程退出");
                return;
            }
            
            initializing = true;
            initializationThread = Thread.currentThread();
        }

        try {
            // 验证线程状态
            if (!client.isOnThread()) {
                throw new IllegalStateException("初始化必须在主线程中执行，当前线程: " + Thread.currentThread().getName());
            }
            
            LOGGER.info("开始在主线程 [{}] 中初始化ImGui渲染器...", Thread.currentThread().getName());
            
            // 获取ImGui渲染器实例
            ImGuiRenderer renderer = ImGuiRenderer.getInstance();
            LOGGER.debug("获取到ImGui渲染器实例: {}", renderer.getClass().getSimpleName());
            
            // 执行初始化
            renderer.init();
            
            // 标记初始化完成
            synchronized (INIT_LOCK) {
                initialized = true;
                initializing = false;
                initializationThread = null;
                retryCount = 0; // 重置重试计数
            }
            
            LOGGER.info("ImGui渲染器初始化成功 [线程: {}]", Thread.currentThread().getName());
            
            // 注册清理事件
            registerCleanupEvents(client);
            
        } catch (Exception e) {
            LOGGER.error("ImGui渲染器初始化失败 [线程: {}]", Thread.currentThread().getName(), e);
            resetInitializationState();
            
            // 考虑重试
            scheduleRetryIfNeeded(client);
            
            throw new RuntimeException("初始化失败", e);
        }
    }
    
    /**
     * 重置初始化状态
     */
    private static void resetInitializationState() {
        synchronized (INIT_LOCK) {
            initializing = false;
            initializationThread = null;
        }
    }
    
    /**
     * 如果需要，安排重试初始化
     */
    private static void scheduleRetryIfNeeded(MinecraftClient client) {
        synchronized (INIT_LOCK) {
            if (retryCount < MAX_RETRY_ATTEMPTS) {
                retryCount++;
                LOGGER.warn("初始化失败，将在 {}ms 后进行第 {} 次重试 (最多 {} 次)", 
                    RETRY_DELAY_MS, retryCount, MAX_RETRY_ATTEMPTS);
                
                // 延迟重试
                new Thread(() -> {
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                        initialize();
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        LOGGER.warn("重试初始化被中断");
                    } catch (Exception e) {
                        LOGGER.error("重试初始化失败", e);
                    }
                }, "MasterPlanner-Init-Retry-" + retryCount).start();
            } else {
                LOGGER.error("已达到最大重试次数 ({}), 放弃初始化", MAX_RETRY_ATTEMPTS);
            }
        }
    }
    
    /**
     * 注册客户端清理事件
     */
    private static void registerCleanupEvents(MinecraftClient client) {
        ClientLifecycleEvents.CLIENT_STOPPING.register(clientInstance -> {
            try {
                LOGGER.info("Minecraft客户端停止，清理ImGui资源...");
                
                // 重置状态
                synchronized (INIT_LOCK) {
                    if (initialized) {
                        ImGuiRenderer.getInstance().dispose();
                        initialized = false;
                        initializing = false;
                        initializationThread = null;
                        retryCount = 0;
                    }
                }
                
                LOGGER.info("ImGui资源清理完成");
            } catch (Exception e) {
                LOGGER.error("ImGui资源清理失败", e);
            }
        });
    }

    /**
     * 检查是否已初始化
     */
    public static boolean isInitialized() {
        return initialized;
    }
}