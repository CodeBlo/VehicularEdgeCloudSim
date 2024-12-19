package edu.boun.edgecloudsim.mobility;

import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.utils.Location;
import edu.boun.edgecloudsim.utils.SimUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.*;

public class VehicularMobility extends MobilityModel {

    public static final int CAR_SPEED = 30; //km/h
    private final Map<Integer, Car> carMap = new HashMap<>();
    private final Map<Integer, RoadNode> roadNodeMap = generateRoadNetwork();


    public VehicularMobility(int _numberOfMobileDevices, double _simulationTime) {
        super(_numberOfMobileDevices, _simulationTime);

    }

    @Override
    public void initialize() {
        for (int i = 0; i < numberOfMobileDevices; i++) {
            Car car = new Car(i);
            int randomNode = SimUtils.getRandomNumber(0, roadNodeMap.size() - 1);
            RoadNode roadNode = roadNodeMap.get(randomNode);
            car.addDestination(SimSettings.CLIENT_ACTIVITY_START_TIME, roadNode); //start from random node. Initially car starts at this road node
            carMap.put(i, car);
        }

        for (Car car : carMap.values()) {
            double time = SimSettings.CLIENT_ACTIVITY_START_TIME;
            RoadNode from = car.getFrom(time).getValue();
            while (time < simulationTime) {
                List<RoadNode> neighbours = from.getNeighbours();
                if (neighbours.isEmpty()) {
                    break;
                }
                NavigableMap<Integer, Integer> neighbourVisitCumulativeMap = new TreeMap<>();
                int totalVisitCount = 0;
                for (int i = 0; i < neighbours.size(); i++) {
                    RoadNode neighbour = neighbours.get(i);
                    int visitCount = car.getVisitCount(from.getId(), neighbour.getId());
                    totalVisitCount += visitCount == 0 ? 1 : visitCount;
                    neighbourVisitCumulativeMap.put(totalVisitCount, i);
                }

                int randomNumber = SimUtils.getRandomNumber(1, totalVisitCount);
                int randomNeighbourIndex = neighbourVisitCumulativeMap.ceilingEntry(randomNumber).getValue();


                RoadNode neighbour = neighbours.get(randomNeighbourIndex);
                double distance = Math.sqrt(Math.pow(neighbour.getX() - from.getX(), 2) + Math.pow(neighbour.getY() - from.getY(), 2));
                double timeToReach = distance / CAR_SPEED;
                time += timeToReach;
                car.addDestination(time, neighbour);
                from = neighbour;
            }
        }


    }

    private double distanceBetweenRoadNodes(RoadNode node, RoadNode neighbour) {
        return distanceToRoadNode(node, neighbour.getX(), neighbour.getY());
    }

    private double distanceToRoadNode(RoadNode node, int x, int y) {
        return Math.sqrt(Math.pow(node.getX() - x, 2) + Math.pow(node.getY() - y, 2));
    }

    @Override
    public Location getLocation(int deviceId, double time) {
        Car car = carMap.get(deviceId);
        Map.Entry<Double, RoadNode> fromEntry  = car.getFrom(time);
        double enterTime = fromEntry.getKey();
        RoadNode from = fromEntry.getValue();

        Map.Entry<Double, RoadNode> destinationEntry = car.getDestination(time);
        if (destinationEntry == null) {
            return new Location(1, from.getServingWlanId(), from.getX(), from.getY());
        }
        double exitTime = destinationEntry.getKey();
        RoadNode destination = destinationEntry.getValue();

        double timePassed = time - enterTime;
        double distanceTraveled = timePassed * CAR_SPEED;

        double normalizedXVector = (destination.getX() - from.getX()) / distanceBetweenRoadNodes(from, destination);
        double normalizedYVector = (destination.getY() - from.getY()) / distanceBetweenRoadNodes(from, destination);

        int x = (int) (from.getX() + distanceTraveled * normalizedXVector);
        int y = (int) (from.getY() + distanceTraveled * normalizedYVector);

        double roadHalfTime = enterTime + (exitTime - enterTime) / 2;
        boolean passedHalf = time > roadHalfTime;
        return new Location(1, passedHalf ? destination.getServingWlanId() : from.getServingWlanId(), x, y);
    }


    private Map<Integer, RoadNode> generateRoadNetwork() {
        Map<Integer, Integer> wlanIdMap = getWlanIdMap();

        Document doc = SimSettings.getInstance().getRoadDocument();
        NodeList roadNodes = doc.getElementsByTagName("node");

        Map<Integer, RoadNode> roadNodeMap = new HashMap<>();
        for (int i = 0; i < roadNodes.getLength(); i++) {
            Element roadNodeElement = (Element) roadNodes.item(i);
            int id = Integer.parseInt(roadNodeElement.getAttribute("id"));
            String name = roadNodeElement.getAttribute("name");
            Element position = (Element) roadNodeElement.getElementsByTagName("position").item(0);
            int x = Integer.parseInt(position.getElementsByTagName("x").item(0).getTextContent());
            int y = Integer.parseInt(position.getElementsByTagName("y").item(0).getTextContent());
            RoadNode roadNode = new RoadNode(id, name, x, y, wlanIdMap.get(id));
            roadNodeMap.put(id, roadNode);
        }

        for (int i = 0; i < roadNodes.getLength(); i++) {
            Element roadNodeElement = (Element) roadNodes.item(i);
            int id = Integer.parseInt(roadNodeElement.getAttribute("id"));
            RoadNode roadNode = roadNodeMap.get(id);
            Element neighbors = (Element) roadNodeElement.getElementsByTagName("neighbors").item(0);
            if (neighbors == null) {
                continue;
            }
            NodeList neighborNodes = neighbors.getElementsByTagName("id");
            for (int j = 0; j < neighborNodes.getLength(); j++) {
                Element neighbourElement = (Element) neighborNodes.item(j);
                int neighbourId = Integer.parseInt(neighbourElement.getTextContent());
                RoadNode neighbour = roadNodeMap.get(neighbourId);
                roadNode.addNeighbour(neighbour);
            }
        }

        return roadNodeMap;
    }

    private Map<Integer, Integer> getWlanIdMap() {
        Document doc = SimSettings.getInstance().getEdgeDevicesDocument();
        NodeList datacenterList = doc.getElementsByTagName("datacenter");
        Map<Integer, Integer> wlanIdMap = new HashMap<>();
        for (int i = 0; i < datacenterList.getLength(); i++) {
            Element datacenterElement = (Element) datacenterList.item(i);
            Element location = (Element) datacenterElement.getElementsByTagName("location").item(0);
            int nodeId = Integer.parseInt(location.getElementsByTagName("node_id").item(0).getTextContent());
            int wlanId = Integer.parseInt(location.getElementsByTagName("wlan_id").item(0).getTextContent());
            wlanIdMap.put(nodeId, wlanId);
        }

        return wlanIdMap;
    }

    public RoadNode getRoadNodeById(int id) {
        return roadNodeMap.get(id);
    }
}
