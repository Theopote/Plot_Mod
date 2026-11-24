package com.plot.plugin.road.style;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.road.RoadMaterialUtils;
import com.plot.plugin.road.RoadParameterLimits;
import com.plot.plugin.road.lamp.RoadLampPlacement;
import com.plot.plugin.road.lamp.RoadLampPresets;
import com.plot.plugin.road.lamp.RoadLampStyle;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.section.CenterLineStyle;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.model.section.RoadCrossSection;
import com.plot.plugin.road.model.section.RoadsideEdgeMode;

/**
 * 道路风格模板：定义横断面、材质与附属设施。
 * 纵向设计参数由 Vertical Strategy / Generate 流程独立管理。
 * 生成器只消费 {@link com.plot.plugin.road.model.section.ResolvedCrossSection}，不直接读 Style。
 */
public class RoadStyle {
    public String id;
    public String name;
    public int width = 5;
    /** 0 表示按宽度自动推断（width &gt;= 7 → 2 车道） */
    public int laneCount;
    public boolean hasSidewalk;
    public int sidewalkWidth;
    public boolean includeShoulder;
    public int shoulderWidth;
    public boolean includeBikeLane;
    public int bikeLaneWidth = 1;
    public boolean includeMedian;
    public int medianWidth;
    public Boolean includeSlopeBatter;
    /** 显式边缘模式；优先于 {@link #includeSlopeBatter}。null 表示由旧字段推断。 */
    public String roadsideEdgeMode;
    public float fillSlopeRatio = 1.0f;
    public float cutSlopeRatio = 1.0f;
    public String roadMaterial;
    public String sidewalkMaterial;
    public String shoulderMaterial;
    public String bikeLaneMaterial;
    public String fillSlopeMaterial;
    public String cutSlopeMaterial;
    public String wallMaterial;
    public String markingMaterial = "minecraft:white_concrete";
    public String streetlightBlock;
    public String centerLineStyle;
    public Boolean laneDividers;
    public Integer streetlightSpacing;
    /**
     * 新版专业路灯预设 id（见 {@link RoadLampPresets}）；非空时写入
     * {@link com.plot.plugin.road.model.section.StreetFurniture#setRoadLampPlacement}。
     */
    public String lampPresetId;
    /** 可选默认主题；全局主题见 {@link com.plot.plugin.config.RoadSystemConfig#getRoadThemeId()}。 */
    public String themeId;

    public RoadStyle() {
    }

    public RoadStyle(String id) {
        this.id = id;
        this.name = id;
    }

    public int resolveLaneCount() {
        if (laneCount > 0) {
            return laneCount;
        }
        return width >= 7 ? 2 : 1;
    }

    public CenterLineStyle resolveCenterLineStyle() {
        if (centerLineStyle == null || centerLineStyle.isBlank()) {
            return CenterLineStyle.NONE;
        }
        try {
            return CenterLineStyle.valueOf(centerLineStyle);
        } catch (IllegalArgumentException ignored) {
            return CenterLineStyle.NONE;
        }
    }

    public boolean resolveLaneDividers() {
        if (laneDividers != null) {
            return laneDividers;
        }
        return resolveLaneCount() > 1;
    }

    public boolean resolveIncludeSlopeBatter() {
        RoadsideEdgeMode mode = resolveRoadsideEdgeMode();
        return mode != null && mode.isSlopeBatter();
    }

    /**
     * 解析风格声明的路边模式；{@code null} 表示不覆盖全局（旧「静默城市」语义已废弃，
     * 新预设应显式写 {@link RoadsideEdgeMode}）。
     */
    public RoadsideEdgeMode resolveRoadsideEdgeMode() {
        RoadsideEdgeMode explicit = RoadsideEdgeMode.fromId(roadsideEdgeMode);
        if (explicit != null) {
            return explicit;
        }
        if (includeSlopeBatter != null) {
            return includeSlopeBatter ? RoadsideEdgeMode.SLOPE_BATTER : RoadsideEdgeMode.NONE;
        }
        if (fillSlopeRatio > 0f || cutSlopeRatio > 0f) {
            return RoadsideEdgeMode.SLOPE_BATTER;
        }
        return null;
    }

    public RoadCrossSection toCrossSection() {
        return toCrossSection(null);
    }

    public RoadCrossSection toCrossSection(String themeIdOverride) {
        String resolvedThemeId = themeIdOverride != null && !themeIdOverride.isBlank()
            ? themeIdOverride
            : (this.themeId != null && !this.themeId.isBlank() ? this.themeId : RoadThemeCatalog.MODERN_ID);
        return buildCrossSectionFrom(RoadThemeCatalog.applyTheme(resolvedThemeId, this));
    }

    public RoadStyle copy() {
        RoadStyle copy = new RoadStyle();
        copy.id = id;
        copy.name = name;
        copy.width = width;
        copy.laneCount = laneCount;
        copy.hasSidewalk = hasSidewalk;
        copy.sidewalkWidth = sidewalkWidth;
        copy.includeShoulder = includeShoulder;
        copy.shoulderWidth = shoulderWidth;
        copy.includeBikeLane = includeBikeLane;
        copy.bikeLaneWidth = bikeLaneWidth;
        copy.includeMedian = includeMedian;
        copy.medianWidth = medianWidth;
        copy.includeSlopeBatter = includeSlopeBatter;
        copy.roadsideEdgeMode = roadsideEdgeMode;
        copy.fillSlopeRatio = fillSlopeRatio;
        copy.cutSlopeRatio = cutSlopeRatio;
        copy.roadMaterial = roadMaterial;
        copy.sidewalkMaterial = sidewalkMaterial;
        copy.shoulderMaterial = shoulderMaterial;
        copy.bikeLaneMaterial = bikeLaneMaterial;
        copy.fillSlopeMaterial = fillSlopeMaterial;
        copy.cutSlopeMaterial = cutSlopeMaterial;
        copy.wallMaterial = wallMaterial;
        copy.markingMaterial = markingMaterial;
        copy.streetlightBlock = streetlightBlock;
        copy.centerLineStyle = centerLineStyle;
        copy.laneDividers = laneDividers;
        copy.streetlightSpacing = streetlightSpacing;
        copy.lampPresetId = lampPresetId;
        copy.themeId = themeId;
        return copy;
    }

    private RoadCrossSection buildCrossSectionFrom(RoadStyle style) {
        String roadMat = style.roadMaterial != null && !style.roadMaterial.isBlank()
            ? style.roadMaterial
            : RoadMaterialUtils.DEFAULT_ROAD_BLOCK;
        String sidewalkMat = style.sidewalkMaterial != null && !style.sidewalkMaterial.isBlank()
            ? style.sidewalkMaterial
            : roadMat;
        String shoulderMat = style.shoulderMaterial != null && !style.shoulderMaterial.isBlank()
            ? style.shoulderMaterial
            : RoadMaterialUtils.DEFAULT_SLOPE_FILL_BLOCK;
        String bikeMat = style.bikeLaneMaterial != null && !style.bikeLaneMaterial.isBlank()
            ? style.bikeLaneMaterial
            : ResolvedCrossSection.DEFAULT_BIKE_LANE_MATERIAL;

        int lanes = style.resolveLaneCount();
        RoadCrossSection section = new RoadCrossSection();
        section.getCarriageway().setLaneCount(lanes);
        section.getCarriageway().setMaterial(MaterialMix.single(roadMat));
        section.getCarriageway().syncLaneCount(lanes);
        // 按风格声明的总宽打包车道行驶宽，避免均匀反推丢格（如双黄线奇数总宽）。
        section.getCarriageway().applyPackedTravelWidths(
            style.width,
            style.resolveCenterLineStyle(),
            style.resolveLaneDividers());

        section.getShoulder().setEnabled(style.includeShoulder);
        section.getShoulder().setWidth(style.shoulderWidth);
        section.getShoulder().setMaterial(shoulderMat);

        section.getBikeLane().setEnabled(style.includeBikeLane);
        section.getBikeLane().setWidth(style.includeBikeLane ? Math.max(1, style.bikeLaneWidth) : 0);
        section.getBikeLane().setMaterial(bikeMat);

        section.getSidewalk().setEnabled(style.hasSidewalk);
        section.getSidewalk().setWidth(style.hasSidewalk ? Math.max(1, style.sidewalkWidth) : 0);
        section.getSidewalk().setMaterial(sidewalkMat);

        section.getMedian().setEnabled(style.includeMedian);
        section.getMedian().setWidth(style.includeMedian ? Math.max(1, style.medianWidth) : 0);

        section.getMarkings().setLaneDividers(style.resolveLaneDividers());
        section.getMarkings().setCenterLineStyle(style.resolveCenterLineStyle());
        section.getMarkings().setMaterial(
            style.markingMaterial != null && !style.markingMaterial.isBlank()
                ? style.markingMaterial
                : "minecraft:white_concrete"
        );

        RoadsideEdgeMode edgeMode = style.resolveRoadsideEdgeMode();
        if (edgeMode != null) {
            section.getSlopeBatter().setMode(edgeMode);
            if (edgeMode.isSlopeBatter()) {
                section.getSlopeBatter().setFillRatio(style.fillSlopeRatio);
                section.getSlopeBatter().setCutRatio(style.cutSlopeRatio);
                section.getSlopeBatter().setFillMaterial(
                    style.fillSlopeMaterial != null && !style.fillSlopeMaterial.isBlank()
                        ? style.fillSlopeMaterial
                        : shoulderMat
                );
                if (style.cutSlopeMaterial != null && !style.cutSlopeMaterial.isBlank()) {
                    section.getSlopeBatter().setCutMaterial(style.cutSlopeMaterial);
                }
            }
            if (edgeMode.isRetainingWall()) {
                section.getSlopeBatter().setWallMaterial(
                    style.wallMaterial != null && !style.wallMaterial.isBlank()
                        ? style.wallMaterial
                        : "minecraft:stone_bricks");
            }
        }

        if (style.streetlightSpacing != null && style.streetlightSpacing > 0) {
            section.getStreetFurniture().setStreetlightSpacing(style.streetlightSpacing);
        } else {
            section.getStreetFurniture().setStreetlightSpacing(null);
        }
        if (style.streetlightBlock != null && !style.streetlightBlock.isBlank()) {
            section.getStreetFurniture().setStreetlightBlock(style.streetlightBlock);
        } else {
            section.getStreetFurniture().setStreetlightBlock(null);
        }
        applyLampPreset(section, style);
        return section;
    }

    private static void applyLampPreset(RoadCrossSection section, RoadStyle style) {
        if (style.lampPresetId == null || style.lampPresetId.isBlank()) {
            return;
        }
        RoadLampStyle lampStyle = null;
        for (RoadLampPresets.Preset preset : RoadLampPresets.ALL) {
            if (style.lampPresetId.equals(preset.id())) {
                lampStyle = preset.style();
                break;
            }
        }
        if (lampStyle == null) {
            return;
        }
        int spacing = style.streetlightSpacing != null && style.streetlightSpacing > 0
            ? RoadParameterLimits.normalizeStreetlightSpacing(style.streetlightSpacing)
            : 16;
        section.getStreetFurniture().setRoadLampPlacement(new RoadLampPlacement(
            lampStyle,
            RoadLampPlacement.Sides.BOTH,
            spacing,
            0,
            4,
            true,
            false));
        if (section.getStreetFurniture().getStreetlightSpacing() == null) {
            section.getStreetFurniture().setStreetlightSpacing(spacing);
        }
    }

    public void applyTo(Road road) {
        applyTo(road, null);
    }

    public void applyTo(Road road, String themeIdOverride) {
        if (road == null) {
            return;
        }
        road.setCrossSection(toCrossSection(themeIdOverride));
        road.setStyleId(id);
        String resolvedThemeId = themeIdOverride != null && !themeIdOverride.isBlank()
            ? themeIdOverride
            : (this.themeId != null && !this.themeId.isBlank() ? this.themeId : RoadThemeCatalog.MODERN_ID);
        road.setThemeId(resolvedThemeId);
    }
}
