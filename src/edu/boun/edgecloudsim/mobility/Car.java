package edu.boun.edgecloudsim.mobility;

import java.util.*;

public class Car {

    private final int id;
    private final NavigableMap<Double, RoadNode> destinationMap = new TreeMap<>();
    private final Map<Integer, Map<Integer, Integer>> roadVisitCount = new HashMap<>();//from -> to -> count


    public Car(int id) {
        this.id = id;
    }

    public void addDestination(double time, RoadNode destination) {
        destinationMap.put(time, destination);
        Map.Entry<Double, RoadNode> fromEntry = destinationMap.lowerEntry(time);
        if (fromEntry == null)  {
            return;
        }
        RoadNode from = fromEntry.getValue();
        roadVisitCount
                .computeIfAbsent(from.getId(), (k) -> new HashMap<>())
                .compute(destination.getId(), (k, v) -> v == null ? 1 : v + 1);
    }

    public Map.Entry<Double, RoadNode> getFrom(double time) {
        return destinationMap.floorEntry(time);
    }
    public Map.Entry<Double, RoadNode> getDestination(double time) {
        return destinationMap.higherEntry(time);
    }

    public int getVisitCount(int from, int to) {
        return roadVisitCount.getOrDefault(from, Collections.emptyMap()).getOrDefault(to, 0);
    }


}
