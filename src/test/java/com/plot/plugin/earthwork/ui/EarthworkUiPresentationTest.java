package com.plot.plugin.earthwork.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.BlockRecord;
import com.plot.plugin.earthwork.manager.EarthworkPreviewState;
import com.plot.plugin.earthwork.model.EarthworkProject;
import com.plot.plugin.earthwork.model.EarthworkStatusLevel;
import com.plot.plugin.earthwork.model.GradingRegion;
import com.plot.plugin.earthwork.pipeline.EarthworkGenerationResult;
import com.plot.plugin.earthwork.terrain.TerrainSnapshot;
import com.plot.plugin.earthwork.volume.EarthworkVolumeReport;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class EarthworkUiPresentationTest {
    private GradingRegion region() {
        return new GradingRegion("pad", List.of(new Vec2d(0, 0), new Vec2d(3, 0),
            new Vec2d(3, 1), new Vec2d(0, 1)));
    }

    @Test void manualHeightCanBeInitializedWithoutCalculatingPreview() {
        var terrain = TerrainSnapshot.forColumns(List.of(
            new TerrainSnapshot.Column(new Vec2d(.5,.5), 0, 0, 64, "", false),
            new TerrainSnapshot.Column(new Vec2d(1.5,.5), 1, 0, 80),
            new TerrainSnapshot.Column(new Vec2d(2.5,.5), 2, 0, 82)));
        assertEquals(82, EarthworkUiPresentation.initialManualHeight(region(), null, false, terrain));
    }

    @Test void missingTerrainDoesNotInventY64() {
        assertNull(EarthworkUiPresentation.initialManualHeight(region(), null, false, TerrainSnapshot.empty()));
    }

    @Test void anotherRegionsOrStalePreviewCannotSeedManualHeight() {
        var region = region();
        region.setManualTargetElevation(90);
        var preview = new EarthworkGenerationResult();
        preview.sourceRegionId = "other";
        preview.resolvedElevation = 64;
        assertEquals(90, EarthworkUiPresentation.initialManualHeight(region, preview, true, TerrainSnapshot.empty()));
        preview.sourceRegionId = region.getId();
        assertEquals(90, EarthworkUiPresentation.initialManualHeight(region, preview, false, TerrainSnapshot.empty()));
        assertEquals(64, EarthworkUiPresentation.initialManualHeight(region, preview, true, TerrainSnapshot.empty()));
    }

    @Test void exactIntegerInputAcceptsWorldBoundsAndRejectsInvalidValues() {
        assertEquals(-64, EarthworkUiPresentation.parseHeight("-64", -64, 319));
        assertEquals(319, EarthworkUiPresentation.parseHeight(" 319 ", -64, 319));
        assertNull(EarthworkUiPresentation.parseHeight("320", -64, 319));
        assertNull(EarthworkUiPresentation.parseHeight("-65", -64, 319));
        assertNull(EarthworkUiPresentation.parseHeight("", -64, 319));
        assertNull(EarthworkUiPresentation.parseHeight("-", -64, 319));
        assertNull(EarthworkUiPresentation.parseHeight("1.5", -64, 319));
        assertNull(EarthworkUiPresentation.parseHeight("999999999999999999", -64, 319));
        assertNull(EarthworkUiPresentation.parseHeight(null, -64, 319));
        assertEquals(600, EarthworkUiPresentation.parseHeight("600", -128, 1023));
    }

    @Test void manualAndStaleResultsAreNotLabeledAsRecommendations() {
        assertEquals("plugin.earthwork.platform_height", EarthworkUiPresentation.elevationLabel(false, true));
        assertEquals("plugin.earthwork.ui.last_height", EarthworkUiPresentation.elevationLabel(true, false));
        assertEquals("plugin.earthwork.ui.last_height", EarthworkUiPresentation.elevationLabel(false, false));
        assertEquals("plugin.earthwork.quick.recommended_y", EarthworkUiPresentation.elevationLabel(true, true));
    }

    @Test void confirmationUsesFinalPlacementsIncludingWallsRatherThanEarthworkVolume() {
        var preview = new EarthworkGenerationResult();
        preview.volumeReport = new EarthworkVolumeReport(10, 10, 1, 1);
        preview.structureChangedBlocks = 1;
        for (int x = 0; x < 3; x++) {
            var pos = new BlockPos(x, 64, 0);
            preview.placementRecords.put(pos, new BlockRecord(pos, "minecraft:air", "minecraft:stone"));
        }
        assertEquals(2, preview.volumeReport.totalChangedBlocks());
        assertEquals(3, EarthworkUiPresentation.plannedChanges(preview));
        assertEquals(0, EarthworkUiPresentation.plannedChanges(null));
    }

    @Test void failureWarningsAndInformationDoNotUseSuccessColor() {
        var success = EarthworkUiPresentation.statusColor(EarthworkStatusLevel.SUCCESS);
        assertNotEquals(success, EarthworkUiPresentation.statusColor(EarthworkStatusLevel.ERROR));
        assertNotEquals(success, EarthworkUiPresentation.statusColor(EarthworkStatusLevel.WARNING));
        assertNotEquals(success, EarthworkUiPresentation.statusColor(EarthworkStatusLevel.INFO));
    }

    @Test void missingManualHeightBlocksPreviewUntilAValueIsEntered() {
        var region = region();
        region.setAutoBalance(false);
        assertTrue(EarthworkUiPresentation.missingManualHeight(region));
        region.setManualTargetElevation(0);
        assertFalse(EarthworkUiPresentation.missingManualHeight(region));
        region.setManualTargetElevation(null);
        region.setAutoBalance(true);
        assertFalse(EarthworkUiPresentation.missingManualHeight(region));
    }

    @Test void sharesSitePreviewAllowsSiblingZoneOnSameSite() {
        EarthworkProject project = new EarthworkProject();
        project.ensureActiveSite();
        GradingRegion a = new GradingRegion("zone-a", List.of(
            new Vec2d(0, 0), new Vec2d(4, 0), new Vec2d(4, 4), new Vec2d(0, 4)));
        GradingRegion b = new GradingRegion("zone-b", List.of(
            new Vec2d(6, 0), new Vec2d(10, 0), new Vec2d(10, 4), new Vec2d(6, 4)));
        project.addRegion(a);
        project.addRegion(b);
        var preview = new EarthworkGenerationResult();
        preview.sourceRegionId = a.getId();
        preview.sourceSiteId = project.getActiveSite().getId();
        assertTrue(EarthworkUiPresentation.belongsTo(preview, a));
        assertFalse(EarthworkUiPresentation.belongsTo(preview, b));
        assertTrue(EarthworkUiPresentation.sharesSitePreview(preview, project, a));
        assertTrue(EarthworkUiPresentation.sharesSitePreview(preview, project, b));
    }

    @Test void cutFillSummaryUsesAbsoluteDiffFromGeometricVolumes() {
        var summary = EarthworkUiPresentation.CutFillSummary.of(
            new EarthworkVolumeReport(320, 185, 0, 0), 72);
        assertEquals(72, summary.platformY());
        assertEquals(320, summary.cut());
        assertEquals(185, summary.fill());
        assertEquals(135, summary.diff());
        var empty = EarthworkUiPresentation.CutFillSummary.of(null, 64);
        assertEquals(64, empty.platformY());
        assertEquals(0, empty.cut());
        assertEquals(0, empty.fill());
        assertEquals(0, empty.diff());
    }
}
