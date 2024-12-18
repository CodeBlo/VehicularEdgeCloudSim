package edu.boun.edgecloudsim.mobility;

import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

public class Car {

    private final int id;
    private final NavigableMap<Double, RoadNode> destinationMap = new TreeMap<>();


    public Car(int id) {
        this.id = id;
    }

    public void addDestination(double time, RoadNode location) {
        destinationMap.put(time, location);
    }

    public Map.Entry<Double, RoadNode> getFrom(double time) {
        return destinationMap.floorEntry(time);
    }
    public Map.Entry<Double, RoadNode> getDestination(double time) {
        return destinationMap.higherEntry(time);
    }


}
