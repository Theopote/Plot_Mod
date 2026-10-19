package com.plot.ui.tools.impl.modify;

import com.plot.api.snap.ISnapManager;
import com.plot.api.state.IAppState;
import com.plot.core.command.commands.ModifyCommand;
import com.plot.core.graphics.DrawContext;
import com.plot.core.model.Shape;
import com.plot.ui.component.Icons;
import com.plot.ui.tools.impl.modify.strategy.CopyWithSelectionStrategy;
import com.plot.ui.tools.impl.modify.strategy.IModifyStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 复制工具：点选/框选图形后，参考点 → 目标点复制，支持连续复制。
 */
public class CopyTool extends ModifyTool {
    private static final Logger LOGGER = LoggerFactory.getLogger(CopyTool.class);

    private final CopyWithSelectionStrategy copyStrategy = new CopyWithSelectionStrategy();

    public CopyTool(IAppState appState, ISnapManager snapManager) {
        super("copy", Icons.COPY_IDENTIFIER, appState, snapManager);
    }

    @Deprecated
    public CopyTool() {
        super("copy", Icons.COPY_IDENTIFIER);
    }

    @Override
    protected IModifyStrategy createStrategy() {
        return copyStrategy;
    }

    @Override
    protected String getInitialStatusMessage() {
        return "status.plot.copy.initial";
    }

    @Override
    protected void renderPreview(DrawContext context) {
        copyStrategy.renderPreview(context);
    }

    @Override
    public void executeModifyCommand(ModifyCommand command) {
        if (command == null) {
            return;
        }
        try {
            concreteAppState.getCommandService().execute(command);
            List<Shape> newShapes = command.getNewShapes();
            if (newShapes != null) {
                for (Shape shape : newShapes) {
                    shape.setSelected(false);
                    shape.setHighlighted(false);
                }
            }
            copyStrategy.prepareForNextCopy(this);
            setModifyToolState(ModifyTool.ToolState.IDLE);
            setPreviewEnabled(false);
        } catch (Exception e) {
            LOGGER.error("CopyTool 执行复制命令失败: {}", e.getMessage(), e);
            resetModification("命令执行失败");
        }
    }

    @Override
    public void updateConfig(String key, String value) {
        // 复制工具无额外配置
    }
}
