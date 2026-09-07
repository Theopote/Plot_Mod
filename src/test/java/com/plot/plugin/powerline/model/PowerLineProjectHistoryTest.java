package com.plot.plugin.powerline.model;

import com.plot.api.geometry.Vec2d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PowerLineProjectHistoryTest {

    @Test
    void undoRestoresSnapshotTakenBeforeMutation() {
        PowerLineProjectHistory history = new PowerLineProjectHistory();
        PowerLineProject project = new PowerLineProject();
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(10, 0)));
        line.setName("Line A");
        project.addLine(line);

        history.push(project);
        line.setPoleDesignId("design-1");
        line.setName("Line B");

        PowerLineProject restored = history.undo(project);
        PowerLineFootprint restoredLine = restored.getLines().get(line.getId());

        assertEquals("Line A", restoredLine.getName());
        assertNull(restoredLine.getPoleDesignId());
    }
}
