package com.plot.plugin.earthwork.overlay;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.RegionGeometry;
import com.plot.core.geometry.polygon.PolygonNormalizer;
import com.plot.core.geometry.polygon.PolygonTriangulator;
import com.plot.core.geometry.polygon.RegionTriangulator;
import com.plot.ui.canvas.CanvasCamera;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按区域 ID + 几何版本缓存世界坐标三角形；画布平移/缩放只做屏幕投影。
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

    /** 测试用：当前缓存条目数。 */
    static int sizeForTest() {
        synchronized (CACHE) {
            return CACHE.size();
        }
    }

    private static CachedMesh resolveMesh(EarthworkRegionOverlayEntry entry) {
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
        TriangulationPlan plan = planTriangulation(entry.toGeometry());
        if (plan == null || !plan.result.success() || plan.vertices.isEmpty()) {
            return null;
        }
        List<WorldTriangle> triangles = new ArrayList<>(plan.result.triangles().size());
        for (PolygonTriangulator.Triangle triangle : plan.result.triangles()) {
            if (triangle.i0() < 0 || triangle.i1() < 0 || triangle.i2() < 0
                || triangle.i0() >= plan.vertices.size()
                || triangle.i1() >= plan.vertices.size()
                || triangle.i2() >= plan.vertices.size()) {
                continue;
            }
            Vec2d a = plan.vertices.get(triangle.i0());
            Vec2d b = plan.vertices.get(triangle.i1());
            Vec2d c = plan.vertices.get(triangle.i2());
            if (a == null || b == null || c == null) {
                continue;
            }
            // 多洞回退：丢弃重心落在孔洞内的三角形
            if (!entry.containsWorldPoint(
                    (a.x + b.x + c.x) / 3.0,
                    (a.y + b.y + c.y) / 3.0)) {
                continue;
            }
            triangles.add(new WorldTriangle(a, b, c));
        }
        return new CachedMesh(entry.geometryVersion(), List.copyOf(triangles));
    }

    /**
     * 与 {@link PolygonTriangulator#triangulate} / {@link RegionTriangulator} 对齐：
     * 返回结果中的三角形索引对应规范化后的顶点列表。
     */
    static TriangulationPlan planTriangulation(RegionGeometry geometry) {
        if (geometry == null || geometry.isEmpty()) {
            return null;
        }
        if (!geometry.hasHoles()) {
            List<Vec2d> vertices = PolygonNormalizer.normalizeOutline(geometry.outerRing());
            return new TriangulationPlan(vertices, PolygonTriangulator.triangulate(vertices));
        }
        List<Vec2d> hole = geometry.holes().getFirst();
        List<Vec2d> bridged = RegionTriangulator.bridgeSingleHole(geometry.outerRing(), hole);
        if (bridged.size() >= 3) {
            List<Vec2d> vertices = PolygonNormalizer.normalizeOutline(bridged);
            PolygonTriangulator.TriangulationResult bridgedResult = PolygonTriangulator.triangulate(vertices);
            if (bridgedResult.success()) {
                return new TriangulationPlan(vertices, bridgedResult);
            }
        }
        // 桥接失败时回退：三角化外轮廓，由重心过滤剔除孔洞内三角形
        List<Vec2d> outer = PolygonNormalizer.normalizeOutline(geometry.outerRing());
        return new TriangulationPlan(outer, PolygonTriangulator.triangulate(outer));
    }

    record TriangulationPlan(List<Vec2d> vertices, PolygonTriangulator.TriangulationResult result) {
    }

    private record WorldTriangle(Vec2d a, Vec2d b, Vec2d c) {
    }

    private record CachedMesh(long geometryVersion, List<WorldTriangle> worldTriangles) {
    }
}
