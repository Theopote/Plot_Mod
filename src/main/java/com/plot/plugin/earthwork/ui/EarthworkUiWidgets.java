package com.plot.plugin.earthwork.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.PolygonRegionUtils;
import com.plot.core.geometry.RegionGeometry;
import com.plot.core.geometry.shapes.FreeDrawPath;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.core.model.Shape;
import com.plot.core.plugin.PluginManager;
import com.plot.core.tool.BaseTool;
import com.plot.core.tool.ToolManager;
import com.plot.plugin.BuildingPlugin;
import com.plot.plugin.RoadSystemPlugin;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.config.EarthworkConfig;
import com.plot.plugin.earthwork.*;
import com.plot.core.material.MaterialConversionModel;
import com.plot.plugin.earthwork.geometry.EarthworkGeometryUtils;
import com.plot.plugin.earthwork.model.*;
import com.plot.plugin.earthwork.pipeline.EarthworkGenerationResult;
import com.plot.plugin.earthwork.ui.EarthworkUiContext;
import com.plot.plugin.road.earthwork.RoadEarthworkSurfaceSampler;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.ui.canvas.Canvas;
import com.plot.ui.component.UIUtils;
import com.plot.utils.PlotI18n;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;


/** 土方 UI 共享控件与辅助方法。 */
public final class EarthworkUiWidgets {
    private EarthworkUiWidgets() {
    }

        public static void renderRegionSelector(EarthworkUiContext ctx) {
            if (ctx.project().getRegionCount() == 0) {
                return;
            }
            String[] labels = ctx.project().getRegions().values().stream()
                .map(GradingRegion::getName)
                .toArray(String[]::new);
            String[] ids = ctx.project().getRegions().keySet().toArray(String[]::new);
            int current = 0;
            for (int i = 0; i < ids.length; i++) {
                if (ids[i].equals(ctx.selectedRegionId())) {
                    current = i;
                    break;
                }
            }
            ImInt regionIndex = new ImInt(current);
            if (ImGui.combo(PlotI18n.tr("plugin.earthwork.select_region"), regionIndex, labels)) {
                ctx.setSelectedRegionId(ids[regionIndex.get()]);
            }
        }

        public static void renderMaterialButton(EarthworkUiContext ctx, String label, String currentBlockId, Consumer<String> onSelected) {
            ImGui.text(label);
            ImGui.sameLine();
            String display = currentBlockId == null || currentBlockId.isBlank()
                ? PlotI18n.tr("plugin.earthwork.cut_material_air")
                : currentBlockId;
            if (ImGui.button(display + "##" + label, 0, 0)) {
                UIUtils.openBlockPicker(
                    currentBlockId == null || currentBlockId.isBlank() ? "minecraft:air" : currentBlockId,
                    onSelected);
            }
        }

        /**
         * 渲染材料换算滑块（可利用率 + 挖转填系数）。
         *
         * @return 是否修改了材料参数
         */
        public static boolean renderMaterialConversionSliders(
                EarthworkUiContext ctx,
                MaterialConversionModel materials,
                Consumer<MaterialConversionModel> onChange) {
            MaterialConversionModel current = materials != null ? materials : MaterialConversionModel.DEFAULT;
            float[] reusableRatio = {current.reusableRatio()};
            boolean changed = false;
            boolean reusableChanged = ImGui.sliderFloat("##reusable_ratio", reusableRatio, 0.50f, 1.00f,
                PlotI18n.tr("plugin.earthwork.reusable_ratio", String.format("%.2f", reusableRatio[0])));
            if (ImGui.isItemActivated()) {
                ctx.projectHistory().push(ctx.project());
            }
            if (reusableChanged) {
                onChange.accept(current.withReusableRatio(reusableRatio[0]));
                changed = true;
            }
            UIUtils.renderEngineeringTooltip("hint.plot.earthwork.reusable_ratio");

            float[] cutToFillRatio = {current.cutToCompactedFillRatio()};
            boolean cutToFillChanged = ImGui.sliderFloat("##cut_to_compacted_fill_ratio", cutToFillRatio, 0.50f, 1.00f,
                PlotI18n.tr("plugin.earthwork.cut_to_compacted_fill_ratio", String.format("%.2f", cutToFillRatio[0])));
            if (ImGui.isItemActivated()) {
                ctx.projectHistory().push(ctx.project());
            }
            if (cutToFillChanged) {
                onChange.accept(current.withCutToCompactedFillRatio(cutToFillRatio[0]));
                changed = true;
            }
            UIUtils.renderEngineeringTooltip("hint.plot.earthwork.cut_to_compacted_fill_ratio");

            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr(
                "plugin.earthwork.effective_cut_to_fill_ratio",
                (materials != null ? materials : current).effectiveCutToCompactedFillRatio()));
            return changed;
        }

        public static void locateRegion(EarthworkUiContext ctx, GradingRegion region) {
            Vec2d centroid = EarthworkGeometryUtils.computeCentroid(region.getOuterPoints());
            Canvas canvas = com.plot.ui.canvas.CanvasAccess.get();
            if (canvas != null && canvas.getCamera() != null) {
                canvas.getCamera().setOffset(centroid);
                ctx.setSelectedRegionId(region.getId());
                ctx.setProjectStatus(PlotI18n.tr("plugin.earthwork.locate_success", region.getName()));
            }
        }

        public static void syncSelectedRegionAfterHistory(EarthworkUiContext ctx) {
            if (!ctx.selectedRegionId().isEmpty() && ctx.project().getRegion(ctx.selectedRegionId()) == null) {
                ctx.setSelectedRegionId(ctx.project().getRegions().isEmpty()
                    ? ""
                    : ctx.project().getRegions().keySet().iterator().next());
                ctx.setRegionNameEditingRegionId("");
            }
        }

        public static World getClientWorld() {
            MinecraftClient client = MinecraftClient.getInstance();
            return client != null ? client.world : null;
        }
}
