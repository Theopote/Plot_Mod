package com.plot.plugin.powerline.model;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.design.PoleDesign;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PowerLineProjectHistoryTest {

    @Test
    void undoRestoresSnapshotTakenBeforeMutation() {
        PowerLineProjectHistory history = new PowerLineProjectHistory();
        PowerLineProject project = new PowerLineProject();
        PowerLineDesignProject designProject = new PowerLineDesignProject();
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(10, 0)));
        line.setName("Line A");
        project.addLine(line);

        history.push(project, designProject);
        line.setPoleDesignId("design-1");
        line.setName("Line B");

        PowerLineWorkspaceSnapshot restored = history.undo(project, designProject);
        PowerLineFootprint restoredLine = restored.project().getLines().get(line.getId());

        assertEquals("Line A", restoredLine.getName());
        assertNull(restoredLine.getPoleDesignId());
    }

    @Test
    void undoRestoresSavedPoleDesign() {
        PowerLineProjectHistory history = new PowerLineProjectHistory();
        PowerLineProject project = new PowerLineProject();
        PowerLineDesignProject designProject = new PowerLineDesignProject();
        PoleDesign design = new PoleDesign("custom/tower", "Original Tower");
        designProject.addDesign(design);

        history.push(project, designProject);

        PoleDesign updated = design.copy();
        updated.setName("Updated Tower");
        designProject.addDesign(updated);

        PowerLineWorkspaceSnapshot restored = history.undo(project, designProject);

        assertEquals("Original Tower", restored.designProject().getDesign("custom/tower").getName());
    }
}
