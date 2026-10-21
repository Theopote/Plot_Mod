package com.plot.plugin.earthwork.overlay;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.RegionGeometry;
import com.plot.core.geometry.polygon.PolygonNormalizer;
import com.plot.core.geometry.polygon.PolygonTriangulator;
import com.plot.core.geometry.polygon.PolygonUtils;
import com.plot.core.geometry.polygon.RegionHoleClipTriangulator;
import com.plot.ui.canvas.CanvasCamera;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按区域 ID + 几何版本缓存世界坐标三角形；画布平移/缩放只做屏幕投影。
 * <p>
 * 多孔洞走 {@link RegionHoleClipTriangulator}（外环耳切 + 孔外半平面裁剪）；
 * 失败则不填充（仅轮廓），绝不使用重心过滤冒充正确性。
 */
public final class EarthworkRegionTriangulationCache {
    private static final int MAX_ENTRIES = 128;

    private static final Map<String, CachedMesh> CACHE = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CachedMesh> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    private EarthworkRegionTriangulationCache() {
    }

    public record ScreenTriangle(float ax, float ay, float bx, float by, float cx, float cy) {
    }

    public record WorldTriangle(Vec2d a, Vec2d b, Vec2d c) {
        public double absoluteArea() {
            return PolygonUtils.absoluteArea(List.of(a, b, c));
        }

        static WorldTriangle from(RegionHoleClipTriangulator.WorldTriangle t) {
            return new WorldTriangle(t.a(), t.b(), t.c());
        }
    }

    public static List<ScreenTriangle> resolveScreenTriangles(
            EarthworkRegionOverlayEntry entry,
            CanvasCamera camera) {
        if (entry == null || camera == null || entry.outerPoints().size() < 3) {
            return List.of();
        }
        CachedMesh mesh = resolveMesh(entry);
        if (mesh == null || mesh.worldTriangles.isEmpty()) {
            return List.of();
        }
        List<ScreenTriangle> screen = new ArrayList<>(mesh.worldTriangles.size());
        for (WorldTriangle triangle : mesh.worldTriangles) {
            Vec2d a = camera.worldToScreen(triangle.a);
            Vec2d b = camera.worldToScreen(triangle.b);
            Vec2d c = camera.worldToScreen(triangle.c);
            screen.add(new ScreenTriangle(
                (float) a.x, (float) a.y,
                (float) b.x, (float) b.y,
                (float) c.x, (float) c.y));
        }
        return screen;
    }

    public static List<WorldTriangle> resolveWorldTriangles(EarthworkRegionOverlayEntry entry) {
        CachedMesh mesh = resolveMesh(entry);
        if (mesh == null) {
            return List.of();
        }
        return mesh.worldTriangles;
    }

    public static void invalidate(String regionId) {
        if (regionId == null || regionId.isBlank()) {
            return;
        }
        synchronized (CACHE) {
            CACHE.remove(regionId);
        }
    }

    public static void clear() {
        synchronized (CACHE) {
            CACHE.clear();
        }
    }

    static int sizeForTest() {
        synchronized (CACHE) {
            return CACHE.size();
        }
    }

    private static CachedMesh resolveMesh(EarthworkRegionOverlayEntry entry) {
        if (entry == null || entry.outerPoints().size() < 3) {
            return null;
        }
        synchronized (CACHE) {
            CachedMesh cached = CACHE.get(entry.regionId());
            if (cached != null && cached.geometryVersion == entry.geometryVersion()) {
                return cached;
            }
            CachedMesh built = buildMesh(entry);
            if (built != null) {
                CACHE.put(entry.regionId(), built);
            } else {
                CACHE.remove(entry.regionId());
            }
            return built;
        }
    }

    private static CachedMesh buildMesh(EarthworkRegionOverlayEntry entry) {
        List<WorldTriangle> triangles = triangulateGeometry(entry.toGeometry());
        if (triangles.isEmpty()) {
            return null;
        }
        return new CachedMesh(entry.geometryVersion(), List.copyOf(triangles));
    }

    /**
     * 无孔：标准耳切；有孔：外环耳切 + 孔外裁剪。失败返回空（安全降级为轮廓）。
     */
    static List<WorldTriangle> triangulateGeometry(RegionGeometry geometry) {
        if (geometry == null || geometry.isEmpty()) {
            return List.of();
        }
        if (!geometry.hasHoles()) {
            List<Vec2d> vertices = PolygonNormalizer.normalizeOutline(geometry.outerRing());
            PolygonTriangulator.TriangulationResult result = PolygonTriangulator.triangulate(vertices);
            if (!result.success()) {
                return List.of();
            }
            List<WorldTriangle> triangles = new ArrayList<>(result.triangles().size());
            for (PolygonTriangulator.Triangle t : result.triangles()) {
                triangles.add(new WorldTriangle(
                    vertices.get(t.i0()), vertices.get(t.i1()), vertices.get(t.i2())));
            }
            return triangles;
        }
        List<RegionHoleClipTriangulator.WorldTriangle> clipped =
            RegionHoleClipTriangulator.triangulate(geometry);
        if (clipped.isEmpty()) {
            return List.of();
        }
        List<WorldTriangle> triangles = new ArrayList<>(clipped.size());
        for (RegionHoleClipTriangulator.WorldTriangle t : clipped) {
            triangles.add(WorldTriangle.from(t));
        }
        return triangles;
    }

    private record CachedMesh(long geometryVersion, List<WorldTriangle> worldTriangles) {
    }
}
