package com.plot.ui.tools.impl.modify;

import com.plot.api.geometry.Vec2d;
import com.plot.api.snap.ISnapManager;
import com.plot.core.graphics.DrawContext;
import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.ui.component.Icons;
import com.plot.ui.tools.impl.modify.strategy.BreakStrategy;
import com.plot.ui.tools.impl.modify.strategy.IModifyStrategy;
import com.plot.infrastructure.event.EventBus;
import com.plot.infrastructure.event.tool.ToolConfigEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 打断工具 - 策略模式版本
 * 
 * <p>用于在指定点将对象分割成两部分，采用策略模式架构：</p>
 * <ul>
 *   <li>支持单点打断和两点打断模式</li>
 *   <li>支持连续操作模式</li>
 *   <li>实时预览打断效果</li>
 *   <li>完全兼容撤销/重做系统</li>
 * </ul>
 * 
 * @author Plot Team
 * @version 2.0 - 策略模式版本
 */
public class BreakTool extends ModifyTool {
    private static final Logger LOGGER = LoggerFactory.getLogger(BreakTool.class);
    
    private BreakStrategy breakStrategy;
    private boolean eventSubscribed = false;
    
    /**
     * 依赖注入构造函数（推荐）
     * 
     * @param appState 应用状态管理器
     * @param snapManager 吸附管理器
     */
    public BreakTool(AppState appState, ISnapManager snapManager) {
        super("break",
              "打断",
              Icons.BREAK_IDENTIFIER,
              "在指定点将对象分割成两部分",
              appState,
              snapManager);
        this.breakStrategy = new BreakStrategy();
    }
    
    /**
     * 兼容性构造函数（已弃用）
     * 
     * @deprecated 请使用依赖注入构造函数 {@link #BreakTool(AppState, ISnapManager)}
     */
    @Deprecated
    public BreakTool() {
        super("break",
              "打断",
              Icons.BREAK_IDENTIFIER,
              "在指定点将对象分割成两部分");
        this.breakStrategy = new BreakStrategy();
    }
    
    @Override
    protected IModifyStrategy createStrategy() {
        if (breakStrategy == null) {
            breakStrategy = new BreakStrategy();
        }
        return breakStrategy;
    }
    
    /**
     * 处理工具配置事件
     */
    private void handleToolConfigEvent(Object event) {
        if (event instanceof ToolConfigEvent configEvent) {
            if ("break".equals(configEvent.getToolId())) {
                LOGGER.debug("BreakTool 收到配置事件: {} = {}", 
                    configEvent.getOptionName(), configEvent.getValue());
                updateConfig(configEvent.getOptionName(), String.valueOf(configEvent.getValue()));
            }
        }
    }
    
    @Override
    public void updateConfig(String key, String value) {
        // 处理打断工具的配置更新
        LOGGER.debug("BreakTool收到配置更新: key={}, value={}", key, value);
        if (key.equals("mode")) {
            if ("single".equalsIgnoreCase(value)) {
                LOGGER.debug("切换到单点模式");
                setBreakMode(BreakStrategy.BreakMode.SINGLE_POINT);
            } else if ("two_point".equalsIgnoreCase(value)) {
                LOGGER.debug("切换到两点模式");
                setBreakMode(BreakStrategy.BreakMode.TWO_POINT);
            } else {
                LOGGER.warn("未知的模式值: {}", value);
            }
        } else {
            LOGGER.debug("未知的配置键: {}", key);
        }
    }
    
    @Override
    public void onActivate() {
        super.onActivate();
        
        // 在激活时订阅事件
        if (!eventSubscribed) {
            EventBus.getInstance().subscribe(ToolConfigEvent.class, this::handleToolConfigEvent);
            eventSubscribed = true;
            LOGGER.debug("BreakTool 已订阅 ToolConfigEvent");
        }
        
        LOGGER.debug("打断工具已激活");
        updateStatusMessage("选择要打断的对象，按T键切换模式");
    }

    @Override
    public void onDeactivate() {
        LOGGER.debug("打断工具已停用");
        
        // 在停用时取消订阅事件
        if (eventSubscribed) {
            EventBus.getInstance().unsubscribe(ToolConfigEvent.class, this::handleToolConfigEvent);
            eventSubscribed = false;
            LOGGER.debug("BreakTool 已取消订阅 ToolConfigEvent");
        }
        
        super.onDeactivate();
    }
    
    /**
     * 设置打断模式
     * 
     * @param mode 打断模式
     */
    public void setBreakMode(BreakStrategy.BreakMode mode) {
        if (breakStrategy != null) {
            breakStrategy.setBreakMode(mode);
            String message = String.format("打断模式已切换为: %s", mode.getDisplayName());
            updateStatusMessage(message);
            LOGGER.debug(message);
        }
    }

    /**
     * 获取当前打断模式
     * 
     * @return 当前打断模式
     */
    public BreakStrategy.BreakMode getCurrentBreakMode() {
        return breakStrategy != null ? breakStrategy.getCurrentMode() : BreakStrategy.BreakMode.SINGLE_POINT;
    }
    
    /**
     * 获取第一个打断点
     * 
     * @return 第一个打断点，如果没有设置则返回null
     */
    public Vec2d getFirstBreakPoint() {
        return breakStrategy != null ? breakStrategy.getFirstBreakPoint() : null;
    }
    
    /**
     * 获取第二个打断点（两点模式）
     * 
     * @return 第二个打断点，如果没有设置则返回null
     */
    public Vec2d getSecondBreakPoint() {
        return breakStrategy != null ? breakStrategy.getSecondBreakPoint() : null;
    }
    
    @Override
    protected String getInitialStatusMessage() {
        return "选择要打断的对象，按T键切换模式";
    }
    
    @Override
    public String getDefaultCursor() {
        return "crosshair";
    }
    
    @Override
    protected void renderPreview(DrawContext context) {
        // 不调用 super.renderPreview(context) 因为它是抽象方法
        
        if (breakStrategy == null) return;
        
        // 渲染打断点和预览
        Vec2d firstPoint = breakStrategy.getFirstBreakPoint();
        Vec2d secondPoint = breakStrategy.getSecondBreakPoint();
        Vec2d mouse = breakStrategy.getCurrentMousePoint();
        
        if (firstPoint != null) {
            // 渲染第一个打断点
            context.fillCircle(firstPoint, 4.5f, new java.awt.Color(255, 80, 80, 220));
            context.drawCircle(firstPoint, 6.5f, new java.awt.Color(255, 80, 80, 180));
            
            if (breakStrategy.getCurrentMode() == BreakStrategy.BreakMode.TWO_POINT && secondPoint != null) {
                // 渲染第二个打断点和连接线
                context.fillCircle(secondPoint, 4.5f, new java.awt.Color(255, 80, 80, 220));
                context.drawCircle(secondPoint, 6.5f, new java.awt.Color(255, 80, 80, 180));
                // 预估删除段采用半透明虚线显示
                context.drawDashedLine(firstPoint, secondPoint, new java.awt.Color(255, 120, 120, 199));
            }
        }
        
        // 在两点模式且仅有第一点时，用鼠标位置做临时预览
        if (breakStrategy.getCurrentMode() == BreakStrategy.BreakMode.TWO_POINT && firstPoint != null && secondPoint == null && mouse != null) {
            context.drawDashedLine(firstPoint, mouse, new java.awt.Color(255, 120, 120, 120));
            context.fillCircle(mouse, 3.5f, new java.awt.Color(255, 180, 0, 180));
        }
        
        // 高亮目标图形
        Shape targetShape = breakStrategy.getTargetShape();
        if (targetShape != null) {
            targetShape.setHighlighted(true);
        }

        // 渲染断开瞬间的红叉（×）反馈，持续 ~1.5 秒
        try {
            java.lang.reflect.Field markField = breakStrategy.getClass().getDeclaredField("lastBreakMark");
            java.lang.reflect.Field tsField = breakStrategy.getClass().getDeclaredField("lastBreakMarkAtMs");
            java.lang.reflect.Field durField = breakStrategy.getClass().getDeclaredField("BREAK_MARK_DURATION_MS");
            markField.setAccessible(true);
            tsField.setAccessible(true);
            durField.setAccessible(true);
            Object p = markField.get(breakStrategy);
            Object t = tsField.get(breakStrategy);
            Object d = durField.get(breakStrategy);
            if (p instanceof Vec2d mark && t instanceof Long ts && d instanceof Long dur) {
                long now = System.currentTimeMillis();
                if (now - ts <= dur) {
                    float size = 8f;
                    java.awt.Color color = new java.awt.Color(255, 60, 60, 230);
                    // 画一个红叉
                    context.drawLine(mark.add(new Vec2d(-size, -size)), mark.add(new Vec2d(size, size)), color);
                    context.drawLine(mark.add(new Vec2d(size, -size)), mark.add(new Vec2d(-size, size)), color);
                }
            }
        } catch (Exception ignored) {}
    }
} 