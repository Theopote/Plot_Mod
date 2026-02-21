package com.masterplanner.ui.panel.tool.renderer;

import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 偏移工具属性面板渲染器 - 简化版
 * 仅保留“多重偏移”复选框和简短使用说明
 */
public class OffsetToolOptionRenderer extends AbstractToolOptionRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(OffsetToolOptionRenderer.class);

    private final ImBoolean multipleMode = new ImBoolean(false);

    public OffsetToolOptionRenderer() {
        super("offset");
        LOGGER.debug("OffsetToolOptionRenderer(简化) 已初始化");
    }

    @Override
    public float render() {
        float height = 0;
        ImGui.pushID("offset_options");
        try {
            // 多重偏移
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ImGui.alignTextToFramePadding();
            ImGui.text("多重偏移");

            ImGui.tableNextColumn();
            if (ImGui.checkbox("##multiple_mode", multipleMode)) {
                updateToolConfig("multipleMode", String.valueOf(multipleMode.get()));
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("启用后可以连续偏移多个对象");
            }
            height += ImGui.getFrameHeightWithSpacing();
        } finally {
            ImGui.popID();
        }
        return height;
    }

    @Override
    public void initialize() {
        LOGGER.debug("OffsetToolOptionRenderer(简化) 初始化完成");
    }

    @Override
    public void cleanup() {
        // 无资源
    }

    private void renderUsageInstructions() {
        if (ImGui.collapsingHeader("使用说明", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.textWrapped("步骤：先点击线类图形记录参考点，再在目标侧点击第二点完成偏移复制。原图形保留，只添加复制品。");
        }
    }
}


