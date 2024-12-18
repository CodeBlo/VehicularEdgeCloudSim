package edu.boun.edgecloudsim.mobility;

import java.util.ArrayList;
import java.util.List;

public class RoadNode {
    private final int id;
    private final String name;
    private final int x;
    private final int y;
    private final int servingWlanId;
    private final List<RoadNode> neighbours;


    public RoadNode(int id, String name, int x, int y, int servingWlanId) {
        this.id = id;
        this.name = name;
        this.x = x;
        this.y = y;
        this.servingWlanId = servingWlanId;
        this.neighbours = new ArrayList<>();
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }


    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public List<RoadNode> getNeighbours() {
        return neighbours;
    }

    public void addNeighbour(RoadNode neighbour) {
        neighbours.add(neighbour);
    }

    public int getServingWlanId() {
        return servingWlanId;
    }
}
