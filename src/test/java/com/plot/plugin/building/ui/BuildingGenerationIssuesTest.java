package com.plot.plugin.building.ui;

import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.plugin.building.generation.DistrictGenerationResult;
import com.plot.plugin.building.generation.DistrictGenerationResultTestSupport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.plot.plugin.building.generation.DistrictGenerationResultTestSupport.building;
import static com.plot.plugin.building.generation.DistrictGenerationResultTestSupport.districtWithOverlapPair;
import static com.plot.plugin.building.generation.DistrictGenerationResultTestSupport.districtWithSkippedAndWarning;
import static com.plot.plugin.building.generation.DistrictGenerationResultTestSupport.resultWithWarnings;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingGenerationIssuesTest {

    @Test
    void collectDistrictSkippedOverlapAndWarnings() {
        DistrictGenerationResult district = districtWithSkippedAndWarning();

        List<BuildingGenerationIssues.Issue> issues = BuildingGenerationIssues.collect(
            null,
            district,
            null,
            null,
            true);

        assertEquals(3, issues.size());
        assertTrue(issues.stream().anyMatch(issue ->
            issue.kind() == BuildingGenerationIssues.Kind.SKIPPED
                && issue.primaryBuildingId().equals("bad")));
        assertTrue(issues.stream().anyMatch(issue ->
            issue.kind() == BuildingGenerationIssues.Kind.OVERLAP));
        assertTrue(issues.stream().anyMatch(issue ->
            issue.kind() == BuildingGenerationIssues.Kind.BUILDING_WARNING
                && issue.detailKey().equals("plugin.building.warn.water_site")));
    }

    @Test
    void collectSingleBuildingWarnings() {
        BuildingGenerationResult single = resultWithWarnings("plugin.building.warn.steep_site");
        BuildingPreviewIdentity identity = BuildingPreviewIdentity.capture(List.of(building("tower", 12)));

        List<BuildingGenerationIssues.Issue> issues = BuildingGenerationIssues.collect(
            null,
            null,
            single,
            identity,
            false);

        assertEquals(1, issues.size());
        assertEquals(BuildingGenerationIssues.Kind.BUILDING_WARNING, issues.getFirst().kind());
        assertEquals("tower", issues.getFirst().primaryBuildingId());
    }

    @Test
    void previewedAndWarningBuildingIds() {
        DistrictGenerationResult district = districtWithSkippedAndWarning();
        DistrictGenerationResult overlapDistrict = districtWithOverlapPair("a", "c");

        assertEquals(
            Set.of("ok", "overlap"),
            BuildingGenerationIssues.previewedBuildingIds(district, ""));
        assertEquals(Set.of("bad", "ok", "overlap"), BuildingGenerationIssues.warningBuildingIds(
            BuildingGenerationIssues.collect(null, district, null, null, true)));
        assertEquals(
            Set.of("a", "c"),
            BuildingGenerationIssues.warningBuildingIds(
                BuildingGenerationIssues.collect(null, overlapDistrict, null, null, true)));
    }
}
