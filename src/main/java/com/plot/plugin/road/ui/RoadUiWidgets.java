package com.plot.plugin.road.ui;

import com.plot.plugin.road.RoadMaterialUtils;
import com.plot.ui.screen.BlockConfigNativeScreen;
import com.plot.ui.screen.PlotScreen;
import com.plot.ui.screen.PlotScreenState;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * 道路 UI 通用控件。
 */
public final class RoadUiWidgets {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/RoadUI");

    private RoadUiWidgets() {
    }

    @FunctionalInterface
    public interface MaterialSetter {
        void set(String material);
    }

    public static void renderBlockMaterialPicker(
            RoadUiContext ctx,
            String buttonId,
            String label,
            String currentValue,
            MaterialSetter setter,
            boolean pushHistoryOnChange) {
        String displayName = RoadMaterialUtils.getDisplayName(currentValue);
        if (ImGui.button(displayName + buttonId, ImGui.getContentRegionAvailX() * 0.55f, 0)) {
            openBlockPicker(currentValue, blockId -> {
                if (pushHistoryOnChange) {
                    ctx.networkManager().pushHistory();
                }
                setter.set(blockId);
            });
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("plugin.road.select_block_hint"));
        }
        ImGui.sameLine();
        ImGui.textColored((int) 0xFF808080FFL, label);
    }

    public static void openBlockPicker(String currentBlockId, Consumer<String> onSelected) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            LOGGER.warn("MinecraftClient 不可用，无法打开方块选择器");
            return;
        }
        client.execute(() -> {
            if (client.currentScreen instanceof PlotScreen) {
                PlotScreenState.markSwitchingToPlotSubScreen();
            }
            client.setScreen(BlockConfigNativeScreen.forSingleSelection(
                client.currentScreen, currentBlockId, onSelected));
        });
    }

    public static void renderEngineeringTooltip(String i18nKey) {
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr(i18nKey));
        }
    }

    public static void renderRoadVisibilityWarning(RoadUiContext ctx) {
        String message = ctx.previewManager().formatVisibilityWarning();
        if (!message.isBlank()) {
            ImGui.textColored((int) 0xFFFFA060FFL, message);
        }
    }
}
