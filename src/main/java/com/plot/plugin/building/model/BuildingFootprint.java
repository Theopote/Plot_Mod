package com.plot.plugin.building.model;

import com.plot.api.geometry.Vec2d;
import com.plot.core.material.MaterialMix;
import com.plot.plugin.building.BuildingGeometryUtils;
import com.plot.plugin.building.model.spec.FacadeEdgeScope;
import com.plot.plugin.building.model.spec.FloorPlateSpec;
import com.plot.plugin.building.model.spec.OpeningKind;
import com.plot.plugin.building.model.spec.OpeningSpec;
import com.plot.plugin.building.model.spec.WallFacadeSpec;
import com.plot.plugin.building.model.spec.WindowPatternSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 已认领的建筑轮廓及生成参数。
 * <p>
 * <strong>冻结线：</strong>本类视为 Legacy Persistence DTO（JSON / 认领轮廓）。
 * 新语义能力优先放 {@link com.plot.plugin.building.model.spec.BuildingDefinition}
 * 与后续 {@code ResolvedBuilding}，不要继续往这里堆 {@code setXxx} 字段。
 * 短期为兼容仍可经 Mapper 回写 Footprint，但不要把新模型做成旧 God Object 的包装。
 */
public class BuildingFootprint {
    public static final int MIN_FLOORS = 1;
    public static final int MAX_FLOORS = 64;
    public static final int MAX_WINDOW_WIDTH = 10;

    public static final String DEFAULT_WALL_MATERIAL = "minecraft:stone_bricks";
    public static final String DEFAULT_FLOOR_MATERIAL = "minecraft:oak_planks";
    public static final String DEFAULT_ROOF_MATERIAL = "minecraft:stone_bricks";
    public static final String DEFAULT_FOUNDATION_FILL = "minecraft:stone";
    public static final String DEFAULT_WINDOW_MATERIAL = "minecraft:glass_pane";

    public enum RoofType {
        FLAT, GABLE, HIP, NONE;

        public boolean isSloped() {
            return this == GABLE || this == HIP;
        }
    }

    public static class Canopy {
        public int wallSegmentIndex;
        public double positionRatio;
        public int floor;
        public int width = 3;
        public int depth = 2;
        public int clearance = 3;
        public String material;

        public Canopy() {
        }

        public Canopy(
                int wallSegmentIndex,
                double positionRatio,
                int floor,
                int width,
                int depth,
                int clearance,
                String material) {
            this.wallSegmentIndex = wallSegmentIndex;
            this.positionRatio = positionRatio;
            this.floor = floor;
            this.width = width;
            this.depth = depth;
            this.clearance = clearance;
            this.material = material;
        }

        public Canopy copy() {
            return new Canopy(wallSegmentIndex, positionRatio, floor, width, depth, clearance, material);
        }
    }

    public static class Balcony {
        public int wallSegmentIndex;
        public double positionRatio;
        public int floor;
        public int width = 3;
        public int depth = 2;
        public String slabMaterial;
        public String railingMaterial;

        public Balcony() {
        }

        public Balcony(
                int wallSegmentIndex,
                double positionRatio,
                int floor,
                int width,
                int depth,
                String slabMaterial,
                String railingMaterial) {
            this.wallSegmentIndex = wallSegmentIndex;
            this.positionRatio = positionRatio;
            this.floor = floor;
            this.width = width;
            this.depth = depth;
            this.slabMaterial = slabMaterial;
            this.railingMaterial = railingMaterial;
        }

        public Balcony copy() {
            return new Balcony(
                wallSegmentIndex, positionRatio, floor, width, depth, slabMaterial, railingMaterial);
        }
    }

    private final String id;
    private String name;
    private List<Vec2d> outerPoints;
    private boolean isRectangular;
    private transient Boolean slopedRoofEligible;

    private int floors = 1;
    private int floorHeight = 3;
    private int wallThickness = 1;
    private MaterialMix wallMaterial = MaterialMix.single(DEFAULT_WALL_MATERIAL);
    private MaterialMix floorMaterial = MaterialMix.single(DEFAULT_FLOOR_MATERIAL);
    private String roofMaterial = DEFAULT_ROOF_MATERIAL;
    private String foundationFillMaterial = DEFAULT_FOUNDATION_FILL;
    private String windowMaterial = DEFAULT_WINDOW_MATERIAL;

    private RoofType roofType = RoofType.FLAT;
    private int roofPitchRatio = 1;
    private int roofEaves = 0;

    private Integer manualBaseElevation;
    private boolean windowsEnabled = true;
    private int windowPierWidth = 3;
    private int windowWidth = 1;
    private int windowHeight = 2;
    private int windowSillHeight = 1;

    private List<FloorPlateSpec> floorPlates = new ArrayList<>();
    private List<WallFacadeSpec> wallFacades = new ArrayList<>();
    private List<OpeningSpec> openings = new ArrayList<>();
    /** 立面边索引作用域；默认相对基础 footprint。 */
    private FacadeEdgeScope facadeEdgeScope = FacadeEdgeScope.BASE_FOOTPRINT;

    private boolean parapetEnabled;
    private int parapetHeight = 1;
    private String parapetMaterial;
    private List<Canopy> canopies = new ArrayList<>();
    private List<Balcony> balconies = new ArrayList<>();
    private String presetId = "";

    public BuildingFootprint(List<Vec2d> outerPoints, boolean isRectangular) {
        this(UUID.randomUUID().toString(), outerPoints, isRectangular);
    }

    public BuildingFootprint(String id, List<Vec2d> outerPoints, boolean isRectangular) {
        this.id = id;
        this.outerPoints = copyPoints(outerPoints);
        this.isRectangular = isRectangular;
        this.name = id.substring(0, Math.min(8, id.length()));
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name != null && !name.isBlank() ? name.trim() : this.name;
    }

    public List<Vec2d> getOuterPoints() {
        return copyPoints(outerPoints);
    }

    public void setOuterPoints(List<Vec2d> outerPoints) {
        this.outerPoints = copyPoints(outerPoints);
        this.slopedRoofEligible = null;
    }

    /** 已缓存的坡顶 eligibility；未计算时返回 {@code null}（UI 渲染路径勿强制触发计算）。 */
    public Boolean peekSlopedRoofEligibility() {
        return slopedRoofEligible;
    }

    public boolean isRectangular() {
        return isRectangular;
    }

    public void setRectangular(boolean rectangular) {
        isRectangular = rectangular;
    }

    public int getFloors() {
        return floors;
    }

    public void setFloors(int floors) {
        this.floors = Math.max(MIN_FLOORS, Math.min(MAX_FLOORS, floors));
    }

    public int getFloorHeight() {
        return floorHeight;
    }

    public void setFloorHeight(int floorHeight) {
        this.floorHeight = Math.max(2, Math.min(16, floorHeight));
        clampWindowToFloorHeight();
    }

    public int getWallThickness() {
        return wallThickness;
    }

    public void setWallThickness(int wallThickness) {
        this.wallThickness = Math.max(1, Math.min(8, wallThickness));
    }

    public MaterialMix getWallMaterial() {
        return wallMaterial;
    }

    public void setWallMaterial(MaterialMix wallMaterial) {
        this.wallMaterial = wallMaterial != null && wallMaterial.getPrimaryMaterial() != null
            && !wallMaterial.getPrimaryMaterial().isBlank()
            ? wallMaterial
            : MaterialMix.single(DEFAULT_WALL_MATERIAL);
    }

    public void setWallMaterial(String wallMaterial) {
        this.wallMaterial = wallMaterial != null && !wallMaterial.isBlank()
            ? MaterialMix.single(wallMaterial.trim())
            : MaterialMix.single(DEFAULT_WALL_MATERIAL);
    }

    public MaterialMix getFloorMaterial() {
        return floorMaterial;
    }

    public void setFloorMaterial(MaterialMix floorMaterial) {
        this.floorMaterial = floorMaterial != null && floorMaterial.getPrimaryMaterial() != null
            && !floorMaterial.getPrimaryMaterial().isBlank()
            ? floorMaterial
            : MaterialMix.single(DEFAULT_FLOOR_MATERIAL);
    }

    public void setFloorMaterial(String floorMaterial) {
        this.floorMaterial = floorMaterial != null && !floorMaterial.isBlank()
            ? MaterialMix.single(floorMaterial.trim())
            : MaterialMix.single(DEFAULT_FLOOR_MATERIAL);
    }

    public String getRoofMaterial() {
        return roofMaterial;
    }

    public void setRoofMaterial(String roofMaterial) {
        this.roofMaterial = roofMaterial != null && !roofMaterial.isBlank()
            ? roofMaterial.trim() : DEFAULT_ROOF_MATERIAL;
    }

    public String getFoundationFillMaterial() {
        return foundationFillMaterial;
    }

    public void setFoundationFillMaterial(String foundationFillMaterial) {
        this.foundationFillMaterial = foundationFillMaterial != null && !foundationFillMaterial.isBlank()
            ? foundationFillMaterial.trim() : DEFAULT_FOUNDATION_FILL;
    }

    public String getWindowMaterial() {
        return windowMaterial;
    }

    public void setWindowMaterial(String windowMaterial) {
        this.windowMaterial = windowMaterial != null && !windowMaterial.isBlank()
            ? windowMaterial.trim() : DEFAULT_WINDOW_MATERIAL;
    }

    public RoofType getRoofType() {
        return roofType;
    }

    public void setRoofType(RoofType roofType) {
        this.roofType = roofType != null ? roofType : RoofType.FLAT;
    }

    public int getRoofPitchRatio() {
        return roofPitchRatio;
    }

    public void setRoofPitchRatio(int roofPitchRatio) {
        this.roofPitchRatio = Math.max(1, Math.min(16, roofPitchRatio));
    }

    public int getRoofEaves() {
        return roofEaves;
    }

    public void setRoofEaves(int roofEaves) {
        this.roofEaves = Math.max(0, Math.min(5, roofEaves));
    }

    /** 按当前坡度/屋檐重算坡顶 eligibility（Straight Skeleton，较重，勿在拖动时每帧调用）。 */
    public void refreshSlopedRoofEligibility() {
        slopedRoofEligible = BuildingGeometryUtils.isSlopedRoofEligible(
            outerPoints, roofPitchRatio, roofEaves);
    }

    public Integer getManualBaseElevation() {
        return manualBaseElevation;
    }

    public void setManualBaseElevation(Integer manualBaseElevation) {
        this.manualBaseElevation = manualBaseElevation;
    }

    public boolean isWindowsEnabled() {
        return windowsEnabled;
    }

    public void setWindowsEnabled(boolean windowsEnabled) {
        this.windowsEnabled = windowsEnabled;
    }

    /** 窗间墙宽度（格）。 */
    public int getWindowPierWidth() {
        return windowPierWidth;
    }

    public void setWindowPierWidth(int windowPierWidth) {
        this.windowPierWidth = Math.max(0, Math.min(32, windowPierWidth));
    }

    /**
     * 兼容旧 API：相邻窗起始列间距 = 窗宽 + 窗间墙宽；未启用时返回 0。
     */
    public int getWindowSpacing() {
        return windowsEnabled ? windowWidth + windowPierWidth : 0;
    }

    /**
     * 兼容旧 API：{@code spacing <= 0} 表示不开窗，否则按 pier = spacing - width 迁移。
     */
    public void setWindowSpacing(int windowSpacing) {
        if (windowSpacing <= 0) {
            windowsEnabled = false;
            return;
        }
        windowsEnabled = true;
        windowPierWidth = Math.max(0, Math.min(32, windowSpacing - windowWidth));
    }

    public int getWindowWidth() {
        return windowWidth;
    }

    public void setWindowWidth(int windowWidth) {
        this.windowWidth = Math.max(1, Math.min(MAX_WINDOW_WIDTH, windowWidth));
    }

    public int getWindowHeight() {
        return windowHeight;
    }

    public void setWindowHeight(int windowHeight) {
        this.windowHeight = Math.max(1, Math.min(16, windowHeight));
    }

    public int getWindowSillHeight() {
        return windowSillHeight;
    }

    public void setWindowSillHeight(int windowSillHeight) {
        this.windowSillHeight = Math.max(0, Math.min(16, windowSillHeight));
    }

    /** 窗宽/窗高/窗台与当前层高校验一致（窗台自楼板上表面起算，与生成阶段相同）。 */
    public void clampWindowToFloorHeight() {
        int fh = getFloorHeight();
        int maxSpan = Math.max(0, fh - 1);
        windowWidth = Math.max(1, Math.min(MAX_WINDOW_WIDTH, windowWidth));
        windowSillHeight = Math.max(0, Math.min(maxSpan, windowSillHeight));
        windowHeight = Math.max(1, Math.min(fh, windowHeight));
        if (windowSillHeight + windowHeight > maxSpan) {
            windowHeight = Math.max(1, maxSpan - windowSillHeight);
        }
    }

    /** 显式门洞（{@link OpeningKind#DOOR}）。 */
    public List<OpeningSpec> doorOpenings() {
        return openings.stream()
            .filter(opening -> opening.kind() == OpeningKind.DOOR)
            .map(this::copyOpening)
            .toList();
    }


    /**
     * 显式立面开洞（门、拱、单窗等）。窗型阵列 pattern 仍由全局/分立面窗型参数控制。
     */
    public List<OpeningSpec> getOpenings() {
        return openings.stream()
            .map(this::copyOpening)
            .toList();
    }

    public void setOpenings(List<OpeningSpec> openings) {
        this.openings = openings != null
            ? openings.stream().map(this::copyOpening).collect(java.util.stream.Collectors.toCollection(ArrayList::new))
            : new ArrayList<>();
    }

    public void addOpening(OpeningSpec opening) {
        if (opening != null) {
            openings.add(copyOpening(opening));
        }
    }

    private OpeningSpec copyOpening(OpeningSpec opening) {
        return new OpeningSpec(
            opening.kind(),
            opening.wallSegmentIndex(),
            opening.positionRatio(),
            opening.floor(),
            opening.width(),
            opening.height(),
            opening.bottomOffset()
        );
    }

    /**
     * 分楼层轮廓板。为空时表示全楼统一使用 {@link #getOuterPoints()}。
     */
    public List<FloorPlateSpec> getFloorPlates() {
        return floorPlates.stream()
            .map(plate -> FloorPlateSpec.of(plate.floorStart(), plate.floorEnd(), plate.outerPoints()))
            .toList();
    }

    public void setFloorPlates(List<FloorPlateSpec> floorPlates) {
        this.floorPlates = floorPlates != null
            ? floorPlates.stream()
                .map(plate -> FloorPlateSpec.of(plate.floorStart(), plate.floorEnd(), plate.outerPoints()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new))
            : new ArrayList<>();
    }

    /**
     * 分墙段立面覆盖。为空时全楼使用全局窗型参数。
     */
    public List<WallFacadeSpec> getWallFacades() {
        return wallFacades.stream()
            .map(facade -> WallFacadeSpec.of(facade.wallSegmentIndex(), facade.windowPattern()))
            .toList();
    }

    public void setWallFacades(List<WallFacadeSpec> wallFacades) {
        this.wallFacades = wallFacades != null
            ? wallFacades.stream()
                .map(facade -> WallFacadeSpec.of(facade.wallSegmentIndex(), facade.windowPattern()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new))
            : new ArrayList<>();
    }

    public FacadeEdgeScope getFacadeEdgeScope() {
        return facadeEdgeScope != null ? facadeEdgeScope : FacadeEdgeScope.BASE_FOOTPRINT;
    }

    public void setFacadeEdgeScope(FacadeEdgeScope facadeEdgeScope) {
        this.facadeEdgeScope = facadeEdgeScope != null
            ? facadeEdgeScope
            : FacadeEdgeScope.BASE_FOOTPRINT;
    }

    public boolean isParapetEnabled() {
        return parapetEnabled;
    }

    public void setParapetEnabled(boolean parapetEnabled) {
        this.parapetEnabled = parapetEnabled;
    }

    public int getParapetHeight() {
        return parapetHeight;
    }

    public void setParapetHeight(int parapetHeight) {
        this.parapetHeight = Math.max(1, Math.min(parapetHeight, 8));
    }

    public String getParapetMaterial() {
        return parapetMaterial;
    }

    public void setParapetMaterial(String parapetMaterial) {
        this.parapetMaterial = parapetMaterial;
    }

    public List<Canopy> getCanopies() {
        return canopies.stream().map(Canopy::copy).toList();
    }

    public void setCanopies(List<Canopy> canopies) {
        this.canopies = canopies != null
            ? canopies.stream().map(Canopy::copy)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new))
            : new ArrayList<>();
    }

    public void addCanopy(Canopy canopy) {
        if (canopy != null) {
            canopies.add(canopy.copy());
        }
    }

    public List<Balcony> getBalconies() {
        return balconies.stream().map(Balcony::copy).toList();
    }

    public void setBalconies(List<Balcony> balconies) {
        this.balconies = balconies != null
            ? balconies.stream().map(Balcony::copy)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new))
            : new ArrayList<>();
    }

    public void addBalcony(Balcony balcony) {
        if (balcony != null) {
            balconies.add(balcony.copy());
        }
    }

    public String getPresetId() {
        return presetId != null ? presetId : "";
    }

    public void setPresetId(String presetId) {
        this.presetId = presetId != null ? presetId.trim() : "";
    }

    public double computeArea() {
        return Math.abs(signedArea(outerPoints));
    }

    public int geometryFingerprint() {
        int hash = outerPoints.size();
        for (Vec2d point : outerPoints) {
            hash = 31 * hash + pointFingerprint(point);
        }
        return hash;
    }

    /** 影响生成结果的参数指纹（供 Preview Identity 等契约校验使用）。 */
    public int generationFingerprint() {
        int hash = geometryFingerprint();
        hash = 31 * hash + floors;
        hash = 31 * hash + floorHeight;
        hash = 31 * hash + wallThickness;
        hash = 31 * hash + materialMixFingerprint(wallMaterial);
        hash = 31 * hash + materialMixFingerprint(floorMaterial);
        hash = 31 * hash + Objects.hashCode(roofMaterial);
        hash = 31 * hash + Objects.hashCode(foundationFillMaterial);
        hash = 31 * hash + Objects.hashCode(windowMaterial);
        hash = 31 * hash + Objects.hashCode(roofType);
        hash = 31 * hash + roofPitchRatio;
        hash = 31 * hash + roofEaves;
        hash = 31 * hash + Objects.hashCode(manualBaseElevation);
        hash = 31 * hash + (windowsEnabled ? 1 : 0);
        hash = 31 * hash + windowPierWidth;
        hash = 31 * hash + windowWidth;
        hash = 31 * hash + windowHeight;
        hash = 31 * hash + windowSillHeight;
        hash = 31 * hash + Objects.hashCode(facadeEdgeScope);
        hash = 31 * hash + (parapetEnabled ? 1 : 0);
        hash = 31 * hash + parapetHeight;
        hash = 31 * hash + Objects.hashCode(parapetMaterial);
        hash = 31 * hash + Objects.hashCode(presetId);
        hash = 31 * hash + (isRectangular ? 1 : 0);
        hash = 31 * hash + floorPlatesFingerprint(floorPlates);
        hash = 31 * hash + wallFacadesFingerprint(wallFacades);
        hash = 31 * hash + openingsFingerprint(openings);
        hash = 31 * hash + canopiesFingerprint(canopies);
        hash = 31 * hash + balconiesFingerprint(balconies);
        return hash;
    }

    private static int materialMixFingerprint(MaterialMix mix) {
        if (mix == null) {
            return 0;
        }
        int hash = Objects.hashCode(mix.getPrimaryMaterial());
        hash = 31 * hash + Objects.hashCode(mix.getAccentMaterial());
        hash = 31 * hash + Float.floatToIntBits(mix.getAccentRatio());
        return hash;
    }

    private static int floorPlatesFingerprint(List<FloorPlateSpec> plates) {
        if (plates == null || plates.isEmpty()) {
            return 0;
        }
        int hash = plates.size();
        for (FloorPlateSpec plate : plates) {
            hash = 31 * hash + plate.floorStart();
            hash = 31 * hash + plate.floorEnd();
            for (Vec2d point : plate.outerPoints()) {
                hash = 31 * hash + pointFingerprint(point);
            }
        }
        return hash;
    }

    private static int wallFacadesFingerprint(List<WallFacadeSpec> facades) {
        if (facades == null || facades.isEmpty()) {
            return 0;
        }
        int hash = facades.size();
        for (WallFacadeSpec facade : facades) {
            hash = 31 * hash + facade.wallSegmentIndex();
            WindowPatternSpec pattern = facade.windowPattern();
            hash = 31 * hash + pattern.spacing();
            hash = 31 * hash + pattern.width();
            hash = 31 * hash + pattern.height();
            hash = 31 * hash + pattern.sillHeight();
        }
        return hash;
    }

    private static int openingsFingerprint(List<OpeningSpec> specs) {
        if (specs == null || specs.isEmpty()) {
            return 0;
        }
        int hash = specs.size();
        for (OpeningSpec opening : specs) {
            hash = 31 * hash + Objects.hashCode(opening.kind());
            hash = 31 * hash + opening.wallSegmentIndex();
            hash = 31 * hash + Long.hashCode(Double.doubleToLongBits(opening.positionRatio()));
            hash = 31 * hash + opening.floor();
            hash = 31 * hash + opening.width();
            hash = 31 * hash + opening.height();
            hash = 31 * hash + opening.bottomOffset();
        }
        return hash;
    }

    private static int canopiesFingerprint(List<Canopy> items) {
        if (items == null || items.isEmpty()) {
            return 0;
        }
        int hash = items.size();
        for (Canopy canopy : items) {
            hash = 31 * hash + canopy.wallSegmentIndex;
            hash = 31 * hash + Long.hashCode(Double.doubleToLongBits(canopy.positionRatio));
            hash = 31 * hash + canopy.floor;
            hash = 31 * hash + canopy.width;
            hash = 31 * hash + canopy.depth;
            hash = 31 * hash + canopy.clearance;
            hash = 31 * hash + Objects.hashCode(canopy.material);
        }
        return hash;
    }

    private static int balconiesFingerprint(List<Balcony> items) {
        if (items == null || items.isEmpty()) {
            return 0;
        }
        int hash = items.size();
        for (Balcony balcony : items) {
            hash = 31 * hash + balcony.wallSegmentIndex;
            hash = 31 * hash + Long.hashCode(Double.doubleToLongBits(balcony.positionRatio));
            hash = 31 * hash + balcony.floor;
            hash = 31 * hash + balcony.width;
            hash = 31 * hash + balcony.depth;
            hash = 31 * hash + Objects.hashCode(balcony.slabMaterial);
            hash = 31 * hash + Objects.hashCode(balcony.railingMaterial);
        }
        return hash;
    }

    private static int pointFingerprint(Vec2d point) {
        if (point == null) {
            return 0;
        }
        long bitsX = Double.doubleToLongBits(point.x);
        long bitsY = Double.doubleToLongBits(point.y);
        return (int) (bitsX ^ (bitsX >>> 32) ^ bitsY ^ (bitsY >>> 32));
    }

    /**
     * 转为分层 {@link com.plot.plugin.building.model.spec.BuildingDefinition}。
     */
    public com.plot.plugin.building.model.spec.BuildingDefinition toDefinition() {
        return com.plot.plugin.building.model.spec.BuildingDefinition.fromFootprint(this);
    }

    public static double signedArea(List<Vec2d> points) {
        if (points == null || points.size() < 3) {
            return 0.0;
        }
        double area = 0.0;
        int n = points.size();
        for (int i = 0; i < n; i++) {
            Vec2d a = points.get(i);
            Vec2d b = points.get((i + 1) % n);
            area += a.x * b.y - b.x * a.y;
        }
        return area / 2.0;
    }

    private static List<Vec2d> copyPoints(List<Vec2d> points) {
        List<Vec2d> copy = new ArrayList<>();
        if (points != null) {
            for (Vec2d point : points) {
                copy.add(point != null ? point.copy() : new Vec2d(0, 0));
            }
        }
        return copy;
    }
}
