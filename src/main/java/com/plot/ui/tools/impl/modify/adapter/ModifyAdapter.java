package com.plot.ui.tools.impl.modify.adapter;

import com.plot.core.command.commands.ModifyCommand;
import com.plot.core.model.Shape;
import com.plot.ui.tools.impl.modify.helper.IModifyHandler;
import com.plot.ui.tools.impl.modify.constants.ModifyConstraints;
import com.plot.ui.tools.impl.modify.dto.ModifyParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 修改工具适配器
 * 
 * <p>为修改工具提供统一的适配层，包括：</p>
 * <ul>
 *   <li>处理器注册和管理</li>
 *   <li>修改操作的统一接口</li>
 *   <li>错误处理和恢复</li>
 *   <li>性能监控和优化</li>
 * </ul>
 * 
 * <p>这个适配器类似于绘制工具的DrawingAdapter，
 * 为修改工具提供了一个统一的操作接口。</p>
 * 
 * @author Plot Team
 * @version 1.0 - 修改适配器
 */
public class ModifyAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModifyAdapter.class);
    
    // 处理器注册表
    private final Map<IModifyHandler.ModifyType, IModifyHandler> handlers = new ConcurrentHashMap<>();
    
    // 性能统计
    private final Map<IModifyHandler.ModifyType, Long> operationCounts = new ConcurrentHashMap<>();
    private final Map<IModifyHandler.ModifyType, Long> totalExecutionTime = new ConcurrentHashMap<>();
    
    // 单例实例
    private static volatile ModifyAdapter instance;
    
    /**
     * 获取单例实例
     */
    public static ModifyAdapter getInstance() {
        if (instance == null) {
            synchronized (ModifyAdapter.class) {
                if (instance == null) {
                    instance = new ModifyAdapter();
                }
            }
        }
        return instance;
    }
    
    /**
     * 私有构造函数
     */
    private ModifyAdapter() {
        LOGGER.debug("ModifyAdapter 初始化");
        
        // 注册默认的修改处理器
        registerDefaultHandlers();
    }
    
    /**
     * 注册默认的修改处理器
     */
    private void registerDefaultHandlers() {
        try {
            // 注册打断处理器
            registerHandler(new com.plot.ui.tools.impl.modify.helper.BreakHandler());
            
            LOGGER.info("ModifyAdapter 默认处理器注册完成，共注册 {} 个处理器", handlers.size());
            
        } catch (Exception e) {
            LOGGER.error("注册默认处理器时发生错误: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 注册修改处理器
     * @param handler 修改处理器
     */
    public void registerHandler(IModifyHandler handler) {
        if (handler != null) {
            IModifyHandler.ModifyType type = handler.getModifyType();
            handlers.put(type, handler);
            operationCounts.put(type, 0L);
            totalExecutionTime.put(type, 0L);
            LOGGER.debug("注册修改处理器: {}", type.getDisplayName());
        }
    }

    
    /**
     * 获取修改处理器
     * @param type 修改类型
     * @return 修改处理器，如果未注册则返回null
     */
    public IModifyHandler getHandler(IModifyHandler.ModifyType type) {
        return handlers.get(type);
    }

    /**
     * 执行修改操作
     * @param type 修改类型
     * @param shapes 要修改的图形列表
     * @param parameters 修改参数
     * @return 修改结果
     */
    public IModifyHandler.ModifyResult performModification(IModifyHandler.ModifyType type, 
                                                          List<Shape> shapes, 
                                                          ModifyParameters parameters) {
        return performModification(type, shapes, parameters, null);
    }
    
    /**
     * 执行修改操作（带约束）
     * @param type 修改类型
     * @param shapes 要修改的图形列表
     * @param parameters 修改参数
     * @param constraints 修改约束
     * @return 修改结果
     */
    public IModifyHandler.ModifyResult performModification(IModifyHandler.ModifyType type, 
                                                          List<Shape> shapes, 
                                                          ModifyParameters parameters,
                                                          ModifyConstraints constraints) {
        long startTime = System.nanoTime();
        
        try {
            // 获取处理器
            IModifyHandler handler = getHandler(type);
            if (handler == null) {
                return IModifyHandler.ModifyResult.failure("不支持的修改类型: " + type.getDisplayName());
            }
            
            // 应用约束
            IModifyHandler.ModifyParameters finalParameters = parameters;
            if (constraints != null) {
                finalParameters = handler.applyConstraints(parameters, constraints);
            }
            
            // 执行修改操作
            IModifyHandler.ModifyResult result = handler.performModification(shapes, finalParameters);
            
            // 更新统计信息
            updateStatistics(type, startTime);
            
            return result;
            
        } catch (Exception e) {
            LOGGER.error("修改操作失败: type={}, error={}", type.getDisplayName(), e.getMessage(), e);
            return IModifyHandler.ModifyResult.failure("修改操作异常: " + e.getMessage());
        }
    }
    
    /**
     * 验证修改操作
     * @param type 修改类型
     * @param shapes 要修改的图形列表
     * @param parameters 修改参数
     * @return 验证结果
     */
    public IModifyHandler.ValidationResult validateModification(IModifyHandler.ModifyType type, 
                                                               List<Shape> shapes, 
                                                               ModifyParameters parameters) {
        try {
            IModifyHandler handler = getHandler(type);
            if (handler == null) {
                return IModifyHandler.ValidationResult.invalid("不支持的修改类型: " + type.getDisplayName());
            }
            
            return handler.validateModification(shapes, parameters);
            
        } catch (Exception e) {
            LOGGER.error("修改验证失败: type={}, error={}", type.getDisplayName(), e.getMessage(), e);
            return IModifyHandler.ValidationResult.invalid("验证异常: " + e.getMessage());
        }
    }
    
    /**
     * 创建预览图形
     * @param type 修改类型
     * @param shapes 原始图形列表
     * @param parameters 修改参数
     * @return 预览图形列表
     */
    public List<Shape> createPreviewShapes(IModifyHandler.ModifyType type, 
                                          List<Shape> shapes, 
                                          ModifyParameters parameters) {
        return createPreviewShapes(type, shapes, parameters, null);
    }
    
    /**
     * 创建预览图形（带约束）
     * @param type 修改类型
     * @param shapes 原始图形列表
     * @param parameters 修改参数
     * @param constraints 修改约束
     * @return 预览图形列表
     */
    public List<Shape> createPreviewShapes(IModifyHandler.ModifyType type, 
                                          List<Shape> shapes, 
                                          ModifyParameters parameters,
                                          ModifyConstraints constraints) {
        try {
            IModifyHandler handler = getHandler(type);
            if (handler == null) {
                LOGGER.warn("无法创建预览图形，不支持的修改类型: {}", type.getDisplayName());
                return List.of();
            }
            
            // 应用约束
            IModifyHandler.ModifyParameters finalParameters = parameters;
            if (constraints != null) {
                finalParameters = handler.applyConstraints(parameters, constraints);
            }
            
            return handler.createPreviewShapes(shapes, finalParameters);
            
        } catch (Exception e) {
            LOGGER.error("创建预览图形失败: type={}, error={}", type.getDisplayName(), e.getMessage(), e);
            return List.of();
        }
    }
    
    /**
     * 创建修改命令
     * @param type 修改类型
     * @param originalShapes 原始图形列表
     * @param modifiedShapes 修改后的图形列表
     * @param parameters 修改参数
     * @return 修改命令
     */
    public ModifyCommand createModifyCommand(IModifyHandler.ModifyType type,
                                           List<Shape> originalShapes,
                                           List<Shape> modifiedShapes,
                                           ModifyParameters parameters) {
        try {
            IModifyHandler handler = getHandler(type);
            if (handler == null) {
                LOGGER.warn("无法创建修改命令，不支持的修改类型: {}", type.getDisplayName());
                return null;
            }
            
            return handler.createModifyCommand(originalShapes, modifiedShapes, parameters);
            
        } catch (Exception e) {
            LOGGER.error("创建修改命令失败: type={}, error={}", type.getDisplayName(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * 清理资源
     */
    public void cleanup() {
        handlers.clear();
        operationCounts.clear();
        totalExecutionTime.clear();
        LOGGER.debug("修改适配器资源已清理");
    }
    
    /**
     * 更新统计信息
     */
    private void updateStatistics(IModifyHandler.ModifyType type, long startTime) {
        long executionTime = System.nanoTime() - startTime;
        operationCounts.merge(type, 1L, Long::sum);
        totalExecutionTime.merge(type, executionTime, Long::sum);
    }

    /**
     * 检查适配器是否为空
     * @return 是否为空
     */
    public boolean isEmpty() {
        return handlers.isEmpty();
    }
}