package com.plot.plugin.road.ui.editor;

import com.plot.core.material.BlockColorRegistry;
import com.plot.plugin.road.RoadDimensionUtils;
import com.plot.plugin.road.RoadMaterialUtils;
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
import com.plot.plugin.road.ui.RoadPanelScopedStatus;
import com.plot.plugin.road.ui.RoadUiContext;
import com.plot.plugin.road.ui.RoadUiWidgets;
import com.plot.plugin.ui.PluginSectionCard;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.ui.component.BlockIconRenderer;
import com.plot.ui.component.UIUtils;
import com.plot.ui.imgui.GuiOverlayRenderer;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
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
    private static final float LIGHT_ICON_SIZE = 28f;
    private static final float LIGHT_ICON_GAP = 4f;
    private static final RoadLampStyle.Layout[] LOWER_LAYOUTS = {
        RoadLampStyle.Layout.INWARD, RoadLampStyle.Layout.OUTWARD, RoadLampStyle.Layout.TWIN, RoadLampStyle.Layout.CROSS
    };
    private final ImString name = new ImString(320);
    private final ImString lightFilter = new ImString(64);
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
    /** 参数区层级卡焦点；-1 表示无高亮 */
    private int hoveredOrFocusedLevelIndex = -1;

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
        name.set(draft != null ? localizedStyleName(draft.name()) : "");
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
        var furniture = road.getCrossSection().getStreetFurniture();
        boolean enabled = renderEnabledAndPresets(furniture, ctx);
        if (!enabled) {
            return;
        }
        var section = road.getCrossSection().resolve(ctx.networkManager().getConfig());
        float width = ImGui.getContentRegionAvailX();
        float availY = ImGui.getContentRegionAvailY();
        if (width < 700f) {
            // 先写参数再预览，避免本帧改动要等下一帧才反映到编译结果。
            renderParameters(furniture, section.includeMedian);
            renderPreview(placementDraft, section);
            return;
        }
        float previewWidth = width * 0.46f;
        float spacing = ImGui.getStyle().getItemSpacingX();
        float paramsWidth = Math.max(160f, width - previewWidth - spacing);
        float rowStartX = ImGui.getCursorPosX();
        float rowStartY = ImGui.getCursorPosY();
        float rowHeight = Math.max(220f, availY);
        // 先更新右侧参数，再画左侧预览；两侧共用同一行高，避免父级内容被算成「双倍高度」冒出外层滚动条。
        ImGui.setCursorPos(rowStartX + previewWidth + spacing, rowStartY);
        ImGui.beginChild("##lamp_parameters", paramsWidth, rowHeight, false, 0);
        try {
            renderParameters(furniture, section.includeMedian);
        } finally {
            ImGui.endChild();
        }
        ImGui.setCursorPos(rowStartX, rowStartY);
        ImGui.beginChild("##lamp_preview", previewWidth, rowHeight, false, ImGuiWindowFlags.NoScrollbar);
        try {
            renderPreview(placementDraft, section);
        } finally {
            ImGui.endChild();
        }
        ImGui.setCursorPos(rowStartX, rowStartY + rowHeight);
    }

    /** 首行：布置开关 + 预设；关闭布置时不渲染下方预览与参数。 */
    @Override
    protected boolean showPanelStatusInBodyHeader() {
        return false;
    }

    private boolean renderEnabledAndPresets(StreetFurniture furniture, RoadUiContext ctx) {
        // 旧存档/内置预设可能仍是英文内部名，同步成当前语言显示名。
        String localizedName = localizedStyleName(name.get());
        if (!localizedName.equals(name.get())) {
            name.set(localizedName);
        }
        boolean enabled = toggle("enabled", placementDraft.enabled());
        applyEnabled(furniture, enabled);
        ImGui.sameLine(0f, ImGui.getStyle().getItemSpacingX() * 2f);

        var style = placementDraft.style();
        String display = RoadLampPresets.ALL.stream()
            .filter(preset -> customId == null && sameLampDesign(preset.style(), style))
            .map(preset -> tr("preset." + preset.id()))
            .findFirst()
            .orElseGet(() -> localizedStyleName(style.name()));
        // 预设下拉收窄，右侧留给面板反馈（如重名警告）。
        float comboWidth = Math.clamp(ImGui.getContentRegionAvailX() * 0.28f, 100f, 160f);
        ImGui.setNextItemWidth(comboWidth);
        if (ImGui.beginCombo(tr("presets") + "##road_lamp_presets", display)) {
            for (var preset : RoadLampPresets.ALL) {
                if (ImGui.selectable(tr("preset." + preset.id()) + "##" + preset.id(),
                        customId == null && sameLampDesign(preset.style(), style))) {
                    // 写入本地化名称，避免样式名称/预设框在改参数后掉回英文内部名。
                    select(furniture, renameStyle(preset.style(), tr("preset." + preset.id())), enabled);
                    customId = null;
                }
            }
            if (!custom.isEmpty()) {
                ImGui.separator();
                for (var preset : custom) {
                    if (ImGui.selectable(preset.style().name() + "##" + preset.id(), preset.id().equals(customId))) {
                        select(furniture, preset.style(), enabled);
                        customId = preset.id();
                    }
                }
            }
            ImGui.endCombo();
        }
        renderPanelStatusInline();
        if (!enabled) {
            return false;
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
        return true;
    }

    private void applyEnabled(StreetFurniture furniture, boolean enabled) {
        var active = placementDraft;
        if (active.enabled() == enabled) {
            return;
        }
        var next = new RoadLampPlacement(
            active.style(), active.sides(), active.spacing(), active.inset(),
            active.junctionSetback(), enabled, active.bridgeAllowed());
        var resolved = draft.resolve(next);
        if (resolved != null) {
            furniture.setRoadLampPlacement(resolved);
            placementDraft = resolved;
            return;
        }
        placementDraft = next;
        var live = furniture.getRoadLampPlacement();
        if (live != null) {
            furniture.setRoadLampPlacement(new RoadLampPlacement(
                live.style(), live.sides(), live.spacing(), live.inset(),
                live.junctionSetback(), enabled, live.bridgeAllowed()));
        }
    }

    private UUID matchingCustomId(RoadLampStyle style) {
        return custom.stream().filter(entry -> entry.style().equals(style)).map(RoadLampLibrary.Entry::id)
            .findFirst().orElse(null);
    }

    private void saveCustom(RoadUiContext ctx, UUID id, RoadLampStyle style) {
        try {
            custom = library.save(id, style);
            customId = matchingCustomId(style);
            panelSuccess(tr("custom_saved"));
            RoadPanelScopedStatus.clearFromToolbarIfPresent(ctx.status());
        } catch (IllegalArgumentException e) {
            panelWarning(tr("custom_duplicate"));
            RoadPanelScopedStatus.clearFromToolbarIfPresent(ctx.status());
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
                    panelInfo(tr("custom_deleted"));
                    RoadPanelScopedStatus.clearFromToolbarIfPresent(ctx.status());
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

    private void select(StreetFurniture furniture, RoadLampStyle style, boolean enabled) {
        draft = RoadLampDraft.from(style);
        var next = draft.resolve(new RoadLampPlacement(
            style, placementDraft.sides(), placementDraft.spacing(), placementDraft.inset(),
            placementDraft.junctionSetback(), enabled, placementDraft.bridgeAllowed()));
        placementDraft = next != null ? next : new RoadLampPlacement(
            style, placementDraft.sides(), placementDraft.spacing(), placementDraft.inset(),
            placementDraft.junctionSetback(), enabled, placementDraft.bridgeAllowed());
        if (next != null) {
            furniture.setRoadLampPlacement(next);
        }
        name.set(localizedStyleName(style.name()));
    }

    /** 忽略样式名称，只比较灯杆几何与材质（用于判定是否仍为某内置预设）。 */
    private static boolean sameLampDesign(RoadLampStyle left, RoadLampStyle right) {
        return left.height() == right.height()
            && left.material() == right.material()
            && left.plinth() == right.plinth()
            && left.detail() == right.detail()
            && Objects.equals(left.materialOverrides(), right.materialOverrides())
            && left.levels().equals(right.levels());
    }

    private static RoadLampStyle renameStyle(RoadLampStyle style, String name) {
        return new RoadLampStyle(
            name,
            style.height(),
            style.material(),
            style.plinth(),
            style.levels(),
            style.detail(),
            style.materialOverrides());
    }

    /** 内置预设的英文内部名映射为当前语言显示名。 */
    private static String localizedStyleName(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        for (var preset : RoadLampPresets.ALL) {
            String localized = tr("preset." + preset.id());
            if (preset.style().name().equals(name) || localized.equals(name)) {
                return localized;
            }
        }
        return name;
    }

    private void renderParameters(StreetFurniture furniture, boolean includeMedian) {
        var active = placementDraft;
        final var sidesBox = new Object() {
            RoadLampPlacement.Sides value = active.sides();
        };
        final var spacingBox = new Object() {
            int value = active.spacing();
        };
        final var insetBox = new Object() {
            int value = active.inset();
        };
        final var setbackBox = new Object() {
            int value = active.junctionSetback();
        };
        final var bridgeBox = new Object() {
            boolean value = active.bridgeAllowed();
        };
        final var applyBox = new Object() {
            boolean value = false;
        };

        PluginSectionCard.render(
            "##lamp_sec_placement",
            tr("section.placement"),
            null,
            PluginSectionCard.SectionTone.NEUTRAL,
            true,
            () -> {
                sidesBox.value = combo("sides", sidesBox.value, RoadLampPlacement.Sides.values());
                if (sidesBox.value == RoadLampPlacement.Sides.MEDIAN && !includeMedian) {
                    RoadUiWidgets.textWrappedColored(PluginUiColors.WARNING, tr("median_unavailable"));
                }
                spacingBox.value = slider("spacing", spacingBox.value,
                    RoadParameterLimits.MIN_STREETLIGHT_SPACING, RoadParameterLimits.MAX_STREETLIGHT_SPACING);
                insetBox.value = sidesBox.value == RoadLampPlacement.Sides.MEDIAN
                    ? insetBox.value
                    : slider("inset", insetBox.value, 0, 8);
                setbackBox.value = slider("setback", setbackBox.value, 0, 32);
                bridgeBox.value = toggle("bridge", bridgeBox.value);
                if (furniture.getRoadLampPlacement() == null) {
                    ImGui.textWrapped(tr("inactive_draft"));
                    ImGui.beginDisabled(hasInvalidDrafts());
                    applyBox.value = ImGui.button(tr("apply_defaults"));
                    ImGui.endDisabled();
                }
            });

        final var heightBox = new Object() {
            int value = draft.height();
        };
        final var materialBox = new Object() {
            RoadLampStyle.Material value = draft.material();
        };
        final var plinthBox = new Object() {
            boolean value = draft.plinth();
        };
        final var detailBox = new Object() {
            RoadLampStyle.Detail value = draft.detail();
        };
        final var countBox = new Object() {
            int value = draft.levels().size();
        };
        // 材质覆盖以 draft 为权威；方块选择器异步回调直接写回 draft/furniture，不能用帧内临时引用。
        var materialOverrides = draft.materialOverrides() == null
            ? RoadLampStyle.MaterialOverrides.NONE
            : draft.materialOverrides();

        PluginSectionCard.render(
            "##lamp_sec_pole",
            tr("section.pole"),
            null,
            PluginSectionCard.SectionTone.NEUTRAL,
            true,
            () -> {
                ImGui.setNextItemWidth(controlWidth());
                ImGui.inputText(tr("name"), name);
                heightBox.value = slider("height", heightBox.value, 5, 24);
                materialBox.value = combo("material", materialBox.value, RoadLampStyle.Material.values());
                renderMaterialOverrides(furniture, materialBox.value, materialOverrides);
                plinthBox.value = toggle("plinth", plinthBox.value);
                detailBox.value = combo("detail", detailBox.value, RoadLampStyle.Detail.values());
                countBox.value = slider("levels", countBox.value, 1, 3);
            });

        var sized = RoadLampDraft.withLevelCount(heightBox.value, draft.levels(), countBox.value);
        int height = sized.height();
        final ArrayList<RoadLampStyle.Level> levels = new ArrayList<>(sized.levels());
        var topSeed = levels.getLast();
        levels.set(levels.size() - 1, new RoadLampStyle.Level(
            height - 1, topSeed.layout(), topSeed.mount(), topSeed.reach(), topSeed.chain(), topSeed.light()));

        final int levelCount = levels.size();
        final int[] heightRef = {height};
        PluginSectionCard.render(
            "##lamp_sec_levels",
            tr("section.levels"),
            PlotI18n.tr(KEY + "section.levels_summary", levelCount),
            PluginSectionCard.SectionTone.NEUTRAL,
            true,
            () -> {
                for (int i = 0; i < levels.size(); i++) {
                    final int levelIndex = i;
                    ImGui.pushID(levelIndex);
                    var existing = levels.get(levelIndex);
                    boolean top = levelIndex == levels.size() - 1;
                    PluginSectionCard.SectionTone tone = switch (levelIndex) {
                        case 0 -> PluginSectionCard.SectionTone.LEVEL_0;
                        case 1 -> PluginSectionCard.SectionTone.LEVEL_1;
                        default -> PluginSectionCard.SectionTone.LEVEL_2;
                    };
                    String layoutLabel = enumLabel("layout", existing.layout());
                    String title = PlotI18n.tr(KEY + "level_card_title", levelIndex + 1, layoutLabel);
                    String summary = PlotI18n.tr(KEY + "level_card_summary", existing.y());
                    var levelCard = PluginSectionCard.renderResult(
                        "##lamp_level_" + levelIndex,
                        title,
                        summary,
                        tone,
                        levelIndex == 0,
                        () -> {
                            var current = levels.get(levelIndex);
                            var layouts = top ? RoadLampStyle.Layout.values() : LOWER_LAYOUTS;
                            var layout = combo("layout", current.layout(), layouts);
                            boolean center = layout == RoadLampStyle.Layout.SINGLE
                                || layout == RoadLampStyle.Layout.TRIPLE;
                            var mount = center ? RoadLampStyle.Mount.TOP
                                : combo("mount", current.mount(), RoadLampStyle.Mount.values());
                            int previousY = levelIndex == 0 ? 0 : levels.get(levelIndex - 1).y() + 1;
                            int minY = RoadLampDraft.minLevelY(previousY, mount, current.chain());
                            int maxY = top
                                ? heightRef[0] - 1
                                : Math.max(minY, RoadLampDraft.levelLowest(levels.get(levelIndex + 1)) - 2);
                            int y = top
                                ? heightRef[0] - 1
                                : Math.clamp(slider("level_y", current.y(), minY, maxY), minY, maxY);
                            int reach = layout == RoadLampStyle.Layout.SINGLE
                                ? current.reach()
                                : slider("reach", current.reach(), 1, 4);
                            int chain = mount == RoadLampStyle.Mount.CHAIN
                                ? slider("chain", current.chain(), 1, 3)
                                : current.chain();
                            if (!top && mount == RoadLampStyle.Mount.CHAIN) {
                                minY = RoadLampDraft.minLevelY(previousY, mount, chain);
                                maxY = Math.max(minY, RoadLampDraft.levelLowest(levels.get(levelIndex + 1)) - 2);
                                y = Math.clamp(y, minY, maxY);
                            }
                            var light = lightIconPicker(current.light(), mount);
                            levels.set(levelIndex, new RoadLampStyle.Level(y, layout, mount, reach, chain, light));
                        });
                    if (levelCard.hoveredOrActive()) {
                        hoveredOrFocusedLevelIndex = levelIndex;
                    }
                    ImGui.popID();
                }
            });

        if (hoveredOrFocusedLevelIndex >= levels.size()) {
            hoveredOrFocusedLevelIndex = levels.isEmpty() ? -1 : levels.size() - 1;
        }

        var reconciled = RoadLampDraft.reconcileLevels(height, levels);
        height = reconciled.height();
        levels.clear();
        levels.addAll(reconciled.levels());
        renderDetailInactiveHint(detailBox.value, levels);

        // 异步方块选择可能已改写 draft.materialOverrides；本帧控件以 draft 最新覆盖为准。
        var overridesForCandidate = draft.materialOverrides() == null
            ? RoadLampStyle.MaterialOverrides.NONE
            : draft.materialOverrides();
        var candidate = new RoadLampDraft(
            name.get(),
            height,
            materialBox.value,
            plinthBox.value,
            levels,
            detailBox.value,
            overridesForCandidate);
        var placementControls = new RoadLampPlacement(
            active.style(), sidesBox.value, spacingBox.value, insetBox.value, setbackBox.value,
            active.enabled(), bridgeBox.value);
        var resolved = candidate.resolve(placementControls);
        if (resolved != null && (applyBox.value || !candidate.equals(draft) || !placementControls.equals(active)
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
        preview.render(scene, height, yaw, zoom, hoveredOrFocusedLevelIndex);
        int totalHeight = compiled.stream().mapToInt(voxel -> voxel.position().getY()).max().orElse(0) + 1;
        int heads = compiledStyle.levels().stream().mapToInt(level -> switch (level.layout()) {
            case SINGLE, INWARD, OUTWARD -> 1;
            case TWIN -> 2;
            case TRIPLE -> 3;
            case CROSS -> 4;
        }).sum();
        ImGui.textWrapped(PlotI18n.tr(KEY + "structure_size", totalHeight, heads, compiled.size()));
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
            switch (placement.sides()) {
                case LEFT -> previewSide(scene, section, placement, 1, z);
                case RIGHT -> previewSide(scene, section, placement, -1, z);
                case BOTH -> {
                    previewSide(scene, section, placement, 1, z);
                    previewSide(scene, section, placement, -1, z);
                }
                case STAGGERED -> {
                    int rightZ = RoadLampPlanner.nearestStationSample(z + placement.rightSidePhase());
                    previewSide(scene, section, placement, 1, z);
                    if (rightZ <= placement.spacing()) {
                        previewSide(scene, section, placement, -1, rightZ);
                    }
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

    /**
     * 材质套装下方的可选槽位覆盖；默认折叠，留空沿用套装方块。
     * 方块选择器异步回调必须写回 {@link #draft}/{@code furniture}，不能写帧内临时变量。
     */
    private void renderMaterialOverrides(
            StreetFurniture furniture,
            RoadLampStyle.Material theme,
            RoadLampStyle.MaterialOverrides overrides) {
        var current = overrides == null ? RoadLampStyle.MaterialOverrides.NONE : overrides;
        String summary = current.isEmpty()
            ? tr("material_overrides_summary_none")
            : tr("material_overrides_summary_active");
        if (!ImGui.collapsingHeader(
                tr("material_overrides") + "  ·  " + summary + "##lamp_material_overrides")) {
            return;
        }
        RoadUiWidgets.textWrappedColored(PluginUiColors.HINT_GRAY, tr("material_overrides_hint"));
        renderMaterialOverrideSlot(
            "override_pole", current.pole(), theme.poleId(),
            id -> commitMaterialOverrides(furniture, o -> o.withPole(id)));
        renderMaterialOverrideSlot(
            "override_beam", current.beam(), theme.beamId(),
            id -> commitMaterialOverrides(furniture, o -> o.withBeam(id)));
        renderMaterialOverrideSlot(
            "override_trim", current.trim(), theme.trimId(),
            id -> commitMaterialOverrides(furniture, o -> o.withTrim(id)));
        renderMaterialOverrideSlot(
            "override_slab", current.slab(), theme.slabId(),
            id -> commitMaterialOverrides(furniture, o -> o.withSlab(id)));
        if (!current.isEmpty()
                && ImGui.button(tr("material_overrides_clear_all") + "##lamp_mat_clear_all")) {
            commitMaterialOverrides(furniture, ignored -> RoadLampStyle.MaterialOverrides.NONE);
        }
    }

    private void commitMaterialOverrides(
            StreetFurniture furniture,
            java.util.function.UnaryOperator<RoadLampStyle.MaterialOverrides> update) {
        if (draft == null || furniture == null || update == null) {
            return;
        }
        var baseOverrides = draft.materialOverrides() == null
            ? RoadLampStyle.MaterialOverrides.NONE
            : draft.materialOverrides();
        var nextOverrides = update.apply(baseOverrides);
        if (Objects.equals(baseOverrides, nextOverrides)) {
            return;
        }
        var nextDraft = new RoadLampDraft(
            draft.name(),
            draft.height(),
            draft.material(),
            draft.plinth(),
            draft.levels(),
            draft.detail(),
            nextOverrides);
        draft = nextDraft;
        var basePlacement = placementDraft != null
            ? placementDraft
            : furniture.getRoadLampPlacement();
        if (basePlacement == null) {
            return;
        }
        var resolved = nextDraft.resolve(basePlacement);
        if (resolved != null) {
            furniture.setRoadLampPlacement(resolved);
            placementDraft = resolved;
            compiledStyle = null;
        }
    }

    private static void renderMaterialOverrideSlot(
            String key,
            String override,
            String themeFallback,
            java.util.function.Consumer<String> onPicked) {
        boolean hasOverride = override != null && !override.isBlank();
        String effective = hasOverride ? override : themeFallback;
        String display = hasOverride
            ? RoadMaterialUtils.getDisplayName(effective)
            : tr("material_override_inherit") + " · " + RoadMaterialUtils.getDisplayName(themeFallback);
        ImGui.text(tr(key));
        ImGui.sameLine();
        if (ImGui.button(display + "##" + key + "_pick", 0, 0)) {
            UIUtils.openBlockPicker(effective, onPicked);
        }
        if (hasOverride) {
            ImGui.sameLine();
            if (ImGui.smallButton(tr("material_override_clear") + "##" + key + "_clear")) {
                onPicked.accept(null);
            }
        }
    }

    private static boolean toggle(String key, boolean value) {
        return ImGui.checkbox(tr(key), value) != value;
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

    private static void renderDetailInactiveHint(RoadLampStyle.Detail detail, List<RoadLampStyle.Level> levels) {
        if (detail.isVisiblyActive(levels)) {
            return;
        }
        String key = switch (detail) {
            case URBAN -> "detail_hint.urban";
            case TIMBER -> "detail_hint.timber";
            case EAVES -> "detail_hint.eaves";
            case NONE, HERITAGE -> null;
        };
        if (key != null) {
            ImGui.textDisabled(tr(key));
        }
    }

    /**
     * 灯具：触发按钮打开图标面板；悬停显示名称，点击选中。
     * 方块色块由 ImGui 绘制（可裁剪），原生物品图标经 overlay 叠在可见格上。
     */
    private RoadLampStyle.Light lightIconPicker(RoadLampStyle.Light current, RoadLampStyle.Mount mount) {
        if (!current.supportsMount(mount)) {
            current = RoadLampStyle.Light.LANTERN;
        }
        String currentLabel = lightLabel(current);
        float btnW = controlWidth();
        float btnH = LIGHT_ICON_SIZE + 8f;
        ImGui.alignTextToFramePadding();
        ImGui.text(tr("light"));
        ImGui.sameLine(0f, ImGui.getStyle().getItemInnerSpacingX());
        if (ImGui.button("##lamp_light_trigger", btnW, btnH)) {
            ImGui.openPopup("##lamp_light_popup");
        }
        float triggerX = ImGui.getItemRectMinX() + 4f;
        float triggerY = ImGui.getItemRectMinY() + (btnH - LIGHT_ICON_SIZE) * 0.5f;
        paintLightSwatch(triggerX, triggerY, LIGHT_ICON_SIZE, current, true);
        queueLightItemIcon(triggerX, triggerY, LIGHT_ICON_SIZE, current);
        var draw = ImGui.getWindowDrawList();
        draw.addText(
            triggerX + LIGHT_ICON_SIZE + 6f,
            ImGui.getItemRectMinY() + (btnH - ImGui.getTextLineHeight()) * 0.5f,
            ImGui.getColorU32(imgui.flag.ImGuiCol.Text, 1f),
            currentLabel);

        ImGui.setNextWindowSizeConstraints(260f, 180f, 440f, 380f);
        if (ImGui.beginPopup("##lamp_light_popup")) {
            ImGui.setNextItemWidth(-1f);
            ImGui.inputTextWithHint("##lamp_light_filter", tr("light_filter_hint"), lightFilter);
            String query = lightFilter.get().strip().toLowerCase(Locale.ROOT);
            float cell = LIGHT_ICON_SIZE + LIGHT_ICON_GAP;
            if (ImGui.beginChild("##lamp_light_grid", 0f, 0f, false, 0)) {
                for (RoadLampStyle.CatalogGroup group : RoadLampStyle.CatalogGroup.values()) {
                    boolean header = false;
                    int column = 0;
                    int columns = Math.max(1, (int) ((ImGui.getContentRegionAvailX() + LIGHT_ICON_GAP) / cell));
                    for (RoadLampStyle.Light value : RoadLampStyle.Light.values()) {
                        if (!value.inCatalog() || value.catalogGroup() != group
                                || !isRegisteredLight(value) || !value.supportsMount(mount)) {
                            continue;
                        }
                        String label = lightLabel(value);
                        if (!query.isEmpty()
                                && !label.toLowerCase(Locale.ROOT).contains(query)
                                && !value.name().toLowerCase(Locale.ROOT).contains(query)
                                && !value.blockId().toLowerCase(Locale.ROOT).contains(query)) {
                            continue;
                        }
                        if (!header) {
                            ImGui.separator();
                            ImGui.textDisabled(tr("light_group." + group.name().toLowerCase(Locale.ROOT)));
                            header = true;
                            column = 0;
                        }
                        if (column > 0) {
                            ImGui.sameLine(0f, LIGHT_ICON_GAP);
                        }
                        ImGui.pushID(value.name());
                        boolean selected = value == current;
                        if (ImGui.invisibleButton("##pick", LIGHT_ICON_SIZE, LIGHT_ICON_SIZE)) {
                            current = value;
                            ImGui.closeCurrentPopup();
                        }
                        float x = ImGui.getItemRectMinX();
                        float y = ImGui.getItemRectMinY();
                        paintLightSwatch(x, y, LIGHT_ICON_SIZE, value, selected);
                        if (ImGui.isItemVisible()) {
                            queueLightItemIcon(x, y, LIGHT_ICON_SIZE, value);
                        }
                        if (ImGui.isItemHovered()) {
                            UIUtils.setTooltip(label);
                        }
                        ImGui.popID();
                        column = (column + 1) % columns;
                    }
                }
            }
            ImGui.endChild();
            ImGui.endPopup();
        }
        return current;
    }

    /**
     * 使用语言包里的灯具名（字体会扫 zh_cn 入字库），避免直接用游戏内名称时缺字显示为「?」。
     */
    private static String lightLabel(RoadLampStyle.Light light) {
        String key = KEY + "light." + light.name().toLowerCase(Locale.ROOT);
        String translated = PlotI18n.tr(key);
        if (translated != null && !translated.isBlank() && !translated.equals(key)) {
            return translated;
        }
        return RoadMaterialUtils.getDisplayName(light.blockId());
    }

    private static void paintLightSwatch(
            float x, float y, float size, RoadLampStyle.Light light, boolean selected) {
        var draw = ImGui.getWindowDrawList();
        draw.addRectFilled(x, y, x + size, y + size, toImGuiAbgr(BlockColorRegistry.colorFor(light.blockId())), 2f);
        int border = selected ? PluginUiColors.ACCENT_BLUE : PluginUiColors.PANEL_BORDER;
        draw.addRect(x, y, x + size, y + size, border, 2f, 0, selected ? 2f : 1f);
    }

    private static void queueLightItemIcon(float x, float y, float size, RoadLampStyle.Light light) {
        try {
            var block = Registries.BLOCK.get(Identifier.of(light.blockId()));
            ItemStack stack = BlockIconRenderer.getItemStackForBlock(block);
            if (stack != null && !stack.isEmpty()) {
                GuiOverlayRenderer.queueItem(stack, x + 1f, y + 1f, Math.max(0.75f, (size - 2f) / 16f));
            }
        } catch (RuntimeException | Error ignored) {
            // 无 bootstrap 或方块无物品形态时仅保留色块。
        }
    }

    /** BlockColorRegistry 为 ARGB；ImGui draw list 为 ABGR。 */
    private static int toImGuiAbgr(int argb) {
        int a = (argb >>> 24) & 255;
        int r = (argb >> 16) & 255;
        int g = (argb >> 8) & 255;
        int b = argb & 255;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    private static boolean isRegisteredLight(RoadLampStyle.Light light) {
        try {
            return Registries.BLOCK.containsId(Identifier.of(light.blockId()));
        } catch (RuntimeException | Error e) {
            // 无 Minecraft bootstrap 的单测路径：仍展示全部选项。
            return true;
        }
    }

    private static float controlWidth() {
        return Math.max(80f, 0.52f * ImGui.getContentRegionAvailX());
    }

    private static String enumLabel(String group, Enum<?> value) {
        return tr(group + "." + value.name().toLowerCase(Locale.ROOT));
    }

    private static String tr(String key) { return PlotI18n.tr(KEY + key); }

    private void reportLibraryFailure(RoadUiContext ctx, IOException e) {
        LoggerFactory.getLogger(RoadLampDesignerWindow.class).warn("Road lamp library unavailable", e);
        panelWarning(tr("custom_error"));
        RoadPanelScopedStatus.clearFromToolbarIfPresent(ctx.status());
    }
}
