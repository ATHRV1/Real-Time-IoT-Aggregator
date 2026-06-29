package com.iotaggregator.core.model;

/**
 * An immutable snapshot of calculated statistics for a specific sensor.
 * Captured under a Read Lock to guarantee data consistency at a specific point in time.
 */
public final class MetricSnapshot {
    private final String sensorId;
    private final SensorType sensorType;
    private final long count;
    private final double mean;
    private final double min;
    private final double max;
    private final double standardDeviation;
    private final String unit;
    private final long lastUpdated;

    public MetricSnapshot(String sensorId, SensorType sensorType, long count, double mean, 
                          double min, double max, double standardDeviation, String unit, long lastUpdated) {
        this.sensorId = sensorId;
        this.sensorType = sensorType;
        this.count = count;
        this.mean = mean;
        this.min = min;
        this.max = max;
        this.standardDeviation = standardDeviation;
        this.unit = unit;
        this.lastUpdated = lastUpdated;
    }

    public String getSensorId() {
        return sensorId;
    }

    public SensorType getSensorType() {
        return sensorType;
    }

    public long getCount() {
        return count;
    }

    public double getMean() {
        return mean;
    }

    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }

    public double getStandardDeviation() {
        return standardDeviation;
    }

    public String getUnit() {
        return unit;
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    @Override
    public String toString() {
        return String.format("MetricSnapshot[ID=%s, Count=%d, Avg=%.2f, Range=[%.2f - %.2f], StdDev=%.2f %s]",
                sensorId, count, mean, min, max, standardDeviation, unit);
    }
}
