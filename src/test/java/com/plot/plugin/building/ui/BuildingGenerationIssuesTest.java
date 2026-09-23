package com.plot.plugin.building.ui;

import com.plot.api.world.WorldProjectionSnapshot;
import com.plot.plugin.building.BuildingBlockCountCache;
import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.plugin.building.generation.DistrictGenerationResult;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.building.model.BuildingProject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.plot.plugin.building.generation.DistrictGenerationResultTestSupport.building;
import static com.plot.plugin.building.generation.DistrictGenerationResultTestSupport.districtWithClosePair;
import static com.plot.plugin.building.generation.DistrictGenerationResultTestSupport.districtWithOverlapPair;
import static com.plot.plugin.building.generation.DistrictGenerationResultTestSupport.districtWithSkippedAndWarning;
import static com.plot.plugin.building.generation.DistrictGenerationResultTestSupport.offsetBuilding;
import static com.plot.plugin.building.generation.DistrictGenerationResultTestSupport.resultWithWarnings;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingGenerationIssuesTest {
    private static final BuildingBlockCountCache BLOCK_COUNT_CACHE = new BuildingBlockCountCache();
    private static final WorldProjectionSnapshot PROJECTION = WorldProjectionSnapshot.UNKNOWN;

    @Test
    void collectDistrictSkippedOnlyWhenTerrainAndOverlapSuppressed() {
        DistrictGenerationResult district = districtWithSkippedAndWarning();

        List<BuildingGenerationIssues.Issue> issues = BuildingGenerationIssues.collect(
            null,
            district,
            null,
            null,
            true,
            Map.of(),
            BLOCK_COUNT_CACHE,
            PROJECTION);

        assertEquals(1, issues.size());
        assertTrue(issues.stream().anyMatch(issue ->
            issue.kind() == BuildingGenerationIssues.Kind.SKIPPED
                && issue.severity() == BuildingGenerationIssues.Severity.ERROR
                && issue.primaryBuildingId().equals("bad")));
    }

    @Test
    void collectDistrictTooCloseButNotOverlapping() {
        BuildingProject project = new BuildingProject();
        project.addBuilding(offsetBuilding("near", 0, 0, 10));
        project.addBuilding(offsetBuilding("far", 11, 0, 10));
        DistrictGenerationResult district = districtWithClosePair("near", "far", 1.0);

        List<BuildingGenerationIssues.Issue> issues = BuildingGenerationIssues.collect(
            project,
            district,
            null,
            null,
            true,
            Map.of(),
            BLOCK_COUNT_CACHE,
            PROJECTION);

        assertEquals(1, issues.size());
        assertEquals(BuildingGenerationIssues.Kind.TOO_CLOSE, issues.getFirst().kind());
        assertEquals(BuildingGenerationIssues.Severity.WARNING, issues.getFirst().severity());
    }

    @Test
    void overlappingBuildingsAreNotFlaggedAsTooClose() {
        BuildingProject project = new BuildingProject();
        project.addBuilding(offsetBuilding("a", 0, 0, 10));
        project.addBuilding(offsetBuilding("b", 5, 0, 10));
        DistrictGenerationResult district = districtWithOverlapPair("a", "b");

        List<BuildingGenerationIssues.Issue> issues = BuildingGenerationIssues.collect(
            project,
            district,
            null,
            null,
            true,
            Map.of(),
            BLOCK_COUNT_CACHE,
            PROJECTION);

        assertTrue(issues.stream().noneMatch(issue -> issue.kind() == BuildingGenerationIssues.Kind.TOO_CLOSE));
    }

    @Test
    void collectSingleBuildingSuppressesTerrainWarnings() {
        BuildingGenerationResult single = resultWithWarnings("plugin.building.warn.steep_site");
        BuildingPreviewIdentity identity = BuildingPreviewIdentity.capture(List.of(building("tower", 12)));

        List<BuildingGenerationIssues.Issue> issues = BuildingGenerationIssues.collect(
            null,
            null,
            single,
            identity,
            false,
            Map.of(),
            BLOCK_COUNT_CACHE,
            PROJECTION);

        assertEquals(0, issues.size());
    }

    @Test
    void collectSingleBuildingFlagsExcessiveHeightAndArea() {
        BuildingProject project = new BuildingProject();
        BuildingFootprint huge = offsetBuilding("huge", 0, 0, 200);
        huge.setFloors(30);
        huge.setFloorHeight(10);
        project.addBuilding(huge);

        BuildingGenerationResult single = resultWithWarnings();
        BuildingPreviewIdentity identity = BuildingPreviewIdentity.capture(List.of(huge));
        int footprintBlocks = BLOCK_COUNT_CACHE.blockCount(huge, PROJECTION);

        List<BuildingGenerationIssues.Issue> issues = BuildingGenerationIssues.collect(
            project,
            null,
            single,
            identity,
            false,
            Map.of(),
            BLOCK_COUNT_CACHE,
            PROJECTION);

        assertEquals(2, issues.size());
        assertTrue(issues.stream().anyMatch(issue ->
            issue.kind() == BuildingGenerationIssues.Kind.EXCESSIVE_HEIGHT));
        assertTrue(issues.stream().anyMatch(issue ->
            issue.kind() == BuildingGenerationIssues.Kind.EXCESSIVE_AREA
                && issue.messageArg().equals(Integer.toString(footprintBlocks))));
        assertTrue(footprintBlocks > BuildingGenerationIssues.MAX_WARNING_AREA_BLOCKS);
    }

    @Test
    void previewedAndWarningBuildingIds() {
        BuildingProject project = new BuildingProject();
        project.addBuilding(offsetBuilding("ok", 0, 0, 10));
        project.addBuilding(offsetBuilding("overlap", 11, 0, 10));
        DistrictGenerationResult district = districtWithClosePair("ok", "overlap", 1.0);
        List<BuildingGenerationIssues.Issue> districtIssues = BuildingGenerationIssues.collect(
            project, district, null, null, true, Map.of(), BLOCK_COUNT_CACHE, PROJECTION);

        assertEquals(
            Set.of("ok", "overlap"),
            BuildingGenerationIssues.previewedBuildingIds(district, ""));
        assertEquals(
            Set.of("ok", "overlap"),
            BuildingGenerationIssues.warningBuildingIds(districtIssues));
    }
}
