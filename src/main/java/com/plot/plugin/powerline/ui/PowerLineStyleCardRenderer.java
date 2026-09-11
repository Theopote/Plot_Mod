package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignResolver;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.preview.PoleVoxelElevationRenderer;
import com.plot.plugin.powerline.preview.PowerLinePreviewOverlayRenderer;
import com.plot.plugin.powerline.preview.TowerStructuralElevationRenderer;
import com.plot.plugin.powerline.style.EffectiveStylePreview;
import com.plot.plugin.powerline.style.EffectiveStylePreviewResolver;
import com.plot.plugin.powerline.style.PowerLineStylePreset;
import com.plot.plugin.powerline.style.PowerLineStylePreviewBinding;
import com.plot.plugin.powerline.style.PreviewRepresentation;
import com.plot.plugin.powerline.style.StyleCardPreviewBinding;
import com.plot.utils.PlotI18n;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;

/** 样式 Tab 的杆塔风格卡片（缩略图 + 标签 + 选中高亮）。 */
public final class PowerLineStyleCardRenderer {
    public static final float CARD_WIDTH = 120f;
    public static final float CARD_HEIGHT = 144f;
    private static final float PREVIEW_HEIGHT = 96f;
    private static final float LABEL_PADDING = 4f;

    private static final int COLOR_BG = 0xFF1E1E1E;
    private static final int COLOR_BG_SELECTED = 0xFF263238;
    private static final int COLOR_BORDER = 0xFF484848;
    private static final int COLOR_SELECTED_RING = 0xFF4DA6FF;
    private static final int COLOR_LABEL = 0xFFE8E8E8;
    private static final int COLOR_LABEL_DIM = 0xFFB0B0B0;
    private static final int COLOR_CUSTOM_BG = 0xFF171717;
    private static final int COLOR_CUSTOM_BORDER = 0xFF757575;
    private static final int COLOR_CUSTOM_ACCENT = 0xFF78909C;
    private static final int COLOR_CUSTOM_SLIDER = 0xFF546E7A;
    private static final int COLOR_CUSTOM_KNOB = 0xFFB0BEC5;

    private PowerLineStyleCardRenderer() {
    }

    /**
     * 绘制可点击的风格卡片；返回是否被点击。
     */
    public static boolean renderStyleCard(PowerLineStylePreset pack, String label, boolean selected) {
        return renderStyleCard(pack, label, selected, null);
    }

    public static boolean renderStyleCard(
            PowerLineStylePreset pack,
            String label,
            boolean selected,
            PowerLineFootprint lineContext) {
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

        int bg = selected ? COLOR_BG_SELECTED : COLOR_BG;
        drawList.addRectFilled(x0, y0, x1, y1, bg);
        int borderColor = selected ? COLOR_SELECTED_RING : COLOR_BORDER;
        float borderThickness = selected ? 2f : 1f;
        drawList.addRect(x0, y0, x1, y1, borderColor, 4f, 0, borderThickness);

        float previewY1 = y0 + PREVIEW_HEIGHT;
        drawPackPreview(drawList, pack, x0 + 2f, y0 + 2f, x1 - 2f, previewY1 - 2f);

        float labelY = previewY1 + LABEL_PADDING;
        int labelColor = selected ? COLOR_LABEL : COLOR_LABEL_DIM;
        drawCenteredLabel(drawList, label, x0, labelY, CARD_WIDTH, COLOR_LABEL_DIM, labelColor);
        String wireLabel = PlotI18n.tr(
            "plugin.powerline.style.card_wires",
            pack.attachmentChannelCount());
        float wireY = labelY + ImGui.getFontSize() + 2f;
        drawCenteredLabel(drawList, wireLabel, x0, wireY, CARD_WIDTH, COLOR_LABEL_DIM, COLOR_LABEL_DIM);

        ImGui.invisibleButton(buttonId, CARD_WIDTH, CARD_HEIGHT);
        if (ImGui.isItemHovered()) {
            renderPackTooltip(pack, label, lineContext);
        }
        return ImGui.isItemClicked(0);
    }

    private static void renderPackTooltip(PowerLineStylePreset pack, String label, PowerLineFootprint lineContext) {
        ImGui.beginTooltip();
        PowerLineUiWidgets.text(label);
        ImGui.separator();
        PoleDesign previewDesign = PowerLineStylePreviewBinding.previewDesign(pack);
        if (previewDesign != null) {
            float previewW = ImGui.getFontSize() * 7f;
            float previewH = ImGui.getFontSize() * 9f;
            PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.style.preview_front"));
            ImDrawList drawList = ImGui.getWindowDrawList();
            ImVec2 frontOrigin = ImGui.getCursorScreenPos();
            drawList.addRectFilled(
                frontOrigin.x,
                frontOrigin.y,
                frontOrigin.x + previewW,
                frontOrigin.y + previewH,
                0xFF141414);
            drawPackPreview(
                drawList,
                pack,
                frontOrigin.x,
                frontOrigin.y,
                frontOrigin.x + previewW,
                frontOrigin.y + previewH);
            ImGui.dummy(previewW, previewH);
            ImGui.sameLine();
            ImGui.beginGroup();
            PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.style.preview_side"));
            ImVec2 sideOrigin = ImGui.getCursorScreenPos();
            drawList.addRectFilled(
                sideOrigin.x,
                sideOrigin.y,
                sideOrigin.x + previewW,
                sideOrigin.y + previewH,
                0xFF141414);
            PoleVoxelElevationRenderer.drawSide(
                drawList,
                previewDesign,
                sideOrigin.x,
                sideOrigin.y,
                sideOrigin.x + previewW,
                sideOrigin.y + previewH);
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

    public static float cardHeight() {
        return CARD_HEIGHT;
    }

    public static final float COMPACT_WIDTH = 72f;
    public static final float COMPACT_HEIGHT = 80f;
    private static final float COMPACT_PREVIEW_HEIGHT = 56f;
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
            COLOR_BG,
            6f);
        drawList.addRect(
            origin.x,
            origin.y,
            origin.x + panelW,
            origin.y + LARGE_PREVIEW_PANEL_HEIGHT,
            COLOR_BORDER,
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
                innerX,
                innerY,
                innerX + previewW - 4f,
                innerY + previewH);
            PoleVoxelElevationRenderer.drawSide(
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

    /** Build 摘要：当前生效设计的紧凑预览。 */
    public static void renderCompactStylePreview(
            PowerLineFootprint line,
            PowerLineStylePreset base,
            PoleDesignResolver resolver) {
        ImVec2 origin = ImGui.getCursorScreenPos();
        ImDrawList drawList = ImGui.getWindowDrawList();
        float x0 = origin.x;
        float y0 = origin.y;
        float x1 = x0 + COMPACT_WIDTH;
        float y1 = y0 + COMPACT_HEIGHT;
        drawList.addRectFilled(x0, y0, x1, y1, COLOR_BG);
        drawList.addRect(x0, y0, x1, y1, COLOR_BORDER, 3f, 0, 1f);
        EffectiveStylePreview effective = EffectiveStylePreviewResolver.resolve(line, base, resolver);
        PoleDesign previewDesign = effective != null ? effective.previewDesign() : null;
        if (previewDesign != null) {
            drawCardPreview(
                drawList,
                PowerLineStylePreviewBinding.bindingForDesign(previewDesign, base),
                PowerLineStylePreviewBinding.usesAdaptiveHeightMarker(base),
                x0 + 2f,
                y0 + 2f,
                x1 - 2f,
                y0 + COMPACT_PREVIEW_HEIGHT);
        } else if (base != null) {
            drawPackPreview(drawList, base, x0 + 2f, y0 + 2f, x1 - 2f, y0 + COMPACT_PREVIEW_HEIGHT);
        } else {
            drawMissingPreviewPlaceholder(
                drawList,
                x0 + 2f,
                y0 + 2f,
                x1 - 2f,
                y0 + COMPACT_PREVIEW_HEIGHT);
        }
        ImGui.dummy(COMPACT_WIDTH, COMPACT_HEIGHT);
    }

    /** 只读紧凑风格预览（Gallery 卡片等，base preset 默认）。 */
    public static void renderCompactStylePreview(PowerLineStylePreset pack) {
        ImVec2 origin = ImGui.getCursorScreenPos();
        ImDrawList drawList = ImGui.getWindowDrawList();
        float x0 = origin.x;
        float y0 = origin.y;
        float x1 = x0 + COMPACT_WIDTH;
        float y1 = y0 + COMPACT_HEIGHT;
        drawList.addRectFilled(x0, y0, x1, y1, COLOR_BG);
        drawList.addRect(x0, y0, x1, y1, COLOR_BORDER, 3f, 0, 1f);
        if (pack != null) {
            drawPackPreview(drawList, pack, x0 + 2f, y0 + 2f, x1 - 2f, y0 + COMPACT_PREVIEW_HEIGHT);
        } else {
            drawMissingPreviewPlaceholder(
                drawList,
                x0 + 2f,
                y0 + 2f,
                x1 - 2f,
                y0 + COMPACT_PREVIEW_HEIGHT);
        }
        ImGui.dummy(COMPACT_WIDTH, COMPACT_HEIGHT);
    }

    /** 自定义样式占位图（Build 摘要：未选中或未匹配的风格预设）。 */
    public static void renderCompactCustomStylePreview() {
        ImVec2 origin = ImGui.getCursorScreenPos();
        ImDrawList drawList = ImGui.getWindowDrawList();
        float x0 = origin.x;
        float y0 = origin.y;
        float x1 = x0 + COMPACT_WIDTH;
        float y1 = y0 + COMPACT_HEIGHT;
        drawList.addRectFilled(x0, y0, x1, y1, COLOR_CUSTOM_BG);
        drawDashedRect(drawList, x0 + 1f, y0 + 1f, x1 - 1f, y1 - 1f, COLOR_CUSTOM_BORDER, 3f, 1f, 4f, 3f);
        drawCustomStylePreview(drawList, x0 + 2f, y0 + 2f, x1 - 2f, y0 + COMPACT_PREVIEW_HEIGHT);
        ImGui.dummy(COMPACT_WIDTH, COMPACT_HEIGHT);
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
            x0,
            y0,
            x1,
            y1);
    }

    private static void drawCardPreview(
            ImDrawList drawList,
            StyleCardPreviewBinding binding,
            boolean adaptiveHeightMarker,
            float x0,
            float y0,
            float x1,
            float y1) {
        PoleDesign design = binding != null ? binding.design() : null;
        PreviewRepresentation representation = binding != null
            ? binding.representation()
            : PreviewRepresentation.VOXEL_FRONT;
        boolean drawn = false;
        if (design != null) {
            if (representation == PreviewRepresentation.STRUCTURAL_FRONT) {
                drawn = TowerStructuralElevationRenderer.drawFront(drawList, design, x0, y0, x1, y1);
            }
            if (!drawn) {
                drawn = PoleVoxelElevationRenderer.drawFront(drawList, design, x0, y0, x1, y1);
            }
        }
        if (!drawn) {
            drawMissingPreviewPlaceholder(drawList, x0, y0, x1, y1);
            return;
        }
        if (binding != null) {
            PowerLinePreviewOverlayRenderer.draw(
                drawList,
                design,
                representation,
                binding.overlay(),
                adaptiveHeightMarker,
                x0,
                y0,
                x1,
                y1);
        }
    }

    private static void drawMissingPreviewPlaceholder(
            ImDrawList drawList,
            float x0,
            float y0,
            float x1,
            float y1) {
        drawList.addRectFilled(x0, y0, x1, y1, 0xFF141414);
        drawDashedRect(drawList, x0 + 2f, y0 + 2f, x1 - 2f, y1 - 2f, COLOR_BORDER, 2f, 1f, 3f, 3f);
    }

    /** 自定义样式：材质色块 + 调节滑条 + 虚线导线。 */
    private static void drawCustomStylePreview(ImDrawList drawList, float x0, float y0, float x1, float y1) {
        float swatchSize = 6f;
        float swatchY = y1 - swatchSize - 3f;
        float swatchGap = 4f;
        float swatchX = x0 + 6f;
        drawList.addRectFilled(swatchX, swatchY, swatchX + swatchSize, swatchY + swatchSize, 0xFF9E9E9E);
        swatchX += swatchSize + swatchGap;
        drawList.addRectFilled(swatchX, swatchY, swatchX + swatchSize, swatchY + swatchSize, 0xFF8D6E63);
        swatchX += swatchSize + swatchGap;
        drawList.addRectFilled(swatchX, swatchY, swatchX + swatchSize, swatchY + swatchSize, 0xFF546E7A);

        float sliderLeft = x0 + 8f;
        float sliderRight = x1 - 8f;
        drawCustomSlider(drawList, sliderLeft, y0 + 8f, sliderRight, 0.35f);
        drawCustomSlider(drawList, sliderLeft, y0 + 16f, sliderRight, 0.62f);
        drawCustomSlider(drawList, sliderLeft, y0 + 24f, sliderRight, 0.48f);

        float poleX = x0 + (x1 - x0) * 0.72f;
        float poleTop = y0 + 10f;
        float poleBottom = swatchY - 2f;
        drawList.addLine(poleX, poleTop, poleX, poleBottom, COLOR_CUSTOM_ACCENT, 1.8f);

        float wireLeft = x0 + 10f;
        float wireRight = x1 - 10f;
        float wireBase = y0 + (y1 - y0) * 0.42f;
        drawDashedLine(drawList, wireLeft, wireBase, wireRight, wireBase - 4f, COLOR_CUSTOM_ACCENT, 1.2f, 4f, 3f);
    }

    private static void drawCustomSlider(
            ImDrawList drawList,
            float left,
            float y,
            float right,
            float knobT) {
        drawList.addLine(left, y, right, y, COLOR_CUSTOM_SLIDER, 1.2f);
        float knobX = left + (right - left) * knobT;
        drawList.addCircleFilled(knobX, y, 2.5f, COLOR_CUSTOM_KNOB);
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
            float width,
            int dimColor,
            int brightColor) {
        if (text == null || text.isBlank()) {
            return;
        }
        float textW = ImGui.calcTextSize(text).x;
        float maxW = width - LABEL_PADDING * 2f;
        if (textW > maxW && text.length() > 3) {
            String clipped = clipLabel(text, maxW);
            textW = ImGui.calcTextSize(clipped).x;
            drawList.addText(x + (width - textW) * 0.5f, y, dimColor, clipped);
            return;
        }
        drawList.addText(x + (width - textW) * 0.5f, y, brightColor, text);
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
