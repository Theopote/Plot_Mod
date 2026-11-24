import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.golden.RoadGoldenScenarioFactory;
import com.plot.plugin.road.model.section.CenterLineStyle;
import com.plot.plugin.road.model.section.Median;
import com.plot.plugin.road.model.section.ResolvedCrossSection;

var scenario = RoadGoldenScenarioFactory.r01StraightFlat();
var road = scenario.network().getRoads().values().iterator().next();
road.getCrossSection().getMarkings().setCenterLineStyle(CenterLineStyle.DOUBLE_SOLID);
road.getCrossSection().setMedian(new Median(true, 2, 3, "minecraft:stone_bricks"));
var resolved = ResolvedCrossSection.resolve(road.getCrossSection(), scenario.config());
System.out.println("lanes="+resolved.laneCount+" includeMedian="+resolved.includeMedian+" medianW="+resolved.medianWidth+" min="+resolved.medianMinLateral+" max="+resolved.medianMaxLateral+" style="+resolved.centerLineStyle+" offsets="+resolved.laneDividerOffsets);
