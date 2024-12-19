package edu.boun.edgecloudsim.utils;

public class AverageAccumulator {
    private double sum;
    private int count;

    public AverageAccumulator() {
        sum = 0;
        count = 0;
    }

    public void add(double value) {
        sum += value;
        count++;
    }

    public double getAverage() {
        return sum / count;
    }
}
