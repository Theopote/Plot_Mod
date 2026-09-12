package com.plot.plugin.powerline.ui.tower;

import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.parametric.StructureDensity;
import com.plot.plugin.powerline.design.parametric.TowerBuildEnvelope;
import com.plot.plugin.powerline.design.parametric.TowerParametricEditor;
import com.plot.plugin.powerline.design.parametric.TowerParameterProfiles;
import com.plot.plugin.powerline.design.parametric.TowerParameterSet;
import com.plot.plugin.powerline.design.structure.TowerSilhouette;
import com.plot.plugin.powerline.design.structure.TowerStructureDesign;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** UI-level parametric edit state flow (no ImGui). */
class TowerDesignerParameterControllerTest {

    @Test
    void heightChangeUpdatesVerticalGeometryOnly() {
        PoleDesign design = classicDesign();
        double originalTop = design.getTowerStructure().maxHeight();
        double originalArmSpan = design.getGeneratorConfig().parameters().armSpan();
        double originalBaseWidth = design.getGeneratorConfig().parameters().baseWidth();

        TowerDesignerParameterController.applyParametricChange(
            design,
            null,
            source -> new TowerParameterSet(
                48.0,
                source.baseWidth(),
                source.armSpan(),
                source.depthScale(),
                source.waistRatio(),
                source.armLevelScales(),
                source.density()));

        assertTrue(design.getTowerStructure().maxHeight() > originalTop);
        assertEquals(48.0, design.getGeneratorConfig().parameters().height(), 0.01);
        assertEquals(originalArmSpan, design.getGeneratorConfig().parameters().armSpan(), 0.01);
        assertEquals(originalBaseWidth, design.getGeneratorConfig().parameters().baseWidth(), 0.01);
    }

    @Test
    void baseWidthChangeDoesNotChangeArmSpan() {
        PoleDesign design = classicDesign();
        double originalArmSpan = design.getGeneratorConfig().parameters().armSpan();
        double originalBottomWidth = bottomHalfWidth(design);

        TowerDesignerParameterController.applyParametricChange(
            design,
            null,
            source -> new TowerParameterSet(
                source.height(),
                16.0,
                source.armSpan(),
                source.depthScale(),
                source.waistRatio(),
                source.armLevelScales(),
                source.density()));

        assertEquals(16.0, design.getGeneratorConfig().parameters().baseWidth(), 0.01);
        assertEquals(originalArmSpan, design.getGeneratorConfig().parameters().armSpan(), 0.01);
        assertNotEquals(originalBottomWidth, bottomHalfWidth(design), 0.01);
    }

    @Test
    void armSpanChangeUpdatesArmReach() {
        PoleDesign design = classicDesign();
        double originalReach = design.getTowerStructure().getArms().getFirst().getLateralReach();

        TowerDesignerParameterController.applyParametricChange(
            design,
            null,
            source -> new TowerParameterSet(
                source.height(),
                source.baseWidth(),
                30.0,
                source.depthScale(),
                source.waistRatio(),
                source.armLevelScales(),
                source.density()));

        assertEquals(30.0, design.getGeneratorConfig().parameters().armSpan(), 0.01);
        assertTrue(design.getTowerStructure().getArms().getFirst().getLateralReach() > originalReach);
    }

    @Test
    void densityChangePreservesSilhouette() {
        PoleDesign design = classicDesign();
        TowerSilhouette silhouette = design.getTowerStructure().getSilhouette();
        double top = design.getTowerStructure().maxHeight();

        TowerDesignerParameterController.applyParametricChange(
            design,
            null,
            source -> new TowerParameterSet(
                source.height(),
                source.baseWidth(),
                source.armSpan(),
                source.depthScale(),
                source.waistRatio(),
                source.armLevelScales(),
                StructureDensity.HIGH));

        assertEquals(TowerSilhouette.DOUBLE_ARM, design.getTowerStructure().getSilhouette());
        assertEquals(silhouette, design.getTowerStructure().getSilhouette());
        assertEquals(top, design.getTowerStructure().maxHeight(), 0.5);
    }

    @Test
    void profileSwitchRebuildsAttachmentTopology() {
        PoleDesign design = classicDesign();
        int classicAttachments = design.getAttachments().size();

        TowerParametricEditor.enableParametricTripleArm(design, TowerParameterSet.tripleArmDefaults());

        assertEquals(TowerParameterProfiles.TRIPLE_ARM_ID, design.getGeneratorConfig().profileId());
        assertNotEquals(classicAttachments, design.getAttachments().size());
        assertEquals(8, design.getAttachments().size());
    }

    @Test
    void constraintErrorBlocksBuild() {
        PoleDesign design = classicDesign();
        TowerBuildEnvelope envelope = new TowerBuildEnvelope(-64, 320, 280.0, 4);

        TowerDesignerParameterController.ApplyResult result = TowerDesignerParameterController.applyParametricChange(
            design,
            envelope,
            source -> new TowerParameterSet(
                52.0,
                source.baseWidth(),
                source.armSpan(),
                source.depthScale(),
                source.waistRatio(),
                source.armLevelScales(),
                source.density()));

        assertTrue(result.constraintResult().hasErrors());
        assertFalse(TowerDesignerParameterController.canBuild(result.constraintResult()));
    }

    @Test
    void convertToManualPreservesGeometry() {
        PoleDesign design = classicDesign();
        TowerStructureDesign before = copyStructureSnapshot(design.getTowerStructure());

        TowerParametricEditor.convertToManual(design);

        assertFalse(design.isParametricMode());
        assertEquals(before.maxHeight(), design.getTowerStructure().maxHeight(), 0.01);
        assertEquals(before.getStations().size(), design.getTowerStructure().getStations().size());
        assertEquals(before.getArms().size(), design.getTowerStructure().getArms().size());
    }

    private static PoleDesign classicDesign() {
        PoleDesign design = new PoleDesign("ui-test", "UI Test");
        TowerParametricEditor.enableParametricClassic(design, TowerParameterSet.classicDefaults());
        return design;
    }

    private static double bottomHalfWidth(PoleDesign design) {
        return design.getTowerStructure().sortedStations().getFirst().getHalfWidth();
    }

    private static TowerStructureDesign copyStructureSnapshot(TowerStructureDesign structure) {
        PoleDesign scratch = new PoleDesign("snap", "Snap");
        scratch.setTowerStructure(structure);
        return scratch.getTowerStructure();
    }
}
