package com.plot.ui.tools.impl.modify;

import com.plot.api.snap.ISnapManager;
import com.plot.api.state.IAppState;
import com.plot.core.command.commands.ModifyCommand;
import com.plot.core.graphics.DrawContext;
import com.plot.ui.component.Icons;
import com.plot.ui.tools.impl.modify.strategy.IModifyStrategy;
import com.plot.ui.tools.impl.modify.strategy.JoinStrategy;

/**
 * 合并工具：将首尾相连的线段/折线段合并为多段线。
 */
public class JoinTool extends ModifyTool {

    private final JoinStrategy joinStrategy = new JoinStrategy();

    public JoinTool(IAppState appState, ISnapManager snapManager) {
        super("join", Icons.JOIN_IDENTIFIER, appState, snapManager);
    }

    @Deprecated
    public JoinTool() {
        super("join", Icons.JOIN_IDENTIFIER);
    }

    @Override
    protected IModifyStrategy createStrategy() {
        return joinStrategy;
    }

    @Override
    protected String getInitialStatusMessage() {
        return "status.plot.join.initial";
    }

    @Override
    protected void renderPreview(DrawContext context) {
        joinStrategy.renderPreview(context);
    }

    @Override
    public void onActivate() {
        super.onActivate();
        executeIfReady();
    }

    private void executeIfReady() {
        if (joinStrategy.prepare(this)) {
            ModifyCommand command = joinStrategy.getModifyCommand();
            if (command != null) {
                executeModifyCommand(command);
            }
        }
    }

    @Override
    public void updateConfig(String key, String value) {
        // 单次命令工具，无额外配置
    }
}
