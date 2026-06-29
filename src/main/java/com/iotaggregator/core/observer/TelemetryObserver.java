package com.iotaggregator.core.observer;

import com.iotaggregator.core.model.MetricSnapshot;
import com.iotaggregator.core.model.TelemetryPacket;

/**
 * Observer contract in our custom Observer Pattern implementation.
 * Implementing classes can listen to real-time events published by the DataAggregator.
 */
public interface TelemetryObserver {
    /**
     * Called when a new telemetry packet has been successfully processed and aggregated.
     *
     * @param packet         the immutable telemetry packet that was processed
     * @param sensorSnapshot the updated snapshot of running metrics for this specific sensor ID
     * @param typeSnapshot   the updated snapshot of running metrics for this entire sensor type combined
     */
    void onTelemetryReceived(TelemetryPacket packet, MetricSnapshot sensorSnapshot, MetricSnapshot typeSnapshot);
}
