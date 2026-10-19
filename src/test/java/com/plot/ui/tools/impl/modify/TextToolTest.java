package com.plot.ui.tools.impl.modify;

import com.plot.api.geometry.Vec2d;
import com.plot.api.graphics.IShapeStyle;
import com.plot.api.graphics.ITextStyle;
import com.plot.api.model.ICanvas;
import com.plot.api.model.ILayer;
import com.plot.api.model.IShape;
import com.plot.core.context.ApplicationContext;
import com.plot.core.geometry.shapes.TextShape;
import com.plot.core.graphics.style.TextStyle;
import com.plot.core.state.AppState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextToolTest {

    private static final int MOUSE_LEFT = 0;
    private static final int ESC_KEY = KeyEvent.VK_ESCAPE;

    private AppState appState;
    private StubCanvas canvas;
    private TextTool tool;

    @BeforeEach
    void setUp() throws Exception {
        appState = ApplicationContext.getInstance().getAppState();
        appState.ensureDefaultLayer();
        canvas = new StubCanvas();
        tool = new TextTool(canvas);
        setUseDialog(false);
        tool.activate();
    }

    @Test
    void dialogModeIdleClickIsIgnored() throws Exception {
        setUseDialog(true);

        boolean handled = tool.onMouseDown(new Vec2d(5, 5), MOUSE_LEFT);

        assertFalse(handled);
        assertEquals("IDLE", getCurrentStateName());
    }

    @Test
    void defaultTextModeStartsPlacingOnFirstClick() throws Exception {
        boolean handled = tool.onMouseDown(new Vec2d(0, 0), MOUSE_LEFT);

        assertTrue(handled);
        assertEquals("PLACING", getCurrentStateName());
        assertNotNull(getPreviewText());
        assertEquals("crosshair", canvas.getCursor());
    }

    @Test
    void mouseMoveUpdatesPreviewPosition() throws Exception {
        beginPlacingMode("Hello", createDefaultTextStyle());
        tool.onMouseMove(new Vec2d(1, 2));

        TextShape preview = getPreviewText();
        assertNotNull(preview);
        assertEquals(1.0, preview.getPosition().x, 1e-6);
        assertEquals(2.0, preview.getPosition().y, 1e-6);
    }

    @Test
    void secondClickCommitsConvertedGraphicsToActiveLayer() throws Exception {
        int before = appState.getShapes().size();
        beginPlacingMode("A", createDefaultTextStyle());
        tool.onMouseMove(new Vec2d(12, 8));

        boolean handled = tool.onMouseDown(new Vec2d(12, 8), MOUSE_LEFT);

        assertTrue(handled);
        assertTrue(appState.getShapes().size() > before);
        assertEquals("IDLE", getCurrentStateName());
    }

    @Test
    void escapeDuringPlacingClearsPendingState() throws Exception {
        beginPlacingMode("Cancel me", createDefaultTextStyle());
        tool.onMouseMove(new Vec2d(3, 4));

        boolean handled = tool.onKeyDown(ESC_KEY);

        assertTrue(handled);
        assertEquals("IDLE", getCurrentStateName());
        assertNull(getPreviewText());
        assertNull(getPendingText());
    }

    private void setUseDialog(boolean enabled) throws Exception {
        Field field = TextTool.class.getDeclaredField("useDialog");
        field.setAccessible(true);
        field.setBoolean(tool, enabled);
    }

    private void beginPlacingMode(String text, TextStyle style) throws Exception {
        Method method = TextTool.class.getDeclaredMethod("beginPlacingMode", String.class, TextStyle.class);
        method.setAccessible(true);
        method.invoke(tool, text, style);
    }

    private TextStyle createDefaultTextStyle() throws Exception {
        Method method = TextTool.class.getDeclaredMethod("createDefaultTextStyle");
        method.setAccessible(true);
        return (TextStyle) method.invoke(tool);
    }

    private String getCurrentStateName() throws Exception {
        Field field = TextTool.class.getDeclaredField("currentState");
        field.setAccessible(true);
        return field.get(tool).toString();
    }

    private TextShape getPreviewText() throws Exception {
        Field field = TextTool.class.getDeclaredField("previewText");
        field.setAccessible(true);
        return (TextShape) field.get(tool);
    }

    private String getPendingText() throws Exception {
        Field field = TextTool.class.getDeclaredField("pendingText");
        field.setAccessible(true);
        return (String) field.get(tool);
    }

    private static final class StubCanvas implements ICanvas {
        private String cursor = "default";
        private final Vec2d origin = new Vec2d(0, 0);

        @Override
        public List<ILayer> getLayers() {
            return List.of();
        }

        @Override
        public void addLayer(ILayer layer) {
        }

        @Override
        public void removeLayer(ILayer layer) {
        }

        @Override
        public ILayer getCurrentLayer() {
            return null;
        }

        @Override
        public void setCurrentLayer(ILayer layer) {
        }

        @Override
        public Vec2d getOrigin() {
            return origin;
        }

        @Override
        public void setOrigin(Vec2d origin) {
        }

        @Override
        public double getScale() {
            return 1.0;
        }

        @Override
        public void setScale(double scale) {
        }

        @Override
        public double getRotation() {
            return 0.0;
        }

        @Override
        public void setRotation(double rotation) {
        }

        @Override
        public Vec2d worldToCanvas(Vec2d worldPoint) {
            return worldPoint;
        }

        @Override
        public Vec2d canvasToWorld(Vec2d canvasPoint) {
            return canvasPoint;
        }

        @Override
        public ITextStyle getCurrentTextStyle() {
            return null;
        }

        @Override
        public void setCurrentTextStyle(ITextStyle style) {
        }

        @Override
        public IShapeStyle getCurrentShapeStyle() {
            return null;
        }

        @Override
        public void setCurrentShapeStyle(IShapeStyle style) {
        }

        @Override
        public void setCursor(String cursorType) {
            cursor = cursorType;
        }

        @Override
        public String getCursor() {
            return cursor;
        }

        @Override
        public void addShape(IShape shape) {
        }

        @Override
        public void removeShape(IShape shape) {
        }

        @Override
        public void clear() {
        }

        @Override
        public void refresh() {
        }

        @Override
        public List<? extends IShape> getShapes() {
            return List.of();
        }

        @Override
        public Vec2d screenToWorld(Vec2d screenPos) {
            return screenPos;
        }

        @Override
        public Vec2d worldToScreen(Vec2d worldPos) {
            return worldPos;
        }
    }
}
