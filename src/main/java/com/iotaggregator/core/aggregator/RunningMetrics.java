package com.iotaggregator.core.aggregator;

import com.iotaggregator.core.model.MetricSnapshot;
import com.iotaggregator.core.model.SensorType;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

/**
 * Thread-safe accumulator for sensor metrics.
 * Uses a ReentrantReadWriteLock to allow multiple concurrent readers (e.g. REST API, SSE, logs)
 * while ensuring exclusive access for updates (writes).
 *
 * Utilizes Welford's algorithm for running variance to prevent floating-point instability.
 */
public class RunningMetrics {
    private final String sensorId;
    private final SensorType sensorType;
    private final String unit;

    // Synchronization primitives
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final ReadLock readLock = rwLock.readLock();
    private final WriteLock writeLock = rwLock.writeLock();

    // Aggregation state (guarded by rwLock)
    private long count = 0;
    private double min = Double.MAX_VALUE;
    private double max = -Double.MAX_VALUE;
    private double mean = 0.0;
    private double m2 = 0.0; // Sum of squares of differences from the current mean
    private long lastUpdated = 0;

    public RunningMetrics(String sensorId, SensorType sensorType, String unit) {
        this.sensorId = sensorId;
        this.sensorType = sensorType;
        this.unit = unit;
    }

    /**
     * Updates the running statistics with a new sensor reading.
     * Requires the Write Lock.
     *
     * @param value     the telemetry reading
     * @param timestamp the timestamp of the reading
     */
    public void update(double value, long timestamp) {
        writeLock.lock();
        try {
            count++;
            lastUpdated = timestamp;

            if (count == 1) {
                min = value;
                max = value;
                mean = value;
                m2 = 0.0;
            } else {
                if (value < min) {
                    min = value;
                }
                if (value > max) {
                    max = value;
                }

                // Welford's online variance calculation
                double oldMean = mean;
                mean += (value - oldMean) / count;
                m2 += (value - oldMean) * (value - mean);
            }
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Generates a read-only snapshot of the current state of metrics.
     * Requires the Read Lock to ensure consistency.
     *
     * @return a consistent MetricSnapshot
     */
    public MetricSnapshot getSnapshot() {
        readLock.lock();
        try {
            double standardDeviation = 0.0;
            if (count > 1) {
                double variance = m2 / (count - 1); // Sample variance
                standardDeviation = Math.sqrt(variance);
            }
            
            // If no data has been received yet, correct the defaults
            double currentMin = (count == 0) ? 0.0 : min;
            double currentMax = (count == 0) ? 0.0 : max;

            return new MetricSnapshot(
                    sensorId,
                    sensorType,
                    count,
                    mean,
                    currentMin,
                    currentMax,
                    standardDeviation,
                    unit,
                    lastUpdated
            );
        } finally {
            readLock.unlock();
        }
    }
}
