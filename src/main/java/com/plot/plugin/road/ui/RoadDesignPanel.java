package com.plot.plugin.road.ui;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.station.ChainageDisplayMode;
import com.plot.plugin.road.ui.design.RoadAlignmentSection;
import com.plot.plugin.road.ui.design.RoadDesignSectionContext;
import com.plot.plugin.road.ui.design.RoadDiagnosticsSection;
import com.plot.plugin.road.ui.design.RoadFacilitySection;
import com.plot.plugin.road.ui.design.RoadIdentitySection;
import com.plot.plugin.road.ui.design.RoadSegmentSection;
import com.plot.plugin.road.ui.design.RoadVariableSectionSection;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;

/**
 * 单条逻辑道路高级设计区编排器（各 Section 按信息架构分组渲染）。
 */
final class RoadDesignPanel {

    private final RoadUiContext ctx;
    private final RoadIdentitySection identitySection = new RoadIdentitySection();
    private final RoadAlignmentSection alignmentSection = new RoadAlignmentSection();
    private final RoadVariableSectionSection variableSectionSection = new RoadVariableSectionSection();
    private final RoadFacilitySection facilitySection = new RoadFacilitySection();
    private final RoadSegmentSection segmentSection = new RoadSegmentSection();
    private final RoadDiagnosticsSection diagnosticsSection = new RoadDiagnosticsSection();
    private ChainageDisplayMode chainageDisplayMode = ChainageDisplayMode.FROM_START;
    private boolean forceOpenAdvancedDesign;
    private boolean advancedDesignOpen;
    private String lastAdvancedDesignRoadId = "";
    private String lastAdvancedDesignEdgeId = "";

    RoadDesignPanel(RoadUiContext ctx) {
        this.ctx = ctx;
    }

    void requestOpenDiagnostics() {
        forceOpenAdvancedDesign = true;
        diagnosticsSection.requestOpen();
    }

    void finishPendingEdits() {
        segmentSection.finishPendingEdits(ctx);
        lastAdvancedDesignRoadId = "";
        lastAdvancedDesignEdgeId = "";
    }

    void renderAdvancedDesignSection(RoadNetwork network, Road road) {
        int headerFlags = forceOpenAdvancedDesign ? ImGuiTreeNodeFlags.DefaultOpen : 0;
        if (forceOpenAdvancedDesign) {
            forceOpenAdvancedDesign = false;
        }
        boolean open = ImGui.collapsingHeader(
            PlotI18n.tr("plugin.road.style.advanced_design"),
            headerFlags);
        if (advancedDesignOpen && !open) {
            finishPendingEdits();
        }
        advancedDesignOpen = open;
        if (!open) {
            return;
        }
        String primaryId = ctx.networkManager().getPrimarySelectedEdgeId();
        if (!lastAdvancedDesignRoadId.isEmpty()
                && (!lastAdvancedDesignRoadId.equals(road.getId())
                    || !lastAdvancedDesignEdgeId.equals(primaryId))) {
            finishPendingEdits();
        }
        lastAdvancedDesignRoadId = road.getId();
        lastAdvancedDesignEdgeId = primaryId;
        RoadEdge current = network.getEdge(primaryId);
        if (current == null) {
            return;
        }
        RoadDesignSectionContext sectionCtx = new RoadDesignSectionContext(
            ctx,
            network,
            road,
            current,
            chainageDisplayMode,
            mode -> chainageDisplayMode = mode);

        // Diagnostics first when present (results), then metadata / core design / local edits / tools.
        diagnosticsSection.render(sectionCtx);
        identitySection.render(sectionCtx);
        alignmentSection.render(sectionCtx);
        variableSectionSection.render(sectionCtx);
        facilitySection.render(sectionCtx);
        segmentSection.render(sectionCtx);
    }
}
