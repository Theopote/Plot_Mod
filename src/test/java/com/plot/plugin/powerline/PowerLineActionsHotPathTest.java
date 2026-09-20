package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.powerline.design.PoleDesignResolver;
import com.plot.plugin.powerline.engineering.TerrainCollisionAnalysis;
import com.plot.plugin.powerline.model.PowerLineDesignProject;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.PowerLineProject;
import com.plot.plugin.powerline.model.PowerLineProjectHistory;
import com.plot.plugin.powerline.path.ClosedLoopLayoutException;
import com.plot.plugin.powerline.path.PowerLinePathLayout;
import com.plot.plugin.powerline.style.PowerLineStylePresetCatalog;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UI 热路径回归：预览不改 footprint、地形修正才入撤销栈、换绑失败回滚、清预览清报告。
 */
class PowerLineActionsHotPathTest {

    @Test
    void previewGenerationDoesNotMutateFootprint() {
        PowerLineFootprint line = WireTestSupport.horizontalLine(40.0);
        PowerLineStylePresetCatalog.classicWood().apply(line);
        PowerLineProject project = projectWith(line);
        String before = project.toJson();

        PowerLineGeneratorWireTest.generate(line);

        assertEquals(before, project.toJson());
    }

    @Test
    void previewGenerationDoesNotGrowWorkspaceHistory() {
        PowerLineProjectHistory history = new PowerLineProjectHistory();
        PowerLineFootprint line = WireTestSupport.horizontalLine(30.0);

        PowerLineGeneratorWireTest.generate(line);

        assertFalse(history.canUndo());
    }

    @Test
    void terrainFitOnlyRunsWhenTerrainIssuesPresent() {
        TerrainSampler flat = TerrainTestFixtures.flatTerrain(64);
        PowerLineFootprint flatLine = WireTestSupport.horizontalLine(60.0);
        PowerLineStylePresetCatalog.classicWood().apply(flatLine);
        flatLine.setMaxPoleSpacing(80.0);
        flatLine.setSagRatio(0.03);
        PowerLineGenerationResult flatResult = TerrainTestFixtures.generate(flatLine, flat);
        TerrainCollisionAnalysis flatReport = TerrainTestFixtures.analyze(flatResult, flat);
        assertFalse(flatReport.hasIssues());

        TerrainSampler rolling = TerrainTestFixtures.rollingHill(64, 74, 30.0, 8.0);
        PowerLineFootprint hillLine = TerrainTestFixtures.rollingHillLine();
        PowerLineGenerationResult hillResult = TerrainTestFixtures.generate(hillLine, rolling);
        TerrainCollisionAnalysis hillReport = TerrainTestFixtures.analyze(hillResult, rolling);
        assertTrue(hillReport.hasIssues());
    }

    @Test
    void failedSnapshotApplyPreservesFootprint() {
        PolylineShape triangle = new PolylineShape(
            List.of(new Vec2d(0, 0), new Vec2d(40, 0), new Vec2d(20, 30)),
            true);
        PolylineShape degenerate = new PolylineShape(
            List.of(new Vec2d(0, 0), new Vec2d(20, 0), new Vec2d(40, 0)),
            true);
        PowerLineFootprint footprint = PowerLinePathLayout.adopt(triangle, IdentityCoordinateService.INSTANCE);

        int towerCountBefore = footprint.getPathPoints().size();

        assertThrows(
            ClosedLoopLayoutException.class,
            () -> PowerLinePathLayout.applySnapshot(
                footprint,
                degenerate,
                IdentityCoordinateService.INSTANCE));

        assertEquals(towerCountBefore, footprint.getPathPoints().size());
        assertNull(footprint.getSourceDescriptor());
    }

    @Test
    void terrainFixMutatesFootprintOnlyWhenApplyOneFixRuns() {
        PowerLineFootprint line = TerrainTestFixtures.rollingHillLine();
        TerrainSampler terrain = TerrainTestFixtures.rollingHill(64, 74, 30.0, 8.0);
        PowerLineProject project = projectWith(line);
        String before = project.toJson();

        PoleDesignResolver resolver = new PoleDesignResolver(new PowerLineDesignProject());
        PowerLineGenerationResult result = PowerLineGeneratorWireTest.createGenerator()
            .generate(line, terrain, resolver);
        TerrainCollisionAnalysis report = TerrainTestFixtures.analyze(result, terrain);
        assertTrue(report.hasIssues());

        assertEquals(before, project.toJson());

        assertTrue(TerrainTestFixtures.applyTerrainFix(line, terrain, 1));
        assertNotEquals(project.toJson(), before);
    }

    private static PowerLineProject projectWith(PowerLineFootprint line) {
        PowerLineProject project = new PowerLineProject();
        project.addLine(line);
        return project;
    }
}
