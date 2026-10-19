package com.plot.ui.tools.impl.modify;

import com.plot.api.snap.ISnapManager;
import com.plot.api.state.IAppState;
import com.plot.core.command.commands.ModifyCommand;
import com.plot.core.graphics.DrawContext;
import com.plot.ui.component.Icons;
import com.plot.ui.tools.impl.modify.strategy.ExplodeStrategy;
import com.plot.ui.tools.impl.modify.strategy.IModifyStrategy;

/**
 * 炸开工具：将多段线、矩形、多边形等分解为独立线段。
 */
public class ExplodeTool extends ModifyTool {

    private final ExplodeStrategy explodeStrategy = new ExplodeStrategy();

    public ExplodeTool(IAppState appState, ISnapManager snapManager) {
        super("explode", Icons.EXPLODE_IDENTIFIER, appState, snapManager);
    }

    @Deprecated
    public ExplodeTool() {
        super("explode", Icons.EXPLODE_IDENTIFIER);
    }

    @Override
    protected IModifyStrategy createStrategy() {
        return explodeStrategy;
    }

    @Override
    protected String getInitialStatusMessage() {
        return "status.plot.explode.initial";
    }

    @Override
    protected void renderPreview(DrawContext context) {
        explodeStrategy.renderPreview(context);
    }

    @Override
    public void onActivate() {
        super.onActivate();
        executeIfReady();
    }

    private void executeIfReady() {
        if (explodeStrategy.prepare(this)) {
            ModifyCommand command = explodeStrategy.getModifyCommand();
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
