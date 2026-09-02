package com.plot.plugin.earthwork;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.RegionGeometry;
import com.plot.plugin.earthwork.pipeline.EarthworkGenerationResult;
import com.plot.plugin.earthwork.pipeline.EarthworkPipelineContext;
import com.plot.plugin.earthwork.pipeline.EarthworkPipelines;
import com.plot.plugin.earthwork.model.CompositionPolicy;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.GradingRegion;
import com.plot.plugin.earthwork.model.GradingSurfaceMode;
import com.plot.plugin.earthwork.model.GradingZone;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.plot.plugin.earthwork.EarthworkTestFixtures.AIR;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.DIRT;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.STONE;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.generateLegacy;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.isInsideClosedRect;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.levelPadRegion;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.rectangleCellCount;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.rectangleOutline;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.rectangleTerrain;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.solidColumnSampler;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.donutZone;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.tinyCompanionZone;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.twoZoneSiteForCompose;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 解析解基准：用可手算期望验证 Solve → 方量/落地方块。
 */
class EarthworkAnalyticalBenchmarkTest {

  private static final int MIN = 0;
  private static final int MAX = 3;
  private static final int AREA = rectangleCellCount(MIN, MAX, MIN, MAX);

  @Test
  void flatTerrainSameElevationProducesZeroVolume() {
    TerrainSnapshot terrain = rectangleTerrain(MIN, MAX, MIN, MAX, 64);
    GradingRegion region = levelPadRegion(MIN, MAX, MIN, MAX, 64, false);

    EarthworkGenerationResult result = generateLegacy(region, terrain, solidColumnSampler(terrain, STONE));

    assertEquals(0L, result.volumeReport.geometricCutVolume());
    assertEquals(0L, result.volumeReport.geometricFillVolume());
    assertTrue(result.placementRecords.isEmpty());
  }

  @Test
  void flatTerrainOneBlockHigherProducesFillEqualToArea() {
    TerrainSnapshot terrain = rectangleTerrain(MIN, MAX, MIN, MAX, 64);
    GradingRegion region = levelPadRegion(MIN, MAX, MIN, MAX, 65, false);

    EarthworkGenerationResult result = generateLegacy(region, terrain, solidColumnSampler(terrain, STONE));

    assertEquals(0L, result.volumeReport.geometricCutVolume());
    assertEquals(AREA, result.volumeReport.geometricFillVolume());
    assertEquals(AREA, result.placementRecords.size());
  }

  @Test
  void flatTerrainOneBlockLowerProducesCutEqualToArea() {
    TerrainSnapshot terrain = rectangleTerrain(MIN, MAX, MIN, MAX, 64);
    GradingRegion region = levelPadRegion(MIN, MAX, MIN, MAX, 63, false);

    EarthworkGenerationResult result = generateLegacy(region, terrain, solidColumnSampler(terrain, STONE));

    assertEquals(AREA, result.volumeReport.geometricCutVolume());
    assertEquals(0L, result.volumeReport.geometricFillVolume());
    assertEquals(AREA, result.placementRecords.size());
  }

  @Test
  void symmetricTerrainAutoBalanceProducesNearEqualCutAndFill() {
    // 单行 4 格：地面 62/64/66/68 → 平衡标高 65 → 每侧挖填各 1+3=4
    TerrainSnapshot terrain = rectangleTerrain(MIN, MAX, 0, 0, (x, z) -> 62 + (x - MIN) * 2);
    GradingRegion region = levelPadRegion(MIN, MAX, 0, 0, 64, true);

    EarthworkGenerationResult result = generateLegacy(region, terrain, solidColumnSampler(terrain, STONE));

    long cut = result.volumeReport.geometricCutVolume();
    long fill = result.volumeReport.geometricFillVolume();
    assertEquals(4L, cut);
    assertEquals(4L, fill);
    assertEquals(65, result.resolvedElevationMin);
  }

  @Test
  void fixedSlopeProducesPredictableStepElevation() {
    GradingRegion region = new GradingRegion(rectangleOutline(0, 8, 0, 2));
    region.setSurfaceMode(GradingSurfaceMode.SINGLE_SLOPE_PLANE);
    region.setAutoBalance(false);
    region.setSlopeDirectionDegrees(0.0);
    region.setSlopePitchRatio(4);
    region.setSlopeAnchorCanvas(new Vec2d(0, 0));
    region.setSlopeAnchorElevation(64);
    region.setPreviewGridSize(1);

    TerrainSnapshot terrain = rectangleTerrain(0, 8, 0, 0, 64);
    EarthworkGenerationResult result = generateLegacy(region, terrain, solidColumnSampler(terrain, STONE));

    assertEquals(64, result.resolvedElevationMin);
    assertEquals(66, result.resolvedElevationMax);
    // pitch 4 沿 +X：x=2..5 填 1 格，x=6..8 填 2 格 → 共 10 方
    assertEquals(10L, result.volumeReport.geometricFillVolume());
    assertEquals(0L, result.volumeReport.geometricCutVolume());
    assertEquals(10, result.placementRecords.size());
  }

  @Test
  void threePointPlanePassesThroughControlPoints() {
    GradingRegion region = new GradingRegion(rectangleOutline(0, 10, 0, 10));
    region.setSurfaceMode(GradingSurfaceMode.THREE_POINT_PLANE);
    region.setAutoBalance(false);
    region.setPreviewGridSize(1);
    region.setThreePointControl(0, new Vec2d(0, 0), 60);
    region.setThreePointControl(1, new Vec2d(10, 0), 64);
    region.setThreePointControl(2, new Vec2d(0, 10), 62);

    TerrainSnapshot terrain = rectangleTerrain(0, 0, 0, 0, 64);
    EarthworkGenerationResult result = generateLegacy(region, terrain, solidColumnSampler(terrain, STONE));

    GradingPlane plane = GradingSurfaceResolver.resolve(
        region, terrain.centers(), terrain.groundHeights(), null).plane();
    assertEquals(60, plane.evaluateAt(0, 0));
    assertEquals(64, plane.evaluateAt(10, 0));
    assertEquals(62, plane.evaluateAt(0, 10));
    assertTrue(result.volumeReport.hasGeometricVolume());
  }

  @Test
  void regionWithHoleProducesNoBlocksInsideHole() {
    EarthworkSite site = twoZoneSiteForCompose();
    site.addZone(donutZone("donut", 9, 3, 6, 60));
    site.addZone(tinyCompanionZone("companion"));

    TerrainSnapshot terrain = rectangleTerrain(0, 9, 0, 9, 64);
    EarthworkGenerationResult result = EarthworkPipelines.create(
        null, solidColumnSampler(terrain, STONE))
        .site().execute(EarthworkPipelineContext.of(site, null, terrain, null));

    assertFalse(result.placementRecords.isEmpty());
    for (BlockPos pos : result.placementRecords.keySet()) {
      assertFalse(
          isInsideClosedRect(pos.getX(), pos.getZ(), 3, 6, 3, 6),
          () -> "hole cell should not be modified: " + pos);
    }

    DesignTerrainGrid grid = result.designTerrainGrid;
    for (int x = 3; x <= 6; x++) {
      for (int z = 3; z <= 6; z++) {
        DesignTerrainCell cell = grid.get(x, z);
        assertEquals(64, cell.targetY());
        assertTrue(cell.zoneId() == null || cell.zoneId().isBlank());
      }
    }
  }

  @Test
  void overlappingZonesResolveByPriority() {
    EarthworkSite site = new EarthworkSite();
    site.setSiteBoundary(rectangleOutline(0, 9, 0, 9));
    site.getCompositionPolicy().setBalanceScope(CompositionPolicy.BALANCE_SCOPE_PER_ZONE);

    GradingZone low = new GradingZone("low", rectangleOutline(0, 9, 0, 9));
    low.setPriority(50);
    low.getRegion().setAutoBalance(false);
    low.getRegion().setManualTargetElevation(60);
    low.getRegion().setPreviewGridSize(1);

    GradingZone high = new GradingZone("high", rectangleOutline(2, 7, 2, 7));
    high.setPriority(100);
    high.getRegion().setAutoBalance(false);
    high.getRegion().setManualTargetElevation(70);
    high.getRegion().setPreviewGridSize(1);

    site.addZone(low);
    site.addZone(high);

    List<ZoneOverlapAnalyzer.ZoneOverlap> overlaps = ZoneOverlapAnalyzer.findOverlaps(site);
    assertFalse(overlaps.isEmpty());

    TerrainSnapshot terrain = rectangleTerrain(0, 9, 0, 9, 64);
    DesignTerrainGrid grid = DesignTerrainComposer.compose(site, terrain, null).grid();
    assertEquals(70, grid.get(5, 5).targetY());
    assertEquals("high", grid.get(5, 5).zoneId());
    assertEquals(60, grid.get(1, 1).targetY());
    assertEquals("low", grid.get(1, 1).zoneId());

    String winner = overlaps.getFirst().resolveWinner(site.getCompositionPolicy());
    assertEquals("high", winner);
  }
}
