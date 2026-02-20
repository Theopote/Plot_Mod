package com.masterplanner.ui.tools.impl.modify;

import com.masterplanner.core.graphics.DrawContext;
import com.masterplanner.ui.component.Icons;
import com.masterplanner.api.snap.ISnapManager;
import com.masterplanner.core.state.AppState;
import com.masterplanner.ui.tools.impl.modify.strategy.OffsetStrategy;
import com.masterplanner.ui.tools.impl.modify.strategy.IModifyStrategy;
import com.masterplanner.ui.tools.impl.modify.strategy.SimpleOffsetStrategy;
// import com.masterplanner.ui.tools.impl.modify.helper.OffsetHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 偏移工具 - 策略模式版本
 * 
 * <p>用于创建图形的偏移副本，支持：</p>
 * <ul>
 *   <li>距离偏移：指定偏移距离</li>
 *   <li>穿点偏移：通过点击确定偏移位置</li>
 *   <li>多重偏移：连续偏移多个对象</li>
 *   <li>实时预览：显示偏移效果</li>
 *   <li>观察者模式：配置变化时自动通知UI</li>
 * </ul>
 * 
 * @author MasterPlanner Team
 * @version 2.2 - PropertyChangeSupport版本
 */
public class OffsetTool extends ModifyTool {
    private static final Logger LOGGER = LoggerFactory.getLogger(OffsetTool.class);
    
    // 配置键常量
    private static final String CONFIG_KEY_DISTANCE = "distance";
    private static final String CONFIG_KEY_MODE = "mode";
    private static final String CONFIG_KEY_MULTIPLE_MODE = "multipleMode";
    
    /**
     * 构造函数（推荐方式 - 依赖注入）
     * @param appState 应用状态管理器
     * @param snapManager 吸附管理器
     */
    public OffsetTool(AppState appState, ISnapManager snapManager) {
        super("offset", "偏移", Icons.OFFSET_IDENTIFIER, 
              "创建偏移对象，支持距离偏移和穿点偏移",
              appState, snapManager);
        LOGGER.info("OffsetTool 已创建");
    }

    /**
     * 构造函数（兼容方式 - 单例获取）
     * @deprecated 推荐使用依赖注入构造函数
     */
    @Deprecated
    public OffsetTool() {
        super("offset", "偏移", Icons.OFFSET_IDENTIFIER, 
              "创建偏移对象，支持距离偏移和穿点偏移");
        LOGGER.info("OffsetTool 已创建（兼容模式）");
    }

    @Override
    protected IModifyStrategy createStrategy() {
        // 使用简化版两点偏移线段策略
        return new com.masterplanner.ui.tools.impl.modify.strategy.SimpleOffsetStrategy();
    }

    @Override
    protected String getInitialStatusMessage() {
        return "点击线类图形确定参考点 → 在目标侧点击第二点，完成偏移复制";
    }

    @Override
    protected void renderPreview(DrawContext context) {
        // 简化策略不提供预览
    }

    @Override
    public void updateConfig(String key, String value) {
        try {
            IModifyStrategy strategy = getStrategy();

            if (strategy instanceof OffsetStrategy offsetStrategy) {
                switch (key) {
                    case CONFIG_KEY_DISTANCE -> {
                        double newDistance = Double.parseDouble(value);
                        offsetStrategy.setDistance(newDistance);
                    }
                    case CONFIG_KEY_MULTIPLE_MODE -> {
                        boolean multipleMode = Boolean.parseBoolean(value);
                        offsetStrategy.setMultipleMode(multipleMode);
                    }
                    case CONFIG_KEY_MODE -> {
                        if ("DISTANCE".equals(value)) {
                            offsetStrategy.setCurrentMode(OffsetStrategy.OffsetMode.DISTANCE);
                        } else if ("THROUGH_POINT".equals(value)) {
                            offsetStrategy.setCurrentMode(OffsetStrategy.OffsetMode.THROUGH_POINT);
                        }
                    }
                    default -> LOGGER.debug("未知的配置键: {}", key);
                }
                return;
            }

            if (strategy instanceof SimpleOffsetStrategy simpleOffsetStrategy) {
                if (CONFIG_KEY_MULTIPLE_MODE.equals(key)) {
                    boolean multipleMode = Boolean.parseBoolean(value);
                    simpleOffsetStrategy.setMultipleMode(multipleMode);
                } else {
                    LOGGER.debug("SimpleOffsetStrategy 暂不支持配置项: {} = {}", key, value);
                }
                return;
            }

            LOGGER.debug("当前偏移策略不支持配置更新: {}", strategy != null ? strategy.getClass().getSimpleName() : "null");
        } catch (NumberFormatException e) {
            LOGGER.warn("无效的配置值: {} = {}", key, value);
        } catch (Exception e) {
            LOGGER.error("更新配置失败: {} = {}", key, value, e);
        }
    }

    /**
     * 获取当前偏移模式
     * @return 当前偏移模式
     */
    public OffsetStrategy.OffsetMode getCurrentMode() {
        IModifyStrategy strategy = getStrategy();
        if (strategy instanceof OffsetStrategy offsetStrategy) {
            return offsetStrategy.getCurrentMode();
        }
        return OffsetStrategy.OffsetMode.DISTANCE;
    }
}
