package com.plot.ui.tools.impl.modify;

import com.plot.api.snap.ISnapManager;
import com.plot.api.state.IAppState;
import com.plot.ui.component.Icons;
import com.plot.ui.tools.impl.modify.constants.FilletConstants;
import com.plot.ui.tools.impl.modify.strategy.FilletStrategy;
import com.plot.ui.tools.impl.modify.strategy.IModifyStrategy;
import com.plot.core.graphics.DrawContext;
import com.plot.infrastructure.event.EventBus;
import com.plot.infrastructure.event.EventListener;
import com.plot.infrastructure.event.base.Event;
import com.plot.infrastructure.event.tool.ToolConfigEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * 圆角工具 - 完全解耦版本
 * 
 * <p>使用策略模式重构后的版本，支持在两条直线之间创建圆角。
 * 采用依赖注入架构，提供完整的生命周期管理和健壮的错误处理。</p>
 * 
 * <p><strong>主要特性：</strong></p>
 * <ul>
 *   <li>强制依赖注入，确保组件可用性</li>
 *   <li>完整的事件生命周期管理</li>
 *   <li>通过接口进行配置交互，完全解耦</li>
 *   <li>封装内部实现细节</li>
 *   <li>优化的日志记录和错误处理</li>
 *   <li>策略模式完全实现，无具体策略依赖</li>
 * </ul>
 * 
 * @author Plot Team
 * @version 4.0 - 优化版本，清理已弃用构造函数
 */
public class FilletTool extends ModifyTool implements EventListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(FilletTool.class);
    
    // 事件订阅状态
    private boolean isEventSubscribed = false;
    
    /**
     * 依赖注入构造函数（推荐方式）
     * 
     * @param appState 应用状态管理器，不能为空
     * @param snapManager 吸附管理器，不能为空
     * @throws IllegalArgumentException 如果参数为空
     */
    public FilletTool(IAppState appState, ISnapManager snapManager) {
        super("fillet", "圆角", Icons.FILLET_IDENTIFIER, "创建圆角", 
              Objects.requireNonNull(appState, "AppState 不能为空"), 
              Objects.requireNonNull(snapManager, "ISnapManager 不能为空"));
        
        LOGGER.info("FilletTool 已创建（依赖注入模式）");
        
        // 初始化事件订阅（在构造函数中订阅，在deactivate时取消）
        subscribeToEvents();
    }
    
    @Override
    protected IModifyStrategy createStrategy() {
        try {
            FilletStrategy strategy = new FilletStrategy(concreteAppState);
            LOGGER.debug("FilletStrategy 创建成功");
            return strategy;
        } catch (Exception e) {
            LOGGER.error("创建 FilletStrategy 失败", e);
            return null;
        }
    }
    
    @Override
    public void onEvent(Event event) {
        if (!(event instanceof ToolConfigEvent configEvent)) {
            return;
        }
        
        if (!"fillet".equals(configEvent.getToolId())) {
            return;
        }
        
        LOGGER.debug("FilletTool 收到配置事件: {} = {}", 
                    configEvent.getOptionName(), configEvent.getValue());
        
        try {
            updateConfig(configEvent.getOptionName(), String.valueOf(configEvent.getValue()));
        } catch (Exception e) {
            LOGGER.error("处理配置事件失败: {} = {}", 
                        configEvent.getOptionName(), configEvent.getValue(), e);
        }
    }
    
    @Override
    public void updateConfig(String key, String value) {
        if (key == null || value == null) {
            LOGGER.warn("配置键或值为空: key={}, value={}", key, value);
            return;
        }
        
        IModifyStrategy strategy = getStrategy();
        if (strategy == null) {
            LOGGER.warn("策略不可用，无法更新配置: {}", key);
            return;
        }
        
        try {
            // 通过策略接口更新配置，实现解耦
            Object parsedValue = parseConfigValue(key, value);
            strategy.updateConfig(key, parsedValue);
            LOGGER.debug("通过接口更新配置: {} = {} (策略类型: {})", key, value, strategy.getClass().getSimpleName());
        } catch (Exception e) {
            LOGGER.error("更新配置失败: {} = {}", key, value, e);
        }
    }
    
    @Override
    protected void renderPreview(DrawContext context) {
        if (context == null) {
            LOGGER.warn("DrawContext 为空，跳过预览渲染");
            return;
        }
        
        IModifyStrategy strategy = getStrategy();
        if (strategy != null) {
            try {
                strategy.renderPreview(context);
            } catch (Exception e) {
                LOGGER.error("预览渲染失败", e);
            }
        }
    }
    
    @Override
    protected String getInitialStatusMessage() {
        return "status.plot.fillet.initial";
    }
    
    // ====== 生命周期管理 ======
    
    @Override
    public void onActivate() {
        super.onActivate();
        subscribeToEvents();
        LOGGER.debug("FilletTool 已激活");
    }
    
    @Override
    public void onDeactivate() {
        super.onDeactivate();
        unsubscribeFromEvents();
        LOGGER.debug("FilletTool 已停用");
    }
    
    // ====== 事件管理 ======
    
    /**
     * 订阅事件
     */
    private void subscribeToEvents() {
        if (!isEventSubscribed) {
            eventBus.subscribe(ToolConfigEvent.class, this);
            isEventSubscribed = true;
            LOGGER.debug("FilletTool 已订阅 ToolConfigEvent");
        }
    }
    
    /**
     * 取消订阅事件
     */
    private void unsubscribeFromEvents() {
        if (isEventSubscribed) {
            eventBus.unsubscribe(ToolConfigEvent.class, this);
            isEventSubscribed = false;
            LOGGER.debug("FilletTool 已取消订阅 ToolConfigEvent");
        }
    }
    
    // ====== 配置解析 ======
    
    /**
     * 解析配置值
     * 
     * @param key 配置键
     * @param value 配置值字符串
     * @return 解析后的值对象
     * @throws IllegalArgumentException 如果解析失败
     */
    private Object parseConfigValue(String key, String value) {
        return switch (key) {
            case FilletConstants.CONFIG_KEY_RADIUS -> parseRadius(value);
            case FilletConstants.CONFIG_KEY_PREVIEW, FilletConstants.CONFIG_KEY_HIGHLIGHT -> Boolean.parseBoolean(value);
            default -> {
                LOGGER.debug("未知的配置项: {} = {}", key, value);
                yield value; // 返回原始字符串
            }
        };
    }
    
    private double parseRadius(String value) {
        try {
            double radius = Double.parseDouble(value);
            if (radius < FilletConstants.MIN_RADIUS || radius > FilletConstants.MAX_RADIUS) {
                throw new IllegalArgumentException("半径值超出范围 [" + FilletConstants.MIN_RADIUS + ", " + FilletConstants.MAX_RADIUS + "]: " + radius);
            }
            return radius;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("无效的半径值: " + value);
        }
    }
    
    // ====== 公共API方法（封装内部实现） ======
    
    /**
     * 设置圆角半径
     * 
     * @param radius 半径值，必须在 [MIN_RADIUS, MAX_RADIUS] 范围内
     * @throws IllegalArgumentException 如果半径值无效
     */
    public void setRadius(double radius) {
        updateConfig(FilletConstants.CONFIG_KEY_RADIUS, String.valueOf(radius));
    }
    
    /**
     * 获取当前圆角半径
     * 
     * @return 当前半径值
     */
    public double getRadius() {
        IModifyStrategy strategy = getStrategy();
        if (strategy != null) {
            Object value = strategy.getParameter(FilletConstants.CONFIG_KEY_RADIUS);
            if (value instanceof Number) {
                double radius = ((Number) value).doubleValue();
                LOGGER.debug("通过接口获取半径值: {}", radius);
                return radius;
            }
        }
        LOGGER.debug("使用默认半径值: {}", FilletConstants.DEFAULT_RADIUS);
        return FilletConstants.DEFAULT_RADIUS;
    }
    
    /**
     * 设置预览开关
     * 
     * @param enabled 是否启用预览
     */
    public void setPreviewEnabled(boolean enabled) {
        updateConfig(FilletConstants.CONFIG_KEY_PREVIEW, String.valueOf(enabled));
    }

    /**
     * 获取当前状态消息
     * 
     * @return 当前状态消息
     */
    public String getStatusMessage() {
        IModifyStrategy strategy = getStrategy();
        if (strategy != null) {
            // 直接调用接口方法，无需类型检查
            String statusMessage = strategy.getStatusMessage();
            LOGGER.debug("通过接口获取状态消息: {}", statusMessage);
            return statusMessage;
        }
        String defaultMessage = getInitialStatusMessage();
        LOGGER.debug("使用默认状态消息: {}", defaultMessage);
        return defaultMessage;
    }
    
    // ====== 工具方法 ======
    

    

} 