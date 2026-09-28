package com.plot.plugin.road.ui;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.station.ChainageDisplayContext;
import com.plot.plugin.road.station.RoadStationFormat;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.VerticalAlignmentGeometry;
import com.plot.plugin.road.vertical.VerticalAlignmentValidator;
import com.plot.plugin.road.vertical.VerticalAlignmentViolation;
import com.plot.plugin.road.vertical.VerticalProfileDesignRules;
import com.plot.plugin.road.vertical.VerticalAlignmentJunctionSynchronizer;
import com.plot.plugin.road.vertical.FlatVerticalIntent;
import com.plot.plugin.road.vertical.FlatVerticalIntentSupport;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.plugin.road.vertical.RoadVerticalStrategy;
import com.plot.plugin.road.vertical.VerticalProfileNetworkPropagator;
import com.plot.plugin.road.validation.RoadValidationMessage;
import com.plot.plugin.road.validation.RoadValidationMessageCatalog;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiCol;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 纵断面 PVI CRUD：桩号、标高、中间变坡点竖曲线长度。
 */
public final class VerticalAlignmentEditor {

    private String syncedRoadId = "";
    private final List<PviDraft> drafts = new ArrayList<>();
    private float flatElevation = 64f;

    public void render(
            RoadNetwork network,
            Road road,
            ChainageDisplayContext chainageDisplay,
            RoadSystemConfig config,
            Supplier<TerrainSampler> terrainSupplier,
            Runnable onHistory) {
        if (road == null || network == null) {
            return;
        }

        ImGui.spacing();
        if (!ImGui.collapsingHeader(PlotI18n.tr("plugin.road.vertical_alignment_section"))) {
            return;
        }

        if (!RoadStationing.isStationable(network, road)) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.vertical_alignment_requires_stationing"));
            return;
        }

        double roadLength = RoadStationing.canonicalLength(network, road);
        RoadVerticalStrategy strategy = RoadVerticalStrategy.fromRoad(road);
        if (strategy == RoadVerticalStrategy.FLAT) {
            renderFlatGenerateSection(network, road, roadLength, config, onHistory);
            return;
        }
        if (road.getVerticalMode() != RoadVerticalMode.MANUAL_PROFILE) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.vertical_alignment_terrain_adaptive_generate_hint"));
            return;
        }
        if (VerticalAlignmentJunctionSynchronizer.applySharedJunctionConstraints(network, road) > 0) {
            syncedRoadId = "";
        }
        syncDrafts(road);
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.vertical_alignment_hint"));

        if (drafts.isEmpty()) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.vertical_alignment_none"));
        }

        if (drafts.size() > 0 && drafts.size() < 2) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.vertical_alignment_incomplete"));
        }

        if (drafts.size() >= 2) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.vertical_alignment_generation_hint"));
        }

        for (int i = 0; i < drafts.size(); i++) {
            renderDraftRow(road, drafts.get(i), i, drafts.size(), (float) roadLength, chainageDisplay, onHistory);
            if (i < drafts.size() - 1) {
                ImGui.separator();
            }
        }

        applyDraftsIfChanged(road);
        VerticalProfileNetworkPropagator.propagate(
            network, road, connected -> connected.getEffectiveMaxSlope(config));
        renderValidationMessages(road, roadLength);

        boolean slopeAllowed = VerticalProfileDesignRules.slopeAllowed(roadLength);
        if (!slopeAllowed) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.INVALID,
                PlotI18n.tr("plugin.road.vertical_alignment_short_road",
                    (int) VerticalProfileDesignRules.MIN_ROAD_LENGTH_FOR_SLOPE));
        }

        if (slopeAllowed && ImGui.button(PlotI18n.tr("plugin.road.vertical_alignment_add"))) {
            if (onHistory != null) {
                onHistory.run();
            }
            drafts.add(PviDraft.defaultEntry(drafts, roadLength));
            applyDraftsIfChanged(road);
        }
    }

    /** 编辑 Tab：仅纵向设计模式；PVI / 标高编辑在生成 Tab。 */
    public void renderModeOnly(
            RoadNetwork network,
            Road road,
            RoadSystemConfig config,
            Runnable onHistory,
            RoadVerticalStrategySwitchDialog switchDialog,
            Supplier<TerrainSampler> terrainSupplier) {
        if (road == null || network == null) {
            return;
        }
        if (!RoadStationing.isStationable(network, road)) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.vertical_alignment_requires_stationing"));
            return;
        }
        double roadLength = RoadStationing.canonicalLength(network, road);
        renderVerticalStrategy(network, road, roadLength, config, onHistory, switchDialog, terrainSupplier);
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.vertical_alignment_edit_in_generate_hint"));
    }

    private void renderVerticalStrategy(
            RoadNetwork network,
            Road road,
            double roadLength,
            RoadSystemConfig config,
            Runnable onHistory,
            RoadVerticalStrategySwitchDialog switchDialog,
            Supplier<TerrainSampler> terrainSupplier) {
        RoadVerticalStrategy current = RoadVerticalStrategy.fromRoad(road);
        if (ImGui.beginCombo(
                PlotI18n.tr("plugin.road.vertical_strategy"),
                current.label())) {
            for (RoadVerticalStrategy strategy : RoadVerticalStrategy.values()) {
                if (!VerticalProfileDesignRules.slopeAllowed(roadLength)
                        && strategy == RoadVerticalStrategy.TERRAIN_ADAPTIVE) {
                    continue;
                }
                if (ImGui.selectable(strategy.label(), strategy == current)
                        && strategy != current) {
                    if (switchDialog != null) {
                        if (strategy == RoadVerticalStrategy.FLAT) {
                            TerrainSampler terrain = terrainSupplier != null ? terrainSupplier.get() : null;
                            switchDialog.requestSingleToFlat(network, road, config, terrain);
                        } else {
                            switchDialog.requestToTerrainAdaptive(List.of(road.getId()));
                        }
                    } else {
                        if (onHistory != null) {
                            onHistory.run();
                        }
                        strategy.applyToRoad(network, road, config);
                        syncedRoadId = "";
                        syncDrafts(road);
                        FlatVerticalIntent intent = FlatVerticalIntentSupport.resolveIntent(network, road);
                        if (intent != null) {
                            flatElevation = (float) intent.getBaseElevation();
                        }
                    }
                }
            }
            ImGui.endCombo();
        }
        if (current == RoadVerticalStrategy.FLAT) {
            renderFlatElevationField(network, road, config, onHistory);
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.vertical_strategy_flat_hint"));
        } else {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.vertical_strategy_terrain_adaptive_hint"));
        }
    }

    private void renderFlatGenerateSection(
            RoadNetwork network,
            Road road,
            double roadLength,
            RoadSystemConfig config,
            Runnable onHistory) {
        FlatVerticalIntent intent = FlatVerticalIntentSupport.resolveIntent(network, road);
        if (intent != null) {
            flatElevation = (float) intent.getBaseElevation();
        }
        renderFlatElevationField(network, road, config, onHistory);
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.vertical_strategy_flat_generate_hint"));
        if (roadLength > 1e-6) {
            FlatVerticalIntentSupport.syncCompiledAlignment(
                network, road, road.getEffectiveMaxSlope(config));
        }
    }

    private void renderFlatElevationField(
            RoadNetwork network,
            Road road,
            RoadSystemConfig config,
            Runnable onHistory) {
        float[] elevation = {flatElevation};
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        if (ImGui.dragFloat(
                PlotI18n.tr("plugin.road.vertical_alignment_flat_elevation"),
                elevation,
                0.5f,
                -64f,
                320f,
                "%.1f")) {
            if (ImGui.isItemDeactivatedAfterEdit()) {
                if (onHistory != null) {
                    onHistory.run();
                }
                flatElevation = elevation[0];
                FlatVerticalIntentSupport.setBaseElevation(
                    network,
                    road,
                    flatElevation,
                    road.getEffectiveMaxSlope(config));
            } else {
                flatElevation = elevation[0];
            }
        }
    }

    private void renderDraftRow(
            Road road,
            PviDraft draft,
            int index,
            int total,
            float roadLength,
            ChainageDisplayContext chainageDisplay,
            Runnable onHistory) {
        ImGui.pushID(index);

        float[] station = {draft.station};
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        ImGui.sliderFloat(
            PlotI18n.tr("plugin.road.vertical_alignment_station") + "##station",
            station,
            0,
            roadLength,
            "%.1fm");
        if (ImGui.isItemActivated() && onHistory != null) {
            onHistory.run();
        }
        draft.station = station[0];

        float[] elevation = {(float) draft.elevation};
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        ImGui.dragFloat(
            PlotI18n.tr("plugin.road.vertical_alignment_elevation") + "##elevation",
            elevation,
            0.5f,
            -64f,
            320f,
            "%.1f");
        if (ImGui.isItemActivated() && onHistory != null) {
            onHistory.run();
        }
        draft.elevation = elevation[0];

        boolean middlePvi = index > 0 && index < total - 1;
        if (middlePvi) {
            float[] curveLength = {draft.curveLength};
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            ImGui.sliderFloat(
                PlotI18n.tr("plugin.road.vertical_alignment_curve_length") + "##curve",
                curveLength,
                0,
                Math.max(10f, roadLength / 2f),
                "%.1fm");
            if (ImGui.isItemActivated() && onHistory != null) {
                onHistory.run();
            }
            draft.curveLength = Math.max(0f, curveLength[0]);
        }

        String validation = validateDraft(draft, drafts, index, roadLength);
        if (validation != null) {
            RoadUiWidgets.textWrappedColored(PluginUiColors.INVALID, validation);
        } else {
            PointOfVerticalIntersection pvi = draft.toPvi(middlePvi);
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                chainageDisplay != null
                    ? VerticalAlignmentGeometry.describePvi(pvi, index, total, chainageDisplay)
                    : VerticalAlignmentGeometry.describePvi(pvi, index, total, RoadStationFormat.KILOMETER_PLUS));
            if (middlePvi && draft.curveLength > 0f) {
                String curve = chainageDisplay != null
                    ? VerticalAlignmentGeometry.describeCurveAtPvi(
                        buildPvis(drafts), index, chainageDisplay)
                    : VerticalAlignmentGeometry.describeCurveAtPvi(
                        buildPvis(drafts), index, RoadStationFormat.KILOMETER_PLUS);
                if (!curve.isBlank()) {
                    RoadUiWidgets.textWrappedColored(PluginUiColors.HINT_GRAY, "  " + curve);
                }
            }
        }

        ImGui.pushStyleColor(ImGuiCol.Button, PluginUiColors.DELETE);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, PluginUiColors.DELETE_HOVER);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, PluginUiColors.DELETE_ACTIVE);
        if (ImGui.button(PlotI18n.tr("plugin.road.delete") + "##va_delete")) {
            if (onHistory != null) {
                onHistory.run();
            }
            drafts.remove(index);
            applyDraftsIfChanged(road);
            ImGui.popStyleColor(3);
            ImGui.popID();
            return;
        }
        ImGui.popStyleColor(3);

        ImGui.popID();
    }

    private void applyDraftsIfChanged(Road road) {
        if (road == null) {
            return;
        }
        List<PointOfVerticalIntersection> built = buildPvis(drafts);
        List<PointOfVerticalIntersection> current = road.getVerticalAlignment() != null
            ? road.getVerticalAlignment().getPvis()
            : List.of();
        if (pvisEqual(current, built)) {
            return;
        }
        road.setVerticalAlignment(built.isEmpty() ? null : new RoadVerticalAlignment(built));
        if (!built.isEmpty()) {
            road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        }
    }

    private void syncDrafts(Road road) {
        if (Objects.equals(syncedRoadId, road.getId())) {
            return;
        }
        syncedRoadId = road.getId();
        drafts.clear();
        RoadVerticalAlignment alignment = road.getVerticalAlignment();
        if (alignment == null) {
            return;
        }
        for (PointOfVerticalIntersection pvi : alignment.getPvis()) {
            int index = drafts.size();
            drafts.add(PviDraft.from(pvi, index, alignment.pviCount()));
        }
        if (!drafts.isEmpty()) {
            flatElevation = drafts.getFirst().elevation;
        }
    }

    private void renderValidationMessages(Road road, double roadLength) {
        if (drafts.size() < 2) {
            return;
        }
        RoadVerticalAlignment preview = new RoadVerticalAlignment(buildPvis(drafts));
        for (VerticalAlignmentViolation violation : VerticalAlignmentValidator.validate(preview, roadLength)) {
            RoadValidationMessage message = RoadValidationMessageCatalog.fromVerticalKind(violation.kind());
            if (message != null) {
                Object[] args = violation.relatedPviIndex() != null
                    ? new Object[] {violation.pviIndex() + 1, violation.relatedPviIndex() + 1}
                    : new Object[] {violation.pviIndex() + 1};
                RoadValidationMessageUi.render(new RoadValidationMessage(
                    message.severity(),
                    message.titleKey(),
                    message.detailKey(),
                    args,
                    message.action()));
            }
        }
        double maxGrade = road.getMaxSlope() != null ? road.getMaxSlope() : 8.0;
        for (VerticalProfileDesignRules.Issue issue
                : VerticalProfileDesignRules.assess(preview, roadLength, maxGrade)) {
            String key = switch (issue.kind()) {
                case SHORT_ROAD_MUST_BE_FLAT -> "plugin.road.vertical_alignment_short_road_detail";
                case GRADE_EXCEEDS_LIMIT -> "plugin.road.vertical_alignment_grade_exceeds";
                case GRADE_RUN_TOO_SHORT -> "plugin.road.vertical_alignment_grade_run_short";
                case CONTINUOUS_GRADE_TOO_LONG -> "plugin.road.vertical_alignment_grade_run_long";
                case ELEVATION_CHANGE_NOT_VISIBLE ->
                    "plugin.road.vertical_alignment_elevation_change_not_visible";
            };
            RoadUiWidgets.textWrappedColored(
                issue.kind() == VerticalProfileDesignRules.IssueKind.ELEVATION_CHANGE_NOT_VISIBLE
                    ? PluginUiColors.WARNING_LIGHT
                    : PluginUiColors.INVALID,
                PlotI18n.tr(key, issue.fromPviIndex() + 1, issue.toPviIndex() + 1,
                    issue.actual(), issue.limit()));
        }
    }

    static List<PointOfVerticalIntersection> buildPvis(List<PviDraft> drafts) {
        List<PviDraft> valid = new ArrayList<>();
        for (int i = 0; i < drafts.size(); i++) {
            PviDraft draft = drafts.get(i);
            if (!isStructurallyValid(draft, drafts, i)) {
                continue;
            }
            valid.add(draft);
        }
        List<PointOfVerticalIntersection> pvis = new ArrayList<>();
        for (int i = 0; i < valid.size(); i++) {
            boolean middle = i > 0 && i < valid.size() - 1;
            try {
                pvis.add(valid.get(i).toPvi(middle));
            } catch (IllegalArgumentException ignored) {
                // Skip until user fixes invalid values.
            }
        }
        return pvis;
    }

    static boolean pvisEqual(List<PointOfVerticalIntersection> left, List<PointOfVerticalIntersection> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            PointOfVerticalIntersection a = left.get(i);
            PointOfVerticalIntersection b = right.get(i);
            if (Double.compare(a.getStation(), b.getStation()) != 0
                || Double.compare(a.getElevation(), b.getElevation()) != 0
                || a.getConstraint() != b.getConstraint()) {
                return false;
            }
            Double curveA = a.getCurveLength();
            Double curveB = b.getCurveLength();
            if (curveA == null && curveB == null) {
                continue;
            }
            if (curveA == null || curveB == null
                || Double.compare(curveA, curveB) != 0) {
                return false;
            }
        }
        return true;
    }

    private static String validateDraft(PviDraft draft, List<PviDraft> all, int index, float roadLength) {
        if (draft.station < 0.0f || draft.station > roadLength + 1e-6f) {
            return PlotI18n.tr("plugin.road.vertical_alignment_station_invalid");
        }
        for (int i = 0; i < all.size(); i++) {
            if (i != index && Math.abs(all.get(i).station - draft.station) < 1e-6f) {
                return PlotI18n.tr("plugin.road.vertical_alignment_duplicate", index + 1, i + 1);
            }
        }
        if (index > 0 && draft.station <= all.get(index - 1).station + 1e-6f) {
            return PlotI18n.tr("plugin.road.vertical_alignment_station_not_increasing", index + 1, index);
        }
        return null;
    }

    private static boolean isStructurallyValid(PviDraft draft, List<PviDraft> all, int index) {
        if (draft.station < 0.0f) {
            return false;
        }
        for (int i = 0; i < index; i++) {
            if (Math.abs(all.get(i).station - draft.station) < 1e-6f) {
                return false;
            }
        }
        return true;
    }

    static final class PviDraft {
        float station;
        float elevation;
        float curveLength;
        com.plot.plugin.road.vertical.VerticalControlPointConstraint constraint =
            com.plot.plugin.road.vertical.VerticalControlPointConstraint.FREE;

        static PviDraft from(PointOfVerticalIntersection pvi, int index, int total) {
            PviDraft draft = new PviDraft();
            draft.station = (float) pvi.getStation();
            draft.elevation = (float) pvi.getElevation();
            boolean middle = index > 0 && index < total - 1;
            draft.curveLength = middle && pvi.hasCurve() ? pvi.getCurveLength().floatValue() : 0f;
            draft.constraint = pvi.getConstraint();
            return draft;
        }

        static PviDraft defaultEntry(List<PviDraft> existing, double roadLength) {
            PviDraft draft = new PviDraft();
            if (existing.isEmpty()) {
                draft.station = 0f;
                draft.elevation = 64f;
            } else if (existing.size() == 1) {
                draft.station = (float) roadLength;
                draft.elevation = existing.getFirst().elevation;
            } else {
                float maxStation = 0f;
                for (PviDraft entry : existing) {
                    maxStation = Math.max(maxStation, entry.station);
                }
                draft.station = Math.min((float) roadLength, maxStation + 10f);
                draft.elevation = 64f;
            }
            draft.curveLength = 0f;
            return draft;
        }

        PointOfVerticalIntersection toPvi(boolean allowCurve) {
            Double curve = allowCurve && curveLength > 0f ? (double) curveLength : null;
            return new PointOfVerticalIntersection(station, elevation, curve, constraint);
        }
    }
}
