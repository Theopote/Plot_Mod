package com.plot.plugin.road.model.section;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadDimensionUtils;
import com.plot.plugin.road.RoadMaterialMixUtils;
import com.plot.plugin.road.RoadMaterialUtils;
import com.plot.plugin.road.pipeline.construction.RoadConstructionMaterialPolicy;
import com.plot.plugin.road.pipeline.construction.RoadConstructionMaterialPolicy.MaterialRole;
import com.plot.plugin.road.solid.RoadSolidLayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 横断面解析结果：道路级覆盖与全局默认合并后的有效值，供生成器与预览使用。
 */
public final class ResolvedCrossSection {
    public static final String DEFAULT_MARKING_MATERIAL = "material.plot.white_concrete";
    public static final String DEFAULT_BIKE_LANE_MATERIAL = "minecraft:light_blue_concrete";
    public static final String DEFAULT_STREETLIGHT_BLOCK = "minecraft:lantern";

    public final int laneCount;
    public final List<Integer> laneWidths;
    public final List<Double> laneDividerOffsets;
    public final int carriagewayWidth;
    /** Paved width excluding center median when {@link #includeMedian}. */
    public final int laneOnlyWidth;
    public final MaterialMix carriagewayMaterial;
    public final boolean includeMedian;
    public final int medianWidth;
    public final String medianMaterial;
    public final boolean laneDividers;
    public final CenterLineStyle centerLineStyle;
    public final String markingMaterial;
    public final boolean includeShoulder;
    public final int shoulderWidth;
    public final String shoulderMaterial;
    public final boolean includeBikeLane;
    public final int bikeLaneWidth;
    public final String bikeLaneMaterial;
    public final boolean includeSidewalk;
    public final int sidewalkWidth;
    public final String sidewalkMaterial;
    public final Integer streetlightSpacing;
    public final String streetlightBlock;
    public final boolean includeSlopeBatter;
    public final float fillSlopeRatio;
    public final float cutSlopeRatio;
    public final String fillSlopeMaterial;
    public final String cutSlopeMaterial;

    private ResolvedCrossSection(
            int laneCount,
            List<Integer> laneWidths,
            List<Double> laneDividerOffsets,
            int carriagewayWidth,
            int laneOnlyWidth,
            MaterialMix carriagewayMaterial,
            boolean includeMedian,
            int medianWidth,
            String medianMaterial,
            boolean laneDividers,
            CenterLineStyle centerLineStyle,
            String markingMaterial,
            boolean includeShoulder,
            int shoulderWidth,
            String shoulderMaterial,
            boolean includeBikeLane,
            int bikeLaneWidth,
            String bikeLaneMaterial,
            boolean includeSidewalk,
            int sidewalkWidth,
            String sidewalkMaterial,
            Integer streetlightSpacing,
            String streetlightBlock,
            boolean includeSlopeBatter,
            float fillSlopeRatio,
            float cutSlopeRatio,
            String fillSlopeMaterial,
            String cutSlopeMaterial) {
        this.laneCount = laneCount;
        this.laneWidths = List.copyOf(laneWidths);
        this.laneDividerOffsets = List.copyOf(laneDividerOffsets);
        this.carriagewayWidth = carriagewayWidth;
        this.laneOnlyWidth = laneOnlyWidth;
        this.carriagewayMaterial = carriagewayMaterial;
        this.includeMedian = includeMedian;
        this.medianWidth = medianWidth;
        this.medianMaterial = medianMaterial;
        this.laneDividers = laneDividers;
        this.centerLineStyle = centerLineStyle != null ? centerLineStyle : CenterLineStyle.NONE;
        this.markingMaterial = markingMaterial;
        this.includeShoulder = includeShoulder;
        this.shoulderWidth = shoulderWidth;
        this.shoulderMaterial = shoulderMaterial;
        this.includeBikeLane = includeBikeLane;
        this.bikeLaneWidth = bikeLaneWidth;
        this.bikeLaneMaterial = bikeLaneMaterial;
        this.includeSidewalk = includeSidewalk;
        this.sidewalkWidth = sidewalkWidth;
        this.sidewalkMaterial = sidewalkMaterial;
        this.streetlightSpacing = streetlightSpacing;
        this.streetlightBlock = streetlightBlock;
        this.includeSlopeBatter = includeSlopeBatter;
        this.fillSlopeRatio = fillSlopeRatio;
        this.cutSlopeRatio = cutSlopeRatio;
        this.fillSlopeMaterial = fillSlopeMaterial;
        this.cutSlopeMaterial = cutSlopeMaterial;
    }

    public static ResolvedCrossSection fromConfig(RoadSystemConfig defaults) {
        return resolve(RoadCrossSection.fromConfig(defaults), defaults);
    }

    public static ResolvedCrossSection resolve(RoadCrossSection section, RoadSystemConfig defaults) {
        return resolveWithReport(section, defaults).section();
    }

    public static CrossSectionResolveReport.ResolveResult resolveWithReport(
            RoadCrossSection section,
            RoadSystemConfig defaults) {
        CrossSectionResolveReport report = new CrossSectionResolveReport();
        RoadCrossSection source = section != null ? section : new RoadCrossSection();
        RoadSystemConfig config = defaults != null ? defaults : new RoadSystemConfig("road_system");

        LaneGroup carriageway = source.getCarriageway();
        Median median = source.getMedian();
        Markings markings = source.getMarkings();
        Shoulder shoulder = source.getShoulder();
        BikeLane bikeLane = source.getBikeLane();
        Sidewalk sidewalk = source.getSidewalk();
        SlopeBatter slopeBatter = source.getSlopeBatter();
        StreetFurniture furniture = source.getStreetFurniture();

        int width = carriageway.getWidth() != null ? carriageway.getWidth() : config.getRoadWidth();
        int safeWidth = Math.max(1, width);
        int laneCount = carriageway.getEffectiveLaneCount();

        boolean includeMedian = median.getEnabled() != null && median.getEnabled();
        int requestedMedian = includeMedian
            ? Math.max(1, median.getWidth() != null ? median.getWidth() : 1)
            : 0;
        int medianWidth = requestedMedian;
        if (includeMedian && requestedMedian > 0) {
            medianWidth = CrossSectionGeometryRules.clampMedianWidth(safeWidth, laneCount, requestedMedian);
            if (medianWidth < requestedMedian) {
                report.noteGeometry(
                    "median_clamped:" + requestedMedian + "->" + medianWidth
                        + " width=" + safeWidth + " lanes=" + laneCount);
            }
            if (medianWidth <= 0) {
                includeMedian = false;
                medianWidth = 0;
            }
        }
        int laneOnlyWidth = CrossSectionGeometryRules.laneOnlyWidth(safeWidth, laneCount, medianWidth);
        List<Integer> laneWidths = carriageway.resolveLaneWidths(laneOnlyWidth);
        List<Double> laneDividerOffsets = filterLaneDividerOffsets(
            carriageway.resolveLaneDividerOffsets(laneOnlyWidth),
            medianWidth);
        MaterialMix roadMaterial = MaterialMix.orDefault(
            carriageway.getMaterial(),
            config.getSelectedMaterial());
        String medianMaterial = median.getMaterial() != null
            ? median.getMaterial()
            : "material.plot.grass_block";

        boolean laneDividers = MarkingRules.resolveLaneDividers(markings, laneCount);
        CenterLineStyle centerLineStyle = MarkingRules.resolveCenterLineStyle(markings, config);
        String markingMaterial = markings.getMaterial() != null
            ? markings.getMaterial()
            : DEFAULT_MARKING_MATERIAL;

        boolean includeShoulder = shoulder.getEnabled() != null
            ? shoulder.getEnabled()
            : config.isIncludeShoulder();
        int shoulderWidth = shoulder.getWidth() != null ? shoulder.getWidth() : config.getShoulderWidth();
        String shoulderMaterial = shoulder.getMaterial() != null
            ? shoulder.getMaterial()
            : (config.getFillSlopeMaterial() != null && !config.getFillSlopeMaterial().isBlank()
                ? config.getFillSlopeMaterial()
                : com.plot.plugin.road.RoadMaterialUtils.DEFAULT_SLOPE_FILL_BLOCK);

        boolean includeBikeLane = bikeLane.getEnabled() != null && bikeLane.getEnabled();
        int bikeLaneWidth = includeBikeLane
            ? Math.max(1, bikeLane.getWidth() != null ? bikeLane.getWidth() : 1)
            : 0;
        String bikeLaneMaterial = bikeLane.getMaterial() != null
            ? bikeLane.getMaterial()
            : DEFAULT_BIKE_LANE_MATERIAL;

        boolean includeSidewalk = sidewalk.getEnabled() != null
            ? sidewalk.getEnabled()
            : config.isIncludeSidewalk();
        int sidewalkWidth = sidewalk.getWidth() != null ? sidewalk.getWidth() : config.getSidewalkWidth();
        String sidewalkMaterial = sidewalk.getMaterial() != null
            ? sidewalk.getMaterial()
            : config.getSelectedSidewalkMaterial();

        String streetlightBlock = furniture.getStreetlightBlock() != null
            ? furniture.getStreetlightBlock()
            : DEFAULT_STREETLIGHT_BLOCK;

        float fillRatio = slopeBatter.getFillRatio() != null
            ? slopeBatter.getFillRatio()
            : config.getFillSlopeRatio();
        float cutRatio = slopeBatter.getCutRatio() != null
            ? slopeBatter.getCutRatio()
            : config.getCutSlopeRatio();
        boolean slopeEnabled = slopeBatter.getEnabled() != null
            ? slopeBatter.getEnabled()
            : (fillRatio > 0f || cutRatio > 0f);
        String fillSlopeMaterial = slopeBatter.getFillMaterial() != null && !slopeBatter.getFillMaterial().isBlank()
            ? slopeBatter.getFillMaterial()
            : config.getFillSlopeMaterial();
        String cutSlopeMaterial = slopeBatter.getCutMaterial() != null
            ? slopeBatter.getCutMaterial()
            : config.getCutSlopeMaterial();
        boolean includeSlopeBatter = slopeEnabled && (fillRatio > 0f || cutRatio > 0f);

        ResolvedCrossSection resolved = new ResolvedCrossSection(
            laneCount,
            laneWidths,
            laneDividerOffsets,
            safeWidth,
            laneOnlyWidth,
            resolveMix(roadMaterial, MaterialRole.CARRIAGEWAY, report),
            includeMedian,
            medianWidth,
            resolveMaterial(medianMaterial, MaterialRole.MEDIAN, report),
            laneDividers,
            centerLineStyle,
            resolveMaterial(markingMaterial, MaterialRole.MARKING, report),
            includeShoulder,
            Math.max(0, shoulderWidth),
            resolveMaterial(shoulderMaterial, MaterialRole.SHOULDER, report),
            includeBikeLane,
            bikeLaneWidth,
            resolveMaterial(bikeLaneMaterial, MaterialRole.BIKE_LANE, report),
            includeSidewalk,
            Math.max(0, sidewalkWidth),
            resolveMaterial(sidewalkMaterial, MaterialRole.SIDEWALK, report),
            furniture.getStreetlightSpacing(),
            RoadMaterialUtils.normalizeStoredMaterial(streetlightBlock),
            includeSlopeBatter,
            includeSlopeBatter ? fillRatio : 0f,
            includeSlopeBatter ? cutRatio : 0f,
            resolveMaterial(fillSlopeMaterial, MaterialRole.FILL_SLOPE, report),
            cutSlopeMaterial != null && !cutSlopeMaterial.isBlank()
                ? resolveMaterial(cutSlopeMaterial, MaterialRole.CUT_SLOPE, report)
                : null
        );
        return new CrossSectionResolveReport.ResolveResult(resolved, report);
    }

    private static MaterialMix resolveMix(
            MaterialMix mix,
            MaterialRole role,
            CrossSectionResolveReport report) {
        return RoadConstructionMaterialPolicy.resolve(
            RoadMaterialMixUtils.normalizeStored(mix),
            role,
            (requested, resolved) -> report.noteMaterialReplacement(requested, resolved, role.name()));
    }

    private static String resolveMaterial(
            String material,
            MaterialRole role,
            CrossSectionResolveReport report) {
        return RoadConstructionMaterialPolicy.resolveStored(
            material,
            role,
            (requested, resolved) -> report.noteMaterialReplacement(requested, resolved, role.name()));
    }

    private static List<Double> filterLaneDividerOffsets(List<Double> offsets, int medianWidth) {
        if (offsets == null || offsets.isEmpty() || medianWidth <= 0) {
            return offsets != null ? List.copyOf(offsets) : List.of();
        }
        double halfMedian = medianWidth / 2.0;
        List<Double> filtered = new ArrayList<>(offsets.size());
        for (Double offset : offsets) {
            if (offset == null) {
                continue;
            }
            if (Math.abs(offset) < halfMedian - 1e-6) {
                continue;
            }
            filtered.add(offset);
        }
        return List.copyOf(filtered);
    }

    public int totalHardSurfaceWidthBlocks() {
        return carriagewayWidth + 2 * outerBandBlockCount();
    }

    public boolean occupiesMedianColumn(int lateralOffset) {
        if (!includeMedian || medianWidth <= 0) {
            return false;
        }
        int min = RoadDimensionUtils.minLateralOffset(medianWidth);
        int max = RoadDimensionUtils.maxLateralOffset(medianWidth);
        return lateralOffset >= min && lateralOffset <= max;
    }

    public double carriagewayHalfWidth() {
        return RoadDimensionUtils.halfExtentFromCenter(carriagewayWidth);
    }

    /** 路肩 + 自行车道 + 人行道总方块宽度。 */
    public int outerBandBlockCount() {
        return (includeShoulder ? shoulderWidth : 0)
            + (includeBikeLane ? bikeLaneWidth : 0)
            + (includeSidewalk ? sidewalkWidth : 0);
    }

    private static double stripCenterOffset(int startLateral, int widthBlocks) {
        if (widthBlocks <= 0) {
            return 0.0;
        }
        return startLateral + (widthBlocks - 1) / 2.0;
    }

    public double shoulderCenterOffset() {
        if (!includeShoulder || shoulderWidth <= 0) {
            return 0.0;
        }
        int start = RoadDimensionUtils.maxLateralOffset(carriagewayWidth) + 1;
        return stripCenterOffset(start, shoulderWidth);
    }

    /** 边坡锚点：最外侧硬质横断面条带（人行道 &gt; 自行车道 &gt; 路肩 &gt; 行车道外缘）的中心线横向偏移。 */
    public double slopeAnchorCenterOffset() {
        if (includeSidewalk && sidewalkWidth > 0) {
            return sidewalkCenterOffset();
        }
        if (includeBikeLane && bikeLaneWidth > 0) {
            return bikeLaneCenterOffset();
        }
        if (includeShoulder && shoulderWidth > 0) {
            return shoulderCenterOffset();
        }
        return carriagewayHalfWidth();
    }

    /** 边坡锚点条带宽度；锚点在行车道外缘时为 0。 */
    public int slopeAnchorBandWidth() {
        if (includeSidewalk && sidewalkWidth > 0) {
            return sidewalkWidth;
        }
        if (includeBikeLane && bikeLaneWidth > 0) {
            return bikeLaneWidth;
        }
        if (includeShoulder && shoulderWidth > 0) {
            return shoulderWidth;
        }
        return 0;
    }

    public double bikeLaneCenterOffset() {
        if (!includeBikeLane || bikeLaneWidth <= 0) {
            return 0.0;
        }
        int start = RoadDimensionUtils.maxLateralOffset(carriagewayWidth) + 1;
        if (includeShoulder) {
            start += shoulderWidth;
        }
        return stripCenterOffset(start, bikeLaneWidth);
    }

    public double sidewalkCenterOffset() {
        if (!includeSidewalk || sidewalkWidth <= 0) {
            return 0.0;
        }
        int start = RoadDimensionUtils.maxLateralOffset(carriagewayWidth) + 1;
        if (includeShoulder) {
            start += shoulderWidth;
        }
        if (includeBikeLane) {
            start += bikeLaneWidth;
        }
        return stripCenterOffset(start, sidewalkWidth);
    }

    /** 路肩外侧过渡带总宽度（路肩 + 自行车道 + 人行道）。 */
    public double outerBandWidth() {
        return (includeShoulder ? shoulderWidth : 0)
            + (includeBikeLane ? bikeLaneWidth : 0)
            + (includeSidewalk ? sidewalkWidth : 0);
    }

    /** Resolved surface layer and material for one signed lateral block offset from centerline. */
    public record SurfaceBandSpec(
            RoadSolidLayer layer,
            MaterialMix materialMix,
            String materialId) {
    }

    public int minSurfaceLateral() {
        return RoadDimensionUtils.minLateralOffset(totalHardSurfaceWidthBlocks());
    }

    public int maxSurfaceLateral() {
        return RoadDimensionUtils.maxLateralOffset(totalHardSurfaceWidthBlocks());
    }

    public Optional<SurfaceBandSpec> surfaceBandAtLateral(int lateral) {
        int roadMin = RoadDimensionUtils.minLateralOffset(carriagewayWidth);
        int roadMax = RoadDimensionUtils.maxLateralOffset(carriagewayWidth);
        if (lateral >= roadMin && lateral <= roadMax) {
            return Optional.of(new SurfaceBandSpec(RoadSolidLayer.ROAD, carriagewayMaterial, null));
        }
        if (lateral > roadMax) {
            return outerBandAtDistance(lateral - roadMax);
        }
        if (lateral < roadMin) {
            return outerBandAtDistance(roadMin - lateral);
        }
        return Optional.empty();
    }

    private Optional<SurfaceBandSpec> outerBandAtDistance(int distanceFromRoadEdge) {
        if (distanceFromRoadEdge <= 0) {
            return Optional.empty();
        }
        int cursor = 0;
        if (includeShoulder && shoulderWidth > 0) {
            cursor += shoulderWidth;
            if (distanceFromRoadEdge <= cursor) {
                return Optional.of(new SurfaceBandSpec(RoadSolidLayer.SHOULDER, null, shoulderMaterial));
            }
        }
        if (includeBikeLane && bikeLaneWidth > 0) {
            int bandStart = cursor;
            cursor += bikeLaneWidth;
            if (distanceFromRoadEdge <= cursor) {
                return Optional.of(new SurfaceBandSpec(RoadSolidLayer.BIKE_LANE, null, bikeLaneMaterial));
            }
        }
        if (includeSidewalk && sidewalkWidth > 0) {
            int bandStart = cursor;
            cursor += sidewalkWidth;
            if (distanceFromRoadEdge <= cursor) {
                return Optional.of(new SurfaceBandSpec(RoadSolidLayer.SIDEWALK, null, sidewalkMaterial));
            }
        }
        return Optional.empty();
    }

    /**
     * 最终显示等价：比较 {@link #resolve} 后的有效断面，使「继承全局」与「显式同值」在预览中视为一致。
     */
    public boolean displayEquals(ResolvedCrossSection other) {
        if (this == other) {
            return true;
        }
        if (other == null) {
            return false;
        }
        return laneCount == other.laneCount
            && carriagewayWidth == other.carriagewayWidth
            && laneOnlyWidth == other.laneOnlyWidth
            && Objects.equals(laneWidths, other.laneWidths)
            && Objects.equals(laneDividerOffsets, other.laneDividerOffsets)
            && materialMixEquals(carriagewayMaterial, other.carriagewayMaterial)
            && includeMedian == other.includeMedian
            && medianWidth == other.medianWidth
            && Objects.equals(medianMaterial, other.medianMaterial)
            && laneDividers == other.laneDividers
            && centerLineStyle == other.centerLineStyle
            && Objects.equals(markingMaterial, other.markingMaterial)
            && includeShoulder == other.includeShoulder
            && shoulderWidth == other.shoulderWidth
            && Objects.equals(shoulderMaterial, other.shoulderMaterial)
            && includeBikeLane == other.includeBikeLane
            && bikeLaneWidth == other.bikeLaneWidth
            && Objects.equals(bikeLaneMaterial, other.bikeLaneMaterial)
            && includeSidewalk == other.includeSidewalk
            && sidewalkWidth == other.sidewalkWidth
            && Objects.equals(sidewalkMaterial, other.sidewalkMaterial)
            && Objects.equals(streetlightSpacing, other.streetlightSpacing)
            && Objects.equals(streetlightBlock, other.streetlightBlock)
            && includeSlopeBatter == other.includeSlopeBatter
            && Float.compare(fillSlopeRatio, other.fillSlopeRatio) == 0
            && Float.compare(cutSlopeRatio, other.cutSlopeRatio) == 0
            && Objects.equals(fillSlopeMaterial, other.fillSlopeMaterial)
            && Objects.equals(cutSlopeMaterial, other.cutSlopeMaterial);
    }

    private static boolean materialMixEquals(MaterialMix left, MaterialMix right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return Objects.equals(left.getPrimaryMaterial(), right.getPrimaryMaterial())
            && Objects.equals(left.getAccentMaterial(), right.getAccentMaterial())
            && Float.compare(left.getAccentRatio(), right.getAccentRatio()) == 0;
    }

    /**
     * 平滑过渡：宽度/坡比等连续量线性插值；车道数、材质、标线样式等离散量取较近侧（t&lt;0.5 取 from）。
     * 可选条带的 enable 由插值后的有效宽度是否 &gt;0 决定。
     */
    public static ResolvedCrossSection lerpContinuous(
            ResolvedCrossSection from,
            ResolvedCrossSection to,
            double t) {
        if (from == null) {
            return to;
        }
        if (to == null || t <= 0.0) {
            return from;
        }
        if (t >= 1.0) {
            return to;
        }
        ResolvedCrossSection discrete = t < 0.5 ? from : to;

        int carriagewayWidth = Math.max(1, lerpRound(from.carriagewayWidth, to.carriagewayWidth, t));
        int medianWidth = lerpRound(
            effectiveWidth(from.includeMedian, from.medianWidth),
            effectiveWidth(to.includeMedian, to.medianWidth),
            t);
        boolean includeMedian = medianWidth > 0;
        if (includeMedian) {
            medianWidth = CrossSectionGeometryRules.clampMedianWidth(
                carriagewayWidth, discrete.laneCount, Math.max(1, medianWidth));
            if (medianWidth <= 0) {
                includeMedian = false;
                medianWidth = 0;
            }
        }
        int laneOnlyWidth = CrossSectionGeometryRules.laneOnlyWidth(
            carriagewayWidth, discrete.laneCount, medianWidth);
        List<Integer> laneWidths = scaleLaneWidths(discrete.laneWidths, laneOnlyWidth);
        List<Double> laneDividerOffsets = scaleOffsets(
            discrete.laneDividerOffsets, discrete.laneOnlyWidth, laneOnlyWidth);

        int shoulderWidth = lerpRound(
            effectiveWidth(from.includeShoulder, from.shoulderWidth),
            effectiveWidth(to.includeShoulder, to.shoulderWidth),
            t);
        boolean includeShoulder = shoulderWidth > 0;

        int bikeLaneWidth = lerpRound(
            effectiveWidth(from.includeBikeLane, from.bikeLaneWidth),
            effectiveWidth(to.includeBikeLane, to.bikeLaneWidth),
            t);
        boolean includeBikeLane = bikeLaneWidth > 0;

        int sidewalkWidth = lerpRound(
            effectiveWidth(from.includeSidewalk, from.sidewalkWidth),
            effectiveWidth(to.includeSidewalk, to.sidewalkWidth),
            t);
        boolean includeSidewalk = sidewalkWidth > 0;

        float fillRatio = lerpFloat(
            from.includeSlopeBatter ? from.fillSlopeRatio : 0f,
            to.includeSlopeBatter ? to.fillSlopeRatio : 0f,
            t);
        float cutRatio = lerpFloat(
            from.includeSlopeBatter ? from.cutSlopeRatio : 0f,
            to.includeSlopeBatter ? to.cutSlopeRatio : 0f,
            t);
        boolean includeSlopeBatter = fillRatio > 0f || cutRatio > 0f;

        Integer streetlightSpacing = lerpNullableInt(from.streetlightSpacing, to.streetlightSpacing, t);

        return new ResolvedCrossSection(
            discrete.laneCount,
            laneWidths,
            laneDividerOffsets,
            carriagewayWidth,
            laneOnlyWidth,
            discrete.carriagewayMaterial,
            includeMedian,
            medianWidth,
            discrete.medianMaterial,
            discrete.laneDividers,
            discrete.centerLineStyle,
            discrete.markingMaterial,
            includeShoulder,
            Math.max(0, shoulderWidth),
            discrete.shoulderMaterial,
            includeBikeLane,
            includeBikeLane ? Math.max(1, bikeLaneWidth) : 0,
            discrete.bikeLaneMaterial,
            includeSidewalk,
            Math.max(0, sidewalkWidth),
            discrete.sidewalkMaterial,
            streetlightSpacing,
            discrete.streetlightBlock,
            includeSlopeBatter,
            includeSlopeBatter ? fillRatio : 0f,
            includeSlopeBatter ? cutRatio : 0f,
            discrete.fillSlopeMaterial,
            discrete.cutSlopeMaterial
        );
    }

    private static int effectiveWidth(boolean enabled, int width) {
        return enabled ? Math.max(0, width) : 0;
    }

    private static int lerpRound(int a, int b, double t) {
        return (int) Math.round(a + (b - a) * t);
    }

    private static float lerpFloat(float a, float b, double t) {
        return (float) (a + (b - a) * t);
    }

    private static Integer lerpNullableInt(Integer a, Integer b, double t) {
        if (a == null && b == null) {
            return null;
        }
        if (a == null || b == null) {
            return t < 0.5 ? a : b;
        }
        return lerpRound(a, b, t);
    }

    private static List<Integer> scaleLaneWidths(List<Integer> source, int targetSum) {
        if (source == null || source.isEmpty()) {
            return List.of(Math.max(1, targetSum));
        }
        int safeTarget = Math.max(source.size(), targetSum);
        int sum = 0;
        for (Integer width : source) {
            sum += width != null ? Math.max(0, width) : 0;
        }
        if (sum <= 0) {
            int each = Math.max(1, safeTarget / source.size());
            List<Integer> equal = new ArrayList<>(source.size());
            int used = 0;
            for (int i = 0; i < source.size(); i++) {
                int w = i == source.size() - 1 ? Math.max(1, safeTarget - used) : each;
                equal.add(w);
                used += w;
            }
            return List.copyOf(equal);
        }
        List<Integer> scaled = new ArrayList<>(source.size());
        int used = 0;
        for (int i = 0; i < source.size(); i++) {
            int raw = source.get(i) != null ? Math.max(0, source.get(i)) : 0;
            int w;
            if (i == source.size() - 1) {
                w = Math.max(1, safeTarget - used);
            } else {
                w = Math.max(1, (int) Math.round(raw * (double) safeTarget / sum));
                used += w;
            }
            scaled.add(w);
        }
        return List.copyOf(scaled);
    }

    private static List<Double> scaleOffsets(List<Double> offsets, int fromWidth, int toWidth) {
        if (offsets == null || offsets.isEmpty()) {
            return List.of();
        }
        if (fromWidth <= 0 || fromWidth == toWidth) {
            return List.copyOf(offsets);
        }
        double scale = (double) toWidth / fromWidth;
        List<Double> scaled = new ArrayList<>(offsets.size());
        for (Double offset : offsets) {
            if (offset != null) {
                scaled.add(offset * scale);
            }
        }
        return List.copyOf(scaled);
    }
}
