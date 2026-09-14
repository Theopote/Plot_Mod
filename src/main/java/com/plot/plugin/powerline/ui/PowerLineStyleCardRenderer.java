package com.plot.plugin.powerline.ui;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignResolver;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.preview.PoleVoxelElevationRenderer;
import com.plot.plugin.powerline.preview.PowerLinePreviewOverlayRenderer;
import com.plot.plugin.powerline.preview.StylePreviewLayout;
import com.plot.plugin.powerline.preview.TowerStructuralElevationRenderer;
import com.plot.plugin.powerline.style.EffectiveStylePreview;
import com.plot.plugin.powerline.style.EffectiveStylePreviewResolver;
import com.plot.plugin.powerline.style.PowerLineStyleEditor;
import com.plot.plugin.powerline.style.PowerLineStylePreset;
import com.plot.plugin.powerline.style.PowerLineStylePreviewBinding;
import com.plot.plugin.powerline.style.UserPoleDesignTemplateCatalog;
import com.plot.plugin.powerline.style.PreviewRepresentation;
import com.plot.plugin.powerline.style.StyleCardPreviewBinding;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;

/** 样式 Tab 的杆塔风格卡片（缩略图 + 标签 + 选中高亮）。 */
public final class PowerLineStyleCardRenderer {
    public static final float CARD_WIDTH = 120f;
    public static final float CARD_HEIGHT = 150f;
    private static final float PREVIEW_HEIGHT = 102f;
    private static final float LABEL_PADDING = 4f;

    private static final int COLOR_BG_SELECTED = 0xFF263238;
    private static final int COLOR_LABEL = 0xFFE8E8E8;
    private static final int COLOR_LABEL_DIM = 0xFFB0B0B0;
    private PowerLineStyleCardRenderer() {
    }


    public static boolean renderStyleCard(
            PowerLineStylePreset pack,
            String label,
            boolean selected,
            PowerLineFootprint lineContext,
            PoleDesignResolver resolver) {
        if (pack == null) {
            return false;
        }
        String buttonId = "##powerline_style_card_" + pack.getId().replace('/', '_');
        ImVec2 origin = ImGui.getCursorScreenPos();
        ImDrawList drawList = ImGui.getWindowDrawList();
        float x0 = origin.x;
        float y0 = origin.y;
        float x1 = x0 + CARD_WIDTH;
        float y1 = y0 + CARD_HEIGHT;

        int bg = selected ? COLOR_BG_SELECTED : PluginUiColors.MAP_BG;
        drawList.addRectFilled(x0, y0, x1, y1, bg);
        int borderColor = selected ? PluginUiColors.ACCENT_BLUE : PluginUiColors.PANEL_BORDER;
        float borderThickness = selected ? 2f : 1f;
        drawList.addRect(x0, y0, x1, y1, borderColor, 4f, 0, borderThickness);

        float previewY1 = y0 + PREVIEW_HEIGHT;
        EffectiveStylePreview effective = resolveEffectiveCardPreview(pack, lineContext, resolver);
        if (effective != null) {
            drawEffectiveCardPreview(
                drawList,
                effective,
                x0 + 2f,
                y0 + 2f,
                x1 - 2f,
                previewY1 - 2f);
        } else {
            drawPackPreview(drawList, pack, x0 + 2f, y0 + 2f, x1 - 2f, previewY1 - 2f);
        }

        float labelY = previewY1 + LABEL_PADDING;
        int labelColor = selected ? COLOR_LABEL : COLOR_LABEL_DIM;
        drawCenteredLabel(drawList, label, x0, labelY, labelColor);
        String wireLabel = PlotI18n.tr(
            "plugin.powerline.style.card_wires",
            pack.attachmentChannelCount());
        float wireY = labelY + ImGui.getFontSize() + 2f;
        drawCenteredLabel(drawList, wireLabel, x0, wireY, COLOR_LABEL_DIM);

        ImGui.invisibleButton(buttonId, CARD_WIDTH, CARD_HEIGHT);
        if (ImGui.isItemHovered()) {
            renderPackTooltip(pack, label, lineContext, resolver);
        }
        return ImGui.isItemClicked(0);
    }

    /**
     * 用户造型模板卡片；左键选中，右键打开删除菜单。
     *
     * @return {@code clicked} 左键选中；{@code deleteRequested} 右键菜单请求删除
     */
    public static UserTemplateCardResult renderUserTemplateCard(
            PoleDesign design,
            boolean selected,
            PowerLineFootprint lineContext) {
        if (design == null) {
            return UserTemplateCardResult.none();
        }
        String label = design.getName();
        String buttonId = "##powerline_user_template_card_" + design.getId();
        ImVec2 origin = ImGui.getCursorScreenPos();
        ImDrawList drawList = ImGui.getWindowDrawList();
        float x0 = origin.x;
        float y0 = origin.y;
        float x1 = x0 + CARD_WIDTH;
        float y1 = y0 + CARD_HEIGHT;

        int bg = selected ? COLOR_BG_SELECTED : PluginUiColors.MAP_BG;
        drawList.addRectFilled(x0, y0, x1, y1, bg);
        int borderColor = selected ? PluginUiColors.ACCENT_BLUE : PluginUiColors.PANEL_BORDER;
        float borderThickness = selected ? 2f : 1f;
        drawList.addRect(x0, y0, x1, y1, borderColor, 4f, 0, borderThickness);

        float previewY1 = y0 + PREVIEW_HEIGHT;
        PowerLineStylePreset preset = UserPoleDesignTemplateCatalog.toPreset(design, lineContext);
        StyleCardPreviewBinding binding = PowerLineStylePreviewBinding.bindingForDesign(design, preset);
        drawCardPreview(
            drawList,
            binding,
            false,
            preset.getWireMaterial(),
            preset.getTopWireMaterial(),
            x0 + 2f,
            y0 + 2f,
            x1 - 2f,
            previewY1 - 2f);

        float labelY = previewY1 + LABEL_PADDING;
        int labelColor = selected ? COLOR_LABEL : COLOR_LABEL_DIM;
        drawCenteredLabel(drawList, label, x0, labelY, labelColor);
        String wireLabel = PlotI18n.tr(
            "plugin.powerline.style.card_wires",
            UserPoleDesignTemplateCatalog.attachmentChannelCount(design));
        float wireY = labelY + ImGui.getFontSize() + 2f;
        drawCenteredLabel(drawList, wireLabel, x0, wireY, COLOR_LABEL_DIM);

        ImGui.invisibleButton(buttonId, CARD_WIDTH, CARD_HEIGHT);
        if (ImGui.isItemHovered() && !ImGui.isPopupOpen("##powerline_user_template_ctx_" + design.getId())) {
            renderUserTemplateTooltip(design, label, lineContext);
        }
        boolean clicked = ImGui.isItemClicked(0);
        boolean deleteRequested = false;
        if (ImGui.beginPopupContextItem("##powerline_user_template_ctx_" + design.getId())) {
            if (ImGui.menuItem(PlotI18n.tr("plugin.powerline.style.delete_user_template"))) {
                deleteRequested = true;
            }
            ImGui.endPopup();
        }
        return new UserTemplateCardResult(clicked, deleteRequested);
    }

    public record UserTemplateCardResult(boolean clicked, boolean deleteRequested) {
        public static UserTemplateCardResult none() {
            return new UserTemplateCardResult(false, false);
        }
    }

    private static void renderUserTemplateTooltip(
            PoleDesign design,
            String label,
            PowerLineFootprint lineContext) {
        ImGui.beginTooltip();
        PowerLineUiWidgets.text(label);
        ImGui.separator();
        float previewW = ImGui.getFontSize() * 7f;
        float previewH = ImGui.getFontSize() * 9f;
        ImVec2 headerOrigin = ImGui.getCursorScreenPos();
        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.style.preview_front"));
        ImDrawList drawList = ImGui.getWindowDrawList();
        ImVec2 frontOrigin = ImGui.getCursorScreenPos();
        drawList.addRectFilled(
            frontOrigin.x,
            frontOrigin.y,
            frontOrigin.x + previewW,
            frontOrigin.y + previewH,
            PluginUiColors.PANEL_BG_DARK);
        PowerLineStylePreset preset = UserPoleDesignTemplateCatalog.toPreset(design, lineContext);
        StyleCardPreviewBinding binding = PowerLineStylePreviewBinding.bindingForDesign(design, preset);
        drawCardPreview(
            drawList,
            binding,
            false,
            preset.getWireMaterial(),
            preset.getTopWireMaterial(),
            frontOrigin.x,
            frontOrigin.y,
            frontOrigin.x + previewW,
            frontOrigin.y + previewH);
        ImGui.dummy(previewW, previewH);
        ImGui.sameLine();
        ImGui.beginGroup();
        ImVec2 sideOrigin = ImGui.getCursorScreenPos();
        drawList.addText(
            sideOrigin.x,
            headerOrigin.y,
            COLOR_LABEL_DIM,
            PlotI18n.tr("plugin.powerline.style.preview_side"));
        drawList.addRectFilled(
            sideOrigin.x,
            sideOrigin.y,
            sideOrigin.x + previewW,
            sideOrigin.y + previewH,
            PluginUiColors.PANEL_BG_DARK);
        drawSidePreview(
            drawList, design, sideOrigin.x, sideOrigin.y, sideOrigin.x + previewW, sideOrigin.y + previewH);
        ImGui.dummy(previewW, previewH);
        PowerLineUiWidgets.text(PlotI18n.tr(
            "plugin.powerline.style.preview_height",
            design.totalHeight()));
        PowerLineUiWidgets.text(PlotI18n.tr(
            "plugin.powerline.style.preview_wires",
            UserPoleDesignTemplateCatalog.attachmentChannelCount(design)));
        ImGui.endGroup();
        ImGui.separator();
        PowerLineUiWidgets.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.powerline.style.user_template_hint"));
        ImGui.endTooltip();
    }

    private static void renderPackTooltip(
            PowerLineStylePreset pack,
            String label,
            PowerLineFootprint lineContext,
            PoleDesignResolver resolver) {
        ImGui.beginTooltip();
        PowerLineUiWidgets.text(label);
        ImGui.separator();
        EffectiveStylePreview effective = resolveEffectiveCardPreview(pack, lineContext, resolver);
        PoleDesign previewDesign = effective != null
            ? effective.previewDesign()
            : PowerLineStylePreviewBinding.previewDesign(pack);
        if (previewDesign != null) {
            float previewW = ImGui.getFontSize() * 7f;
            float previewH = ImGui.getFontSize() * 9f;
            ImVec2 headerOrigin = ImGui.getCursorScreenPos();
            PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.style.preview_front"));
            ImDrawList drawList = ImGui.getWindowDrawList();
            ImVec2 frontOrigin = ImGui.getCursorScreenPos();
            drawList.addRectFilled(
                frontOrigin.x,
                frontOrigin.y,
                frontOrigin.x + previewW,
                frontOrigin.y + previewH,
                PluginUiColors.PANEL_BG_DARK);
            if (effective != null) {
                drawEffectiveCardPreview(
                    drawList,
                    effective,
                    frontOrigin.x,
                    frontOrigin.y,
                    frontOrigin.x + previewW,
                    frontOrigin.y + previewH);
            } else {
                drawPackPreview(
                    drawList,
                    pack,
                    frontOrigin.x,
                    frontOrigin.y,
                    frontOrigin.x + previewW,
                    frontOrigin.y + previewH);
            }
            ImGui.dummy(previewW, previewH);
            ImGui.sameLine();
            ImGui.beginGroup();
            ImVec2 sideOrigin = ImGui.getCursorScreenPos();
            drawList.addText(
                sideOrigin.x,
                headerOrigin.y,
                COLOR_LABEL_DIM,
                PlotI18n.tr("plugin.powerline.style.preview_side"));
            drawList.addRectFilled(
                sideOrigin.x,
                sideOrigin.y,
                sideOrigin.x + previewW,
                sideOrigin.y + previewH,
                PluginUiColors.PANEL_BG_DARK);
            drawSidePreview(
                drawList, previewDesign, sideOrigin.x, sideOrigin.y, sideOrigin.x + previewW, sideOrigin.y + previewH);
            ImGui.dummy(previewW, previewH);
            PowerLineUiWidgets.text(PlotI18n.tr(
                "plugin.powerline.style.preview_height",
                previewDesign.totalHeight()));
            PowerLineUiWidgets.text(PlotI18n.tr(
                "plugin.powerline.style.preview_wires",
                pack.attachmentChannelCount()));
            if (lineContext != null) {
                PowerLineUiWidgets.text(PlotI18n.tr(
                    "plugin.powerline.style.preview_spacing",
                    lineContext.getMaxPoleSpacing()));
            } else {
                PowerLineUiWidgets.text(PlotI18n.tr(
                    "plugin.powerline.style.preview_spacing",
                    pack.getSpacingProfile().preferred()));
            }
            ImGui.endGroup();
            ImGui.separator();
        }
        PowerLineUiWidgets.text(PlotI18n.tr(pack.getDescriptionKey()));
        ImGui.endTooltip();
    }

    public static float cardWidth() {
        return CARD_WIDTH;
    }

    private static final float LARGE_PREVIEW_PANEL_HEIGHT = 148f;

    /** 选中风格的立面预览：显示线路当前生效设计（含 Quick Tune），非 base preset 默认。 */
    public static void renderLargeSelectedPreview(
            PowerLineFootprint line,
            PowerLineStylePreset base,
            PoleDesignResolver resolver) {
        EffectiveStylePreview effective = EffectiveStylePreviewResolver.resolve(line, base, resolver);
        PoleDesign previewDesign = effective != null ? effective.previewDesign() : null;
        float availW = ImGui.getContentRegionAvail().x;
        float panelW = Math.max(160f, availW);
        float gap = 8f;
        float previewW = (panelW - gap) * 0.5f;
        float previewH = LARGE_PREVIEW_PANEL_HEIGHT - 8f;

        ImVec2 origin = ImGui.getCursorScreenPos();
        ImDrawList drawList = ImGui.getWindowDrawList();
        drawList.addRectFilled(
            origin.x,
            origin.y,
            origin.x + panelW,
            origin.y + LARGE_PREVIEW_PANEL_HEIGHT,
            PluginUiColors.MAP_BG,
            6f);
        drawList.addRect(
            origin.x,
            origin.y,
            origin.x + panelW,
            origin.y + LARGE_PREVIEW_PANEL_HEIGHT,
            PluginUiColors.PANEL_BORDER,
            6f,
            0,
            1f);

        float innerX = origin.x + 4f;
        float innerY = origin.y + 4f;
        if (previewDesign != null) {
            StyleCardPreviewBinding binding = PowerLineStylePreviewBinding.bindingForDesign(previewDesign, base);
            drawCardPreview(
                drawList,
                binding,
                PowerLineStylePreviewBinding.usesAdaptiveHeightMarker(base),
                    effective.wireMaterial(),
                    effective.topWireMaterial(),
                innerX,
                innerY,
                innerX + previewW - 4f,
                innerY + previewH);
            drawSidePreview(
                drawList,
                previewDesign,
                innerX + previewW + gap,
                innerY,
                innerX + previewW + gap + previewW - 4f,
                innerY + previewH);
        } else if (base != null) {
            drawPackPreview(drawList, base, innerX, innerY, innerX + panelW - 8f, innerY + previewH);
        } else {
            drawMissingPreviewPlaceholder(drawList, innerX, innerY, innerX + panelW - 8f, innerY + previewH);
        }
        ImGui.dummy(panelW, LARGE_PREVIEW_PANEL_HEIGHT);
    }

    /** @deprecated 使用 {@link #renderLargeSelectedPreview(PowerLineFootprint, PowerLineStylePreset, PoleDesignResolver)} */
    @Deprecated
    public static void renderLargeSelectedPreview(PowerLineStylePreset preset) {
        renderLargeSelectedPreview(null, preset, null);
    }

    private static void drawPackPreview(
            ImDrawList drawList,
            PowerLineStylePreset pack,
            float x0,
            float y0,
            float x1,
            float y1) {
        if (pack == null) {
            drawMissingPreviewPlaceholder(drawList, x0, y0, x1, y1);
            return;
        }
        StyleCardPreviewBinding binding = PowerLineStylePreviewBinding.cardPreviewBinding(pack);
        drawCardPreview(
            drawList,
            binding,
            PowerLineStylePreviewBinding.usesAdaptiveHeightMarker(pack),
            pack.getWireMaterial(),
            pack.getTopWireMaterial(),
            x0,
            y0,
            x1,
            y1);
    }

    private static EffectiveStylePreview resolveEffectiveCardPreview(
            PowerLineStylePreset pack,
            PowerLineFootprint line,
            PoleDesignResolver resolver) {
        if (!shouldUseEffectivePreview(pack, line) || resolver == null) {
            return null;
        }
        return EffectiveStylePreviewResolver.resolve(line, pack, resolver);
    }

    private static boolean shouldUseEffectivePreview(PowerLineStylePreset pack, PowerLineFootprint line) {
        if (pack == null || line == null || !PowerLineStyleEditor.isModified(line)) {
            return false;
        }
        PowerLineStylePreset base = PowerLineStyleEditor.basePreset(line);
        return base != null && pack.getId().equals(base.getId());
    }

    private static void drawEffectiveCardPreview(
            ImDrawList drawList,
            EffectiveStylePreview effective,
            float x0,
            float y0,
            float x1,
            float y1) {
        if (effective == null || effective.previewDesign() == null) {
            return;
        }
        PowerLineStylePreset base = effective.basePreset();
        StyleCardPreviewBinding binding = PowerLineStylePreviewBinding.bindingForDesign(
            effective.previewDesign(),
            base);
        drawCardPreview(
            drawList,
            binding,
            PowerLineStylePreviewBinding.usesAdaptiveHeightMarker(base),
            effective.wireMaterial(),
            effective.topWireMaterial(),
            x0,
            y0,
            x1,
            y1);
    }

    private static void drawCardPreview(
            ImDrawList drawList,
            StyleCardPreviewBinding binding,
            boolean adaptiveHeightMarker,
            MaterialMix wireMaterial,
            MaterialMix topWireMaterial,
            float x0,
            float y0,
            float x1,
            float y1) {
        PoleDesign design = binding != null ? binding.design() : null;
        PreviewRepresentation representation = binding != null
            ? binding.representation()
            : PreviewRepresentation.VOXEL_FRONT;
        TowerStructuralElevationRenderer.LayoutFit layoutFit =
            StylePreviewLayout.fitForBounds(x0, y0, x1, y1);
        boolean drawn = false;
        if (design != null) {
            if (representation == PreviewRepresentation.STRUCTURAL_FRONT) {
                drawn = TowerStructuralElevationRenderer.drawFront(
                    drawList, design, x0, y0, x1, y1, layoutFit);
            }
            if (!drawn) {
                drawn = PoleVoxelElevationRenderer.drawFront(drawList, design, x0, y0, x1, y1);
            }
        }
        if (!drawn) {
            drawMissingPreviewPlaceholder(drawList, x0, y0, x1, y1);
            return;
        }
        PowerLinePreviewOverlayRenderer.draw(
            drawList,
            design,
            representation,
            binding.overlay(),
            adaptiveHeightMarker,
            wireMaterial,
            topWireMaterial,
            layoutFit,
            x0,
            y0,
            x1,
            y1);
    }

    private static void drawSidePreview(
            ImDrawList drawList,
            PoleDesign design,
            float x0,
            float y0,
            float x1,
            float y1) {
        if (design.hasTowerStructure()
                && TowerStructuralElevationRenderer.drawSide(drawList, design, x0, y0, x1, y1)) {
            return;
        }
        PoleVoxelElevationRenderer.drawSide(drawList, design, x0, y0, x1, y1);
    }

    private static void drawMissingPreviewPlaceholder(
            ImDrawList drawList,
            float x0,
            float y0,
            float x1,
            float y1) {
        drawList.addRectFilled(x0, y0, x1, y1, PluginUiColors.PANEL_BG_DARK);
        drawDashedRect(drawList, x0 + 2f, y0 + 2f, x1 - 2f, y1 - 2f, PluginUiColors.PANEL_BORDER, 2f, 1f, 3f, 3f);
    }

    private static void drawDashedRect(
            ImDrawList drawList,
            float x0,
            float y0,
            float x1,
            float y1,
            int color,
            float cornerRadius,
            float thickness,
            float dashLen,
            float gapLen) {
        drawDashedLine(drawList, x0 + cornerRadius, y0, x1 - cornerRadius, y0, color, thickness, dashLen, gapLen);
        drawDashedLine(drawList, x1, y0 + cornerRadius, x1, y1 - cornerRadius, color, thickness, dashLen, gapLen);
        drawDashedLine(drawList, x1 - cornerRadius, y1, x0 + cornerRadius, y1, color, thickness, dashLen, gapLen);
        drawDashedLine(drawList, x0, y1 - cornerRadius, x0, y0 + cornerRadius, color, thickness, dashLen, gapLen);
    }

    private static void drawDashedLine(
            ImDrawList drawList,
            float x0,
            float y0,
            float x1,
            float y1,
            int color,
            float thickness,
            float dashLen,
            float gapLen) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.001f) {
            return;
        }
        float ux = dx / len;
        float uy = dy / len;
        float pos = 0f;
        boolean drawing = true;
        while (pos < len) {
            float segment = drawing ? dashLen : gapLen;
            float next = Math.min(pos + segment, len);
            if (drawing) {
                drawList.addLine(
                    x0 + ux * pos,
                    y0 + uy * pos,
                    x0 + ux * next,
                    y0 + uy * next,
                    color,
                    thickness);
            }
            pos = next;
            drawing = !drawing;
        }
    }

    private static void drawCenteredLabel(
            ImDrawList drawList,
            String text,
            float x,
            float y,
            int brightColor) {
        if (text == null || text.isBlank()) {
            return;
        }
        float textW = ImGui.calcTextSize(text).x;
        float maxW = PowerLineStyleCardRenderer.CARD_WIDTH - LABEL_PADDING * 2f;
        if (textW > maxW && text.length() > 3) {
            String clipped = clipLabel(text, maxW);
            textW = ImGui.calcTextSize(clipped).x;
            drawList.addText(x + (PowerLineStyleCardRenderer.CARD_WIDTH - textW) * 0.5f, y, PowerLineStyleCardRenderer.COLOR_LABEL_DIM, clipped);
            return;
        }
        drawList.addText(x + (PowerLineStyleCardRenderer.CARD_WIDTH - textW) * 0.5f, y, brightColor, text);
    }

    private static String clipLabel(String text, float maxWidth) {
        String suffix = "...";
        for (int len = text.length() - 1; len > 0; len--) {
            String candidate = text.substring(0, len) + suffix;
            if (ImGui.calcTextSize(candidate).x <= maxWidth) {
                return candidate;
            }
        }
        return suffix;
    }
}
