package com.plot.plugin.road.ui.editor;

import com.plot.plugin.road.RoadDimensionUtils;
import com.plot.plugin.road.RoadParameterLimits;
import com.plot.plugin.road.lamp.RoadLampCompiler;
import com.plot.plugin.road.lamp.RoadLampLibrary;
import com.plot.plugin.road.lamp.RoadLampPlacement;
import com.plot.plugin.road.lamp.RoadLampPlanner;
import com.plot.plugin.road.lamp.RoadLampPresets;
import com.plot.plugin.road.lamp.RoadLampStyle;
import com.plot.plugin.road.manager.RoadChangeKind;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.model.section.StreetFurniture;
import com.plot.plugin.road.ui.RoadUiContext;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;
import net.minecraft.util.math.BlockPos;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

final class RoadLampDesignerWindow extends AbstractRoadFloatingEditor {
    private static final String KEY = "plugin.road.lamp.";
    private static final RoadLampStyle.Layout[] LOWER_LAYOUTS = {
        RoadLampStyle.Layout.INWARD, RoadLampStyle.Layout.OUTWARD, RoadLampStyle.Layout.TWIN, RoadLampStyle.Layout.CROSS
    };
    private final ImString name = new ImString(320);
    private final float[] yaw = {30f};
    private final float[] zoom = {1f};
    private List<RoadLampLibrary.Entry> custom = List.of();
    private UUID customId;
    private RoadLampLibrary library;
    private boolean contextView;
    private RoadLampDraft draft;
    private RoadLampPlacement placementDraft;
    private final RoadLampPreview preview = new RoadLampPreview();
    private RoadLampPlacement contextPlacement;
    private List<RoadLampCompiler.Voxel> contextScene = List.of();
    private RoadLampStyle compiledStyle;
    private List<RoadLampCompiler.Voxel> compiled = List.of();

    @Override protected RoadFloatingEditorKind editorKind() { return RoadFloatingEditorKind.ROAD_LAMP; }
    @Override protected String windowTitleKey() { return KEY + "title"; }
    @Override protected String windowImGuiId() { return "###road_lamp_designer"; }
    @Override protected String closeConfirmPopupId() { return "##road_lamp_close_confirm"; }
    @Override protected RoadChangeKind confirmChangeKind() { return RoadChangeKind.CROSS_SECTION; }
    @Override protected boolean hasInvalidDrafts() { return draft != null && draft.validStyle() == null; }

    @Override
    protected RoadNetworkEditorSession createSession(RoadUiContext ctx, String roadId) {
        var live = ctx.networkManager().getNetwork();
        var furniture = furniture(live, roadId);
        resetDraft(furniture);
        if (library == null) library = new RoadLampLibrary();
        try {
            custom = library.load();
        } catch (IOException e) {
            custom = List.of();
            reportLibraryFailure(ctx, e);
        }
        customId = matchingCustomId(placementDraft != null ? placementDraft.style() : null);
        return RoadNetworkEditorSession.open(live, ctx.networkManager().getNetworkRevision(),
            (open, working) -> !Objects.equals(placement(open, roadId), placement(working, roadId)));
    }

    @Override
    protected void restoreSession() {
        super.restoreSession();
        resetDraft(furniture(session.workingNetwork(), roadId));
        customId = matchingCustomId(placementDraft != null ? placementDraft.style() : null);
    }

    private void resetDraft(StreetFurniture furniture) {
        contextPlacement = null;
        placementDraft = furniture != null ? current(furniture) : null;
        draft = placementDraft != null ? RoadLampDraft.from(placementDraft.style()) : null;
        name.set(draft != null ? draft.name() : "");
    }

    @Override
    protected void applySessionToLive(RoadUiContext ctx) {
        var live = furniture(ctx.networkManager().getNetwork(), roadId);
        if (live != null) live.setRoadLampPlacement(placement(session.workingNetwork(), roadId));
    }

    @Override
    protected void renderWindowBody(RoadUiContext ctx) {
        var road = session.workingNetwork().getRoad(roadId);
        if (road == null) {
            ImGui.text(PlotI18n.tr("plugin.road.floating_editor.road_missing"));
            return;
        }
        ImGui.textUnformatted(road.getName());
        var furniture = road.getCrossSection().getStreetFurniture();
        renderLibrary(furniture, ctx);
        float width = ImGui.getContentRegionAvailX();
        if (width < 700f) {
            renderPreview(placementDraft, road.getCrossSection().resolve(ctx.networkManager().getConfig()));
            renderParameters(furniture);
            return;
        }
        ImGui.beginChild("##lamp_preview", width * 0.46f, 0f, false, 0);
        try {
            renderPreview(placementDraft, road.getCrossSection().resolve(ctx.networkManager().getConfig()));
        } finally {
            ImGui.endChild();
        }
        ImGui.sameLine();
        ImGui.beginChild("##lamp_parameters", 0f, 0f, false, 0);
        try {
            renderParameters(furniture);
        } finally {
            ImGui.endChild();
        }
    }

    private void renderLibrary(StreetFurniture furniture, RoadUiContext ctx) {
        var style = placementDraft.style();
        String display = RoadLampPresets.ALL.stream().filter(preset -> customId == null && preset.style().equals(style))
            .map(preset -> tr("preset." + preset.id())).findFirst().orElse(style.name());
        ImGui.setNextItemWidth(Math.max(115f, ImGui.getContentRegionAvailX() * 0.46f));
        if (ImGui.beginCombo(tr("presets") + "##road_lamp_presets", display)) {
            for (var preset : RoadLampPresets.ALL) {
                if (ImGui.selectable(tr("preset." + preset.id()) + "##" + preset.id(),
                        customId == null && preset.style().equals(style))) {
                    select(furniture, preset.style());
                    customId = null;
                }
            }
            if (!custom.isEmpty()) {
                ImGui.separator();
                for (var preset : custom) {
                    if (ImGui.selectable(preset.style().name() + "##" + preset.id(), preset.id().equals(customId))) {
                        select(furniture, preset.style());
                        customId = preset.id();
                    }
                }
            }
            ImGui.endCombo();
        }
        ImGui.beginDisabled(hasInvalidDrafts());
        if (ImGui.button(tr("save_custom"))) {
            saveCustom(ctx, null, draft.validStyle());
        }
        if (customId != null) {
            ImGui.sameLine();
            if (ImGui.button(tr("update_custom"))) {
                saveCustom(ctx, customId, draft.validStyle());
            }
        }
        ImGui.endDisabled();
        if (customId != null) {
            ImGui.sameLine();
            renderDeleteCustom(ctx);
        }
        if (furniture.getRoadLampPlacement() != null) {
            if (customId == null || ImGui.getContentRegionAvailX() >= 650f) ImGui.sameLine();
            if (ImGui.button(tr("clear_override"))) {
                furniture.setRoadLampPlacement(null);
                customId = null;
                resetDraft(furniture);
            }
        }
        ImGui.separator();
    }

    private UUID matchingCustomId(RoadLampStyle style) {
        return custom.stream().filter(entry -> entry.style().equals(style)).map(RoadLampLibrary.Entry::id)
            .findFirst().orElse(null);
    }

    private void saveCustom(RoadUiContext ctx, UUID id, RoadLampStyle style) {
        try {
            custom = library.save(id, style);
            customId = matchingCustomId(style);
            ctx.status().info(tr("custom_saved"));
        } catch (IllegalArgumentException e) {
            ctx.status().warning(tr("custom_duplicate"));
        } catch (IOException e) {
            reportLibraryFailure(ctx, e);
        }
    }

    private void renderDeleteCustom(RoadUiContext ctx) {
        String popup = tr("delete_custom") + "###delete_custom_lamp";
        if (ImGui.button(tr("delete_custom"))) ImGui.openPopup(popup);
        if (!ImGui.beginPopupModal(popup, ImGuiWindowFlags.AlwaysAutoResize)) return;
        try {
            ImGui.textUnformatted(tr("delete_confirmation"));
            if (ImGui.button(PlotI18n.tr("button.plot.confirm"))) {
                try {
                    custom = library.delete(customId);
                    customId = null;
                    ctx.status().info(tr("custom_deleted"));
                } catch (IOException e) {
                    reportLibraryFailure(ctx, e);
                }
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button(PlotI18n.tr("button.plot.cancel"))) ImGui.closeCurrentPopup();
        } finally {
            ImGui.endPopup();
        }
    }

    private void select(StreetFurniture furniture, RoadLampStyle style) {
        draft = RoadLampDraft.from(style);
        placementDraft = draft.resolve(placementDraft);
        furniture.setRoadLampPlacement(placementDraft);
        name.set(style.name());
    }

    private void renderParameters(StreetFurniture furniture) {
        var active = placementDraft;
        boolean enabled = toggle("enabled", active.enabled());
        var sides = combo("sides", active.sides(), RoadLampPlacement.Sides.values());
        int spacing = slider("spacing", active.spacing(),
            RoadParameterLimits.MIN_STREETLIGHT_SPACING, RoadParameterLimits.MAX_STREETLIGHT_SPACING);
        int inset = sides == RoadLampPlacement.Sides.MEDIAN ? active.inset() : slider("inset", active.inset(), 0, 8);
        int setback = slider("setback", active.junctionSetback(), 0, 32);
        boolean bridge = toggle("bridge", active.bridgeAllowed());
        boolean applyCurrent = false;
        if (furniture.getRoadLampPlacement() == null) {
            ImGui.textWrapped(tr("inactive_draft"));
            ImGui.beginDisabled(hasInvalidDrafts());
            applyCurrent = ImGui.button(tr("apply_defaults"));
            ImGui.endDisabled();

        }
        ImGui.separator();
        ImGui.setNextItemWidth(controlWidth());
        ImGui.inputText(tr("name"), name);
        int height = slider("height", draft.height(), 5, 24);
        var material = combo("material", draft.material(), RoadLampStyle.Material.values());
        boolean plinth = toggle("plinth", draft.plinth());
        var detail = combo("detail", draft.detail(), RoadLampStyle.Detail.values());
        int count = slider("levels", draft.levels().size(), 1, 3);
        var levels = new ArrayList<>(draft.levels());
        while (levels.size() < count) {
            levels.addFirst(new RoadLampStyle.Level(levels.getFirst().y() - 3,
                RoadLampStyle.Layout.TWIN, RoadLampStyle.Mount.TOP, 1, 1, RoadLampStyle.Light.LANTERN));
        }
        while (levels.size() > count) {
            levels.removeFirst();
        }

        for (int i = 0; i < levels.size(); i++) {
            ImGui.pushID(i);
            var existing = levels.get(i);
            boolean top = i == levels.size() - 1;
            ImGui.text(PlotI18n.tr(KEY + "level_number", i + 1));
            var layouts = top ? RoadLampStyle.Layout.values() : LOWER_LAYOUTS;
            var layout = combo("layout", existing.layout(), layouts);
            boolean center = layout == RoadLampStyle.Layout.SINGLE || layout == RoadLampStyle.Layout.TRIPLE;
            var mount = center ? RoadLampStyle.Mount.TOP
                : combo("mount", existing.mount(), RoadLampStyle.Mount.values());
            int y = top ? height - 1 : slider("level_y", existing.y(), 4, Math.max(4, height - 2));
            int reach = layout == RoadLampStyle.Layout.SINGLE ? existing.reach() : slider("reach", existing.reach(), 1, 4);
            int chain = mount == RoadLampStyle.Mount.CHAIN ? slider("chain", existing.chain(), 1, 3) : existing.chain();
            var light = combo("light", existing.light(), RoadLampStyle.Light.values());
            levels.set(i, new RoadLampStyle.Level(y, layout, mount, reach, chain, light));
            ImGui.popID();
        }

        var candidate = new RoadLampDraft(name.get(), height, material, plinth, levels, detail);
        var placementControls = new RoadLampPlacement(active.style(), sides, spacing, inset, setback, enabled, bridge);
        var resolved = candidate.resolve(placementControls);
        if (resolved != null && (applyCurrent || !candidate.equals(draft) || !placementControls.equals(active)
                || !resolved.equals(active))) {
            furniture.setRoadLampPlacement(resolved);
        }
        draft = candidate;
        placementDraft = resolved != null ? resolved : placementControls;
        if (resolved == null) {
            ImGui.textWrapped(draft.hasInvalidName() ? tr("name_required") : tr("level_error"));
            ImGui.textWrapped(tr("preview_last_valid"));
        }
    }

    private void renderPreview(RoadLampPlacement placement, ResolvedCrossSection section) {
        contextView = toggle("road_context", contextView);
        ImGui.setNextItemWidth(controlWidth());
        ImGui.sliderFloat(tr("rotate"), yaw, -180f, 180f, "%.0f°");
        ImGui.setNextItemWidth(controlWidth());
        ImGui.sliderFloat(tr("zoom"), zoom, 0.6f, 2f, "%.1fx");
        if (!placement.style().equals(compiledStyle)) {
            compiledStyle = placement.style();
            compiled = RoadLampCompiler.compile(compiledStyle);
        }
        if (contextView && !placement.equals(contextPlacement)) {
            contextScene = sectionScene(section, placement);
            contextPlacement = placement;
        }
        var scene = contextView ? contextScene : compiled;
        float height = Math.clamp(ImGui.getContentRegionAvailY() - 36f, 180f, 380f);
        preview.render(scene, height, yaw[0], zoom[0]);
        int totalHeight = compiled.stream().mapToInt(voxel -> voxel.position().getY()).max().orElse(0) + 1;
        int heads = compiledStyle.levels().stream().mapToInt(level -> switch (level.layout()) {
            case SINGLE, INWARD, OUTWARD -> 1;
            case TWIN -> 2;
            case TRIPLE -> 3;
            case CROSS -> 4;
        }).sum();
        ImGui.textWrapped(PlotI18n.tr(KEY + "structure_size", totalHeight, heads, compiled.size()));
        if (contextView) ImGui.textWrapped(PlotI18n.tr(KEY + "context_hint", placement.spacing()));
    }

    private List<RoadLampCompiler.Voxel> sectionScene(ResolvedCrossSection section, RoadLampPlacement placement) {
        var scene = new ArrayList<RoadLampCompiler.Voxel>();
        int start = -placement.spacing() - 3;
        int end = placement.spacing() + 3;
        int left = RoadLampPlanner.lateralOffset(section, 1, 0);
        int right = RoadLampPlanner.lateralOffset(section, -1, 0);
        // Bound the local display when the road has a very wide section.
        for (int x = Math.max(-48, right); x <= Math.min(48, left); x++) {
            var band = section.surfaceBandAtLateral(x);
            if (band.isEmpty()) continue;
            String block = band.get().materialId();
            if (block == null) block = section.carriagewayMaterial.getPrimaryMaterial();
            for (int z = start; z <= end; z++) {
                scene.add(new RoadLampCompiler.Voxel(new BlockPos(x, -1, z), block));
            }
        }
        for (int z = -placement.spacing(); placement.enabled() && z <= placement.spacing(); z += placement.spacing()) {
            int rightZ = z + (int) Math.round(placement.rightSidePhase());
            switch (placement.sides()) {
                case LEFT -> previewSide(scene, section, placement, 1, z);
                case RIGHT -> previewSide(scene, section, placement, -1, z);
                case BOTH, STAGGERED -> {
                    previewSide(scene, section, placement, 1, z);
                    if (rightZ <= placement.spacing()) previewSide(scene, section, placement, -1, rightZ);
                }
                case MEDIAN -> {
                    if (section.includeMedian) {
                        scene.addAll(RoadLampCompiler.position(compiled, new BlockPos(0, section.medianHeight, z), 1, 0));
                    }
                }
            }
        }
        if (section.includeMedian) {
            int min = RoadDimensionUtils.minLateralOffset(section.medianWidth);
            int max = RoadDimensionUtils.maxLateralOffset(section.medianWidth);
            for (int x = Math.max(-48, min); x <= Math.min(48, max); x++) {
                for (int y = 0; y < section.medianHeight; y++) {
                    for (int z = start; z <= end; z++) {
                        scene.add(new RoadLampCompiler.Voxel(new BlockPos(x, y, z), section.medianMaterial));
                    }
                }
            }
        }
        return scene;
    }

    private void previewSide(List<RoadLampCompiler.Voxel> scene, ResolvedCrossSection section,
                             RoadLampPlacement placement, int side, int z) {
        int x = RoadLampPlanner.lateralOffset(section, side, placement.inset());
        if (RoadLampPlanner.supportLayer(section, side, placement.inset()) != null && Math.abs(x) <= 48) {
            scene.addAll(RoadLampCompiler.position(compiled, new BlockPos(x, 0, z), -side, 0));
        }
    }

    private static StreetFurniture furniture(RoadNetwork network, String roadId) {
        var road = network.getRoad(roadId);
        return road != null ? road.getCrossSection().getStreetFurniture() : null;
    }

    private static RoadLampPlacement placement(RoadNetwork network, String roadId) {
        var furniture = furniture(network, roadId);
        return furniture != null ? furniture.getRoadLampPlacement() : null;
    }

    private static RoadLampPlacement current(StreetFurniture furniture) {
        var current = furniture.getRoadLampPlacement();
        if (current != null) return current;
        Integer spacing = furniture.getStreetlightSpacing();
        return new RoadLampPlacement(RoadLampPresets.ALL.getFirst().style(), RoadLampPlacement.Sides.BOTH,
            spacing != null && spacing > 0 ? RoadParameterLimits.normalizeStreetlightSpacing(spacing) : 16,
            0, 4, true, false);
    }

    private static boolean toggle(String key, boolean value) {
        return ImGui.checkbox(tr(key), value) ? !value : value;
    }

    private static int slider(String key, int value, int min, int max) {
        int[] buffer = {value};
        ImGui.setNextItemWidth(controlWidth());
        return ImGui.sliderInt(tr(key), buffer, min, max) ? Math.clamp(buffer[0], min, max) : value;
    }

    private static <E extends Enum<E>> E combo(String key, E current, E[] values) {
        ImGui.setNextItemWidth(controlWidth());
        if (ImGui.beginCombo(tr(key), enumLabel(key, current))) {
            for (E value : values) {
                if (ImGui.selectable(enumLabel(key, value), value == current)) current = value;
            }
            ImGui.endCombo();
        }
        return current;
    }

    private static float controlWidth() {
        return Math.max(80f, 0.52f * ImGui.getContentRegionAvailX());
    }

    private static String enumLabel(String group, Enum<?> value) {
        return tr(group + "." + value.name().toLowerCase(Locale.ROOT));
    }

    private static String tr(String key) { return PlotI18n.tr(KEY + key); }

    private static void reportLibraryFailure(RoadUiContext ctx, IOException e) {
        LoggerFactory.getLogger(RoadLampDesignerWindow.class).warn("Road lamp library unavailable", e);
        ctx.status().warning(tr("custom_error"));
    }
}
