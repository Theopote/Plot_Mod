package com.plot.ui.tools.impl.modify;

import com.plot.api.snap.ISnapManager;
import com.plot.api.state.IAppState;
import com.plot.core.graphics.DrawContext;
import com.plot.core.state.AppState;
import com.plot.ui.component.Icons;
import com.plot.ui.tools.impl.modify.strategy.IModifyStrategy;
import com.plot.ui.tools.impl.modify.strategy.MoveWithSelectionStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * 移动工具 - 策略模式版本
 *
 * <p>用于移动选中的图形，采用策略模式架构：</p>
 * <ul>
 *   <li>支持点击-移动-点击模式</li>
 *   <li>支持拖拽移动模式</li>
 *   <li>实时预览移动效果</li>
 *   <li>支持约束移动（网格吸附、正交等）</li>
 * </ul>
 *
 * @author Plot Team
 * @version 2.0 - 策略模式版本
 */
public class MoveTool extends ModifyTool {
    private static final Logger LOGGER = LoggerFactory.getLogger(MoveTool.class);

    /**
     * 构造函数（推荐方式 - 依赖注入）
     * @param appState 应用状态管理器
     * @param snapManager 吸附管理器
     */
    public MoveTool(IAppState appState, ISnapManager snapManager) {
        super("move", "移动", Icons.MOVE_IDENTIFIER, "移动选中的图形",
              (AppState) appState, snapManager);
        LOGGER.info("MoveTool 已创建");
    }

    /**
     * 构造函数（兼容方式 - 单例获取）
     * @deprecated 推荐使用依赖注入构造函数
     */
    @Deprecated
    public MoveTool() {
        super("move", "移动", Icons.MOVE_IDENTIFIER, "移动选中的图形");
        LOGGER.info("MoveTool 已创建（兼容模式）");
    }

    @Override
    protected IModifyStrategy createStrategy() {
        return new MoveWithSelectionStrategy();
    }

    @Override
    protected String getInitialStatusMessage() {
        return "左键选择图形，右键完成选择并进入移动模式";
    }
    


    @Override
    protected void renderPreview(DrawContext context) {
        // 对于新的策略，预览渲染已经在策略内部处理
        // 这里只需要调用策略的渲染方法
        if (modifyStrategy instanceof MoveWithSelectionStrategy moveStrategy) {
            moveStrategy.renderPreview(context);
        }
    }
    

    


    @Override
    public void updateConfig(String key, String value) {
        // 对于新的策略，配置更新已移到策略内部处理
        if (modifyStrategy instanceof MoveWithSelectionStrategy moveStrategy) {
            moveStrategy.updateConfig(key, value);
        } else {
            LOGGER.debug("未知的配置项: {} = {}", key, value);
        }
    }
}
