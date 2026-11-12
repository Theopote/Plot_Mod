package com.plot.plugin.road.pipeline.corridor;
import com.plot.api.geometry.Vec2d;
import com.plot.core.command.BlockRecord;
import com.plot.plugin.road.*;
import com.plot.plugin.road.model.*;
import com.plot.plugin.road.solid.*;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import java.util.*;
class LoftDebugDumpTest {
  @Test void dump() throws Exception {
    Vec2d center = new Vec2d(6, 6); Vec2d tStart = new Vec2d(2, 6); Vec2d tEnd = new Vec2d(6, 2);
    double sweep = FilletArcSweep.resolveShort(center, tStart, tEnd);
    var fillet = new JunctionGeometryResult.FilletCorner(center, tStart, tEnd, 4.0, Math.PI/2.0, sweep);
    RoadNetwork network = new RoadNetwork();
    Road nsRoad = network.createRoad("ns"); nsRoad.setWidth(7);
    Road ewRoad = network.createRoad("ew"); ewRoad.setWidth(7);
    RoadNode junction = network.createNode(new Vec2d(0,0));
    RoadNode north = network.createNode(new Vec2d(0,40));
    RoadNode east = network.createNode(new Vec2d(40,0));
    network.createEdge(junction.getId(), north.getId(), List.of(junction.getPosition(), north.getPosition()), nsRoad.getId());
    network.createEdge(junction.getId(), east.getId(), List.of(junction.getPosition(), east.getPosition()), ewRoad.getId());
    RoadEdge ns = network.getEdges().values().stream().filter(e -> e.getRoadId().equals(nsRoad.getId())).findFirst().orElseThrow();
    RoadEdge ew = network.getEdges().values().stream().filter(e -> e.getRoadId().equals(ewRoad.getId())).findFirst().orElseThrow();
    RoadGenerationResult nsResult = new RoadGenerationResult(0);
    for (int z=2; z<=14; z++) put(nsResult,2,z,64);
    RoadGenerationResult ewResult = new RoadGenerationResult(0);
    for (int x=2; x<=14; x++) put(ewResult,x,2,72);
    put(ewResult,2,4,90); put(ewResult,2,5,90); put(nsResult,4,2,40); put(nsResult,5,2,40);
    System.out.println("ewHas(2,2) layer="+ewResult.placementLayers.get(new BlockPos(2,72,2))+" rec="+ewResult.placementRecords.get(new BlockPos(2,72,2)));
    System.out.println("profile="+ewResult.buildProfile+" active="+(ewResult.buildProfile!=null && ewResult.buildProfile.isActive()));
    var loft = JunctionFilletElevationLoft.build(List.of(fillet), Map.of(ns.getId(), nsResult, ew.getId(), ewResult),
      List.copyOf(network.getEdges().values()), network,
      new RoadTerrainClearanceUtils.BlockColumnResolver(){ public int worldX(Vec2d p){return (int)Math.floor(p.x);} public int worldZ(Vec2d p){return (int)Math.floor(p.y);} }, 64);
    var cornersField = JunctionFilletElevationLoft.class.getDeclaredField("corners");
    cornersField.setAccessible(true);
    Object corner = ((List<?>) cornersField.get(loft)).get(0);
    System.out.println("curbB="+corner.getClass().getRecordComponents()[5].getAccessor().invoke(corner));
    // Call index via reflection
    Method index = JunctionFilletElevationLoft.class.getDeclaredMethod("indexRoadSurfaceYByEdge", Map.class);
    index.setAccessible(true);
    @SuppressWarnings("unchecked")
    Map<String, Map<ColumnKey,Integer>> byEdge = (Map<String, Map<ColumnKey,Integer>>) index.invoke(null, Map.of(ns.getId(), nsResult, ew.getId(), ewResult));
    System.out.println("indexed ew (2,2)="+byEdge.get(ew.getId()).get(new ColumnKey(2,2)));
    System.out.println("indexed ew (2,4)="+byEdge.get(ew.getId()).get(new ColumnKey(2,4)));
    System.out.println("indexed ew keys near="+byEdge.get(ew.getId()).keySet().stream().filter(k->Math.abs(k.worldX()-2)<=2&&Math.abs(k.worldZ()-2)<=2).toList());
  }
  static void put(RoadGenerationResult r,int x,int z,int y){ BlockPos p=new BlockPos(x,y,z); r.placementRecords.put(p,new BlockRecord(p,"minecraft:air","minecraft:stone")); r.placementLayers.put(p,RoadSolidLayer.ROAD);} 
}
