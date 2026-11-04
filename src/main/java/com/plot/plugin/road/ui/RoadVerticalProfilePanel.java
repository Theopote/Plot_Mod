package com.plot.plugin.road.ui;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.profile.RoadProfileRoadList;
import com.plot.plugin.road.vertical.FlatElevationProfileOverlay;
import com.plot.plugin.road.vertical.NetworkElevationAuthority;
import com.plot.plugin.road.vertical.RoadVerticalStrategy;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/**
 * 纵断面工作区：全网标高模式、逐路纵向策略、概览与专业曲线编辑入口。
 */
public final class RoadVerticalProfilePanel {
    private final RoadUiContext ctx;
    private final VerticalProfileEditor profileEditor = new VerticalProfileEditor();
    private final VerticalModeControls verticalModeControls = new VerticalModeControls();
    private final RoadPathVerticalPropertyPanel pathPropertyPanel =
        new RoadPathVerticalPropertyPanel(new RoadVerticalStrategySwitchDialog());

    public RoadVerticalProfilePanel(RoadUiContext ctx) {
        this.ctx = ctx;
        profileEditor.setFlatOverlayResolver(
            road -> resolveFlatProfileOverlay(ctx.networkManager().getNetwork(), road));
        // 确认纵断面只提交设计并标记正式预览过期，不自动启动全路网重算。
        profileEditor.setOnAlignmentCommitted(this::markFormalPreviewStaleAfterProfileConfirm);
    }

    public VerticalProfileEditor profileEditor() {
        return profileEditor;
    }

    public void render() {
        RoadNetwork network = ctx.networkManager().getNetwork();
        if (network.getEdges().isEmpty()) {
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.style.no_roads_hint"));
            return;
        }

        RoadGenerationSettingsPanel.renderVerticalDesign(
            ctx,
            pathPropertyPanel,
            () -> {
                if (rejectProfileEditorIfUniformFlat()) {
                    return;
                }
                Road road = RoadProfileRoadList.resolveActiveRoad(network, ctx);
                if (road != null) {
                    profileEditor.openEditorForRoad(ctx, road.getId());
                }
            });

        boolean uniformFlat = NetworkElevationAuthority.isUniformFlat(network);
        if (!uniformFlat) {
            ImGui.separator();
            RoadUiSections.section("plugin.road.generate.profile_section");
            RoadProfileOverviewSection.render(
                ctx,
                network,
                profileEditor,
                road -> resolveFlatProfileOverlay(network, road));
        } else {
            ImGui.separator();
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.uniform_flat_profile_editor_blocked"));
        }
    }

    public void openProfileForEdge(String edgeId) {
        if (edgeId == null || edgeId.isBlank()) {
            return;
        }
        if (rejectProfileEditorIfUniformFlat()) {
            return;
        }
        ctx.networkManager().setPrimarySelectedEdge(edgeId);
        RoadEdge edge = ctx.networkManager().getNetwork().getEdge(edgeId);
        if (edge != null && edge.getRoadId() != null) {
            profileEditor.openEditorForRoad(ctx, edge.getRoadId());
        } else {
            profileEditor.openEditorForEdge(ctx, ctx.networkManager().getNetwork(), edgeId);
        }
    }

    public void renderProfileEditorWindow(RoadNetwork network) {
        profileEditor.setFlatOverlayResolver(road -> resolveFlatProfileOverlay(network, road));
        profileEditor.renderEditorWindow(ctx, network);
    }

    public void clearProfileCache() {
        profileEditor.clearCache();
    }

    public void renderDeferredModals() {
        pathPropertyPanel.renderDeferredModals(ctx);
    }

    private void markFormalPreviewStaleAfterProfileConfirm() {
        if (ctx.previewManager() == null) {
            return;
        }
        ctx.previewManager().invalidatePreview();
    }

    private boolean rejectProfileEditorIfUniformFlat() {
        if (!NetworkElevationAuthority.isUniformFlat(ctx.networkManager().getNetwork())) {
            return false;
        }
        ctx.status().warning(PlotI18n.tr("plugin.road.uniform_flat_profile_editor_blocked"));
        return true;
    }

    private FlatElevationProfileOverlay resolveFlatProfileOverlay(RoadNetwork network, Road road) {
        if (road == null || RoadVerticalStrategy.fromRoad(road) != RoadVerticalStrategy.FLAT) {
            return FlatElevationProfileOverlay.EMPTY;
        }
        return verticalModeControls.flatElevationProfileOverlay(
            network, road, ctx.networkManager().getConfig());
    }
}
