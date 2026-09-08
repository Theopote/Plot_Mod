package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.PlacementReadiness;
import com.plot.api.world.WorldViewBounds;
import com.plot.plugin.powerline.design.PoleDesignResolver;
import com.plot.plugin.powerline.engineering.TerrainAvoidance;
import com.plot.plugin.powerline.engineering.analysis.LineEngineeringReport;
import com.plot.plugin.powerline.model.PowerLineDesignProject;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.PowerPoleSite;
import com.plot.plugin.powerline.style.PowerLineStylePresetCatalog;
import com.plot.plugin.road.terrain.TerrainSampler;
import net.minecraft.util.math.BlockPos;

/** Terrain Avoidance 测试用地形与生成辅助。 */
public final class TerrainTestFixtures {

    private TerrainTestFixtures() {
    }

    public static TerrainSampler flatTerrain(int y) {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d planPoint) {
                return y;
            }

            @Override
            public boolean isSolidBlock(int worldX, int blockY, int worldZ) {
                return blockY <= y;
            }
        };
    }

    /** 路径中点附近隆起的小丘（线性坡面）。 */
    public static TerrainSampler rollingHill(int baseY, int peakY, double peakCenterX, double halfWidth) {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d planPoint) {
                if (planPoint == null || halfWidth <= 0) {
                    return baseY;
                }
                double dx = Math.abs(planPoint.x - peakCenterX);
                if (dx >= halfWidth) {
                    return baseY;
                }
                double t = 1.0 - dx / halfWidth;
                return baseY + (int) Math.round((peakY - baseY) * t);
            }

            @Override
            public boolean isSolidBlock(int worldX, int blockY, int worldZ) {
                return false;
            }
        };
    }

    /** 两端高、中间低的谷地（V 形剖面）。 */
    public static TerrainSampler valley(int rimY, int floorY, double centerX, double halfWidth) {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d planPoint) {
                if (planPoint == null || halfWidth <= 0) {
                    return rimY;
                }
                double dx = Math.abs(planPoint.x - centerX);
                if (dx >= halfWidth) {
                    return rimY;
                }
                double t = dx / halfWidth;
                return floorY + (int) Math.round((rimY - floorY) * t);
            }

            @Override
            public boolean isSolidBlock(int worldX, int blockY, int worldZ) {
                return false;
            }
        };
    }

    /** 在 plan X 处阶跃的悬崖。 */
    public static TerrainSampler cliff(int lowY, int highY, double cliffPlanX) {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d planPoint) {
                return planPoint != null && planPoint.x >= cliffPlanX ? highY : lowY;
            }

            @Override
            public boolean isSolidBlock(int worldX, int blockY, int worldZ) {
                return false;
            }
        };
    }

    public static PowerLineGenerationResult generate(PowerLineFootprint line, TerrainSampler terrain) {
        return PowerLineGeneratorWireTest.createGenerator().generate(
            line,
            terrain,
            new PoleDesignResolver(new PowerLineDesignProject()));
    }

    public static LineEngineeringReport analyze(PowerLineGenerationResult result, TerrainSampler terrain) {
        return TerrainAvoidance.analyzeCollisions(result.toGeometryModel(), terrain);
    }

    public static double minimumClearance(PowerLineGenerationResult result, TerrainSampler terrain) {
        LineEngineeringReport report = analyze(result, terrain);
        return report.getSpans().stream()
            .mapToDouble(span -> span.getMinimumGroundClearance())
            .filter(value -> !Double.isInfinite(value) && !Double.isNaN(value))
            .min()
            .orElse(Double.MAX_VALUE);
    }

    /**
     * 模拟 UI {@code autoAdjustTerrain} 的修正循环。
     *
     * @return 是否在尝试次数内消除地形碰撞
     */
    public static boolean applyTerrainFix(PowerLineFootprint line, TerrainSampler terrain, int maxAttempts) {
        PoleDesignResolver resolver = new PoleDesignResolver(new PowerLineDesignProject());
        PowerLineGenerator generator = PowerLineGeneratorWireTest.createGenerator();
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            PowerLineGenerationResult result = generator.generate(line, terrain, resolver);
            LineEngineeringReport report = TerrainAvoidance.analyzeCollisions(result.toGeometryModel(), terrain);
            if (!TerrainAvoidance.hasTerrainIssues(report)) {
                return true;
            }
            if (!TerrainAvoidance.applyOneFix(line, report, result, resolver)) {
                return false;
            }
        }
        return !TerrainAvoidance.hasTerrainIssues(
            analyze(generator.generate(line, terrain, resolver), terrain));
    }

    public static PowerLineGenerationResult mockTwoPoleResult(
            PowerLineFootprint line,
            String startId,
            String endId,
            double endStation) {
        PowerLineGenerationResult result = new PowerLineGenerationResult(line);
        PowerPoleSite start = new PowerPoleSite(startId, new Vec2d(0, 0));
        start.setStationing(0.0);
        PowerPoleSite end = new PowerPoleSite(endId, new Vec2d(endStation, 0));
        end.setStationing(endStation);
        result.poleSites.add(start);
        result.poleSites.add(end);
        result.poleCount = 2;
        return result;
    }

    public static PowerLineFootprint rollingHillLine() {
        PowerLineFootprint line = WireTestSupport.horizontalLine(60.0);
        line.setMaxPoleSpacing(80.0);
        PowerLineStylePresetCatalog.classicWood().apply(line);
        line.setSagRatio(0.15);
        return line;
    }

    public static ICoordinateService identityCoordinates() {
        return new ICoordinateService() {
            @Override
            public Vec2d canvasToMinecraftWorld(Vec2d canvasPos) {
                return canvasPos;
            }

            @Override
            public WorldViewBounds getMinecraftWorldViewBounds() {
                return new WorldViewBounds(0, 200, 0, 200);
            }
        };
    }

    public static IBlockProjectionService projection() {
        return new IBlockProjectionService() {
            @Override
            public String getBlockIdAt(BlockPos pos) {
                return "minecraft:air";
            }

            @Override
            public boolean setBlockAt(BlockPos pos, String blockId) {
                return true;
            }

            @Override
            public PlacementReadiness checkWorldModificationReadiness() {
                return PlacementReadiness.ok();
            }
        };
    }
}
