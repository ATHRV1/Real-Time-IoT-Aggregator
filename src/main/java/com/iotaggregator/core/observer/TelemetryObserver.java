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
     * @param packet   the immutable telemetry packet that was processed
     * @param snapshot the updated, thread-safe snapshot of the running metrics for this sensor
     */
    void onTelemetryReceived(TelemetryPacket packet, MetricSnapshot snapshot);
}
