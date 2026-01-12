package com.masterplanner.ui.tools.impl.modify;

import com.masterplanner.api.snap.ISnapManager;
import com.masterplanner.core.state.AppState;
import com.masterplanner.ui.component.Icons;
import com.masterplanner.ui.tools.impl.modify.strategy.ChamferStrategy;
import com.masterplanner.ui.tools.impl.modify.strategy.IModifyStrategy;
import com.masterplanner.core.graphics.DrawContext;
import com.masterplanner.infrastructure.event.EventBus;
import com.masterplanner.infrastructure.event.EventListener;
import com.masterplanner.infrastructure.event.base.Event;
import com.masterplanner.infrastructure.event.tool.ToolConfigEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * 倒角工具 - 优化版本
 * 
 * <p>使用策略模式重构后的版本，支持在两条直线之间创建斜面倒角。
 * 采用依赖注入架构，提供完整的生命周期管理和健壮的错误处理。</p>
 * 
 * <p><strong>主要特性：</strong></p>
 * <ul>
 *   <li>强制依赖注入，确保组件可用性</li>
 *   <li>完整的事件生命周期管理</li>
 *   <li>通过接口进行配置交互，降低耦合</li>
 *   <li>封装内部实现细节</li>
 *   <li>优化的日志记录和错误处理</li>
 * </ul>
 * 
 * @author MasterPlanner Team
 * @version 2.1 - 优化版本
 */
public class ChamferTool extends ModifyTool implements EventListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChamferTool.class);
    
    // 配置键常量
    public static final String CONFIG_KEY_DISTANCE = "distance";
    public static final String CONFIG_KEY_PREVIEW = "preview";
    public static final String CONFIG_KEY_HIGHLIGHT = "highlight";
    
    // 距离参数常量 - 优化：声明为public static final，便于外部访问
    public static final double DEFAULT_DISTANCE = 5.0;
    public static final double MIN_DISTANCE = 0.5;
    public static final double MAX_DISTANCE = 50.0;
    
    // 事件订阅状态
    private boolean isEventSubscribed = false;
    
    /**
     * 依赖注入构造函数（推荐方式）
     * 
     * @param appState 应用状态管理器，不能为空
     * @param snapManager 吸附管理器，不能为空
     * @throws IllegalArgumentException 如果参数为空
     */
    public ChamferTool(AppState appState, ISnapManager snapManager) {
        super("chamfer", "倒角", Icons.CHAMFER_IDENTIFIER, "创建斜面倒角", 
              Objects.requireNonNull(appState, "AppState 不能为空"), 
              Objects.requireNonNull(snapManager, "ISnapManager 不能为空"));
        
        LOGGER.info("ChamferTool 已创建（依赖注入模式）");
        
        // 初始化事件订阅（在构造函数中订阅，在deactivate时取消）
        subscribeToEvents();
    }
    
    /**
     * 构造函数（兼容方式 - 单例获取）
     * 
     * @deprecated 此构造函数将在下一版本移除，请使用依赖注入构造函数
     * @throws UnsupportedOperationException 如果尝试使用此构造函数
     */
    @Deprecated
    public ChamferTool() {
        super("chamfer", "倒角", Icons.CHAMFER_IDENTIFIER, "创建斜面倒角");
        
        LOGGER.warn("ChamferTool 使用已弃用的无参构造函数，将在下一版本移除");
        throw new UnsupportedOperationException(
            "此构造函数已弃用，请使用 ChamferTool(AppState, ISnapManager) 构造函数");
    }
    
    @Override
    protected IModifyStrategy createStrategy() {
        // 优化：简化策略创建逻辑，直接使用父类方法
        com.masterplanner.api.state.IAppState iAppState = getAppState();
        if (iAppState == null) {
            LOGGER.error("AppState 不可用，无法创建 ChamferStrategy");
            return null;
        }
        
        // 类型转换：从接口转换为具体实现
        if (!(iAppState instanceof AppState appState)) {
            LOGGER.error("AppState 类型不匹配，期望 AppState 但得到 {}", iAppState.getClass().getSimpleName());
            return null;
        }

        try {
            ChamferStrategy strategy = new ChamferStrategy(appState);
            LOGGER.debug("ChamferStrategy 创建成功");
            return strategy;
        } catch (Exception e) {
            LOGGER.error("创建 ChamferStrategy 失败", e);
            return null;
        }
    }
    
    @Override
    public void onEvent(Event event) {
        if (!(event instanceof ToolConfigEvent configEvent)) {
            return;
        }
        
        // 优化：简化事件处理，保留工具ID检查以确保精确匹配
        if (!"chamfer".equals(configEvent.getToolId())) {
            return;
        }
        
        LOGGER.debug("ChamferTool 收到配置事件: {} = {}", 
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
        
        // 优化：简化策略获取，直接使用父类方法
        IModifyStrategy strategy = getStrategy();
        if (strategy == null) {
            LOGGER.warn("策略不可用，无法更新配置: {}", key);
            return;
        }
        
        try {
            // 直接调用ChamferStrategy的updateConfig方法
            if (strategy instanceof ChamferStrategy chamferStrategy) {
                chamferStrategy.updateConfig(key, parseConfigValue(key, value));
                LOGGER.debug("配置已更新: {} = {}", key, value);
            } else {
                LOGGER.warn("策略不是ChamferStrategy类型，无法更新配置: {}", key);
            }
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
        
        // 优化：简化策略获取
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
        return "选择要倒角的第一条边";
    }
    
    // ====== 生命周期管理 ======
    
    @Override
    public void onActivate() {
        super.onActivate();
        subscribeToEvents();
        LOGGER.debug("ChamferTool 已激活");
    }
    
    @Override
    public void onDeactivate() {
        super.onDeactivate();
        unsubscribeFromEvents();
        LOGGER.debug("ChamferTool 已停用");
    }
    
    // ====== 事件管理 ======
    
    /**
     * 订阅事件
     */
    private void subscribeToEvents() {
        if (!isEventSubscribed) {
            EventBus.getInstance().subscribe(ToolConfigEvent.class, this);
            isEventSubscribed = true;
            LOGGER.debug("ChamferTool 已订阅 ToolConfigEvent");
        }
    }
    
    /**
     * 取消订阅事件
     */
    private void unsubscribeFromEvents() {
        if (isEventSubscribed) {
            EventBus.getInstance().unsubscribe(ToolConfigEvent.class, this);
            isEventSubscribed = false;
            LOGGER.debug("ChamferTool 已取消订阅 ToolConfigEvent");
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
            case CONFIG_KEY_DISTANCE -> parseDistance(value);
            case CONFIG_KEY_PREVIEW, CONFIG_KEY_HIGHLIGHT -> Boolean.parseBoolean(value);
            default -> {
                LOGGER.debug("未知的配置项: {} = {}", key, value);
                yield value; // 返回原始字符串
            }
        };
    }
    
    private double parseDistance(String value) {
        try {
            double distance = Double.parseDouble(value);
            if (distance < MIN_DISTANCE || distance > MAX_DISTANCE) {
                throw new IllegalArgumentException("距离值超出范围 [" + MIN_DISTANCE + ", " + MAX_DISTANCE + "]: " + distance);
            }
            return distance;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("无效的距离值: " + value);
        }
    }
    
    // ====== 公共API方法（封装内部实现） ======
    
    /**
     * 设置倒角距离
     * 
     * @param distance 距离值，必须在 [MIN_DISTANCE, MAX_DISTANCE] 范围内
     * @throws IllegalArgumentException 如果距离值无效
     */
    public void setDistance(double distance) {
        updateConfig(CONFIG_KEY_DISTANCE, String.valueOf(distance));
    }
    
    /**
     * 获取当前倒角距离
     * 
     * @return 当前距离值
     */
    public double getDistance() {
        IModifyStrategy strategy = getStrategy();
        if (strategy instanceof ChamferStrategy chamferStrategy) {
            return chamferStrategy.getDistance();
        }
        return DEFAULT_DISTANCE;
    }
    
    /**
     * 设置预览开关
     * 
     * @param enabled 是否启用预览
     */
    public void setPreviewEnabled(boolean enabled) {
        updateConfig(CONFIG_KEY_PREVIEW, String.valueOf(enabled));
    }

    /**
     * 获取当前状态消息
     * 
     * @return 当前状态消息
     */
    public String getStatusMessage() {
        IModifyStrategy strategy = getStrategy();
        if (strategy instanceof ChamferStrategy chamferStrategy) {
            return chamferStrategy.getStatusMessage();
        }
        return getInitialStatusMessage();
    }
}
