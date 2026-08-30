package com.plot.plugin.road.alignment;

import com.plot.api.geometry.Vec2d;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link RoadHorizontalAlignment} 与 sidecar JSON DTO 互转。
 */
public final class HorizontalAlignmentPersistence {

    public static final class AlignmentElementData {
        public String type;
        public double length;
        public Double radius;
        public String direction;
        public Double spiralParameterA;
    }

    public static final class AlignmentData {
        public Double originX;
        public Double originY;
        public Double startBearingRadians;
        public List<AlignmentElementData> elements = new ArrayList<>();
    }

    private HorizontalAlignmentPersistence() {
    }

    public static AlignmentData toData(RoadHorizontalAlignment alignment) {
        if (alignment == null || alignment.isEmpty()) {
            return null;
        }
        AlignmentData data = new AlignmentData();
        Vec2d origin = alignment.getOrigin();
        data.originX = origin.x;
        data.originY = origin.y;
        data.startBearingRadians = alignment.getStartBearingRadians();
        for (HorizontalAlignmentElement element : alignment.getElements()) {
            AlignmentElementData elementData = new AlignmentElementData();
            elementData.type = element.getType().name();
            elementData.length = element.getLength();
            if (element.getType() == HorizontalAlignmentElementType.CIRCULAR_ARC) {
                elementData.radius = element.getRadius();
                elementData.direction = element.getDirection().name();
            } else if (element.getType() == HorizontalAlignmentElementType.SPIRAL) {
                elementData.spiralParameterA = element.getSpiralParameterA();
            }
            data.elements.add(elementData);
        }
        return data;
    }

    public static RoadHorizontalAlignment fromData(AlignmentData data) {
        if (data == null || data.elements == null || data.elements.isEmpty()) {
            return null;
        }
        Vec2d origin = new Vec2d(
            data.originX != null ? data.originX : 0.0,
            data.originY != null ? data.originY : 0.0
        );
        double bearing = data.startBearingRadians != null ? data.startBearingRadians : 0.0;
        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment();
        alignment.setOrigin(origin);
        alignment.setStartBearingRadians(bearing);
        for (AlignmentElementData elementData : data.elements) {
            if (elementData == null || elementData.type == null) {
                continue;
            }
            HorizontalAlignmentElementType type;
            try {
                type = HorizontalAlignmentElementType.valueOf(elementData.type);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            HorizontalAlignmentElement element = switch (type) {
                case TANGENT -> HorizontalAlignmentElement.tangent(elementData.length);
                case CIRCULAR_ARC -> HorizontalAlignmentElement.circularArc(
                    elementData.length,
                    elementData.radius != null ? elementData.radius : 1.0,
                    TurnDirection.fromStored(elementData.direction)
                );
                case SPIRAL -> HorizontalAlignmentElement.spiral(
                    elementData.length,
                    elementData.spiralParameterA != null ? elementData.spiralParameterA : 1.0
                );
            };
            alignment.addElement(element);
        }
        return alignment.isEmpty() ? null : alignment;
    }
}
