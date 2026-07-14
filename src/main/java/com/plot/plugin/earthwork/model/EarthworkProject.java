package com.plot.plugin.earthwork.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.plot.api.geometry.Vec2d;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 土方平衡项目（管理已认领的多个整平区域）
 */
public class EarthworkProject {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<String, GradingRegion> regions = new LinkedHashMap<>();

    public Map<String, GradingRegion> getRegions() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(regions));
    }

    public GradingRegion getRegion(String id) {
        return regions.get(id);
    }

    public GradingRegion addRegion(GradingRegion region) {
        if (region == null) {
            throw new IllegalArgumentException("Grading region cannot be null");
        }
        regions.put(region.getId(), region);
        return region;
    }

    public void removeRegion(String id) {
        regions.remove(id);
    }

    public int getRegionCount() {
        return regions.size();
    }

    public double getTotalArea() {
        return regions.values().stream().mapToDouble(GradingRegion::computeArea).sum();
    }

    public String toJson() {
        return GSON.toJson(ProjectData.from(this));
    }

    public static EarthworkProject fromJson(String json) {
        if (json == null || json.isBlank()) {
            return new EarthworkProject();
        }
        ProjectData data = GSON.fromJson(json, ProjectData.class);
        return data != null ? data.toProject() : new EarthworkProject();
    }

    public void saveTo(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, toJson());
    }

    public static EarthworkProject loadFrom(Path file) throws IOException {
        if (!Files.exists(file)) {
            return new EarthworkProject();
        }
        return fromJson(Files.readString(file));
    }

    EarthworkProject deepCopy() {
        return fromJson(toJson());
    }

    static class Vec2dData {
        double x;
        double y;

        Vec2dData() {
        }

        Vec2dData(Vec2d vec) {
            this.x = vec.x;
            this.y = vec.y;
        }

        Vec2d toVec2d() {
            return new Vec2d(x, y);
        }
    }

    static class RegionData {
        String id;
        String name;
        List<Vec2dData> outerPoints = new ArrayList<>();
        String surfaceMode = GradingSurfaceMode.FLAT.name();
        boolean autoBalance = true;
        Integer manualTargetElevation;
        float fillFactor = GradingRegion.DEFAULT_FILL_FACTOR;
        String cutExposeMaterial = "";
        String fillMaterial = GradingRegion.DEFAULT_FILL_MATERIAL;
        int gridSize = GradingRegion.DEFAULT_GRID_SIZE;
        double slopeDirectionDegrees = 0.0;
        int slopePitchRatio = GradingRegion.DEFAULT_SLOPE_PITCH_RATIO;
        Double slopeAnchorCanvasX;
        Double slopeAnchorCanvasY;
        Integer slopeAnchorElevation;
        double[] threePointCanvasX = new double[3];
        double[] threePointCanvasY = new double[3];
        int[] threePointElevation = new int[] {64, 64, 64};
        boolean fitSlopeBalanceCutFill = true;
    }

    static class ProjectData {
        List<RegionData> regions = new ArrayList<>();

        static ProjectData from(EarthworkProject project) {
            ProjectData data = new ProjectData();
            for (GradingRegion region : project.regions.values()) {
                RegionData regionData = new RegionData();
                regionData.id = region.getId();
                regionData.name = region.getName();
                for (Vec2d point : region.getOuterPoints()) {
                    regionData.outerPoints.add(new Vec2dData(point));
                }
                regionData.autoBalance = region.isAutoBalance();
                regionData.manualTargetElevation = region.getManualTargetElevation();
                regionData.surfaceMode = region.getSurfaceMode().name();
                regionData.fillFactor = region.getFillFactor();
                regionData.cutExposeMaterial = region.getCutExposeMaterial();
                regionData.fillMaterial = region.getFillMaterial();
                regionData.gridSize = region.getGridSize();
                regionData.slopeDirectionDegrees = region.getSlopeDirectionDegrees();
                regionData.slopePitchRatio = region.getSlopePitchRatio();
                if (region.getSlopeAnchorCanvas() != null) {
                    regionData.slopeAnchorCanvasX = region.getSlopeAnchorCanvas().x;
                    regionData.slopeAnchorCanvasY = region.getSlopeAnchorCanvas().y;
                }
                regionData.slopeAnchorElevation = region.getSlopeAnchorElevation();
                for (int i = 0; i < 3; i++) {
                    regionData.threePointCanvasX[i] = region.getThreePointCanvasX(i);
                    regionData.threePointCanvasY[i] = region.getThreePointCanvasY(i);
                    regionData.threePointElevation[i] = region.getThreePointElevation(i);
                }
                regionData.fitSlopeBalanceCutFill = region.isFitSlopeBalanceCutFill();
                data.regions.add(regionData);
            }
            return data;
        }

        EarthworkProject toProject() {
            EarthworkProject project = new EarthworkProject();
            for (RegionData regionData : regions) {
                List<Vec2d> points = new ArrayList<>();
                for (Vec2dData pointData : regionData.outerPoints) {
                    points.add(pointData.toVec2d());
                }
                GradingRegion region = new GradingRegion(regionData.id, points);
                region.setName(regionData.name);
                region.setAutoBalance(regionData.autoBalance);
                region.setManualTargetElevation(regionData.manualTargetElevation);
                region.setSurfaceMode(GradingSurfaceMode.fromId(regionData.surfaceMode));
                region.setFillFactor(regionData.fillFactor);
                if (regionData.cutExposeMaterial != null) {
                    region.setCutExposeMaterial(regionData.cutExposeMaterial);
                }
                if (regionData.fillMaterial != null) {
                    region.setFillMaterial(regionData.fillMaterial);
                }
                region.setGridSize(regionData.gridSize);
                region.setSlopeDirectionDegrees(regionData.slopeDirectionDegrees);
                region.setSlopePitchRatio(regionData.slopePitchRatio);
                if (regionData.slopeAnchorCanvasX != null && regionData.slopeAnchorCanvasY != null) {
                    region.setSlopeAnchorCanvas(new Vec2d(regionData.slopeAnchorCanvasX, regionData.slopeAnchorCanvasY));
                }
                region.setSlopeAnchorElevation(regionData.slopeAnchorElevation);
                if (regionData.threePointCanvasX != null && regionData.threePointCanvasY != null && regionData.threePointElevation != null) {
                    for (int i = 0; i < 3; i++) {
                        region.setThreePointControl(
                            i,
                            new Vec2d(regionData.threePointCanvasX[i], regionData.threePointCanvasY[i]),
                            regionData.threePointElevation[i]);
                    }
                }
                region.setFitSlopeBalanceCutFill(regionData.fitSlopeBalanceCutFill);
                project.addRegion(region);
            }
            return project;
        }
    }
}
