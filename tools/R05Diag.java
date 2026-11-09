import com.plot.plugin.road.FilletArcSweep;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.RoadJunctionGenerator;
import com.plot.plugin.road.golden.*;
import com.plot.plugin.road.JunctionGeometryResult;
import com.plot.api.geometry.Vec2d;

import java.util.*;

public class R05Diag {
  public static void main(String[] args) {
    RoadGoldenScenario s = RoadGoldenScenarioFactory.r05FiveWay();
    RoadGoldenMetrics actual = RoadGoldenHarness.generate(s);
    RoadGoldenMetrics exp = RoadGoldenExpectations.R05;
    System.out.println("EXPECTED: " + fmt(exp));
    System.out.println("ACTUAL:   " + fmt(actual));
    System.out.println("DIFF fields:");
    diff("surfaceBlocks", exp.surfaceBlocks(), actual.surfaceBlocks());
    diff("fillBlocks", exp.fillBlocks(), actual.fillBlocks());
    diff("cutBlocks", exp.cutBlocks(), actual.cutBlocks());
    diff("markingBlocks", exp.markingBlocks(), actual.markingBlocks());
    diff("bridgeBlocks", exp.bridgeBlocks(), actual.bridgeBlocks());
    diff("tunnelBlocks", exp.tunnelBlocks(), actual.tunnelBlocks());
    diff("junctionBlocks", exp.junctionBlocks(), actual.junctionBlocks());
    diff("cutVolume", exp.cutVolume(), actual.cutVolume());
    diff("fillVolume", exp.fillVolume(), actual.fillVolume());
    diff("placementRecords", exp.placementRecords(), actual.placementRecords());
    diff("bridgeCount", exp.bridgeCount(), actual.bridgeCount());
    diff("tunnelCount", exp.tunnelCount(), actual.tunnelCount());
  }
  static String fmt(RoadGoldenMetrics m) {
    return String.format("%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d",
      m.surfaceBlocks(), m.fillBlocks(), m.cutBlocks(), m.markingBlocks(),
      m.bridgeBlocks(), m.tunnelBlocks(), m.junctionBlocks(), m.cutVolume(),
      m.fillVolume(), m.placementRecords(), m.bridgeCount(), m.tunnelCount());
  }
  static void diff(String name, int e, int a) {
    if (e != a) System.out.println("  " + name + ": expected=" + e + " actual=" + a + " delta=" + (a-e));
    else System.out.println("  " + name + ": MATCH " + a);
  }
}
