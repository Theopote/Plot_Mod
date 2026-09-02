package com.plot.plugin.earthwork;

import com.plot.plugin.earthwork.terrain.TerrainSnapshot;
import com.plot.core.command.BlockRecord;
import com.plot.core.command.commands.EarthworkGenerateCommand;
import com.plot.plugin.earthwork.pipeline.EarthworkGenerationResult;
import com.plot.plugin.earthwork.pipeline.EarthworkPipelineContext;
import com.plot.plugin.earthwork.pipeline.EarthworkPipelines;
import com.plot.plugin.earthwork.voxel.EarthworkVoxelizer;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.GradingRegion;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.plot.plugin.earthwork.EarthworkTestFixtures.AIR;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.DIRT;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.STONE;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.donutZone;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.generateLegacy;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.levelPadRegion;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.rectangleTerrain;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.solidColumnSampler;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.tinyCompanionZone;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.twoZoneSiteForCompose;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TerrainSnapshot → Solve → Generate → Apply → Undo 端到端。
 */
class EarthworkPipelineE2ETest {

  @Test
  void singleRegionPipelineUndoRestoresEveryBlock() {
    TerrainSnapshot terrain = rectangleTerrain(0, 3, 0, 3, 64);
    GradingRegion region = levelPadRegion(0, 3, 0, 3, 65, false);
    InMemoryBlockWorld world = InMemoryBlockWorld.fromTerrain(terrain, STONE);

    EarthworkGenerationResult result = EarthworkPipelines.create(null, world.sampler())
        .legacy().execute(region, null, terrain, null);

    assertEquals(16L, result.volumeReport.geometricFillVolume());
    assertEquals(16, result.placementRecords.size());

    applyAndAssertUndo(world, List.copyOf(result.placementRecords.values()));
  }

  @Test
  void sitePipelineUndoRestoresEveryBlock() {
    EarthworkSite site = twoZoneSiteForCompose();
    site.addZone(donutZone("donut", 9, 3, 6, 60));
    site.addZone(tinyCompanionZone("companion"));

    TerrainSnapshot terrain = rectangleTerrain(0, 9, 0, 9, 64);
    InMemoryBlockWorld world = InMemoryBlockWorld.fromTerrain(terrain, STONE);

    EarthworkGenerationResult result = EarthworkPipelines.create(null, world.sampler())
        .site().execute(EarthworkPipelineContext.of(site, null, terrain, null));

    assertTrue(result.siteGeneration);
    assertTrue(result.volumeReport.geometricCutVolume() > 0L);
    assertFalsePlacementRecordsInsideHole(result);

    applyAndAssertUndo(world, List.copyOf(result.placementRecords.values()));
  }

  @Test
  void mixedCutFillPipelineUndoRestoresEveryBlock() {
    TerrainSnapshot terrain = rectangleTerrain(0, 3, 0, 3, (x, z) -> 64 + (x % 2 == 0 ? 1 : -1));
    GradingRegion region = levelPadRegion(0, 3, 0, 3, 64, false);
    InMemoryBlockWorld world = InMemoryBlockWorld.fromTerrain(terrain, STONE);

    EarthworkGenerationResult result = EarthworkPipelines.create(null, world.sampler())
        .legacy().execute(region, null, terrain, null);

    assertEquals(8L, result.volumeReport.geometricCutVolume());
    assertEquals(8L, result.volumeReport.geometricFillVolume());
    assertEquals(16, result.placementRecords.size());

    applyAndAssertUndo(world, List.copyOf(result.placementRecords.values()));
  }

  private static void applyAndAssertUndo(InMemoryBlockWorld world, List<BlockRecord> records) {
    Map<BlockPos, String> before = world.snapshot();
    EarthworkGenerateCommand command = new EarthworkGenerateCommand(records, world);
    command.execute();

    for (BlockRecord record : records) {
      assertEquals(record.newBlockId, world.get(record.pos));
    }

    command.undo();

    for (Map.Entry<BlockPos, String> entry : before.entrySet()) {
      assertEquals(entry.getValue(), world.get(entry.getKey()), () -> "restore failed at " + entry.getKey());
    }
    for (BlockRecord record : records) {
      assertEquals(before.getOrDefault(record.pos, AIR), world.get(record.pos));
    }
  }

  private static void assertFalsePlacementRecordsInsideHole(EarthworkGenerationResult result) {
    for (BlockPos pos : result.placementRecords.keySet()) {
      boolean insideHole = pos.getX() >= 3 && pos.getX() <= 6 && pos.getZ() >= 3 && pos.getZ() <= 6;
      assertTrue(!insideHole, () -> "unexpected placement inside hole: " + pos);
    }
  }

  static final class InMemoryBlockWorld implements EarthworkGenerateCommand.BlockWriter {
    private final Map<BlockPos, String> blocks = new LinkedHashMap<>();

    static InMemoryBlockWorld fromTerrain(TerrainSnapshot terrain, String solidBlockId) {
      InMemoryBlockWorld world = new InMemoryBlockWorld();
      for (TerrainSnapshot.Column column : terrain.columns()) {
        for (int y = 1; y <= column.groundY(); y++) {
          world.blocks.put(new BlockPos(column.worldX(), y, column.worldZ()), solidBlockId);
        }
      }
      return world;
    }

    EarthworkVoxelizer.BlockSampler sampler() {
      return pos -> blocks.getOrDefault(pos, AIR);
    }

    String get(BlockPos pos) {
      return blocks.getOrDefault(pos, AIR);
    }

    Map<BlockPos, String> snapshot() {
      return Map.copyOf(blocks);
    }

    @Override
    public boolean setBlockAt(BlockPos pos, String blockId) {
      if (AIR.equals(blockId)) {
        blocks.remove(pos);
      } else {
        blocks.put(pos, blockId);
      }
      return true;
    }
  }
}
