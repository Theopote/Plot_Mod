package com.plot.plugin.road;

import com.plot.core.context.ApplicationContext;
import com.plot.core.context.PluginContext;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.model.Shape;
import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.manager.RoadProjectStatus;
import com.plot.plugin.road.manager.RoadToolManager;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadPathPickCancelTest {

    private static RoadToolManager toolManager() {
        ApplicationContext applicationContext = ApplicationContext.getInstance();
        PluginContext host = new PluginContext(
            applicationContext.getAppState(),
            applicationContext.getCommandService(),
            applicationContext.getEventBus(),
            applicationContext.getToolManager(),
            IdentityCoordinateService.INSTANCE,
            null,
            null,
            null);
        return new RoadToolManager(new RoadProjectStatus(), host);
    }

    @Test
    void pathPickSessionCancelEndsActiveState() {
        RoadPathPickSession session = new RoadPathPickSession();
        session.begin();
        assertTrue(session.isActive());
        session.cancel();
        assertFalse(session.isActive());
    }

    @Test
    void pathPickSessionCancelRestoresStartSelection() {
        LineShape path = new LineShape(new Vec2d(0, 0), new Vec2d(10, 0));
        LineShape other = new LineShape(new Vec2d(0, 5), new Vec2d(10, 5));
        ApplicationContext applicationContext = ApplicationContext.getInstance();
        applicationContext.getAppState().setSelectedShapes(List.of(other));

        RoadPathPickSession session = new RoadPathPickSession();
        session.begin(applicationContext.getAppState());
        applicationContext.getAppState().setSelectedShapes(List.of(path));

        session.cancel(applicationContext.getAppState());

        assertFalse(session.isActive());
        assertEqualsShapeIds(List.of(other), applicationContext.getAppState().getSelectedShapes());
    }

    @Test
    void cancelPathPickEndsActiveSession() {
        RoadToolManager toolManager = toolManager();
        toolManager.getPathPickSession().begin(ApplicationContext.getInstance().getAppState());
        assertTrue(toolManager.getPathPickSession().isActive());

        toolManager.cancelPathPick();

        assertFalse(toolManager.getPathPickSession().isActive());
    }

    @Test
    void escapeShortcutListenerCancelsActivePick() {
        RoadToolManager toolManager = toolManager();
        RoadPathPickEscapeShortcutListener listener = new RoadPathPickEscapeShortcutListener(toolManager);
        toolManager.getPathPickSession().begin(ApplicationContext.getInstance().getAppState());

        assertTrue(listener.onShortcutTriggered("escape"));
        assertFalse(toolManager.getPathPickSession().isActive());
        assertFalse(listener.onShortcutTriggered("escape"));
    }

    private static void assertEqualsShapeIds(List<Shape> expected, List<Shape> actual) {
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            assertTrue(expected.get(i).getId().equals(actual.get(i).getId()));
        }
    }
}
